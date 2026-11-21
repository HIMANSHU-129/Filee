# Filee — Windows Integration Specification

This document details the native Windows 10/11 system integrations planned for Filee.

---

## 1. Explorer Context Menu Integration ("Open with Filee")

Filee integrates directly into the Windows Shell context menu:
* **Classic Context Menu (Windows 10 & 11):** Registered under `HKEY_CURRENT_USER\Software\Classes\*\shell\Filee`.
* **Windows 11 Modern Context Menu:** Implemented via a lightweight shell extension package (Sparse MSIX / IExplorerCommand COM server) to surface Filee at top-level.

### Command Line Handling
When a user right-clicks a file or group of files and selects "Open with Filee":
```
"C:\Program Files\Filee\Filee.exe" "%1"
```
Filee parses `args` in `com.filee.Main`, validates file existence, resolves target options, and displays the radial selector or brings an existing single instance to focus via Windows IPC.

---

## 2. File Associations

Filee can optionally register as a secondary handler for recognized format extensions:
* Managed via `com.filee.integration.FileAssociationManager`.
* Clean unregistration on application uninstall or user toggle in Settings.

---

## 3. Native Windows Notifications & Progress

* **Taskbar Progress:** Exposes conversion progress (0-100%) and error state on the Windows Taskbar thumbnail button using Windows Shell API (`ITaskbarList3`).
* **Toast Notifications:** Windows Action Center notification upon batch conversion completion with an "Open Folder" action button.

---

## 4. Packaging and Deployment

* User-level installation (does not require administrator privileges).
* Portable ZIP distribution for running without installation.
* Standard MSI / Inno Setup installer for enterprise and automated deployment.
