package com.filee.conversion;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ConversionJob {

    private final String id;
    private final Path sourceFile;
    private final String sourceFormat;
    private final String targetFormat;
    private final ConversionRequest request;

    private volatile Path outputFile;
    private volatile long startTime;
    private volatile long completionTime;
    private volatile ConversionResult result;

    private final ObjectProperty<ConversionStatus> status = new SimpleObjectProperty<>(ConversionStatus.QUEUED);
    private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
    private final StringProperty errorMessage = new SimpleStringProperty("");

    private final List<Consumer<ConversionJob>> listeners = new CopyOnWriteArrayList<>();

    public ConversionJob(ConversionRequest request) {
        Objects.requireNonNull(request, "ConversionRequest must not be null");
        this.request = request;
        this.id = request.id() != null ? request.id() : UUID.randomUUID().toString();
        this.sourceFile = request.sourceFile();
        this.sourceFormat = extractExtension(request.sourceFile());
        this.targetFormat = request.targetFormat();
        this.startTime = System.currentTimeMillis();
    }

    public ConversionJob(String id, Path sourceFile, String targetFormat) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        this.sourceFormat = extractExtension(sourceFile);
        this.targetFormat = Objects.requireNonNull(targetFormat, "targetFormat must not be null");
        this.request = new ConversionRequest(this.id, sourceFile, targetFormat, null, null);
        this.startTime = System.currentTimeMillis();
    }

    private static String extractExtension(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        String name = path.getFileName().toString();
        int idx = name.lastIndexOf('.');
        return (idx > 0 && idx < name.length() - 1) ? name.substring(idx + 1).toLowerCase() : "";
    }

    public String getId() {
        return id;
    }

    public Path getSourceFile() {
        return sourceFile;
    }

    public Path getInputPath() {
        return sourceFile;
    }

    public String getSourceFormat() {
        return sourceFormat;
    }

    public String getInputFormat() {
        return sourceFormat;
    }

    public String getTargetFormat() {
        return targetFormat;
    }

    public String getOutputFormat() {
        return targetFormat;
    }

    public Path getOutputFile() {
        return outputFile;
    }

    public Path getOutputPath() {
        return outputFile;
    }

    public void setOutputFile(Path outputFile) {
        this.outputFile = outputFile;
    }

    public ConversionRequest getRequest() {
        return request;
    }

    // Status Property & Accessors
    public ConversionStatus getStatus() {
        return status.get();
    }

    public void setStatus(ConversionStatus newStatus) {
        runOnFxThread(() -> status.set(newStatus));
        notifyListeners();
    }

    public ObjectProperty<ConversionStatus> statusProperty() {
        return status;
    }

    // Progress Property & Accessors
    public double getProgress() {
        return progress.get();
    }

    public void setProgress(double progressValue) {
        double clamped = Math.max(0.0, Math.min(1.0, progressValue));
        runOnFxThread(() -> progress.set(clamped));
        notifyListeners();
    }

    public DoubleProperty progressProperty() {
        return progress;
    }

    // Error Message Property & Accessors
    public String getErrorMessage() {
        return errorMessage.get();
    }

    public void setErrorMessage(String message) {
        String safeMsg = message == null ? "" : message;
        runOnFxThread(() -> errorMessage.set(safeMsg));
        notifyListeners();
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    // Timestamps
    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(long completionTime) {
        this.completionTime = completionTime;
    }

    // Result
    public ConversionResult getResult() {
        return result;
    }

    public void setResult(ConversionResult result) {
        this.result = result;
        if (result != null) {
            if (result.success()) {
                if (result.outputFile() != null) {
                    this.outputFile = result.outputFile();
                }
                setStatus(ConversionStatus.COMPLETED);
            } else {
                if (result.errorMessage() != null) {
                    setErrorMessage(result.errorMessage());
                }
                setStatus(ConversionStatus.FAILED);
            }
        }
        notifyListeners();
    }

    // Listener Support
    public void addListener(Consumer<ConversionJob> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Consumer<ConversionJob> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Consumer<ConversionJob> listener : listeners) {
            try {
                listener.accept(this);
            } catch (Exception ignored) {
            }
        }
    }

    private void runOnFxThread(Runnable action) {
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        } catch (IllegalStateException e) {
            // JavaFX toolkit not initialized (e.g. running non-GUI unit test)
            action.run();
        }
    }
}
