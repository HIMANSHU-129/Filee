"""
Document converter for Filee.
Supports: TXT, MD, PDF, DOCX, HTML conversions using local libraries.
"""
import os
import sys
from pathlib import Path
from typing import Callable, Optional


def convert_document(
    source_path: str,
    output_path: str,
    target_format: str,
    progress_callback: Optional[Callable[[float, str], None]] = None,
    options: Optional[dict] = None
) -> dict:
    """
    Convert a document file to the target format.
    Returns {"success": True, "output_path": ...} or {"success": False, "error": ...}
    """
    def progress(val: float, stage: str):
        if progress_callback:
            progress_callback(val, stage)

    try:
        src = Path(source_path)
        out = Path(output_path)
        out.parent.mkdir(parents=True, exist_ok=True)

        src_ext = src.suffix.lower().lstrip(".")
        tgt_ext = target_format.lower().lstrip(".")

        progress(0.1, "Reading source document")

        if src_ext == "md" and tgt_ext == "html":
            result = _md_to_html(src, out, progress)
        elif src_ext == "md" and tgt_ext == "txt":
            result = _md_to_txt(src, out, progress)
        elif src_ext == "md" and tgt_ext == "pdf":
            result = _md_to_pdf(src, out, progress)
        elif src_ext == "html" and tgt_ext == "txt":
            result = _html_to_txt(src, out, progress)
        elif src_ext == "html" and tgt_ext == "pdf":
            result = _html_to_pdf(src, out, progress)
        elif src_ext == "txt" and tgt_ext == "pdf":
            result = _txt_to_pdf(src, out, progress)
        elif src_ext == "txt" and tgt_ext == "md":
            result = _txt_to_md(src, out, progress)
        elif src_ext == "txt" and tgt_ext == "html":
            result = _txt_to_html(src, out, progress)
        elif src_ext == "docx" and tgt_ext == "txt":
            result = _docx_to_txt(src, out, progress)
        elif src_ext == "docx" and tgt_ext == "pdf":
            result = _docx_to_pdf(src, out, progress)
        elif src_ext == "pdf" and tgt_ext == "txt":
            result = _pdf_to_txt(src, out, progress)
        elif src_ext == "pdf" and tgt_ext in ("png", "jpg", "jpeg", "webp", "bmp", "tiff"):
            result = _pdf_to_image(src, out, tgt_ext, progress, options)
        elif src_ext in ("png", "jpg", "jpeg", "webp", "gif", "bmp", "tiff", "ico") and tgt_ext == "pdf":
            from converters.images.image_converter import convert_image
            result = convert_image(str(src), str(out), "pdf", options=options, progress_callback=progress)
        elif src_ext in ("csv",) and tgt_ext == "txt":
            result = _csv_to_txt(src, out, progress)
        else:
            return {"success": False, "error": f"Unsupported document conversion: {src_ext} -> {tgt_ext}"}

        progress(1.0, "Done")
        return result

    except Exception as ex:
        return {"success": False, "error": f"Document conversion error: {str(ex)}"}


def _md_to_html(src: Path, out: Path, progress) -> dict:
    import markdown
    progress(0.3, "Parsing Markdown")
    text = src.read_text(encoding="utf-8")
    html = markdown.markdown(text, extensions=["extra", "toc"])
    full_html = f"""<!DOCTYPE html>
<html><head><meta charset="UTF-8"><title>{src.stem}</title>
<style>body{{font-family:Segoe UI,sans-serif;max-width:800px;margin:40px auto;line-height:1.6;color:#222}}
code{{background:#f4f4f4;padding:2px 6px;border-radius:3px}}</style></head>
<body>{html}</body></html>"""
    progress(0.8, "Writing HTML")
    out.write_text(full_html, encoding="utf-8")
    return {"success": True, "output_path": str(out)}


def _md_to_txt(src: Path, out: Path, progress) -> dict:
    import re
    progress(0.4, "Stripping Markdown")
    text = src.read_text(encoding="utf-8")
    text = re.sub(r"#{1,6}\s+", "", text)
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", text)
    text = re.sub(r"\*(.+?)\*", r"\1", text)
    text = re.sub(r"`(.+?)`", r"\1", text)
    text = re.sub(r"\[(.+?)\]\(.+?\)", r"\1", text)
    progress(0.8, "Writing")
    out.write_text(text, encoding="utf-8")
    return {"success": True, "output_path": str(out)}


