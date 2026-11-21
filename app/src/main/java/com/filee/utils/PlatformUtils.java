package com.filee.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class PlatformUtils {

    private static final String TAG = "PlatformUtils";

    private PlatformUtils() {
    }

    public static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    public static Path getAppDataDirectory() {
        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Paths.get(appData, "Filee");
            }
        }
        return Paths.get(System.getProperty("user.home"), ".filee");
    }

    public static void openFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            if (isWindows()) {
                new ProcessBuilder("cmd.exe", "/c", "start", "", path.toAbsolutePath().toString()).start();
            } else if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                java.awt.Desktop.getDesktop().open(path.toFile());
            }
        } catch (IOException e) {
            Logger.warn(TAG, "Failed to open file: " + e.getMessage());
        }
    }

    public static void openFolder(Path folderOrFile) {
        if (folderOrFile == null) {
            return;
        }
        try {
            Path target = folderOrFile.toAbsolutePath();
            if (isWindows()) {
                if (Files.isRegularFile(target)) {
                    new ProcessBuilder("explorer.exe", "/select,", target.toString()).start();
                } else {
                    new ProcessBuilder("explorer.exe", target.toString()).start();
                }
            } else if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                Path dir = Files.isDirectory(target) ? target : target.getParent();
                if (dir != null) {
                    java.awt.Desktop.getDesktop().open(dir.toFile());
                }
            }
        } catch (IOException e) {
            Logger.warn(TAG, "Failed to open folder: " + e.getMessage());
        }
    }
}
