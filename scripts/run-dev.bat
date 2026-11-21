@echo off
setlocal

echo ============================================================
echo  Launching Filee (Development Mode)
echo ============================================================

if not defined JAVA_HOME (
    if exist "%USERPROFILE%\.jdk-21\jdk-21.0.6+7\bin\java.exe" (
        set "JAVA_HOME=%USERPROFILE%\.jdk-21\jdk-21.0.6+7"
        set "PATH=%USERPROFILE%\.jdk-21\jdk-21.0.6+7\bin;%PATH%"
    )
)

cd /d "%~dp0.."

if exist "gradlew.bat" (
    call gradlew.bat :app:run
) else (
    if exist "%USERPROFILE%\.gradle-dist\gradle-8.12\bin\gradle.bat" (
        call "%USERPROFILE%\.gradle-dist\gradle-8.12\bin\gradle.bat" :app:run
    ) else (
        call gradle :app:run
    )
)

endlocal
