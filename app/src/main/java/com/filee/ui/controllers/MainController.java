package com.filee.ui.controllers;

import com.filee.conversion.ConversionJob;
import com.filee.conversion.ConversionManager;
import com.filee.conversion.ConversionRequest;
import com.filee.conversion.ConversionResult;
import com.filee.conversion.ConversionStatus;
import com.filee.core.ApplicationManager;
import com.filee.core.FileManager;
import com.filee.core.FormatRegistry;
import com.filee.history.HistoryManager;
import com.filee.python.PythonEnvironment;
import com.filee.utils.PlatformUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.*;

public class MainController {

    @FXML private BorderPane rootPane;

    // Navigation Buttons
    @FXML private Button btnNavHome;
    @FXML private Button btnNavHistory;
    @FXML private Button btnNavSettings;
    @FXML private Button btnNavAbout;

    // Sidebar footer & status
    @FXML private Circle sidebarStatusDot;
    @FXML private Label lblSidebarPythonStatus;
    @FXML private Label lblSidebarPythonDetail;
    @FXML private Button btnToggleTheme;

    // Views container
    @FXML private StackPane contentArea;
    @FXML private VBox viewHome;
    @FXML private VBox viewHistory;
    @FXML private VBox viewSettings;
    @FXML private VBox viewAbout;

    // Home View - Empty State
    @FXML private VBox emptyStateSection;
    @FXML private VBox dropZone;
    @FXML private Button btnBrowseFiles;

    // Home View - Selected File State
    @FXML private VBox selectedFileSection;
    @FXML private Label lblFileCategoryBadge;
    @FXML private Label lblFileName;
    @FXML private Label lblFileExtension;
    @FXML private Label lblFileSize;
    @FXML private Label lblFileType;
    @FXML private Label lblFilePath;
    @FXML private SVGPath svgFileIcon;
    @FXML private Button btnAddFiles;
    @FXML private Button btnChangeFile;
    @FXML private Button btnRemoveFile;
    @FXML private FlowPane formatChipsContainer;
    @FXML private CheckBox chkMergePdf;
    @FXML private Button btnChangeOutputDir;
    @FXML private Label lblDestinationPath;
    @FXML private Button btnConvert;
    @FXML private Label lblConversionStatus;

    // Conversion Progress Section
    @FXML private VBox conversionSection;
    @FXML private ProgressBar conversionProgressBar;
    @FXML private Label lblProgressPercent;
    @FXML private Button btnCancelConversion;

    // Success Section
    @FXML private VBox successSection;
    @FXML private Label lblSuccessPath;
    @FXML private Button btnOpenFile;
    @FXML private Button btnOpenFolder;
    @FXML private Button btnConvertAnother;

    // Error Section
    @FXML private VBox errorSection;
    @FXML private Label lblErrorMessage;
    @FXML private Button btnRetryConversion;

    // History View
    @FXML private TableView<ConversionResult> historyTableView;
    @FXML private TableColumn<ConversionResult, String> colHistoryId;
    @FXML private TableColumn<ConversionResult, String> colHistoryStatus;
    @FXML private TableColumn<ConversionResult, String> colHistoryOutput;
    @FXML private TableColumn<ConversionResult, String> colHistoryDuration;
    @FXML private Button btnClearHistory;

    // Settings View
    @FXML private CheckBox chkOverwrite;
    @FXML private RadioButton radioDarkTheme;
    @FXML private RadioButton radioLightTheme;
    @FXML private Label lblSettingsPythonPath;
    @FXML private Label lblSettingsPythonVersion;

