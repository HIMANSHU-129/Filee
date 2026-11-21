package com.filee.integration;

import com.filee.utils.Logger;
import com.filee.utils.PlatformUtils;

import java.io.File;

public class WindowsContextMenu {

    private static final String TAG = "WindowsContextMenu";
    private static final String REG_KEY = "HKCU\\Software\\Classes\\*\\shell\\Filee";

    /**
     * Registers "Convert with Filee" in Windows Explorer context menu for the current user.
     * Operates in HKCU so administrator privileges are not required.
     */
    public boolean registerShellMenu(String executablePath) {
        if (!PlatformUtils.isWindows()) {
            Logger.info(TAG, "Shell menu registration skipped: non-Windows OS.");
            return false;
        }

        try {
            String exeTarget = executablePath != null && !executablePath.isBlank()
                ? executablePath
                : resolveCurrentExecutable();

            // Set main menu text
            ProcessBuilder pb1 = new ProcessBuilder(
                "reg.exe", "add", REG_KEY, "/ve", "/d", "Convert with Filee", "/f"
            );
            pb1.inheritIO().start().waitFor();

            // Set command
            String command = "\"" + exeTarget + "\" \"%1\"";
            ProcessBuilder pb2 = new ProcessBuilder(
                "reg.exe", "add", REG_KEY + "\\command", "/ve", "/d", command, "/f"
            );
            pb2.inheritIO().start().waitFor();

            Logger.info(TAG, "Successfully registered Windows Explorer context menu in HKCU.");
            return true;
        } catch (Exception e) {
            Logger.error(TAG, "Failed to register Windows Explorer context menu: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Unregisters "Convert with Filee" from the Windows Explorer context menu.
     */
    public boolean unregisterShellMenu() {
        if (!PlatformUtils.isWindows()) {
            return false;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "reg.exe", "delete", REG_KEY, "/f"
            );
            pb.inheritIO().start().waitFor();
            Logger.info(TAG, "Successfully unregistered Windows Explorer context menu.");
            return true;
        } catch (Exception e) {
            Logger.error(TAG, "Failed to unregister Windows Explorer context menu: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Checks if the shell context menu is registered.
     */
    public boolean isRegistered() {
        if (!PlatformUtils.isWindows()) {
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "reg.exe", "query", REG_KEY
            );
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveCurrentExecutable() {
        try {
            File currentJarOrClass = new File(
                WindowsContextMenu.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            );
            return currentJarOrClass.getAbsolutePath();
        } catch (Exception e) {
            return "Filee.exe";
        }
    }
}
