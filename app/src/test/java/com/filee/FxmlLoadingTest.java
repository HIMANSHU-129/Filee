package com.filee;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class FxmlLoadingTest {

    private static final AtomicBoolean toolkitInitialized = new AtomicBoolean(false);

    @BeforeAll
    static void initJavaFX() throws Exception {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout initializing JavaFX toolkit");
            }
            toolkitInitialized.set(true);
        } catch (IllegalStateException e) {
            // Toolkit might already be initialized in test runner
            toolkitInitialized.set(true);
        }
    }

    @Test
    @DisplayName("main.fxml loads successfully without parsing errors")
    void testMainFxmlLoads() throws Exception {
        assertTrue(toolkitInitialized.get());

        URL fxmlUrl = getClass().getResource("/fxml/main.fxml");
        assertNotNull(fxmlUrl, "main.fxml resource should exist on classpath");

        CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] error = new Throwable[1];

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(fxmlUrl);
                loader.load();
            } catch (Throwable t) {
                error[0] = t;
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "FXML loading should finish within 5 seconds");
        assertNull(error[0], () -> "FXML loading failed with error: " + (error[0] != null ? error[0].getMessage() : ""));
    }
}
