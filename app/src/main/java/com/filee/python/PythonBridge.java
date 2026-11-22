package com.filee.python;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.filee.utils.Logger;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PythonBridge {

    private static final String TAG = "PythonBridge";

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(String jobId, double progress, String stage);
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Process process;
    private BufferedWriter stdinWriter;
    private BufferedReader stdoutReader;
    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PythonBridge-Reader");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, CompletableFuture<JsonNode>> pendingResponses = new ConcurrentHashMap<>();
    private final List<ProgressListener> progressListeners = new CopyOnWriteArrayList<>();

    public synchronized boolean start() {
        PythonEnvironment env = new PythonEnvironment();
        return start(env.detect());
    }

    public synchronized boolean start(PythonEnvironment.EnvironmentInfo envInfo) {
        if (isRunning()) {
            return true;
        }

        if (envInfo == null || envInfo.executablePath() == null) {
            envInfo = new PythonEnvironment().detect();
        }

        if (envInfo == null || envInfo.executablePath() == null) {
            Logger.warn(TAG, "Cannot start bridge: Python executable not found.");
            return false;
        }

        Path scriptPath = resolvePythonScript();
        if (scriptPath == null || !Files.exists(scriptPath)) {
            Logger.warn(TAG, "Python main entry point not found at expected path.");
            return false;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                envInfo.executablePath().toString(),
                scriptPath.toString()
            );
            pb.directory(scriptPath.getParent() != null ? scriptPath.getParent().toFile() : null);
            pb.environment().put("PYTHONUNBUFFERED", "1");
            pb.environment().put("PYTHONIOENCODING", "UTF-8");
            pb.redirectErrorStream(false);

            process = pb.start();
            stdinWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            running.set(true);
            startStdoutListener();
            startStderrLogger(process.getErrorStream());

            Logger.info(TAG, "Python engine process started successfully: PID " + process.pid() + " using " + envInfo.executablePath());
            return true;
        } catch (IOException e) {
            Logger.error(TAG, "Failed to launch Python engine: " + e.getMessage(), e);
            shutdown();
            return false;
        }
    }

    private Path resolvePythonScript() {
        String userDir = System.getProperty("user.dir", ".");
        List<Path> candidateRoots = new ArrayList<>();
        candidateRoots.add(Paths.get(userDir));
        candidateRoots.add(Paths.get("."));

        try {
            Path codeSource = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (codeSource.getParent() != null) {
                candidateRoots.add(codeSource.getParent());
                if (codeSource.getParent().getParent() != null) {
                    candidateRoots.add(codeSource.getParent().getParent());
                }
            }
        } catch (Exception ignored) {}

        candidateRoots.add(Paths.get("E:", "Filee"));
        candidateRoots.add(Paths.get("E:", "Filee", "app"));

        List<Path> locations = new ArrayList<>();
        for (Path root : candidateRoots) {
            if (root == null) continue;
            locations.add(root.resolve("python/main.py"));
            locations.add(root.resolve("app/python/main.py"));
            locations.add(root.resolve("src/main/python/main.py"));
            locations.add(root.resolve("app/src/main/python/main.py"));
            locations.add(root.resolve("../app/src/main/python/main.py"));
            locations.add(root.resolve("../src/main/python/main.py"));
            locations.add(root.resolve("main.py"));
        }

        for (Path loc : locations) {
            if (Files.isRegularFile(loc)) {
                return loc.toAbsolutePath().normalize();
            }
        }

        URL res = getClass().getResource("/python/main.py");
        if (res != null) {
            try {
                Path resPath = Paths.get(res.toURI());
                if (Files.exists(resPath)) {
                    return resPath.toAbsolutePath().normalize();
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private void startStdoutListener() {
        readerExecutor.submit(() -> {
            try {
                String line;
                while (running.get() && (line = stdoutReader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        handleIncomingMessage(line);
                    }
                }
            } catch (IOException e) {
                if (running.get()) {
                    Logger.warn(TAG, "Stdout reader ended: " + e.getMessage());
                }
            }
        });
    }

    private void startStderrLogger(InputStream stderrStream) {
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stderrStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Logger.warn(TAG + "-STDERR", line);
                }
            } catch (IOException ignored) {
            }
        }, "PythonBridge-Stderr");
        stderrThread.setDaemon(true);
        stderrThread.start();
    }

    private void handleIncomingMessage(String rawJson) {
        try {
            JsonNode message = mapper.readTree(rawJson);

            // Check if this is an event (such as progress)
            String type = message.path("type").asText("");
            if ("event".equalsIgnoreCase(type)) {
                String event = message.path("event").asText("");
                if ("conversion_progress".equalsIgnoreCase(event)) {
                    String jobId = message.path("job_id").asText("");
                    double progress = message.path("progress").asDouble(0.0);
                    String stage = message.path("stage").asText("");
                    dispatchProgress(jobId, progress, stage);
                }
                return;
            }

            // Normal command response
            String id = message.path("id").asText(null);
            if (id != null) {
                CompletableFuture<JsonNode> future = pendingResponses.remove(id);
                if (future != null) {
                    future.complete(message);
                }
            }
        } catch (Exception e) {
            Logger.warn(TAG, "Unable to parse Python IPC message: " + rawJson);
        }
    }

    public void addProgressListener(ProgressListener listener) {
        if (listener != null) {
            progressListeners.add(listener);
        }
    }

    public void removeProgressListener(ProgressListener listener) {
        progressListeners.remove(listener);
    }

    private void dispatchProgress(String jobId, double progress, String stage) {
        for (ProgressListener listener : progressListeners) {
            try {
                listener.onProgress(jobId, progress, stage);
            } catch (Exception e) {
                Logger.warn(TAG, "Error in progress listener: " + e.getMessage());
            }
        }
    }

    public CompletableFuture<JsonNode> sendCommand(String command, ObjectNode params) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();

        if (!isRunning()) {
            // Attempt auto-restart if inactive
            if (!start()) {
                future.completeExceptionally(new IllegalStateException("Python engine is not active and could not be started"));
                return future;
            }
        }

        String messageId = String.valueOf(System.nanoTime());
        ObjectNode payload = mapper.createObjectNode();
        payload.put("id", messageId);
        payload.put("command", command);
        if (params != null) {
            payload.set("params", params);
        }

        pendingResponses.put(messageId, future);

        try {
            synchronized (stdinWriter) {
                stdinWriter.write(payload.toString());
                stdinWriter.newLine();
                stdinWriter.flush();
            }
        } catch (IOException e) {
            pendingResponses.remove(messageId);
            future.completeExceptionally(e);
        }

        return future;
    }

    public synchronized void shutdown() {
        running.set(false);
        try {
            if (stdinWriter != null) {
                stdinWriter.close();
            }
            if (stdoutReader != null) {
                stdoutReader.close();
            }
        } catch (IOException ignored) {
        }

        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }

        // Cancel any pending futures
        for (Map.Entry<String, CompletableFuture<JsonNode>> entry : pendingResponses.entrySet()) {
            entry.getValue().completeExceptionally(new IllegalStateException("PythonBridge shut down"));
        }
        pendingResponses.clear();

        readerExecutor.shutdownNow();
        Logger.info(TAG, "Python engine bridge terminated.");
    }

    public boolean isRunning() {
        return running.get() && process != null && process.isAlive();
    }
}
