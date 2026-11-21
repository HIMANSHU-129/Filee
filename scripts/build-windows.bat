@echo off
setlocal

echo ============================================================
echo  Filee - Building Windows Application Package
echo ============================================================

if not defined JAVA_HOME (
    if exist "%USERPROFILE%\.jdk-21\jdk-21.0.6+7\bin\java.exe" (
        set "JAVA_HOME=%USERPROFILE%\.jdk-21\jdk-21.0.6+7"
        set "PATH=%USERPROFILE%\.jdk-21\jdk-21.0.6+7\bin;%PATH%"
    )
)

cd /d "%~dp0.."

if exist "gradlew.bat" (
    call gradlew.bat :app:build
) else (
    if exist "%USERPROFILE%\.gradle-dist\gradle-8.12\bin\gradle.bat" (
        call "%USERPROFILE%\.gradle-dist\gradle-8.12\bin\gradle.bat" :app:build
    ) else (
        call gradle :app:build
    )
)

echo Build complete. Output artifacts located in app\build\libs\
endlocal
