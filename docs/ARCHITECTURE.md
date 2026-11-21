# Filee — System Architecture & Design Specification

## 1. Architectural Philosophy

Filee is built as an offline-first Windows desktop utility with strict boundaries between presentation, lifecycle management, and format conversion.

The system is split into two co-operating runtimes:
1. **Java Host Runtime (Java 21 + JavaFX):** Handles the Windows presentation layer, lifecycle events, desktop integrations, file tracking, settings persistence, and process orchestration.
2. **Python Conversion Engine (Python 3):** Encapsulates conversion logic, format decoding/encoding, media transcoding, and document transformations.

```
+-------------------------------------------------------------+
|                     Filee Java Host (Java 21)               |
|  +--------------------+ +-------------------+ +-----------+ |
|  | JavaFX UI & Shell  | | ConversionManager | | Registry  | |
|  +---------+----------+ +---------+---------+ +-----+-----+ |
|            |                      |                 |       |
|            +----------------------+-----------------+       |
|                                   |                         |
|                         +---------+---------+               |
|                         |   PythonBridge    |               |
+-------------------------+---------+---------+---------------+
                                    | (IPC JSON over stdin/stdout)
+-----------------------------------+-------------------------+
|                  Python Engine Subprocess                   |
|  +--------------------+ +-------------------+ +-----------+ |
|  | CommandDispatcher  | | ConverterRegistry | | Engines   | |
|  +--------------------+ +-------------------+ +-----------+ |
+-------------------------------------------------------------+
```

---

## 2. Java Application Architecture

* **Entry Point (`com.filee.Main` / `com.filee.App`):** Decoupled launcher class to guarantee clean JavaFX initialization across diverse Windows environments.
* **Core Application Manager (`com.filee.core.ApplicationManager`):** Central singleton coordinating application lifecycle, subsystem registration, and shutdown hooks.
* **Format Registry (`com.filee.core.FormatRegistry`):** In-memory catalog of supported input and output formats organized by category (Image, Document, Audio, Video).
* **Settings & Preferences (`com.filee.core.SettingsManager`):** Local configuration stored in standard user appdata (`%APPDATA%/Filee/config.properties`).
* **Conversion Management (`com.filee.conversion.*`):** Non-blocking job pipeline handling queuing, execution monitoring, cancellation, and result dispatching.

---

## 3. Python Conversion Engine Architecture

* **Entry Point (`app/src/main/python/main.py`):** Standalone daemon listening on standard input for structured command payloads and emitting responses to standard output.
* **Protocol (`core/protocol.py`):** Encapsulates message schemas (Request, Response, Progress, Error).
* **Command Dispatcher (`core/dispatcher.py`):** Decodes inbound commands (`ping`, `capabilities`, `convert`, `cancel`) and delegates to target handlers.
* **Engine Registry (`core/registry.py`):** Pluggable map of converter implementations by source and target extensions.
* **Modular Converters (`converters/*`):** Isolated modules containing domain-specific conversion logic:
  * `converters/images/`: PIL/Pillow and vector rasterizers.
  * `converters/documents/`: PDF, markdown, plain text, and office formats.
  * `converters/audio/`: Audio transcoding and normalization.
  * `converters/video/`: Video remuxing and transcoding.

---

## 4. Communication Protocol (Java <-> Python)

The inter-process communication (IPC) uses newline-delimited JSON over standard I/O streams (`stdin` / `stdout`). This eliminates local TCP port conflicts, avoids Windows Firewall prompts, and allows strict lifecycle attachment to the parent process.

### Request Payload (Java -> Python)
```json
{
  "id": "1732204910234",
  "command": "convert",
  "params": {
    "job_id": "job-84920",
    "source_path": "C:\\Users\\User\\Pictures\\photo.png",
    "target_format": "webp",
    "output_dir": "C:\\Users\\User\\Pictures",
    "options": {
      "quality": 85
    }
  }
}
```

### Response Payload (Python -> Java)
```json
{
  "id": "1732204910234",
  "status": "success",
  "data": {
    "output_path": "C:\\Users\\User\\Pictures\\photo.webp",
    "duration_ms": 142
  }
}
```

### Progress Event (Python -> Java)
```json
{
  "type": "event",
  "event": "conversion_progress",
  "job_id": "job-84920",
  "progress": 0.65,
  "stage": "Encoding WebP frames"
}
```

---

## 5. Job Representation & State Machine

Every conversion request is tracked via `ConversionJob`:

```
   [ PENDING ] ---> [ IN_PROGRESS ] ---> [ COMPLETED ]
                           |
                           +-------------> [ FAILED ]
                           |
                           +-------------> [ CANCELLED ]
```

* **Thread Safety:** Job states and progress values are updated via thread-safe observables, alerting UI components without blocking the JavaFX Application Thread.
* **Cancellation:** Cancelling a job sends an explicit cancellation token down the bridge and signals any running Python child processes to terminate gracefully.

---

## 6. Error Handling Strategy

1. **Protocol-Level Failures:** If Python encounters an error, a structured JSON error packet is emitted with an explicit error code and human-readable message.
2. **Crash Resilience:** If the Python worker process terminates unexpectedly, `PythonBridge` detects the process exit code, cleans pending futures, and flags the status before triggering a supervised restart.
3. **Format Validation:** Formats and mime-types are validated against `FormatRegistry` before passing work to Python, preventing unnecessary process invocation.

---

## 7. Temporary Files & Cleanup

* Conversions requiring intermediate scratch files use a dedicated folder under `%TEMP%/Filee/scratch/`.
* Every intermediate file is registered with an auto-closeable tracking context.
* On job completion (success or failure), scratch assets are deleted immediately.
* Application startup triggers an orphan cleanup pass over `%TEMP%/Filee/scratch/`.

---

## 8. Offline Guarantee & Privacy

* Filee carries zero telemetry, zero analytics, and zero external network requests.
* All conversion engines execute locally using pre-bundled Python modules and native binaries.
* The application operates identically with all network interfaces disabled.

---

## 9. Packaging & Windows Distribution

Final releases will be packaged using modern Windows distribution tooling:
* **Java Runtime:** Minified custom Java 21 runtime image built via `jlink`.
* **Python Runtime:** Embedded, self-contained Python distribution packaged alongside the application.
* **Packaging:** MSIX and standard Windows Installer (`.exe` / `.msi`) generated via `jpackage` and Inno Setup.
