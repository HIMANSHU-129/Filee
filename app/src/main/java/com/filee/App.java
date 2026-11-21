package com.filee;

import com.filee.core.ApplicationManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class App extends Application {

    private static final String APP_TITLE = "Filee";
    private static final int DEFAULT_WIDTH = 880;
    private static final int DEFAULT_HEIGHT = 620;
    private static final int MIN_WIDTH = 720;
    private static final int MIN_HEIGHT = 500;

    @Override
    public void init() {
        // Initialize core application components before UI starts
        ApplicationManager.getInstance().initialize();
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        URL fxmlLocation = getClass().getResource("/fxml/main.fxml");
        if (fxmlLocation == null) {
            throw new IllegalStateException("Cannot locate FXML resource: /fxml/main.fxml");
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // Apply theme stylesheets
        applyStylesheets(scene);

        primaryStage.setTitle(APP_TITLE);
        try {
            var iconStream = getClass().getResourceAsStream("/assets/logo.png");
            if (iconStream != null) {
                primaryStage.getIcons().add(new javafx.scene.image.Image(iconStream));
            }
        } catch (Exception ignored) {}
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
        primaryStage.show();

        // If files were passed via CLI / Windows Explorer context menu
        Parameters params = getParameters();
        if (params != null && !params.getRaw().isEmpty()) {
            java.util.List<java.io.File> cliFiles = new java.util.ArrayList<>();
            for (String arg : params.getRaw()) {
                java.io.File f = new java.io.File(arg);
                if (f.exists() && f.isFile()) {
                    cliFiles.add(f);
                }
            }
            if (!cliFiles.isEmpty()) {
                com.filee.ui.controllers.MainController controller = loader.getController();
                if (controller != null) {
                    controller.setSelectedFiles(cliFiles);
                }
            }
        }
    }

    private void applyStylesheets(Scene scene) {
        String[] styleFiles = {
            "/css/variables.css",
            "/css/theme-dark.css",
            "/css/theme-light.css",
            "/css/main.css"
        };

        for (String stylePath : styleFiles) {
            URL resource = getClass().getResource(stylePath);
            if (resource != null) {
                scene.getStylesheets().add(resource.toExternalForm());
            }
        }
    }

    @Override
    public void stop() {
        // Clean shutdown of background threads and helper processes
        ApplicationManager.getInstance().shutdown();
    }

    public static void launchApplication(String[] args) {
        launch(args);
    }
}