    // State
    private File selectedFile;
    private final List<File> selectedFiles = new ArrayList<>();
    private File customOutputDirectory;
    private String selectedTargetFormat;
    private ConversionJob currentJob;
    private boolean isDarkTheme = true;
    private final ObservableList<ConversionResult> historyItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupNavigation();
        setupHistoryTable();
        setupSettings();
        updatePythonStatus();
    }

    // --- Navigation Management ---

    private void setupNavigation() {
        showView(viewHome, btnNavHome);
    }

    @FXML
    public void handleNavHome(ActionEvent event) {
        showView(viewHome, btnNavHome);
    }

    @FXML
    public void handleNavHistory(ActionEvent event) {
        showView(viewHistory, btnNavHistory);
        refreshHistory();
    }

    @FXML
    public void handleNavSettings(ActionEvent event) {
        showView(viewSettings, btnNavSettings);
    }

    @FXML
    public void handleNavAbout(ActionEvent event) {
        showView(viewAbout, btnNavAbout);
    }

    private void showView(VBox targetView, Button targetButton) {
        VBox[] views = {viewHome, viewHistory, viewSettings, viewAbout};
        Button[] buttons = {btnNavHome, btnNavHistory, btnNavSettings, btnNavAbout};

        for (VBox view : views) {
            boolean match = (view == targetView);
            view.setVisible(match);
            view.setManaged(match);
        }

        for (Button btn : buttons) {
            btn.getStyleClass().remove("nav-button-active");
            if (btn == targetButton) {
                btn.getStyleClass().add("nav-button-active");
            }
        }
    }

    // --- Theme Management ---

    @FXML
    public void handleToggleTheme(ActionEvent event) {
        setTheme(!isDarkTheme);
    }

    private void setTheme(boolean dark) {
        isDarkTheme = dark;
        Scene scene = rootPane.getScene();
        if (scene != null) {
            if (dark) {
                scene.getRoot().getStyleClass().remove("theme-light");
                btnToggleTheme.setText("Toggle Light Theme");
                radioDarkTheme.setSelected(true);
            } else {
                if (!scene.getRoot().getStyleClass().contains("theme-light")) {
                    scene.getRoot().getStyleClass().add("theme-light");
                }
                btnToggleTheme.setText("Toggle Dark Theme");
                radioLightTheme.setSelected(true);
            }
        }
    }

    // --- Python Engine Status ---

    private void updatePythonStatus() {
        PythonEnvironment.EnvironmentInfo env = ApplicationManager.getInstance().getDetectedPythonEnv();
        if (env == null || env.status() == PythonEnvironment.DetectionStatus.NOT_FOUND) {
            sidebarStatusDot.setStyle("-fx-fill: #C85C5C;");
            lblSidebarPythonStatus.setText("Engine: Offline");
            lblSidebarPythonDetail.setText("Python not found");
            lblSettingsPythonPath.setText("No Python detected on system");
            lblSettingsPythonVersion.setText("Conversions require local Python 3");
        } else {
            sidebarStatusDot.setStyle("-fx-fill: #5B9A68;");
            lblSidebarPythonStatus.setText("Engine: Ready");
            String venvTag = env.status() == PythonEnvironment.DetectionStatus.VENV_DETECTED ? " (venv)" : "";
            lblSidebarPythonDetail.setText(env.versionString() + venvTag);
            lblSettingsPythonPath.setText(env.executablePath() != null ? env.executablePath().toString() : "Detected");
            lblSettingsPythonVersion.setText(env.versionString() + " — Ready for conversions");
        }
    }

    // --- Drag and Drop Handlers ---

    @FXML
    public void handleDragOver(DragEvent event) {
        if (event.getGestureSource() != dropZone && event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
        }
        event.consume();
    }

    @FXML
    public void handleDragEntered(DragEvent event) {
        if (event.getGestureSource() != dropZone && event.getDragboard().hasFiles()) {
            if (!dropZone.getStyleClass().contains("drop-zone-dragover")) {
                dropZone.getStyleClass().add("drop-zone-dragover");
            }
        }
        event.consume();
    }

    @FXML
    public void handleDragExited(DragEvent event) {
        dropZone.getStyleClass().remove("drop-zone-dragover");
        event.consume();
    }

    @FXML
    public void handleDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            List<File> files = db.getFiles();
            if (!files.isEmpty()) {
                setSelectedFiles(files);
                success = true;
            }
        }
        event.setDropCompleted(success);
        event.consume();
    }

    // --- File Selection & Metadata ---

    @FXML
    public void handleBrowseFiles(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File(s) to Convert - Filee");

        Window window = rootPane.getScene() != null ? rootPane.getScene().getWindow() : null;
        List<File> files = fileChooser.showOpenMultipleDialog(window);
        if (files != null && !files.isEmpty()) {
            setSelectedFiles(files);
        }
    }

    @FXML
    public void handleAddMoreFiles(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Add More Files to Selection - Filee");

        Window window = rootPane.getScene() != null ? rootPane.getScene().getWindow() : null;
        List<File> files = fileChooser.showOpenMultipleDialog(window);
        if (files != null && !files.isEmpty()) {
            for (File f : files) {
                if (f != null && f.exists() && f.isFile() && !this.selectedFiles.contains(f)) {
                    this.selectedFiles.add(f);
                }
            }
            updateMultipleFilesDisplay();
        }
    }

    @FXML
    public void handleToggleMergePdf(ActionEvent event) {
        if (selectedTargetFormat != null) {
            selectTargetFormat(selectedTargetFormat);
        }
    }

    private void updateMultipleFilesDisplay() {
        if (this.selectedFiles.isEmpty()) return;
        File primary = this.selectedFiles.get(0);
        if (this.selectedFiles.size() > 1) {
            int extra = this.selectedFiles.size() - 1;
            lblFileName.setText(primary.getName() + " (+" + extra + " more file" + (extra > 1 ? "s" : "") + " - " + selectedFiles.size() + " total)");
            long totalBytes = this.selectedFiles.stream().mapToLong(File::length).sum();
            lblFileSize.setText(formatFileSize(totalBytes));
        } else {
            lblFileName.setText(primary.getName());
            lblFileSize.setText(formatFileSize(primary.length()));
        }
        if (selectedTargetFormat != null) {
            selectTargetFormat(selectedTargetFormat);
        }
    }

    public void setSelectedFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        this.selectedFiles.clear();
        for (File f : files) {
            if (f != null && f.exists() && f.isFile()) {
                this.selectedFiles.add(f);
            }
        }

        if (this.selectedFiles.isEmpty()) {
            return;
        }

        File primary = this.selectedFiles.get(0);
        setSelectedFile(primary);

        updateMultipleFilesDisplay();
    }

    public void setSelectedFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return;
        }

        this.selectedFile = file;
        this.selectedTargetFormat = null;
        this.currentJob = null;
        btnConvert.setDisable(true);

        // Hide conversion / success / error sections
        hideConversionPanels();

        FileManager fileManager = ApplicationManager.getInstance().getFileManager();
        FormatRegistry registry = ApplicationManager.getInstance().getFormatRegistry();

        String extension = fileManager.extractExtension(file.toPath());
        FormatRegistry.Category category = registry.getCategory(extension);

        // Populate real metadata
        lblFileName.setText(file.getName());
        lblFileExtension.setText("." + extension.toUpperCase());
        lblFileSize.setText(formatFileSize(file.length()));
        lblFileType.setText(category.name() + " FILE");
        lblFilePath.setText(file.getAbsolutePath());
        lblFileCategoryBadge.setText(category.name());

        // Update category icon
        updateFileIcon(category);

        // Populate available target format chips
        populateFormatChips(extension, registry);

        updateDestinationPreview();
        lblConversionStatus.setText("Target format selection required");

        // Show selected file section
        emptyStateSection.setVisible(false);
        emptyStateSection.setManaged(false);
        selectedFileSection.setVisible(true);
        selectedFileSection.setManaged(true);
    }

    @FXML
    public void handleChangeOutputFolder(ActionEvent event) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Destination Folder - Filee");
        if (customOutputDirectory != null && customOutputDirectory.exists()) {
            dirChooser.setInitialDirectory(customOutputDirectory);
        } else if (selectedFile != null && selectedFile.getParentFile() != null) {
            dirChooser.setInitialDirectory(selectedFile.getParentFile());
        }

        Window window = rootPane.getScene() != null ? rootPane.getScene().getWindow() : null;
        File chosen = dirChooser.showDialog(window);
        if (chosen != null) {
            this.customOutputDirectory = chosen;
            updateDestinationPreview();
        }
    }

    private void updateDestinationPreview() {
        if (selectedFile == null) {
            lblDestinationPath.setText("No file selected.");
            return;
        }

        if (selectedTargetFormat == null) {
            String dirStr = customOutputDirectory != null
                ? customOutputDirectory.getAbsolutePath()
                : (selectedFile.getParent() != null ? selectedFile.getParent() : "Same folder as input");
            lblDestinationPath.setText("Output folder: " + dirStr + " (Select format above)");
            return;
        }

        FileManager fileManager = ApplicationManager.getInstance().getFileManager();
        boolean overwrite = chkOverwrite != null && chkOverwrite.isSelected();
        Path destination = fileManager.resolveDestinationPath(
            selectedFile.toPath(),
            selectedTargetFormat,
            customOutputDirectory != null ? customOutputDirectory.toPath() : null,
            overwrite
        );
        lblDestinationPath.setText(destination != null ? destination.toString() : "Resolving output path...");
    }

    private void updateFileIcon(FormatRegistry.Category category) {
        switch (category) {
            case IMAGE -> svgFileIcon.setContent("M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z");
            case AUDIO -> svgFileIcon.setContent("M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z");
            case VIDEO -> svgFileIcon.setContent("M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z");
            default -> svgFileIcon.setContent("M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z");
        }
    }

    private void populateFormatChips(String sourceExtension, FormatRegistry registry) {
        formatChipsContainer.getChildren().clear();
        Set<String> targetFormats = registry.getAvailableTargetFormats(sourceExtension);

        if (targetFormats.isEmpty()) {
            Label noTargetsLabel = new Label("No standard converters available for ." + sourceExtension);
            noTargetsLabel.setStyle("-fx-text-fill: #A0A4AA; -fx-font-style: italic;");
            formatChipsContainer.getChildren().add(noTargetsLabel);
            return;
        }

        for (String target : targetFormats) {
            Button chip = new Button("." + target.toUpperCase());
            chip.getStyleClass().add("format-chip");

            chip.setOnAction(e -> {
                for (Node node : formatChipsContainer.getChildren()) {
                    if (node instanceof Button b) {
                        b.getStyleClass().remove("format-chip-selected");
                    }
                }
                chip.getStyleClass().add("format-chip-selected");
                selectTargetFormat(target);
            });

            formatChipsContainer.getChildren().add(chip);
        }
    }

    private void selectTargetFormat(String targetFormat) {
        this.selectedTargetFormat = targetFormat;
        updateDestinationPreview();
        btnConvert.setDisable(false);

        boolean isMulti = selectedFiles.size() > 1;
        boolean isPdf = "pdf".equalsIgnoreCase(targetFormat);

        FileManager fileManager = ApplicationManager.getInstance().getFileManager();
        FormatRegistry registry = ApplicationManager.getInstance().getFormatRegistry();
        String srcExt = selectedFile != null ? fileManager.extractExtension(selectedFile.toPath()) : "";
        FormatRegistry.Category category = registry.getCategory(srcExt);

        if (chkMergePdf != null) {
            boolean canMerge = isMulti && isPdf && (category == FormatRegistry.Category.IMAGE);
            chkMergePdf.setVisible(canMerge);
            chkMergePdf.setManaged(canMerge);
        }

        if (isMulti && isPdf && (chkMergePdf == null || chkMergePdf.isSelected()) && category == FormatRegistry.Category.IMAGE) {
            btnConvert.setText("Merge " + selectedFiles.size() + " Images to PDF");
            lblConversionStatus.setText("Ready to merge " + selectedFiles.size() + " images into a single PDF");
        } else if (isMulti) {
            btnConvert.setText("Convert " + selectedFiles.size() + " Files to ." + targetFormat.toUpperCase());
            lblConversionStatus.setText("Ready to batch convert " + selectedFiles.size() + " files to ." + targetFormat.toUpperCase());
        } else if ("pdf".equalsIgnoreCase(srcExt) && !isPdf) {
            btnConvert.setText("Convert PDF to ." + targetFormat.toUpperCase());
            lblConversionStatus.setText("Ready to render PDF pages to ." + targetFormat.toUpperCase());
        } else {
            btnConvert.setText("Convert to ." + targetFormat.toUpperCase());
            lblConversionStatus.setText("Ready to convert: " + selectedFile.getName() + " -> ." + targetFormat.toUpperCase());
        }
    }

    @FXML
    public void handleResetFile(ActionEvent event) {
        this.selectedFile = null;
        this.selectedFiles.clear();
        this.selectedTargetFormat = null;
        this.customOutputDirectory = null;
        this.currentJob = null;

        if (chkMergePdf != null) {
            chkMergePdf.setVisible(false);
            chkMergePdf.setManaged(false);
        }

        formatChipsContainer.getChildren().clear();
        btnConvert.setDisable(true);
        btnConvert.setText("Convert File");
        lblConversionStatus.setText("Please choose a target format to proceed");

        hideConversionPanels();

        emptyStateSection.setVisible(true);
        emptyStateSection.setManaged(true);
        selectedFileSection.setVisible(false);
        selectedFileSection.setManaged(false);
    }

    private void hideConversionPanels() {
        if (conversionSection != null) {
            conversionSection.setVisible(false);
            conversionSection.setManaged(false);
        }
        if (successSection != null) {
            successSection.setVisible(false);
            successSection.setManaged(false);
        }
        if (errorSection != null) {
            errorSection.setVisible(false);
            errorSection.setManaged(false);
        }
    }

    // --- Conversion Execution & Progress ---

    @FXML
    public void handleStartConversion(ActionEvent event) {
        if (selectedFile == null || selectedTargetFormat == null) {
            return;
        }

        if (selectedFiles.size() > 1) {
            FileManager fileManager = ApplicationManager.getInstance().getFileManager();
            FormatRegistry registry = ApplicationManager.getInstance().getFormatRegistry();
            String srcExt = fileManager.extractExtension(selectedFile.toPath());
            FormatRegistry.Category category = registry.getCategory(srcExt);

            if ("pdf".equalsIgnoreCase(selectedTargetFormat) && (chkMergePdf == null || chkMergePdf.isSelected()) && category == FormatRegistry.Category.IMAGE) {
                startMergeImagesToPdf();
                return;
            }
            startBatchConversion();
            return;
        }

        // Show conversion UI, hide previous results
        hideConversionPanels();
        conversionSection.setVisible(true);
        conversionSection.setManaged(true);
        conversionProgressBar.setProgress(0.0);
        lblProgressPercent.setText("0%");
        btnConvert.setDisable(true);
        btnCancelConversion.setDisable(false);
        lblConversionStatus.setText("Preparing conversion to ." + selectedTargetFormat.toUpperCase() + "...");

        ConversionManager manager = ApplicationManager.getInstance().getConversionManager();

        Map<String, Object> options = new HashMap<>();
        boolean overwrite = chkOverwrite != null && chkOverwrite.isSelected();
        options.put("overwrite", overwrite);

        Path outDir = customOutputDirectory != null ? customOutputDirectory.toPath() : null;
        ConversionRequest request = new ConversionRequest(
            UUID.randomUUID().toString(),
            selectedFile.toPath(),
            selectedTargetFormat,
            outDir,
            options
        );

        ConversionJob job = manager.submitJob(request);
        this.currentJob = job;

        // Progress binding / listener
        job.progressProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                double p = newVal.doubleValue();
                conversionProgressBar.setProgress(p);
                lblProgressPercent.setText((int) Math.round(p * 100) + "%");
            });
        });

        // Status binding / listener
        job.statusProperty().addListener((obs, oldVal, newStatus) -> {
            Platform.runLater(() -> onJobStatusChanged(job, newStatus));
        });
    }

    private void onJobStatusChanged(ConversionJob job, ConversionStatus status) {
        switch (status) {
            case QUEUED -> lblConversionStatus.setText("Job queued...");
            case PREPARING -> lblConversionStatus.setText("Preparing conversion environment...");
            case CONVERTING -> lblConversionStatus.setText("Converting " + job.getSourceFile().getFileName() + " to ." + job.getTargetFormat().toUpperCase() + "...");
            case COMPLETED -> {
                conversionSection.setVisible(false);
                conversionSection.setManaged(false);
                successSection.setVisible(true);
                successSection.setManaged(true);
                errorSection.setVisible(false);
                errorSection.setManaged(false);
                btnConvert.setDisable(false);

                Path output = job.getOutputFile();
                lblSuccessPath.setText(output != null ? output.toString() : "Output file generated successfully.");
                lblConversionStatus.setText("Conversion completed successfully!");
            }
            case FAILED -> {
                conversionSection.setVisible(false);
                conversionSection.setManaged(false);
                successSection.setVisible(false);
                successSection.setManaged(false);
                errorSection.setVisible(true);
                errorSection.setManaged(true);
                btnConvert.setDisable(false);

                String msg = job.getErrorMessage();
                if (msg == null || msg.isBlank()) {
                    msg = (job.getResult() != null && job.getResult().errorMessage() != null)
                        ? job.getResult().errorMessage()
                        : "An error occurred during conversion.";
                }
                lblErrorMessage.setText(msg);
                lblConversionStatus.setText("Conversion failed: " + msg);
            }
            case CANCELLED -> {
                conversionSection.setVisible(false);
                conversionSection.setManaged(false);
                btnConvert.setDisable(false);
                lblConversionStatus.setText("Conversion was cancelled.");
            }
        }
    }

    @FXML
    public void handleCancelConversion(ActionEvent event) {
        if (currentJob != null) {
            btnCancelConversion.setDisable(true);
            lblConversionStatus.setText("Cancelling conversion...");
            ApplicationManager.getInstance().getConversionManager().cancelJob(currentJob.getId());
        }
    }

    @FXML
    public void handleOpenOutputFile(ActionEvent event) {
        if (currentJob != null && currentJob.getOutputFile() != null) {
            PlatformUtils.openFile(currentJob.getOutputFile());
        } else if (selectedFile != null) {
            PlatformUtils.openFile(selectedFile.toPath());
        }
    }

    @FXML
    public void handleOpenOutputFolder(ActionEvent event) {
        if (currentJob != null && currentJob.getOutputFile() != null) {
            PlatformUtils.openFolder(currentJob.getOutputFile());
        } else if (customOutputDirectory != null) {
            PlatformUtils.openFolder(customOutputDirectory.toPath());
        } else if (selectedFile != null && selectedFile.getParentFile() != null) {
            PlatformUtils.openFolder(selectedFile.getParentFile().toPath());
        }
    }

    private void startMergeImagesToPdf() {
        hideConversionPanels();
        conversionSection.setVisible(true);
        conversionSection.setManaged(true);
        conversionProgressBar.setProgress(0.0);
        lblProgressPercent.setText("0%");
        btnConvert.setDisable(true);
        btnCancelConversion.setDisable(false);

        int total = selectedFiles.size();
        lblConversionStatus.setText("Merging " + total + " images into a single PDF document...");

        ConversionManager manager = ApplicationManager.getInstance().getConversionManager();
        boolean overwrite = chkOverwrite != null && chkOverwrite.isSelected();
        Path outDir = customOutputDirectory != null ? customOutputDirectory.toPath() : null;

        List<Path> paths = selectedFiles.stream().map(File::toPath).toList();
        Map<String, Object> options = new HashMap<>();
        options.put("overwrite", overwrite);

        ConversionRequest request = new ConversionRequest(
            UUID.randomUUID().toString(),
            paths,
            "pdf",
            outDir,
            options
        );

        ConversionJob job = manager.submitJob(request);
        this.currentJob = job;

        job.progressProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                double p = newVal.doubleValue();
                conversionProgressBar.setProgress(p);
                lblProgressPercent.setText((int) Math.round(p * 100) + "%");
            });
        });

        job.statusProperty().addListener((obs, oldVal, newStatus) -> {
            Platform.runLater(() -> onJobStatusChanged(job, newStatus));
        });
    }

    private void startBatchConversion() {
        hideConversionPanels();
        conversionSection.setVisible(true);
        conversionSection.setManaged(true);
        conversionProgressBar.setProgress(0.0);
        lblProgressPercent.setText("0%");
        btnConvert.setDisable(true);
        btnCancelConversion.setDisable(false);

        List<File> filesToConvert = new ArrayList<>(selectedFiles);
        int totalFiles = filesToConvert.size();
        final int[] completedCount = {0};
        final int[] successCount = {0};

        ConversionManager manager = ApplicationManager.getInstance().getConversionManager();
        boolean overwrite = chkOverwrite != null && chkOverwrite.isSelected();
        Path outDir = customOutputDirectory != null ? customOutputDirectory.toPath() : null;

        lblConversionStatus.setText("Starting batch conversion (0/" + totalFiles + ")...");
        convertNextInBatch(filesToConvert, 0, totalFiles, completedCount, successCount, manager, overwrite, outDir);
    }

    private void convertNextInBatch(List<File> files, int index, int total, int[] completed, int[] success,
                                     ConversionManager manager, boolean overwrite, Path outDir) {
        if (index >= files.size()) {
            Platform.runLater(() -> {
                conversionSection.setVisible(false);
                conversionSection.setManaged(false);
                successSection.setVisible(true);
                successSection.setManaged(true);
                btnConvert.setDisable(false);
                lblSuccessPath.setText("Batch complete: " + success[0] + " of " + total + " converted successfully.");
                lblConversionStatus.setText("Batch conversion completed (" + success[0] + "/" + total + ").");
            });
            return;
        }

        File file = files.get(index);
        Map<String, Object> options = new HashMap<>();
        options.put("overwrite", overwrite);

        ConversionRequest request = new ConversionRequest(
            UUID.randomUUID().toString(),
            file.toPath(),
            selectedTargetFormat,
            outDir,
            options
        );

        ConversionJob job = manager.submitJob(request);
        this.currentJob = job;

        job.progressProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                double fileP = newVal.doubleValue();
                double overallP = (completed[0] + fileP) / (double) total;
                conversionProgressBar.setProgress(overallP);
                lblProgressPercent.setText((int) Math.round(overallP * 100) + "%");
                lblConversionStatus.setText("Converting (" + (completed[0] + 1) + "/" + total + "): " + file.getName());
            });
        });

        job.statusProperty().addListener((obs, oldVal, newStatus) -> {
            if (newStatus == ConversionStatus.COMPLETED || newStatus == ConversionStatus.FAILED || newStatus == ConversionStatus.CANCELLED) {
                completed[0]++;
                if (newStatus == ConversionStatus.COMPLETED) {
                    success[0]++;
                }
                if (newStatus == ConversionStatus.CANCELLED) {
                    Platform.runLater(() -> {
                        conversionSection.setVisible(false);
                        conversionSection.setManaged(false);
                        btnConvert.setDisable(false);
                        lblConversionStatus.setText("Batch conversion cancelled.");
                    });
                } else {
                    convertNextInBatch(files, index + 1, total, completed, success, manager, overwrite, outDir);
                }
            }
        });
    }

    // --- History View ---

    private void setupHistoryTable() {
        colHistoryId.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().id()));
        colHistoryStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().success() ? "Success" : "Failed"));
        colHistoryOutput.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().outputFile() != null ? data.getValue().outputFile().toString() : "None"));
        colHistoryDuration.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().durationMs() + " ms"));

        historyTableView.setItems(historyItems);
        historyTableView.setPlaceholder(new Label("No conversion history recorded in this session."));
    }

    private void refreshHistory() {
        historyItems.clear();
        HistoryManager historyManager = ApplicationManager.getInstance().getHistoryManager();
        if (historyManager != null) {
            historyItems.addAll(historyManager.getHistory());
        }
    }

    @FXML
    public void handleClearHistory(ActionEvent event) {
        historyItems.clear();
        HistoryManager historyManager = ApplicationManager.getInstance().getHistoryManager();
        if (historyManager != null) {
            historyManager.clear();
        }
    }

    // --- Settings View ---

    private void setupSettings() {
        ToggleGroup themeGroup = new ToggleGroup();
        radioDarkTheme.setToggleGroup(themeGroup);
        radioLightTheme.setToggleGroup(themeGroup);

        com.filee.core.SettingsManager settings = ApplicationManager.getInstance().getSettingsManager();
        if (settings != null) {
            boolean overwrite = Boolean.parseBoolean(settings.getProperty("conversion.overwrite_existing", "false"));
            if (chkOverwrite != null) {
                chkOverwrite.setSelected(overwrite);
                chkOverwrite.selectedProperty().addListener((obs, oldV, newV) -> {
                    settings.setProperty("conversion.overwrite_existing", String.valueOf(newV));
                    settings.saveToDisk();
                    updateDestinationPreview();
                });
            }
            String savedTheme = settings.getProperty("app.theme", "dark");
            setTheme("dark".equalsIgnoreCase(savedTheme));
        }

        radioDarkTheme.setOnAction(e -> {
            setTheme(true);
            if (settings != null) {
                settings.setProperty("app.theme", "dark");
                settings.saveToDisk();
            }
        });
        radioLightTheme.setOnAction(e -> {
            setTheme(false);
            if (settings != null) {
                settings.setProperty("app.theme", "light");
                settings.saveToDisk();
            }
        });
    }

    // --- Utilities ---

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return new DecimalFormat("#,##0.00").format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
