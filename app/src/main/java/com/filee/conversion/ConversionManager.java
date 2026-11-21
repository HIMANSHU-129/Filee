package com.filee.conversion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.filee.core.ApplicationManager;
import com.filee.core.FileManager;
import com.filee.python.PythonBridge;
import com.filee.utils.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.*;

public class ConversionManager {

    private static final String TAG = "ConversionManager";

    private final PythonBridge pythonBridge;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService jobExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Filee-JobExecutor");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, ConversionJob> activeJobs = new ConcurrentHashMap<>();

    public ConversionManager(PythonBridge pythonBridge) {
        this.pythonBridge = pythonBridge;
        setupProgressListener();
    }

    private void setupProgressListener() {
        if (pythonBridge != null) {
            pythonBridge.addProgressListener((jobId, progress, stage) -> {
                ConversionJob job = activeJobs.get(jobId);
                if (job != null) {
                    ConversionStatus cur = job.getStatus();
                    if (cur != ConversionStatus.COMPLETED && cur != ConversionStatus.FAILED && cur != ConversionStatus.CANCELLED) {
                        job.setProgress(progress);
                        if (cur != ConversionStatus.CONVERTING) {
                            job.setStatus(ConversionStatus.CONVERTING);
                        }
                    }
                }
            });
        }
    }

    public ConversionJob submitJob(ConversionRequest request) {
        ConversionJob job = new ConversionJob(request);
        activeJobs.put(job.getId(), job);
        Logger.info(TAG, "Submitted conversion job: " + job.getId() + " [" + request.sourceFile().getFileName() + " -> ." + request.targetFormat() + "]");

        jobExecutor.submit(() -> executeJob(job));
        return job;
    }

    public ConversionJob submit(ConversionRequest request) {
        return submitJob(request);
    }

    private void executeJob(ConversionJob job) {
        if (job.getStatus() == ConversionStatus.CANCELLED) {
            return;
        }

        job.setStartTime(System.currentTimeMillis());
        job.setStatus(ConversionStatus.PREPARING);
        job.setProgress(0.05);

        ConversionRequest request = job.getRequest();

        // Resolve planned output destination
        FileManager fileManager = ApplicationManager.getInstance().getFileManager();
        boolean overwrite = Boolean.parseBoolean(String.valueOf(request.options().getOrDefault("overwrite", false)));
        Path outputPath = fileManager.resolveDestinationPath(
            request.sourceFile(),
            request.targetFormat(),
            request.outputDirectory(),
            overwrite
        );
        job.setOutputFile(outputPath);

        if (job.getStatus() == ConversionStatus.CANCELLED) {
            return;
        }

        // Prepare JSON payload for Python engine
        ObjectNode params = mapper.createObjectNode();
        params.put("job_id", job.getId());
        params.put("source_path", request.sourceFile().toAbsolutePath().toString());
        if (request.sourceFiles() != null && request.sourceFiles().size() > 1) {
            var arrayNode = mapper.createArrayNode();
            for (Path p : request.sourceFiles()) {
                arrayNode.add(p.toAbsolutePath().toString());
            }
            params.set("source_paths", arrayNode);
        }
        params.put("target_format", request.targetFormat());
        params.put("output_path", outputPath.toAbsolutePath().toString());
        if (request.outputDirectory() != null) {
            params.put("output_dir", request.outputDirectory().toAbsolutePath().toString());
        }

        if (!request.options().isEmpty()) {
            ObjectNode opts = mapper.createObjectNode();
            request.options().forEach((k, v) -> opts.put(k, String.valueOf(v)));
            params.set("options", opts);
        }

        job.setStatus(ConversionStatus.CONVERTING);

        CompletableFuture<JsonNode> future = pythonBridge.sendCommand("convert", params);

        try {
            // Await completion (up to 300 seconds for large files)
            JsonNode response = future.get(300, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - job.getStartTime();
            job.setCompletionTime(System.currentTimeMillis());

            if (job.getStatus() == ConversionStatus.CANCELLED) {
                return;
            }

            String status = response.path("status").asText("");
            if ("success".equalsIgnoreCase(status)) {
                String outPathStr = response.path("data").path("output_path").asText(outputPath.toString());
                Path finalOut = Paths.get(outPathStr);
                job.setOutputFile(finalOut);
                job.setProgress(1.0);

                ConversionResult result = ConversionResult.successful(job.getId(), finalOut, duration);
                job.setResult(result);
                job.setStatus(ConversionStatus.COMPLETED);
                recordHistory(result);
                Logger.info(TAG, "Job " + job.getId() + " completed successfully in " + duration + " ms: " + finalOut);
            } else {
                String errorMsg = response.path("error").path("message").asText("Conversion failed in engine");
                job.setErrorMessage(errorMsg);

                ConversionResult result = ConversionResult.failed(job.getId(), errorMsg, duration);
                job.setResult(result);
                job.setStatus(ConversionStatus.FAILED);
                recordHistory(result);
                Logger.warn(TAG, "Job " + job.getId() + " failed: " + errorMsg);
            }
        } catch (CancellationException e) {
            job.setStatus(ConversionStatus.CANCELLED);
            job.setErrorMessage("Conversion cancelled by user.");
            Logger.info(TAG, "Job " + job.getId() + " was cancelled.");
        } catch (TimeoutException e) {
            long duration = System.currentTimeMillis() - job.getStartTime();
            String err = "Conversion timed out after 300 seconds.";
            job.setErrorMessage(err);
            ConversionResult result = ConversionResult.failed(job.getId(), err, duration);
            job.setResult(result);
            job.setStatus(ConversionStatus.FAILED);
            recordHistory(result);
            Logger.error(TAG, "Job " + job.getId() + " timed out.");
        } catch (Exception e) {
            if (job.getStatus() != ConversionStatus.CANCELLED) {
                long duration = System.currentTimeMillis() - job.getStartTime();
                String err = "Bridge execution failure: " + e.getMessage();
                job.setErrorMessage(err);
                ConversionResult result = ConversionResult.failed(job.getId(), err, duration);
                job.setResult(result);
                job.setStatus(ConversionStatus.FAILED);
                recordHistory(result);
                Logger.error(TAG, "Job " + job.getId() + " encountered unexpected failure: " + e.getMessage(), e);
            }
        }
    }

    private void recordHistory(ConversionResult result) {
        try {
            ApplicationManager app = ApplicationManager.getInstance();
            if (app != null && app.getHistoryManager() != null) {
                app.getHistoryManager().record(result);
            }
        } catch (Exception ignored) {
        }
    }

    public ConversionJob getJob(String jobId) {
        return activeJobs.get(jobId);
    }

    public void cancelJob(String jobId) {
        ConversionJob job = activeJobs.get(jobId);
        if (job != null) {
            ConversionStatus cur = job.getStatus();
            if (cur != ConversionStatus.COMPLETED && cur != ConversionStatus.FAILED && cur != ConversionStatus.CANCELLED) {
                job.setStatus(ConversionStatus.CANCELLED);
                job.setErrorMessage("Conversion cancelled by user.");
                ObjectNode cancelParams = mapper.createObjectNode();
                cancelParams.put("job_id", jobId);
                pythonBridge.sendCommand("cancel", cancelParams);
                Logger.info(TAG, "Cancelled conversion job: " + jobId);
            }
        }
    }

    public void shutdown() {
        jobExecutor.shutdownNow();
        activeJobs.clear();
        Logger.info(TAG, "ConversionManager shut down.");
    }
}
