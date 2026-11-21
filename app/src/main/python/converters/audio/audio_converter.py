"""
Audio converter for Filee.
Uses pydub with bundled FFmpeg. Supports MP3, WAV, AAC, FLAC, OGG, M4A.
"""
import os
import sys
import subprocess
from pathlib import Path
from typing import Callable, Optional


def _find_ffmpeg() -> Optional[str]:
    """Locate bundled FFmpeg or fallback to system PATH."""
    # Look for bundled ffmpeg alongside the script
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
    # Try system PATH
    try:
        result = subprocess.run(["where", "ffmpeg"], capture_output=True, text=True)
        if result.returncode == 0:
            return "ffmpeg"
    except Exception:
        pass
    return None


AUDIO_FORMATS = {"mp3", "wav", "aac", "flac", "ogg", "m4a"}


def convert_audio(
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
        if tgt not in AUDIO_FORMATS:
            return {"success": False, "error": f"Unsupported audio format: {tgt}"}

        ffmpeg_path = _find_ffmpeg()
        if not ffmpeg_path:
            return {"success": False, "error": "FFmpeg not found. Cannot convert audio. Please install FFmpeg."}

        progress(0.1, "Initializing audio engine")

        # Map format to codec
        codec_map = {
            "mp3": ["-codec:a", "libmp3lame", "-q:a", "2"],
            "aac": ["-codec:a", "aac", "-b:a", "192k"],
            "flac": ["-codec:a", "flac"],
            "ogg": ["-codec:a", "libvorbis", "-q:a", "5"],
            "wav": ["-codec:a", "pcm_s16le"],
            "m4a": ["-codec:a", "aac", "-b:a", "192k"],
        }

        progress(0.2, "Starting FFmpeg conversion")
        codec_args = codec_map.get(tgt, [])
        cmd = [ffmpeg_path, "-y", "-i", str(src)] + codec_args + [str(out)]

        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=300
        )

        progress(0.9, "Finalizing")

        if result.returncode != 0:
            error_msg = result.stderr[-500:] if result.stderr else "FFmpeg failed"
            return {"success": False, "error": f"Audio conversion failed: {error_msg}"}

        if not out.exists() or out.stat().st_size == 0:
            return {"success": False, "error": "Output file not generated"}

        progress(1.0, "Done")
        return {"success": True, "output_path": str(out)}

    except subprocess.TimeoutExpired:
        return {"success": False, "error": "Conversion timed out (file too large?)"}
    except Exception as ex:
        return {"success": False, "error": f"Audio conversion error: {str(ex)}"}
