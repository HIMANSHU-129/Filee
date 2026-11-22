import sys
from pathlib import Path
from typing import Any, Dict
from core.protocol import MessageProtocol
from core.registry import ConverterRegistry

IMAGE_FORMATS = {"png", "jpg", "jpeg", "webp", "gif", "bmp", "tiff", "ico"}
DOCUMENT_FORMATS = {"pdf", "txt", "md", "html", "docx", "csv"}
AUDIO_FORMATS = {"mp3", "wav", "aac", "flac", "ogg", "m4a"}
VIDEO_FORMATS = {"mp4", "mkv", "webm", "avi", "mov"}


class CommandDispatcher:
    """Routes IPC requests from Java to the correct conversion subsystem."""

    def __init__(self, registry: ConverterRegistry) -> None:
        self.registry = registry

    def dispatch(self, message: Dict[str, Any]) -> None:
        msg_id = message.get("id", "0")
        command = message.get("command", "")
        params = message.get("params", {})

        if command == "ping":
            MessageProtocol.send(MessageProtocol.success(msg_id, {
                "message": "pong",
                "python_version": sys.version
            }))

        elif command == "capabilities":
            MessageProtocol.send(MessageProtocol.success(msg_id, {
                "supported": self.registry.list_supported_conversions()
            }))

        elif command == "convert":
            source = params.get("source_path", "")
            target_format = params.get("target_format", "").lower().lstrip(".")
            output_path = params.get("output_path", "")
            job_id = params.get("job_id", msg_id)
            options = params.get("options", {})

            # Security check & path normalization
            source_paths = params.get("source_paths") or options.get("source_paths") or options.get("source_files")
            if source_paths and isinstance(source_paths, list):
                clean_sources = [str(Path(p).resolve()) for p in source_paths if p]
            else:
                clean_sources = [str(Path(source).resolve())] if source else []

            # Ensure output directory exists safely
            if output_path:
                out_path_obj = Path(output_path).resolve()
                out_path_obj.parent.mkdir(parents=True, exist_ok=True)

            MessageProtocol.send(MessageProtocol.progress(job_id, 0.05, "Routing conversion"))

            def on_progress(p: float, stage: str) -> None:
                MessageProtocol.send(MessageProtocol.progress(job_id, p, stage))

            # Detect source format
            src_ext = Path(source).suffix.lower().lstrip(".") if source else ""
            if not src_ext and clean_sources:
                src_ext = Path(clean_sources[0]).suffix.lower().lstrip(".")

            # Multi-image to PDF conversion
            if len(clean_sources) > 1 and target_format == "pdf":
                from converters.images.image_converter import convert_images_to_pdf
                result = convert_images_to_pdf(clean_sources, output_path, options=options, progress_callback=on_progress)

            # PDF to Image conversion
            elif src_ext == "pdf" and target_format in IMAGE_FORMATS:
                from converters.documents.document_converter import convert_document
                result = convert_document(source, output_path, target_format,
                                          progress_callback=on_progress, options=options)

            # Single Image to PDF conversion
            elif src_ext in IMAGE_FORMATS and target_format == "pdf":
                from converters.images.image_converter import convert_image
                result = convert_image(source, output_path, target_format,
                                       options=options, progress_callback=on_progress)

            # Standard Image to Image conversion
            elif src_ext in IMAGE_FORMATS and target_format in IMAGE_FORMATS:
                from converters.images.image_converter import convert_image
                result = convert_image(source, output_path, target_format,
                                       options=options, progress_callback=on_progress)

            elif src_ext in DOCUMENT_FORMATS or target_format in DOCUMENT_FORMATS:
                from converters.documents.document_converter import convert_document
                result = convert_document(source, output_path, target_format,
                                          progress_callback=on_progress, options=options)

            elif src_ext in AUDIO_FORMATS or target_format in AUDIO_FORMATS:
                from converters.audio.audio_converter import convert_audio
                result = convert_audio(source, output_path, target_format,
                                       progress_callback=on_progress)

            elif src_ext in VIDEO_FORMATS or target_format in VIDEO_FORMATS:
                from converters.video.video_converter import convert_video
                result = convert_video(source, output_path, target_format,
                                       progress_callback=on_progress)
            else:
                result = {"success": False,
                          "error": f"No converter found for: {src_ext} -> {target_format}"}

            if result.get("success"):
                MessageProtocol.send(MessageProtocol.progress(job_id, 1.0, "Complete"))
                MessageProtocol.send(MessageProtocol.success(msg_id, {
                    "output_path": result["output_path"],
                    "job_id": job_id
                }))
            else:
                MessageProtocol.send(MessageProtocol.error(
                    msg_id,
                    result.get("error", "Conversion failed"),
                    500
                ))

        elif command == "cancel":
            MessageProtocol.send(MessageProtocol.success(msg_id, {"cancelled": True}))

        else:
            MessageProtocol.send(MessageProtocol.error(
                msg_id,
                f"Unknown command: '{command}'",
                code=400
            ))