def _md_to_pdf(src: Path, out: Path, progress) -> dict:
    import markdown
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import getSampleStyleSheet
    from reportlab.lib.units import cm
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer
    import re

    progress(0.3, "Parsing Markdown")
    text = src.read_text(encoding="utf-8")
    lines = text.split("\n")
    styles = getSampleStyleSheet()
    doc = SimpleDocTemplate(str(out), pagesize=A4,
                            leftMargin=2*cm, rightMargin=2*cm,
                            topMargin=2*cm, bottomMargin=2*cm)
    story = []
    for line in lines:
        line = line.rstrip()
        if line.startswith("# "):
            story.append(Paragraph(line[2:], styles["Heading1"]))
        elif line.startswith("## "):
            story.append(Paragraph(line[3:], styles["Heading2"]))
        elif line.startswith("### "):
            story.append(Paragraph(line[4:], styles["Heading3"]))
        elif line.strip() == "":
            story.append(Spacer(1, 0.3*cm))
        else:
            story.append(Paragraph(line, styles["BodyText"]))

    progress(0.8, "Building PDF")
    doc.build(story)
    return {"success": True, "output_path": str(out)}


def _html_to_txt(src: Path, out: Path, progress) -> dict:
    import re
    progress(0.4, "Stripping HTML")
    html = src.read_text(encoding="utf-8", errors="replace")
    text = re.sub(r"<[^>]+>", "", html)
    text = re.sub(r"\n{3,}", "\n\n", text)
    progress(0.8, "Writing")
    out.write_text(text.strip(), encoding="utf-8")
    return {"success": True, "output_path": str(out)}


def _html_to_pdf(src: Path, out: Path, progress) -> dict:
    import re
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import getSampleStyleSheet
    from reportlab.lib.units import cm
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer

    progress(0.3, "Parsing HTML")
    html = src.read_text(encoding="utf-8", errors="replace")
    text = re.sub(r"<[^>]+>", "", html)
    lines = [l.strip() for l in text.split("\n") if l.strip()]
    styles = getSampleStyleSheet()
    doc = SimpleDocTemplate(str(out), pagesize=A4,
                            leftMargin=2*cm, rightMargin=2*cm,
                            topMargin=2*cm, bottomMargin=2*cm)
    story = []
    for line in lines:
        story.append(Paragraph(line, styles["BodyText"]))
        story.append(Spacer(1, 0.2*cm))

    progress(0.8, "Building PDF")
    doc.build(story)
    return {"success": True, "output_path": str(out)}


def _txt_to_pdf(src: Path, out: Path, progress) -> dict:
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import getSampleStyleSheet
    from reportlab.lib.units import cm
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer

    progress(0.3, "Reading text")
    lines = src.read_text(encoding="utf-8", errors="replace").split("\n")
    styles = getSampleStyleSheet()
    doc = SimpleDocTemplate(str(out), pagesize=A4,
                            leftMargin=2*cm, rightMargin=2*cm,
                            topMargin=2*cm, bottomMargin=2*cm)
    story = []
    for line in lines:
        line = line.rstrip()
        if line:
            story.append(Paragraph(line, styles["BodyText"]))
        else:
            story.append(Spacer(1, 0.3*cm))

    progress(0.8, "Building PDF")
    doc.build(story)
    return {"success": True, "output_path": str(out)}


def _txt_to_md(src: Path, out: Path, progress) -> dict:
    progress(0.5, "Converting")
    text = src.read_text(encoding="utf-8")
    out.write_text(text, encoding="utf-8")
    return {"success": True, "output_path": str(out)}


def _txt_to_html(src: Path, out: Path, progress) -> dict:
    progress(0.4, "Converting text to HTML")
    text = src.read_text(encoding="utf-8")
    escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    html = f"""<!DOCTYPE html>
<html><head><meta charset="UTF-8"><title>{src.stem}</title>
<style>body{{font-family:Segoe UI,monospace;white-space:pre-wrap;padding:20px}}</style></head>
<body>{escaped}</body></html>"""
    progress(0.8, "Writing")
    out.write_text(html, encoding="utf-8")
    return {"success": True, "output_path": str(out)}


def _docx_to_txt(src: Path, out: Path, progress) -> dict:
    from docx import Document
    progress(0.3, "Reading DOCX")
    doc = Document(str(src))
    lines = [p.text for p in doc.paragraphs]
    progress(0.7, "Writing text")
    out.write_text("\n".join(lines), encoding="utf-8")
    return {"success": True, "output_path": str(out)}


def _docx_to_pdf(src: Path, out: Path, progress) -> dict:
    from docx import Document
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import getSampleStyleSheet
    from reportlab.lib.units import cm
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer

    progress(0.3, "Reading DOCX")
    doc = Document(str(src))
    styles = getSampleStyleSheet()
    pdf_doc = SimpleDocTemplate(str(out), pagesize=A4,
                                leftMargin=2*cm, rightMargin=2*cm,
                                topMargin=2*cm, bottomMargin=2*cm)
    story = []
    for para in doc.paragraphs:
        if para.text.strip():
            if para.style.name.startswith("Heading"):
                story.append(Paragraph(para.text, styles["Heading2"]))
            else:
                story.append(Paragraph(para.text, styles["BodyText"]))
        else:
            story.append(Spacer(1, 0.3*cm))

    progress(0.8, "Building PDF")
    pdf_doc.build(story)
    return {"success": True, "output_path": str(out)}


