package com.filee.python;

import com.filee.utils.Logger;
import com.filee.utils.PlatformUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PythonEnvironment {

    private static final String TAG = "PythonEnvironment";

    public enum DetectionStatus {
        VENV_DETECTED,
        SYSTEM_PYTHON_DETECTED,
        NOT_FOUND
    }

    public record EnvironmentInfo(
        DetectionStatus status,
        Path executablePath,
        String versionString
    ) {}

    public EnvironmentInfo detect() {
        // Priority 1: Check project-local virtual environment
        Optional<Path> venvPython = findVenvExecutable();
        if (venvPython.isPresent()) {
            String version = queryPythonVersion(venvPython.get());
            if (version != null) {
                Logger.info(TAG, "Detected Python venv: " + venvPython.get() + " (" + version + ")");
                return new EnvironmentInfo(DetectionStatus.VENV_DETECTED, venvPython.get(), version);
            }
        }

        // Priority 2: Check standard system Python
        Optional<Path> systemPython = findSystemPythonExecutable();
        if (systemPython.isPresent()) {
            String version = queryPythonVersion(systemPython.get());
            if (version != null) {
                Logger.info(TAG, "Detected System Python: " + systemPython.get() + " (" + version + ")");
                return new EnvironmentInfo(DetectionStatus.SYSTEM_PYTHON_DETECTED, systemPython.get(), version);
            }
        }

        Logger.warn(TAG, "No valid Python executable discovered.");
        return new EnvironmentInfo(DetectionStatus.NOT_FOUND, null, "Not Found");
    }

    private Optional<Path> findVenvExecutable() {
        String exeName = PlatformUtils.isWindows() ? "python.exe" : "python";
        String scriptsDir = PlatformUtils.isWindows() ? "Scripts" : "bin";
        String userDir = System.getProperty("user.dir", ".");

        List<Path> candidateRoots = new ArrayList<>(List.of(
            Paths.get(".venv", scriptsDir, exeName),
            Paths.get("venv", scriptsDir, exeName),
            Paths.get("..", ".venv", scriptsDir, exeName),
            Paths.get("..", "venv", scriptsDir, exeName),
            Paths.get(userDir, ".venv", scriptsDir, exeName),
            Paths.get(userDir, "..", ".venv", scriptsDir, exeName),
            Paths.get("E:", "work", "Filee", ".venv", scriptsDir, exeName)
        ));

        for (Path candidate : candidateRoots) {
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate.toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }

    private Optional<Path> findSystemPythonExecutable() {
        List<String> candidates = PlatformUtils.isWindows()
            ? List.of("python.exe", "python3.exe", "py.exe")
            : List.of("python3", "python");

        // First check explicit well-known installations on Windows
        if (PlatformUtils.isWindows()) {
            List<Path> knownSystemPaths = List.of(
                Paths.get("C:\\Python314\\python.exe"),
                Paths.get("C:\\Python313\\python.exe"),
                Paths.get("C:\\Python312\\python.exe"),
                Paths.get("C:\\Python311\\python.exe"),
                Paths.get(System.getProperty("user.home"), "AppData\\Local\\Programs\\Python\\Python314\\python.exe"),
                Paths.get(System.getProperty("user.home"), "AppData\\Local\\Programs\\Python\\Python313\\python.exe"),
                Paths.get(System.getProperty("user.home"), "AppData\\Local\\Programs\\Python\\Python312\\python.exe")
            );
            for (Path kp : knownSystemPaths) {
                if (Files.isRegularFile(kp)) {
                    return Optional.of(kp.toAbsolutePath().normalize());
                }
            }
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String separator = File.pathSeparator;
            String[] directories = pathEnv.split(separator);

            for (String dir : directories) {
                for (String exe : candidates) {
                    try {
                        Path fullPath = Paths.get(dir, exe);
                        if (Files.isRegularFile(fullPath)) {
                            return Optional.of(fullPath.toAbsolutePath().normalize());
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return Optional.empty();
    }

    private String queryPythonVersion(Path pythonPath) {
        try {
            Process process = new ProcessBuilder(pythonPath.toString(), "--version")
                .redirectErrorStream(true)
                .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                int exitCode = process.waitFor();
                if (exitCode == 0 && line != null) {
                    return line.trim();
                }
            }
        } catch (Exception e) {
            Logger.warn(TAG, "Failed to query version for: " + pythonPath + " - " + e.getMessage());
        }
        return null;
    }
}
