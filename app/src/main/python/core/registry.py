from typing import Callable, Dict, List, Optional


class ConverterRegistry:
    """Maintains registered converter engines mapped by source and target formats."""

    def __init__(self) -> None:
        self._converters: Dict[str, Dict[str, Callable]] = {}
        self._register_defaults()

    def _register_defaults(self) -> None:
        # Images
        image_formats = ["png", "jpg", "jpeg", "webp", "gif", "bmp", "tiff", "ico"]
        try:
            from converters.images.image_converter import convert_image
            for src in image_formats:
                for tgt in image_formats:
                    if src != tgt:
                        self.register(src, tgt, convert_image)
                # Register image -> PDF
                self.register(src, "pdf", convert_image)
        except ImportError:
            pass

        # Documents
        doc_formats = ["pdf", "txt", "md", "html", "docx", "csv"]
        try:
            from converters.documents.document_converter import convert_document
            valid_pairs = [
                ("md", "html"), ("md", "txt"), ("md", "pdf"),
                ("html", "txt"), ("html", "pdf"),
                ("txt", "pdf"), ("txt", "md"), ("txt", "html"),
                ("docx", "txt"), ("docx", "pdf"),
                ("pdf", "txt"), ("csv", "txt"),
                ("pdf", "png"), ("pdf", "jpg"), ("pdf", "jpeg"),
                ("pdf", "webp"), ("pdf", "bmp"), ("pdf", "tiff"),
            ]
            for src, tgt in valid_pairs:
                self.register(src, tgt, convert_document)
        except ImportError:
            pass

        # Audio
        audio_formats = ["mp3", "wav", "aac", "flac", "ogg", "m4a"]
        try:
            from converters.audio.audio_converter import convert_audio
            for src in audio_formats:
                for tgt in audio_formats:
                    if src != tgt:
                        self.register(src, tgt, convert_audio)
        except ImportError:
            pass

        # Video
        video_formats = ["mp4", "mkv", "webm", "avi", "mov", "gif"]
        try:
            from converters.video.video_converter import convert_video
            for src in video_formats:
                for tgt in video_formats:
                    if src != tgt:
                        self.register(src, tgt, convert_video)
        except ImportError:
            pass

    def register(self, source_format: str, target_format: str, handler: Callable) -> None:
        src = source_format.lower().lstrip(".")
        tgt = target_format.lower().lstrip(".")
        if src not in self._converters:
            self._converters[src] = {}
        self._converters[src][tgt] = handler

    def get_handler(self, source_format: str, target_format: str) -> Optional[Callable]:
        src = source_format.lower().lstrip(".")
        tgt = target_format.lower().lstrip(".")
        return self._converters.get(src, {}).get(tgt)

    def list_supported_conversions(self) -> Dict[str, List[str]]:
        return {src: list(targets.keys()) for src, targets in self._converters.items()}