def _pdf_to_txt(src: Path, out: Path, progress) -> dict:
    try:
        from PyPDF2 import PdfReader
        progress(0.3, "Reading PDF pages")
        reader = PdfReader(str(src))
        pages_text = []
        total = len(reader.pages)
        for i, page in enumerate(reader.pages):
            pages_text.append(page.extract_text() or "")
            progress(0.3 + (0.5 * (i + 1) / max(total, 1)), f"Page {i+1}/{total}")
        progress(0.9, "Writing text")
        out.write_text("\n\n".join(pages_text), encoding="utf-8")
        return {"success": True, "output_path": str(out)}
    except Exception as e:
        return {"success": False, "error": f"PDF extraction failed: {str(e)}"}


def _csv_to_txt(src: Path, out: Path, progress) -> dict:
    import csv
    progress(0.4, "Reading CSV")
    rows = []
    with open(src, newline="", encoding="utf-8", errors="replace") as f:
        reader = csv.reader(f)
        for row in reader:
            rows.append("\t".join(row))
    progress(0.8, "Writing")
    out.write_text("\n".join(rows), encoding="utf-8")
    return {"success": True, "output_path": str(out)}


def _pdf_to_image(src: Path, out: Path, tgt_ext: str, progress, options: Optional[dict] = None) -> dict:
    options = options or {}
    scale = float(options.get("scale", 2.0))
    quality = int(options.get("quality", 92))

    # Primary renderer: pypdfium2 (local Chromium PDFium engine)
    try:
        import pypdfium2 as pdfium
        from PIL import Image

        doc = pdfium.PdfDocument(str(src))
        total = len(doc)
        if total == 0:
            doc.close()
            return {"success": False, "error": "PDF contains no pages to convert"}

        output_files = []
        try:
            for i, page in enumerate(doc):
                progress(0.15 + (0.75 * (i + 1) / total), f"Rendering page {i+1} of {total}")
                pil_img = page.render(scale=scale).to_pil()

                if total == 1:
                    target_file = out
                else:
                    target_file = out.parent / f"{out.stem}_page_{i+1}.{tgt_ext}"

                save_kwargs = {}
                if tgt_ext in ("jpg", "jpeg"):
                    if pil_img.mode != "RGB":
                        bg = Image.new("RGB", pil_img.size, (255, 255, 255))
                        if pil_img.mode == "RGBA":
                            bg.paste(pil_img, mask=pil_img.split()[3])
                        else:
                            bg.paste(pil_img)
                        pil_img = bg
                    save_kwargs["quality"] = quality
                    save_kwargs["optimize"] = True
                elif tgt_ext == "webp":
                    save_kwargs["quality"] = quality
                elif tgt_ext == "png":
                    save_kwargs["optimize"] = True
                elif tgt_ext == "bmp":
                    if pil_img.mode != "RGB":
                        pil_img = pil_img.convert("RGB")

                target_file.parent.mkdir(parents=True, exist_ok=True)
                pil_img.save(target_file, **save_kwargs)
                output_files.append(str(target_file))
        finally:
            doc.close()

        primary = output_files[0] if output_files else str(out)
        return {
            "success": True,
            "output_path": primary,
            "output_files": output_files,
            "page_count": len(output_files),
        }
    except ImportError:
        # Fallback to PyPDF2 image extraction if pypdfium2 is unavailable
        try:
            from PyPDF2 import PdfReader
            from PIL import Image
            import io

            reader = PdfReader(str(src))
            total = len(reader.pages)
            extracted = []
            img_counter = 1
            for p_idx, page in enumerate(reader.pages):
                progress(0.2 + (0.7 * (p_idx + 1) / max(total, 1)), f"Extracting from page {p_idx+1}")
                for img_obj in page.images:
                    target_file = out.parent / f"{out.stem}_page_{p_idx+1}_img_{img_counter}.{tgt_ext}"
                    with Image.open(io.BytesIO(img_obj.data)) as pil_img:
                        if tgt_ext in ("jpg", "jpeg") and pil_img.mode != "RGB":
                            pil_img = pil_img.convert("RGB")
                        pil_img.save(target_file)
                    extracted.append(str(target_file))
                    img_counter += 1

            if not extracted:
                return {
                    "success": False,
                    "error": "No embedded images found in PDF and pypdfium2 renderer is not installed",
                }
            return {
                "success": True,
                "output_path": extracted[0],
                "output_files": extracted,
                "page_count": len(extracted),
            }
        except Exception as ex:
            return {"success": False, "error": f"PDF to image failed: {str(ex)}"}
