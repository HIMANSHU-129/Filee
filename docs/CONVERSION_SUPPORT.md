# Filee — Format Conversion Matrix & Support Plan

This document details the targeted format conversion matrix and implementation roadmap across all categories.

---

## 1. Matrix Overview

| Category | Input Formats | Target Output Formats | Processing Engine | Target Phase |
| :--- | :--- | :--- | :--- | :--- |
| **Image** | PNG, JPG, JPEG, WEBP, GIF, BMP, TIFF, ICO, SVG | PNG, JPG, WEBP, GIF, BMP, TIFF, ICO, PDF | Pillow, CairoSVG | Phase 3 |
| **Document** | TXT, MD, PDF, HTML, DOCX, CSV, EPUB | PDF, TXT, MD, HTML, DOCX, EPUB | pdfplumber, python-docx, markdown | Phase 3 & 4 |
| **Audio** | MP3, WAV, AAC, FLAC, OGG, M4A | MP3, WAV, AAC, FLAC, OGG, M4A | FFmpeg native local binary | Phase 4 |
| **Video** | MP4, MKV, WEBM, AVI, MOV, GIF | MP4, MKV, WEBM, AVI, MOV, GIF | FFmpeg native local binary | Phase 4 |

---

## 2. Image Pipeline (Phase 3)

### Supported Conversions
* `PNG <-> JPEG / JPG`
* `PNG / JPG <-> WEBP` (with lossless and lossy quality dials)
* `PNG / JPG -> ICO` (automatic multi-resolution icon generation: 16x16, 32x32, 48x48, 64x64, 128x128, 256x256)
* `TIFF / BMP -> PNG / JPG`
* `SVG -> PNG / PDF`

### Optimization Rules
* Strip unnecessary EXIF / metadata when requested by user settings.
* Color profile conversion to sRGB for consistent display.
* Preserve alpha channel transparency when converting to PNG, WEBP, or ICO.

---

## 3. Document Pipeline (Phase 3 & 4)

* **Plain Text / Markdown -> PDF:** Clean styled typography using local HTML/CSS rendering.
* **Markdown -> HTML / TXT:** Full CommonMark compliant translation.
* **PDF -> TXT / Images:** Extract embedded text without cloud API calls.
* **DOCX -> PDF / TXT / Markdown:** Structural document parsing.

---

## 4. Audio & Video Pipeline (Phase 4)

* **Audio Normalization:** Configurable bitrate (128k, 192k, 320k) and sample rates (44.1kHz, 48kHz).
* **Video Transcoding:** H.264, HEVC, and VP9 encoding profiles.
* **Video -> Animated GIF:** Frame sampling and palette optimization for lightweight GIF generation.
* **Video -> Audio:** Fast stream copy or audio extraction to MP3/AAC.

---

## 5. Offline & Privacy Enforcement

* All dependencies are executed completely locally on the host machine.
* Zero network sockets opened during conversion.
* Source files are treated as read-only streams.
