package com.filee.core;

import com.filee.conversion.ConversionManager;
import com.filee.history.HistoryManager;
import com.filee.python.PythonBridge;
import com.filee.python.PythonEnvironment;
import com.filee.utils.Logger;

public class ApplicationManager {

    private static final String TAG = "ApplicationManager";
    private static ApplicationManager instance;

    private final SettingsManager settingsManager;
    private final FileManager fileManager;
    private final FormatRegistry formatRegistry;
    private final HistoryManager historyManager;
    private final PythonEnvironment pythonEnvironment;
    private final PythonBridge pythonBridge;
    private final ConversionManager conversionManager;

    private PythonEnvironment.EnvironmentInfo detectedPythonEnv;

    private ApplicationManager() {
        this.settingsManager = new SettingsManager();
        this.fileManager = new FileManager();
        this.formatRegistry = new FormatRegistry();
        this.historyManager = new HistoryManager();
        this.pythonEnvironment = new PythonEnvironment();
        this.pythonBridge = new PythonBridge();
        this.conversionManager = new ConversionManager(this.pythonBridge);
    }

    public static synchronized ApplicationManager getInstance() {
        if (instance == null) {
            instance = new ApplicationManager();
        }
        return instance;
    }

    public void initialize() {
        Logger.info(TAG, "Initializing Filee core runtime...");
        this.detectedPythonEnv = pythonEnvironment.detect();

        if (detectedPythonEnv.status() != PythonEnvironment.DetectionStatus.NOT_FOUND) {
            boolean started = pythonBridge.start(detectedPythonEnv);
            if (started) {
                Logger.info(TAG, "Python conversion engine ready.");
            } else {
                Logger.warn(TAG, "Python engine could not be started in background.");
            }
        } else {
            Logger.warn(TAG, "No Python environment discovered on this system.");
        }
    }

    public void shutdown() {
        Logger.info(TAG, "Shutting down Filee core runtime...");
        if (conversionManager != null) {
            conversionManager.shutdown();
        }
        if (pythonBridge != null) {
            pythonBridge.shutdown();
        }
        if (settingsManager != null) {
            settingsManager.saveToDisk();
        }
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public FormatRegistry getFormatRegistry() {
        return formatRegistry;
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    public PythonEnvironment.EnvironmentInfo getDetectedPythonEnv() {
        return detectedPythonEnv;
    }

    public PythonBridge getPythonBridge() {
        return pythonBridge;
    }

    public ConversionManager getConversionManager() {
        return conversionManager;
    }
}
