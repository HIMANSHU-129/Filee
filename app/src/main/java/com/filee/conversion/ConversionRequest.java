package com.filee.conversion;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ConversionRequest(
    String id,
    Path sourceFile,
    List<Path> sourceFiles,
    String targetFormat,
    Path outputDirectory,
    Map<String, Object> options
) {
    public ConversionRequest {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        Objects.requireNonNull(targetFormat, "targetFormat must not be null");
        sourceFiles = sourceFiles == null || sourceFiles.isEmpty()
            ? List.of(sourceFile)
            : List.copyOf(sourceFiles);
        options = options == null ? Collections.emptyMap() : Collections.unmodifiableMap(options);
    }

    public ConversionRequest(
        String id,
        Path sourceFile,
        String targetFormat,
        Path outputDirectory,
        Map<String, Object> options
    ) {
        this(id, sourceFile, sourceFile != null ? List.of(sourceFile) : Collections.emptyList(), targetFormat, outputDirectory, options);
    }

    public ConversionRequest(
        String id,
        List<Path> sourceFiles,
        String targetFormat,
        Path outputDirectory,
        Map<String, Object> options
    ) {
        this(
            id,
            (sourceFiles != null && !sourceFiles.isEmpty()) ? sourceFiles.get(0) : null,
            sourceFiles,
            targetFormat,
            outputDirectory,
            options
        );
    }
}
