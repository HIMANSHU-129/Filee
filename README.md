# Filee

An offline-first Windows file conversion utility built with Java 21, JavaFX, and Python 3.

---

## Architecture Overview

Filee employs a decoupled dual-runtime design:

- **Java Host (Java 21 + JavaFX):** Manages the application lifecycle, desktop windowing, glassmorphic UI, file drag-and-drop operations, conversion queue, and Windows shell integrations.
- **Python Engine (Python 3):** Executes local offline conversion pipelines (images, documents, audio, and video) communicating via JSON over standard input/output streams.

---

## Prerequisites

- **Java:** JDK 21 or later
- **Python:** Python 3.10 or later
- **Operating System:** Windows 10 or Windows 11 (x64)

---

## Quick Start (Development)

### 1. Initialize Development Environment
Run the automated setup script to verify Java/Python and configure the local virtual environment:

```cmd
scripts\setup-dev.bat
```

### 2. Build the Project
Compile the application, copy Python runtime assets, and run test suites:

```cmd
.\gradlew.bat :app:build
```

### 3. Launch Application
Launch the Filee desktop interface in development mode:

```cmd
scripts\run-dev.bat
```

---

## Directory Structure

```
Filee/
├── README.md
├── LICENSE
├── .gitignore
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew.bat
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── ROADMAP.md
│   ├── CONVERSION_SUPPORT.md
│   └── WINDOWS_INTEGRATION.md
│
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/filee/
│       │   │   ├── Main.java
│       │   │   ├── App.java
│       │   │   ├── core/
│       │   │   ├── conversion/
│       │   │   ├── python/
│       │   │   ├── ui/
│       │   │   ├── integration/
│       │   │   ├── history/
│       │   │   └── utils/
│       │   ├── resources/
│       │   │   ├── fxml/
│       │   │   ├── css/
│       │   │   └── config/
│       │   └── python/
│       │       ├── main.py
│       │       ├── requirements.txt
│       │       ├── core/
│       │       └── converters/
│       └── test/
│
├── scripts/
│   ├── setup-dev.bat
│   ├── run-dev.bat
│   ├── build-windows.bat
│   └── install-python-dependencies.bat
│
└── packaging/
    ├── windows/
    └── assets/
```
