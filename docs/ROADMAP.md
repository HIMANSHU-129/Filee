# Filee — Multi-Phase Development Roadmap

This document outlines the phased milestone delivery plan for the Filee Windows desktop conversion application.

---

### Phase 1 — Project Foundation (Current)
- Modular project architecture and multi-project Gradle configuration.
- Java 21 compilation and JavaFX configuration.
- Standardized IPC protocol and Python bridge foundation.
- Python process launcher and environment detection (system & venv).
- Design system tokens (dark/light themes, typography, subtle glassmorphic styling).
- Initial application launcher and verification.

---

### Phase 2 — UI Foundation
- Modern glassmorphic desktop interface adhering to strict non-distracting design guidelines.
- Dynamic file drop zone with hover/drag feedback.
- Interactive radial/donut format-selection interface.
- Instant file preview card (metadata, mime type, size, thumbnail).
- Light and dark theme switcher with smooth transitions.

---

### Phase 3 — Core File Conversion
- Python image conversion module (PNG, JPG, WebP, GIF, BMP, TIFF, ICO).
- Basic document conversion module (TXT, Markdown, PDF text extraction, HTML).
- Java conversion queue and job manager wiring.
- Asynchronous progress updates and real-time status reporting.
- Error reporting and toast notifications.

---

### Phase 4 — Advanced Conversion Engines
- Audio transcoding module (MP3, WAV, AAC, FLAC, OGG, M4A).
- Video remuxing and transcoding module (MP4, MKV, WebM, AVI, MOV).
- Office and e-book document conversions (DOCX, EPUB, CSV).
- Advanced vector and graphic format conversions (SVG rasterization, PSD extraction).

---

### Phase 5 — Windows Integration
- Windows Explorer context menu integration ("Open with Filee").
- Windows shell file associations for supported extensions.
- Command-line argument handling (`filee.exe <filepath>`).
- Native Windows toast notifications on completion.
- Windows taskbar progress overlay.

---

### Phase 6 — Advanced Functionality
- Multi-file batch conversion with concurrent processing pool.
- Priority conversion queue management.
- Conversion history log with quick output directory reveal.
- User configurable conversion preferences (compression level, default output folder, naming rules).
- Settings persistence in local AppData.

---

### Phase 7 — Optimization
- Memory consumption profiling and idle memory reduction.
- Application cold-start optimization.
- Minified modular dependency bundling (tree-shaken jlink runtime).
- Low-end Windows hardware validation and performance tuning.

---

### Phase 8 — Release & Packaging
- Windows installer generation (`.msi` / `.exe` via Inno Setup and WiX).
- Portable standalone single-directory zip build.
- Native application icons and branding assets.
- Production signing configuration and checksum generation.
- Release documentation and user guides.
