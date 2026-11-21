package com.filee;

import com.filee.conversion.ConversionJob;
import com.filee.conversion.ConversionRequest;
import com.filee.conversion.ConversionResult;
import com.filee.conversion.ConversionStatus;
import com.filee.core.FileManager;
import com.filee.core.FormatRegistry;
import com.filee.python.PythonEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    @Test
    @DisplayName("FormatRegistry correctly maps file extensions to categories")
    void testFormatRegistryCategories() {
        FormatRegistry registry = new FormatRegistry();

        assertEquals(FormatRegistry.Category.IMAGE, registry.getCategory("png"));
        assertEquals(FormatRegistry.Category.IMAGE, registry.getCategory(".jpg"));
        assertEquals(FormatRegistry.Category.DOCUMENT, registry.getCategory("pdf"));
        assertEquals(FormatRegistry.Category.AUDIO, registry.getCategory("mp3"));
        assertEquals(FormatRegistry.Category.VIDEO, registry.getCategory("mp4"));
        assertEquals(FormatRegistry.Category.UNKNOWN, registry.getCategory("xyz999"));
    }

    @Test
    @DisplayName("FormatRegistry provides target formats excluding source")
    void testFormatRegistryTargets() {
        FormatRegistry registry = new FormatRegistry();
        Set<String> targets = registry.getAvailableTargetFormats("png");

        assertNotNull(targets);
        assertFalse(targets.contains("png"), "Target formats should exclude source extension");
        assertTrue(targets.contains("jpg"));
        assertTrue(targets.contains("webp"));
        assertTrue(targets.contains("pdf"), "Images should be convertible to PDF");

        Set<String> pdfTargets = registry.getAvailableTargetFormats("pdf");
        assertNotNull(pdfTargets);
        assertTrue(pdfTargets.contains("png"), "PDF should be convertible to PNG");
        assertTrue(pdfTargets.contains("jpg"), "PDF should be convertible to JPG");
        assertTrue(pdfTargets.contains("webp"), "PDF should be convertible to WEBP");
    }

    @Test
    @DisplayName("FormatRegistry provides target formats for audio and video")
    void testAudioAndVideoTargets() {
        FormatRegistry registry = new FormatRegistry();
        Set<String> audioTargets = registry.getAvailableTargetFormats("mp3");
        assertNotNull(audioTargets);
        assertFalse(audioTargets.contains("mp3"));
        assertTrue(audioTargets.contains("wav"));
        assertTrue(audioTargets.contains("flac"));

        Set<String> videoTargets = registry.getAvailableTargetFormats("mp4");
        assertNotNull(videoTargets);
        assertFalse(videoTargets.contains("mp4"));
        assertTrue(videoTargets.contains("mkv"));
        assertTrue(videoTargets.contains("webm"));
    }

    @Test
    @DisplayName("FileManager resolves valid destination filename")
    void testFileManagerResolution() {
        FileManager manager = new FileManager();
        Path source = Paths.get("sample.png");
        Path dest = manager.resolveDestinationPath(source, "webp", null);

        assertNotNull(dest);
        assertTrue(dest.getFileName().toString().endsWith(".webp"));
        assertEquals("png", manager.extractExtension(source));
    }

    @Test
    @DisplayName("FileManager handles edge cases without failure")
    void testFileManagerEdgeCases() {
        FileManager manager = new FileManager();
        assertEquals("", manager.extractExtension(null));
        assertEquals("", manager.extractExtension(Paths.get("README")));

        Path dest = manager.resolveDestinationPath(Paths.get("data.txt"), "pdf", null);
        assertNotNull(dest);
        assertTrue(dest.getFileName().toString().endsWith(".pdf"));
    }

    @Test
    @DisplayName("FileManager overwrite parameter behaves correctly")
    void testFileManagerOverwrite() throws Exception {
        FileManager manager = new FileManager();
        Path tempDir = Files.createTempDirectory("filee-test");
        try {
            Path existingFile = tempDir.resolve("test_image.webp");
            Files.createFile(existingFile);

            Path source = tempDir.resolve("test_image.png");

            // Without overwrite, should produce test_image_1.webp
            Path nonOverwrite = manager.resolveDestinationPath(source, "webp", tempDir, false);
            assertEquals("test_image_1.webp", nonOverwrite.getFileName().toString());

            // With overwrite, should produce test_image.webp
            Path overwrite = manager.resolveDestinationPath(source, "webp", tempDir, true);
            assertEquals("test_image.webp", overwrite.getFileName().toString());
        } finally {
            manager.cleanTempFiles(tempDir);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    @DisplayName("PythonEnvironment detection executes without unhandled exceptions")
    void testPythonEnvironmentDetection() {
        PythonEnvironment env = new PythonEnvironment();
        PythonEnvironment.EnvironmentInfo info = env.detect();

        assertNotNull(info);
        assertNotNull(info.status());
    }

    @Test
    @DisplayName("ConversionJob initializes with proper status, properties, and listeners")
    void testConversionJobProperties() {
        String jobId = UUID.randomUUID().toString();
        ConversionRequest req = new ConversionRequest(
            jobId,
            Paths.get("C:/photos/cat.png"),
            "webp",
            Paths.get("C:/photos"),
            Map.of("quality", 85)
        );

        ConversionJob job = new ConversionJob(req);

        assertEquals(jobId, job.getId());
        assertEquals("cat.png", job.getSourceFile().getFileName().toString());
        assertEquals("png", job.getSourceFormat());
        assertEquals("webp", job.getTargetFormat());
        assertEquals(ConversionStatus.QUEUED, job.getStatus());
        assertEquals(0.0, job.getProgress());
        assertEquals("", job.getErrorMessage());

        // Test status progression
        AtomicBoolean listenerNotified = new AtomicBoolean(false);
        job.addListener(j -> listenerNotified.set(true));

        job.setStatus(ConversionStatus.PREPARING);
        assertEquals(ConversionStatus.PREPARING, job.getStatus());
        assertTrue(listenerNotified.get());

        job.setProgress(0.45);
        assertEquals(0.45, job.getProgress(), 0.001);

        job.setStatus(ConversionStatus.CONVERTING);
        assertEquals(ConversionStatus.CONVERTING, job.getStatus());

        Path out = Paths.get("C:/photos/cat.webp");
        ConversionResult result = ConversionResult.successful(jobId, out, 120);
        job.setResult(result);

        assertEquals(ConversionStatus.COMPLETED, job.getStatus());
        assertEquals(out, job.getOutputFile());
        assertNotNull(job.getResult());
        assertTrue(job.getResult().success());
    }

    @Test
    @DisplayName("ConversionJob handles failure results properly")
    void testConversionJobFailure() {
        ConversionJob job = new ConversionJob(UUID.randomUUID().toString(), Paths.get("sample.bmp"), "png");
        assertEquals(ConversionStatus.QUEUED, job.getStatus());

        job.setStatus(ConversionStatus.CONVERTING);
        ConversionResult fail = ConversionResult.failed(job.getId(), "Corrupt header", 50);
        job.setResult(fail);

        assertEquals(ConversionStatus.FAILED, job.getStatus());
        assertEquals("Corrupt header", job.getErrorMessage());
        assertFalse(job.getResult().success());
    }

    @Test
    @DisplayName("ConversionStatus enum has all required states")
    void testConversionStatusEnum() {
        ConversionStatus[] values = ConversionStatus.values();
        assertEquals(6, values.length);
        assertNotNull(ConversionStatus.valueOf("QUEUED"));
        assertNotNull(ConversionStatus.valueOf("PREPARING"));
        assertNotNull(ConversionStatus.valueOf("CONVERTING"));
        assertNotNull(ConversionStatus.valueOf("COMPLETED"));
        assertNotNull(ConversionStatus.valueOf("FAILED"));
        assertNotNull(ConversionStatus.valueOf("CANCELLED"));
    }

    @Test
    @DisplayName("ConversionRequest properly handles multiple source files")
    void testMultiFileConversionRequest() {
        List<Path> files = List.of(Paths.get("img1.png"), Paths.get("img2.jpg"), Paths.get("img3.png"));
        ConversionRequest req = new ConversionRequest(
            "multi-test-id",
            files,
            "pdf",
            Paths.get("output"),
            Map.of()
        );

        assertEquals("multi-test-id", req.id());
        assertEquals("pdf", req.targetFormat());
        assertEquals(3, req.sourceFiles().size());
        assertEquals(Paths.get("img1.png"), req.sourceFile());
    }

    @Test
    @DisplayName("FileManager sanitizes filenames against path traversal attacks")
    void testPathSanitizationSecurity() {
        FileManager manager = new FileManager();
        assertEquals("file", manager.sanitizeFilename(null));
        assertEquals("file", manager.sanitizeFilename(""));
        assertEquals("test_file.txt", manager.sanitizeFilename("test:file.txt"));
        assertEquals("secret.txt", manager.sanitizeFilename("../../../secret.txt"));

        Path safeDir = Paths.get(".").toAbsolutePath().normalize();
        Path safeDest = manager.resolveDestinationPath(Paths.get("../../etc/passwd.png"), "pdf", safeDir);
        assertTrue(safeDest.toAbsolutePath().normalize().startsWith(safeDir));
    }
}
