@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo  Filee - Development Environment Setup
echo ============================================================

:: 1. Verify / Configure Java Environment
if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
) else if exist "%USERPROFILE%\.jdk-21\jdk-21.0.6+7\bin\java.exe" (
    set "JAVA_HOME=%USERPROFILE%\.jdk-21\jdk-21.0.6+7"
    set "PATH=%USERPROFILE%\.jdk-21\jdk-21.0.6+7\bin;%PATH%"
)

echo [1/3] Checking Java environment...
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java is not found in PATH. Please install Java 21 or later.
    exit /b 1
)
java -version

:: 2. Verify Python Installation
echo.
echo [2/3] Checking Python environment...
where python >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Python 3 is not found in PATH. Please install Python 3.10+
    exit /b 1
)
python --version

:: 3. Setup Python Virtual Environment
echo.
echo [3/3] Setting up local Python virtual environment (.venv)...
if not exist "%~dp0..\.venv" (
    echo Creating virtual environment at "%~dp0..\.venv"...
    python -m venv "%~dp0..\.venv"
    if %ERRORLEVEL% neq 0 (
        echo [ERROR] Failed to create virtual environment.
        exit /b 1
    )
    echo Virtual environment created successfully.
) else (
    echo Existing virtual environment detected at "%~dp0..\.venv".
)

echo.
echo ============================================================
echo  Setup complete! Run scripts\run-dev.bat to launch Filee.
echo ============================================================
endlocal
