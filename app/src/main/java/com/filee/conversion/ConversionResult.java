package com.filee.conversion;

import java.nio.file.Path;

public record ConversionResult(
    String id,
    boolean success,
    Path outputFile,
    String errorMessage,
    long durationMs
) {
    public static ConversionResult successful(String id, Path outputFile, long durationMs) {
        return new ConversionResult(id, true, outputFile, null, durationMs);
    }

    public static ConversionResult failed(String id, String errorMessage, long durationMs) {
        return new ConversionResult(id, false, null, errorMessage, durationMs);
    }
}
