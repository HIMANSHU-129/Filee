"""
Video converter for Filee.
Uses bundled FFmpeg for all video format conversions.
Supports MP4, MKV, WEBM, AVI, MOV, GIF.
"""
import os
import subprocess
from pathlib import Path
from typing import Callable, Optional


def _find_ffmpeg() -> Optional[str]:
    base = Path(__file__).resolve()
    candidates = [
        base.parent.parent.parent.parent / "resources" / "bin" / "ffmpeg.exe",
        base.parent.parent.parent.parent.parent / "resources" / "bin" / "ffmpeg.exe",
        Path("resources") / "bin" / "ffmpeg.exe",
        Path("bin") / "ffmpeg.exe",
    ]
    for c in candidates:
        if c.exists():
            return str(c)
    try:
        result = subprocess.run(["where", "ffmpeg"], capture_output=True, text=True)
        if result.returncode == 0:
            return "ffmpeg"
    except Exception:
        pass
    return None


VIDEO_FORMATS = {"mp4", "mkv", "webm", "avi", "mov", "gif"}


def convert_video(
    source_path: str,
    output_path: str,
    target_format: str,
    progress_callback: Optional[Callable[[float, str], None]] = None
) -> dict:
    def progress(val, stage):
        if progress_callback:
            progress_callback(val, stage)

    try:
        src = Path(source_path)
        out = Path(output_path)
        out.parent.mkdir(parents=True, exist_ok=True)

        tgt = target_format.lower().lstrip(".")
        if tgt not in VIDEO_FORMATS:
            return {"success": False, "error": f"Unsupported video format: {tgt}"}

        ffmpeg_path = _find_ffmpeg()
        if not ffmpeg_path:
            return {"success": False, "error": "FFmpeg not found. Cannot convert video. Please install FFmpeg."}

        progress(0.1, "Initializing FFmpeg")

        # Build format-specific args
        if tgt == "mp4":
            extra = ["-codec:v", "libx264", "-crf", "23", "-preset", "medium", "-codec:a", "aac"]
        elif tgt == "webm":
            extra = ["-codec:v", "libvpx-vp9", "-crf", "31", "-b:v", "0", "-codec:a", "libvorbis"]
        elif tgt == "mkv":
            extra = ["-codec:v", "libx264", "-crf", "23", "-codec:a", "aac"]
        elif tgt == "avi":
            extra = ["-codec:v", "mpeg4", "-qscale:v", "5", "-codec:a", "mp3"]
        elif tgt == "mov":
            extra = ["-codec:v", "libx264", "-crf", "23", "-codec:a", "aac", "-movflags", "+faststart"]
        elif tgt == "gif":
            # First pass: generate palette for quality GIF
            palette_path = out.with_suffix(".palette.png")
            palette_cmd = [
                ffmpeg_path, "-y", "-i", str(src),
                "-vf", "fps=10,scale=480:-1:flags=lanczos,palettegen",
                str(palette_path)
            ]
            subprocess.run(palette_cmd, capture_output=True, timeout=120)
            progress(0.5, "Generating GIF palette")
            if palette_path.exists():
                gif_cmd = [
                    ffmpeg_path, "-y", "-i", str(src), "-i", str(palette_path),
                    "-filter_complex", "fps=10,scale=480:-1:flags=lanczos[x];[x][1:v]paletteuse",
                    str(out)
                ]
                result = subprocess.run(gif_cmd, capture_output=True, text=True, timeout=300)
                palette_path.unlink(missing_ok=True)
            else:
                # Fallback: simple gif
                gif_cmd = [ffmpeg_path, "-y", "-i", str(src), "-vf", "fps=10,scale=480:-1", str(out)]
                result = subprocess.run(gif_cmd, capture_output=True, text=True, timeout=300)

            progress(0.9, "Finalizing GIF")
            if result.returncode != 0:
                return {"success": False, "error": f"GIF conversion failed: {result.stderr[-400:]}"}
            if not out.exists() or out.stat().st_size == 0:
                return {"success": False, "error": "Output GIF not generated"}
            progress(1.0, "Done")
            return {"success": True, "output_path": str(out)}
        else:
            extra = []

        cmd = [ffmpeg_path, "-y", "-i", str(src)] + extra + [str(out)]
        progress(0.2, f"Converting to {tgt.upper()}")

        result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)

        progress(0.9, "Finalizing")
        if result.returncode != 0:
            return {"success": False, "error": f"Video conversion failed: {result.stderr[-400:]}"}
        if not out.exists() or out.stat().st_size == 0:
            return {"success": False, "error": "Output file not generated"}

        progress(1.0, "Done")
        return {"success": True, "output_path": str(out)}

    except subprocess.TimeoutExpired:
        return {"success": False, "error": "Video conversion timed out (file too large?)"}
    except Exception as ex:
        return {"success": False, "error": f"Video conversion error: {str(ex)}"}
