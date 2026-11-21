package com.filee.history;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filee.conversion.ConversionResult;
import com.filee.utils.Logger;
import com.filee.utils.PlatformUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryManager {

    private static final String TAG = "HistoryManager";
    private static final String HISTORY_FILE_NAME = "history.json";

    public static record HistoryEntry(
        String id,
        boolean success,
        String outputFile,
        String errorMessage,
        long durationMs
    ) {
        public static HistoryEntry fromResult(ConversionResult result) {
            return new HistoryEntry(
                result.id(),
                result.success(),
                result.outputFile() != null ? result.outputFile().toString() : null,
                result.errorMessage(),
                result.durationMs()
            );
        }

        public ConversionResult toResult() {
            Path outPath = outputFile != null && !outputFile.isBlank() ? Paths.get(outputFile) : null;
            return new ConversionResult(id, success, outPath, errorMessage, durationMs);
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<ConversionResult> conversionHistory = Collections.synchronizedList(new ArrayList<>());
    private final Path historyPath;

    public HistoryManager() {
        this.historyPath = PlatformUtils.getAppDataDirectory().resolve(HISTORY_FILE_NAME);
        loadFromDisk();
    }

    private void loadFromDisk() {
        if (!Files.exists(historyPath)) {
            return;
        }
        try {
            List<HistoryEntry> entries = mapper.readValue(
                historyPath.toFile(),
                new TypeReference<List<HistoryEntry>>() {}
            );
            conversionHistory.clear();
            if (entries != null) {
                for (HistoryEntry e : entries) {
                    conversionHistory.add(e.toResult());
                }
            }
            Logger.info(TAG, "Loaded " + conversionHistory.size() + " history items from disk.");
        } catch (Exception e) {
            Logger.warn(TAG, "Failed to load history from disk: " + e.getMessage());
        }
    }

    private void saveToDisk() {
        try {
            Files.createDirectories(historyPath.getParent());
            List<HistoryEntry> entries = new ArrayList<>();
            synchronized (conversionHistory) {
                for (ConversionResult res : conversionHistory) {
                    entries.add(HistoryEntry.fromResult(res));
                }
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(historyPath.toFile(), entries);
        } catch (IOException e) {
            Logger.warn(TAG, "Failed to persist history to disk: " + e.getMessage());
        }
    }

    public void record(ConversionResult result) {
        if (result != null) {
            conversionHistory.add(result);
            saveToDisk();
            Logger.info(TAG, "Recorded conversion event: ID=" + result.id() + ", success=" + result.success());
        }
    }

    public List<ConversionResult> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(conversionHistory));
    }

    public void clear() {
        conversionHistory.clear();
        saveToDisk();
        Logger.info(TAG, "History cleared.");
    }
}
