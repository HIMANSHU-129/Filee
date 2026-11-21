"""Filee Image Converter Module.

Handles local conversion between image formats (PNG, JPG, JPEG, WEBP, GIF, BMP, TIFF, ICO)
using Pillow. Zero cloud dependencies.
"""

from pathlib import Path
from typing import Any, Callable, Dict, List, Optional
from PIL import Image, ImageOps

# Standard Pillow format mapping
FORMAT_MAP = {
    "png": "PNG",
    "jpg": "JPEG",
    "jpeg": "JPEG",
    "webp": "WEBP",
    "gif": "GIF",
    "bmp": "BMP",
    "tiff": "TIFF",
    "tif": "TIFF",
    "ico": "ICO",
    "pdf": "PDF",
}

# Standard Windows icon sizes for multi-resolution ICO files
ICO_SIZES: List[tuple[int, int]] = [
    (16, 16),
    (32, 32),
    (48, 48),
    (64, 64),
    (128, 128),
    (256, 256),
]


def convert_images_to_pdf(
    source_paths: List[str],
    output_path: str,
    options: Optional[Dict[str, Any]] = None,
    progress_callback: Optional[Callable[[float, str], None]] = None,
) -> Dict[str, Any]:
    """Converts multiple image files into a single multi-page PDF document.

    Args:
        source_paths: List of file paths to images to combine.
        output_path: Destination path for the generated PDF.
        options: Optional conversion parameters (dpi, quality, etc.).
        progress_callback: Optional progress reporter callback.

    Returns:
        Dict with "success" (bool), and either "output_path" or "error".
    """
    options = options or {}
    if not source_paths:
        return {"success": False, "error": "No source image files provided for PDF conversion"}

    dst = Path(output_path).resolve()
    dst.parent.mkdir(parents=True, exist_ok=True)

    prepared_images: List[Image.Image] = []
    total = len(source_paths)

    try:
        for idx, src_str in enumerate(source_paths):
            src_file = Path(src_str).resolve()
            if not src_file.exists():
                return {"success": False, "error": f"Image file not found: {src_str}"}

            if progress_callback:
                progress_callback(
                    0.1 + (0.7 * idx / total),
                    f"Processing image {idx + 1}/{total}: {src_file.name}",
                )

            with Image.open(src_file) as raw_img:
                try:
                    raw_img = ImageOps.exif_transpose(raw_img)
                except Exception:
                    pass

                # Convert to RGB, handling transparency
                if raw_img.mode in ("RGBA", "LA") or (
                    raw_img.mode == "P" and "transparency" in raw_img.info
                ):
                    bg = Image.new("RGB", raw_img.size, (255, 255, 255))
                    alpha_img = raw_img.convert("RGBA") if raw_img.mode != "RGBA" else raw_img
                    bg.paste(alpha_img, mask=alpha_img.split()[3])
                    img_to_add = bg
                elif raw_img.mode != "RGB":
                    img_to_add = raw_img.convert("RGB")
                else:
                    img_to_add = raw_img.copy()

                prepared_images.append(img_to_add)

        if not prepared_images:
            return {"success": False, "error": "No valid images could be loaded"}

        if progress_callback:
            progress_callback(0.85, f"Assembling {len(prepared_images)} pages into PDF")

        first_image = prepared_images[0]
        remaining = prepared_images[1:] if len(prepared_images) > 1 else []

        save_kwargs: Dict[str, Any] = {
            "resolution": float(options.get("dpi", 100.0)),
            "save_all": True,
        }
        if remaining:
            save_kwargs["append_images"] = remaining

        first_image.save(dst, format="PDF", **save_kwargs)

        if progress_callback:
            progress_callback(1.0, "PDF created successfully")

        return {
            "success": True,
            "output_path": str(dst),
            "page_count": len(prepared_images),
        }

    except Exception as e:
        return {"success": False, "error": f"Failed to create PDF from images: {str(e)}"}
    finally:
        for im in prepared_images:
            try:
                im.close()
            except Exception:
                pass


