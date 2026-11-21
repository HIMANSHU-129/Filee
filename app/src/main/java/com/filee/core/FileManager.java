package com.filee.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class FileManager {

    public Path resolveDestinationPath(Path sourceFile, String targetExtension, Path customOutputDir) {
        return resolveDestinationPath(sourceFile, targetExtension, customOutputDir, false);
    }

    public Path resolveDestinationPath(Path sourceFile, String targetExtension, Path customOutputDir, boolean overwrite) {
        if (sourceFile == null) {
            return null;
        }

        Path targetDir = (customOutputDir != null && Files.isDirectory(customOutputDir))
            ? customOutputDir
            : (sourceFile.getParent() != null ? sourceFile.getParent() : Paths.get("."));

        String originalFileName = sourceFile.getFileName() != null ? sourceFile.getFileName().toString() : "file";
        int dotIndex = originalFileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? originalFileName.substring(0, dotIndex) : originalFileName;
        baseName = sanitizeFilename(baseName);

        String cleanExt = targetExtension != null
            ? (targetExtension.startsWith(".") ? targetExtension.substring(1) : targetExtension)
            : "";
        cleanExt = sanitizeFilename(cleanExt);
        Path targetPath = targetDir.resolve(baseName + "." + cleanExt.toLowerCase(Locale.ROOT)).normalize();

        // Security check: ensure target path remains safely within target directory
        if (!targetPath.startsWith(targetDir.normalize())) {
            targetPath = targetDir.resolve("sanitized_file." + cleanExt.toLowerCase(Locale.ROOT)).normalize();
        }

        if (overwrite) {
            return targetPath;
        }

        // If file already exists, create unique name baseName_1.ext, baseName_2.ext
        int counter = 1;
        while (Files.exists(targetPath)) {
            targetPath = targetDir.resolve(baseName + "_" + counter + "." + cleanExt.toLowerCase(Locale.ROOT));
            counter++;
        }

        return targetPath;
    }

    public String sanitizeFilename(String filename) {
        if (filename == null) return "file";
        // Normalize slashes and take last segment
        String s = filename.replace('\\', '/');
        int lastSlash = s.lastIndexOf('/');
        if (lastSlash >= 0) {
            s = s.substring(lastSlash + 1);
        }
        // Remove path traversal sequences, null bytes, and illegal Windows filesystem characters
        String safe = s.replaceAll("[\\\\/:*?\"<>|\\x00]", "_");
        safe = safe.replaceAll("^\\.+", "");
        return safe.trim().isEmpty() ? "file" : safe.trim();
    }

    public String extractExtension(Path file) {
        if (file == null || file.getFileName() == null) {
            return "";
        }
        String name = file.getFileName().toString();
        int idx = name.lastIndexOf('.');
        return (idx > 0 && idx < name.length() - 1) ? name.substring(idx + 1).toLowerCase(Locale.ROOT) : "";
    }

    public void cleanTempFiles(Path tempDir) {
        if (tempDir != null && Files.exists(tempDir)) {
            try {
                Files.walk(tempDir)
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {}
                    });
            } catch (IOException ignored) {}
        }
    }
}
