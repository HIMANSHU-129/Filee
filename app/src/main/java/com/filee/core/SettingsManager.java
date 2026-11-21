package com.filee.core;

import com.filee.utils.Logger;
import com.filee.utils.PlatformUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class SettingsManager {

    private static final String TAG = "SettingsManager";
    private static final String SETTINGS_FILE_NAME = "config.properties";

    private final Properties properties = new Properties();
    private final Path settingsPath;

    public SettingsManager() {
        this.settingsPath = PlatformUtils.getAppDataDirectory().resolve(SETTINGS_FILE_NAME);
        loadDefaults();
        loadFromDisk();
    }

    private void loadDefaults() {
        properties.setProperty("app.theme", "dark");
        properties.setProperty("conversion.overwrite_existing", "false");
        properties.setProperty("conversion.output_mode", "same_folder");
        properties.setProperty("history.enabled", "true");
        properties.setProperty("history.max_entries", "100");
    }

    private void loadFromDisk() {
        if (Files.exists(settingsPath)) {
            try (InputStream in = Files.newInputStream(settingsPath)) {
                properties.load(in);
            } catch (IOException e) {
                Logger.warn(TAG, "Failed reading settings: " + e.getMessage());
            }
        }
    }

    public void saveToDisk() {
        try {
            Files.createDirectories(settingsPath.getParent());
            try (OutputStream out = Files.newOutputStream(settingsPath)) {
                properties.store(out, "Filee Configuration");
            }
        } catch (IOException e) {
            Logger.warn(TAG, "Failed writing settings: " + e.getMessage());
        }
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }
}