def convert_image(
    source_path: str,
    output_path: str,
    target_format: str,
    options: Optional[Dict[str, Any]] = None,
    progress_callback: Optional[Callable[[float, str], None]] = None,
) -> Dict[str, Any]:
    """Converts an image file to the target format.

    Args:
        source_path: Absolute or relative path to the input image.
        output_path: Destination path for the converted image.
        target_format: Target format extension (e.g. 'png', 'jpg', 'webp',
          'ico').
        options: Optional conversion parameters (quality, etc.).
        progress_callback: Optional callback receiving (progress: float, stage:
          str).

    Returns:
        Dict with "success" (bool), and either "output_path" or "error".
    """
    options = options or {}
    src = Path(source_path).resolve()
    dst = Path(output_path).resolve()

    if not src.exists():
        return {
            "success": False,
            "error": f"Source file does not exist: {source_path}",
        }

    clean_tgt = target_format.lower().lstrip(".")

    # If multiple source images are supplied and target is PDF, merge them into one PDF
    multi_sources = options.get("source_paths") or options.get("source_files")
    if clean_tgt == "pdf" and multi_sources and isinstance(multi_sources, list) and len(multi_sources) > 1:
        return convert_images_to_pdf(multi_sources, output_path, options=options, progress_callback=progress_callback)

    pillow_format = FORMAT_MAP.get(clean_tgt)
    if not pillow_format:
        return {
            "success": False,
            "error": f"Unsupported target image format: {target_format}",
        }

    try:
        if progress_callback:
            progress_callback(0.2, f"Opening image {src.name}")

        # Ensure parent destination directory exists
        dst.parent.mkdir(parents=True, exist_ok=True)

        with Image.open(src) as img:
            if progress_callback:
                progress_callback(
                    0.4, "Applying color and mode transformations"
                )

            # Auto-orient based on EXIF tag if present
            try:
                img = ImageOps.exif_transpose(img)
            except Exception:
                pass

            save_kwargs: Dict[str, Any] = {}

            if pillow_format == "JPEG":
                # JPEG does not support transparency; composite RGBA/LA/P on white background
                if img.mode in ("RGBA", "LA") or (
                    img.mode == "P" and "transparency" in img.info
                ):
                    bg = Image.new("RGB", img.size, (255, 255, 255))
                    alpha_img = (
                        img.convert("RGBA") if img.mode != "RGBA" else img
                    )
                    bg.paste(alpha_img, mask=alpha_img.split()[3])
                    img = bg
                elif img.mode != "RGB":
                    img = img.convert("RGB")
                save_kwargs["quality"] = int(options.get("quality", 90))
                save_kwargs["optimize"] = True

            elif pillow_format == "WEBP":
                if img.mode not in ("RGB", "RGBA"):
                    img = img.convert("RGBA" if "A" in img.mode else "RGB")
                save_kwargs["quality"] = int(options.get("quality", 90))

            elif pillow_format == "PNG":
                if img.mode not in ("RGB", "RGBA", "L", "LA", "P"):
                    img = img.convert("RGBA")
                save_kwargs["optimize"] = True

            elif pillow_format == "BMP":
                if img.mode in ("RGBA", "LA"):
                    bg = Image.new("RGB", img.size, (255, 255, 255))
                    alpha_img = (
                        img.convert("RGBA") if img.mode != "RGBA" else img
                    )
                    bg.paste(alpha_img, mask=alpha_img.split()[3])
                    img = bg
                elif img.mode != "RGB":
                    img = img.convert("RGB")

            elif pillow_format == "ICO":
                if img.mode not in ("RGBA", "RGB"):
                    img = img.convert("RGBA")
                save_kwargs["sizes"] = ICO_SIZES

            elif pillow_format == "TIFF":
                if img.mode not in ("RGB", "RGBA", "L"):
                    img = img.convert("RGB")

            elif pillow_format == "GIF":
                if img.mode not in ("P", "L", "RGB", "RGBA"):
                    img = img.convert("RGBA")

            elif pillow_format == "PDF":
                if img.mode in ("RGBA", "LA") or (
                    img.mode == "P" and "transparency" in img.info
                ):
                    bg = Image.new("RGB", img.size, (255, 255, 255))
                    alpha_img = (
                        img.convert("RGBA") if img.mode != "RGBA" else img
                    )
                    bg.paste(alpha_img, mask=alpha_img.split()[3])
                    img = bg
                elif img.mode != "RGB":
                    img = img.convert("RGB")
                save_kwargs["resolution"] = float(options.get("dpi", 100.0))

            if progress_callback:
                progress_callback(0.7, f"Encoding into {pillow_format}")

            img.save(dst, format=pillow_format, **save_kwargs)

            if progress_callback:
                progress_callback(0.95, "Finalizing converted file")

        return {"success": True, "output_path": str(dst)}

    except Exception as e:
        return {"success": False, "error": f"Image conversion error: {str(e)}"}
