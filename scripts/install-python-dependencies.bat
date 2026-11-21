@echo off
setlocal

echo ============================================================
echo  Filee - Installing Python Dependencies
echo ============================================================

set VENV_PY=%~dp0..\.venv\Scripts\python.exe

if not exist "%VENV_PY%" (
    echo [INFO] Virtual environment not found. Running setup-dev.bat first...
    call "%~dp0setup-dev.bat"
)

if exist "%VENV_PY%" (
    echo Installing requirements into .venv...
    "%VENV_PY%" -m pip install --upgrade pip
    "%VENV_PY%" -m pip install -r "%~dp0..\app\src\main\python\requirements.txt"
) else (
    echo [ERROR] Virtual environment python.exe could not be found.
    exit /b 1
)

echo Dependencies installation finished.
endlocal
