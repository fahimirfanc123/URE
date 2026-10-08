package gui;

import core.disk.SectorReader;
import core.prediction.RecoveryPrediction;
import core.recovery.RecoveryWriter;
import filesystem.fat32.FAT32BootSector;
import filesystem.fat32.FAT32DirectoryEntry;
import filesystem.fat32.FAT32DirectoryScanner;
import filesystem.fat32.FAT32FileRecovery;
import filesystem.fat32.FAT32RecoveryResult;
import filesystem.fat32.FAT32RecoveryStatus;
import filesystem.fat32.FAT32RobustRecovery;
import gui.report.RecoveryReport;
import gui.report.RecoveryReportItem;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Module 7 — FAT32 Deleted File Recovery
 *
 * Discovers deleted FAT32 directory entries and reconstructs files from clusters.
 * Enhanced with real-time filtering, sorting, batch recovery, safe preview,
 * live summary metrics, and report export.
 */
public class FAT32RecoveryView extends VBox {

    private final Stage stage;
    private final File source;
    private final Runnable onBack;

    // Discovered data & state
    private final List<FAT32DirectoryEntry> allDiscoveredFiles = new ArrayList<>();
    private final Set<FAT32DirectoryEntry> selectedFiles = new LinkedHashSet<>();
    private final Map<FAT32DirectoryEntry, String> recoveryStatusMap = new HashMap<>();
    private final Map<FAT32DirectoryEntry, Button> cardRecoverButtons = new HashMap<>();

    // Active Filters and Sorting
    private String selectedFormatFilter = "ALL";
    private String selectedSort = "CONFIDENCE_DESC";

    // Summary metrics UI
    private final Label metricFoundValue;
    private final Label metricSelectedValue;
    private final Label metricRecoveredValue;
    private final Label metricFailedValue;
    private final Label metricSizeValue;

    // UI Structure
    private final VBox filesContainer;
    private final Label statusLabel;
    private final ProgressIndicator loadingIndicator;
    private final Button scanButton;
    private final Button recoverSelectedButton;
    private final Button exportReportButton;
    private final ComboBox<String> sortComboBox;

    public FAT32RecoveryView(
            Stage stage,
            File source,
            Runnable onBack
    ) {
        this.stage = stage;
        this.source = source;
        this.onBack = onBack;

        setMinWidth(0);
        setMinHeight(0);
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        setSpacing(14);
        setPadding(new Insets(24, 40, 24, 40));

        getStyleClass().add("fat32-recovery-page");

        /*
         * HEADER
         */
        Label module = new Label("MODULE 07  /  FAT32 RECOVERY");
        module.getStyleClass().add("fat32-module-label");

        Label title = new Label("DELETED FILE RECOVERY");
        title.getStyleClass().add("fat32-recovery-title");

        Label subtitle = new Label("Deleted FAT32 directory entries detected by MINION URE.");
        subtitle.getStyleClass().add("fat32-recovery-subtitle");

        VBox header = new VBox(4, module, title, subtitle);

        /*
         * SOURCE
         */
        Label sourceTitle = new Label("SOURCE");
        sourceTitle.getStyleClass().add("fat32-small-label");

        Label sourceName = new Label(source.getName());
        sourceName.getStyleClass().add("fat32-source-name");

        Label sourcePath = new Label(source.getAbsolutePath());
        sourcePath.getStyleClass().add("fat32-source-path");

        VBox sourcePanel = new VBox(4, sourceTitle, sourceName, sourcePath);
        sourcePanel.getStyleClass().add("fat32-source-panel");

        /*
         * SUMMARY METRICS PANEL
         */
        metricFoundValue = new Label("0");
        metricSelectedValue = new Label("0");
        metricRecoveredValue = new Label("0");
        metricFailedValue = new Label("0");
        metricSizeValue = new Label("0 B");

        HBox summaryMetrics = new HBox(12,
                createMetricCard("FILES FOUND", metricFoundValue),
                createMetricCard("SELECTED", metricSelectedValue),
                createMetricCard("RECOVERED", metricRecoveredValue),
                createMetricCard("FAILED", metricFailedValue),
                createMetricCard("TOTAL SIZE", metricSizeValue)
        );
        summaryMetrics.setFillHeight(true);

        VBox summaryPanel = new VBox(8, summaryMetrics);
        summaryPanel.getStyleClass().add("recovery-summary-panel");

        /*
         * STATUS & SCAN ACTION BAR
         */
        statusLabel = new Label("SCANNING FAT32 DIRECTORY...");
        statusLabel.getStyleClass().add("fat32-status");

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(20, 20);

        scanButton = new Button("RE-SCAN DIRECTORY");
        scanButton.getStyleClass().add("carving-scan-button");
        scanButton.setOnAction(event -> startDeletedFileScan());

        exportReportButton = new Button("EXPORT REPORT");
        exportReportButton.getStyleClass().add("report-export-button");
        exportReportButton.setDisable(true);
        exportReportButton.setOnAction(event -> exportReport());

        Region statusSpacer = new Region();
        HBox.setHgrow(statusSpacer, Priority.ALWAYS);

        HBox statusBox = new HBox(10, loadingIndicator, statusLabel, statusSpacer, exportReportButton, scanButton);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        /*
         * FILTER & SORT TOOLBAR
         */
        recoverSelectedButton = new Button("RECOVER SELECTED (0)");
        recoverSelectedButton.getStyleClass().add("batch-recover-button");
        recoverSelectedButton.setDisable(true);
        recoverSelectedButton.setOnAction(event -> recoverSelectedFiles());

        sortComboBox = new ComboBox<>();
        sortComboBox.getItems().addAll(
                "Confidence: High → Low",
                "Size: Large → Small",
                "Cluster: Low → High"
        );
        sortComboBox.setValue("Confidence: High → Low");
        sortComboBox.getStyleClass().add("sort-combo-box");
        sortComboBox.setOnAction(event -> {
            String val = sortComboBox.getValue();
            if (val != null) {
                if (val.startsWith("Confidence")) {
                    selectedSort = "CONFIDENCE_DESC";
                } else if (val.startsWith("Size")) {
                    selectedSort = "SIZE_DESC";
                } else if (val.startsWith("Cluster")) {
                    selectedSort = "CLUSTER_ASC";
                }
                refreshDisplayedResults();
            }
        });

        HBox filterBar = buildFilterToolbar();

        /*
         * FILE LIST CONTAINER
         */
        filesContainer = new VBox(10);
        filesContainer.setFillWidth(true);

        ScrollPane scrollPane = new ScrollPane(filesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("fat32-scroll");

        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        /*
         * FOOTER
         */
        Button backButton = new Button("BACK");
        backButton.getStyleClass().add("fat32-back-button");
        backButton.setOnAction(event -> {
            if (onBack != null) {
                onBack.run();
            }
        });

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);

        HBox footer = new HBox(12, backButton, footerSpacer, recoverSelectedButton);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(
                header,
                sourcePanel,
                summaryPanel,
                statusBox,
                filterBar,
                scrollPane,
                footer
        );

        // Start initial scan
        startDeletedFileScan();
    }

    private HBox buildFilterToolbar() {
        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("filter-bar");

        Label formatFilterLabel = new Label("FORMAT:");
        formatFilterLabel.getStyleClass().add("filter-section-label");

        HBox formatPills = new HBox(4);
        String[] formats = {"ALL", "JPEG", "PNG", "PDF", "ZIP", "OTHER"};
        for (String fmt : formats) {
            Button pill = new Button(fmt);
            pill.getStyleClass().add(fmt.equals(selectedFormatFilter) ? "filter-pill-active" : "filter-pill");
            pill.setOnAction(event -> {
                selectedFormatFilter = fmt;
                updatePillStyles(formatPills, selectedFormatFilter);
                refreshDisplayedResults();
            });
            formatPills.getChildren().add(pill);
        }

        Button selectAllBtn = new Button("SELECT ALL");
        selectAllBtn.getStyleClass().add("selection-action-btn");
        selectAllBtn.setOnAction(event -> selectAllFiltered());

        Button clearSelBtn = new Button("CLEAR");
        clearSelBtn.getStyleClass().add("selection-action-btn");
        clearSelBtn.setOnAction(event -> clearSelection());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label sortLabel = new Label("SORT:");
        sortLabel.getStyleClass().add("filter-section-label");

        toolbar.getChildren().addAll(
                formatFilterLabel,
                formatPills,
                selectAllBtn,
                clearSelBtn,
                spacer,
                sortLabel,
                sortComboBox
        );

        return toolbar;
    }

    private void updatePillStyles(HBox pillContainer, String activeText) {
        for (javafx.scene.Node node : pillContainer.getChildren()) {
            if (node instanceof Button btn) {
                btn.getStyleClass().removeAll("filter-pill-active", "filter-pill");
                btn.getStyleClass().add(btn.getText().equalsIgnoreCase(activeText) ? "filter-pill-active" : "filter-pill");
            }
        }
    }

    private VBox createMetricCard(String title, Label valueLabel) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("summary-metric-title");

        valueLabel.getStyleClass().add("summary-metric-value");

        VBox card = new VBox(3, titleLabel, valueLabel);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("summary-metric-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private void startDeletedFileScan() {
        loadingIndicator.setVisible(true);
        scanButton.setDisable(true);
        exportReportButton.setDisable(true);
        statusLabel.setText("SCANNING FAT32 ROOT DIRECTORY...");
        filesContainer.getChildren().clear();
        allDiscoveredFiles.clear();
        selectedFiles.clear();
        recoveryStatusMap.clear();
        cardRecoverButtons.clear();
        updateSummaryMetrics();

        Task<List<FAT32DirectoryEntry>> task = new Task<>() {
            @Override
            protected List<FAT32DirectoryEntry> call() throws Exception {
                try (SectorReader reader = new SectorReader(source.getAbsolutePath())) {
                    FAT32BootSector boot = FAT32BootSector.parse(reader);
                    FAT32DirectoryScanner scanner = new FAT32DirectoryScanner(reader, boot);
                    return scanner.scanRootDirectory();
                }
            }
        };

        task.setOnSucceeded(event -> {
            loadingIndicator.setVisible(false);
            scanButton.setDisable(false);

            List<FAT32DirectoryEntry> files = task.getValue();
            allDiscoveredFiles.addAll(files);

            if (files.isEmpty()) {
                statusLabel.setText("NO DELETED FILES FOUND");
                exportReportButton.setDisable(true);
                showEmptyState();
                updateSummaryMetrics();
                return;
            }

            exportReportButton.setDisable(false);
            statusLabel.setText(
                    files.size() + (files.size() == 1 ? " DELETED FILE FOUND" : " DELETED FILES FOUND")
            );

            refreshDisplayedResults();
        });

        task.setOnFailed(event -> {
            loadingIndicator.setVisible(false);
            scanButton.setDisable(false);
            exportReportButton.setDisable(true);
            statusLabel.setText("FAT32 SCAN FAILED");

            Label error = new Label(
                    task.getException() != null ? task.getException().getMessage() : "Unknown FAT32 error"
            );
            error.getStyleClass().add("fat32-error");
            filesContainer.getChildren().add(error);

            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
        });

        Thread thread = new Thread(task, "minion-ure-fat32-scan");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshDisplayedResults() {
        filesContainer.getChildren().clear();
        cardRecoverButtons.clear();

        List<FAT32DirectoryEntry> filtered = allDiscoveredFiles.stream()
                .filter(this::matchesFilter)
                .collect(Collectors.toCollection(ArrayList::new));

        Comparator<FAT32DirectoryEntry> comparator;
        switch (selectedSort) {
            case "SIZE_DESC":
                comparator = Comparator.comparingLong(FAT32DirectoryEntry::getFileSize).reversed();
                break;
            case "CLUSTER_ASC":
                comparator = Comparator.comparingLong(FAT32DirectoryEntry::getStartCluster);
                break;
            case "CONFIDENCE_DESC":
            default:
                comparator = Comparator.comparingDouble((FAT32DirectoryEntry e) ->
                        e.getPrediction() != null ? e.getPrediction().getProbability() : 0.0).reversed();
                break;
        }
        filtered.sort(comparator);

        if (filtered.isEmpty()) {
            if (allDiscoveredFiles.isEmpty()) {
                showEmptyState();
            } else {
                showNoFilterMatchesState();
            }
        } else {
            for (FAT32DirectoryEntry entry : filtered) {
                filesContainer.getChildren().add(createFileCard(entry));
            }
        }

        updateSummaryMetrics();
    }

    private boolean matchesFilter(FAT32DirectoryEntry entry) {
        if ("ALL".equalsIgnoreCase(selectedFormatFilter)) {
            return true;
        }
        String format = detectFormat(entry.getFileName());
        return format.equalsIgnoreCase(selectedFormatFilter);
    }

    private String detectFormat(String fileName) {
        if (fileName == null) return "OTHER";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "JPEG";
        if (lower.endsWith(".png")) return "PNG";
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".zip")) return "ZIP";
        return "OTHER";
    }

    private void selectAllFiltered() {
        for (FAT32DirectoryEntry entry : allDiscoveredFiles) {
            if (matchesFilter(entry)) {
                selectedFiles.add(entry);
            }
        }
        refreshDisplayedResults();
    }

    private void clearSelection() {
        selectedFiles.clear();
        refreshDisplayedResults();
    }

    private void updateSummaryMetrics() {
        int found = allDiscoveredFiles.size();
        int selected = selectedFiles.size();
        int recovered = 0;
        int failed = 0;
        long totalBytes = 0;

        for (FAT32DirectoryEntry entry : allDiscoveredFiles) {
            String st = recoveryStatusMap.get(entry);
            if ("RECOVERED".equals(st)) {
                recovered++;
            } else if ("FAILED".equals(st)) {
                failed++;
            }
            totalBytes += entry.getFileSize();
        }

        metricFoundValue.setText(String.valueOf(found));
        metricSelectedValue.setText(String.valueOf(selected));
        metricRecoveredValue.setText(String.valueOf(recovered));
        metricFailedValue.setText(String.valueOf(failed));
        metricSizeValue.setText(formatBytes(totalBytes));

        recoverSelectedButton.setText("RECOVER SELECTED (" + selected + ")");
        recoverSelectedButton.setDisable(selected == 0);
    }

    private VBox createFileCard(FAT32DirectoryEntry entry) {
        CheckBox selectBox = new CheckBox();
        selectBox.getStyleClass().add("recovery-checkbox");
        selectBox.setSelected(selectedFiles.contains(entry));
        selectBox.setOnAction(event -> {
            if (selectBox.isSelected()) {
                selectedFiles.add(entry);
            } else {
                selectedFiles.remove(entry);
            }
            updateSummaryMetrics();
        });

        Label formatBadge = new Label(detectFormat(entry.getFileName()));
        formatBadge.getStyleClass().add("carving-format-badge");

        Label name = new Label(entry.getFileName());
        name.getStyleClass().add("fat32-file-name");

        Label deleted = new Label("DELETED");
        deleted.getStyleClass().add("fat32-deleted-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label recoveryStatusBadge = new Label();
        String currentStatus = recoveryStatusMap.getOrDefault(entry, "PENDING");
        if ("RECOVERED".equals(currentStatus)) {
            recoveryStatusBadge.setText("● RECOVERED");
            recoveryStatusBadge.getStyleClass().add("recovery-status-recovered");
        } else if ("FAILED".equals(currentStatus)) {
            recoveryStatusBadge.setText("● FAILED");
            recoveryStatusBadge.getStyleClass().add("recovery-status-failed");
        }

        HBox heading = new HBox(10, selectBox, formatBadge, name, spacer, deleted);
        if (!currentStatus.equals("PENDING")) {
            heading.getChildren().add(recoveryStatusBadge);
        }
        heading.setAlignment(Pos.CENTER_LEFT);

        Label size = new Label("SIZE  " + formatBytes(entry.getFileSize()));
        Label cluster = new Label("START CLUSTER  " + entry.getStartCluster());
        size.getStyleClass().add("fat32-file-info");
        cluster.getStyleClass().add("fat32-file-info");

        HBox metadata = new HBox(22, size, cluster);

        if (entry.getPrediction() != null) {
            RecoveryPrediction prediction = entry.getPrediction();
            Label confidenceLabel = new Label(String.format(
                    "CONFIDENCE  %.0f%% (%s)",
                    prediction.getProbability(),
                    prediction.getRating()
            ));
            confidenceLabel.getStyleClass().add("fat32-file-info");
            metadata.getChildren().add(confidenceLabel);
        }

        javafx.scene.Node previewNode = createSafePreview(entry);

        Button recover = new Button("RECOVER FILE");
        recover.getStyleClass().add("fat32-recover-button");
        if ("RECOVERED".equals(currentStatus)) {
            recover.setText("RECOVERED");
        }
        recover.setOnAction(event -> chooseOutput(entry, recover));
        cardRecoverButtons.put(entry, recover);

        VBox card = new VBox(10, heading, metadata, previewNode, recover);
        card.getStyleClass().add("fat32-file-card");

        return card;
    }

    private javafx.scene.Node createSafePreview(FAT32DirectoryEntry entry) {
        String fmt = detectFormat(entry.getFileName());
        HBox previewBox = new HBox(12);
        previewBox.setAlignment(Pos.CENTER_LEFT);
        previewBox.getStyleClass().add("preview-container");

        if ("JPEG".equals(fmt) || "PNG".equals(fmt)) {
            StackPane imageHolder = new StackPane();
            imageHolder.setPrefSize(140, 90);
            imageHolder.setMinSize(140, 90);
            imageHolder.setMaxSize(140, 90);
            imageHolder.getStyleClass().add("preview-image-frame");

            Label loadingLabel = new Label("Loading Preview...");
            loadingLabel.getStyleClass().add("preview-loading-text");
            imageHolder.getChildren().add(loadingLabel);

            Task<Image> imgTask = new Task<>() {
                @Override
                protected Image call() {
                    try (SectorReader reader = new SectorReader(source.getAbsolutePath())) {
                        FAT32BootSector boot = FAT32BootSector.parse(reader);
                        FAT32RobustRecovery recovery = new FAT32RobustRecovery(reader, boot);
                        FAT32RecoveryResult result = recovery.recover(entry);
                        if (result == null || result.getData() == null || result.getData().length == 0) return null;
                        int maxLen = Math.min(result.getData().length, 2 * 1024 * 1024);
                        Image img = new Image(new ByteArrayInputStream(result.getData(), 0, maxLen), 140, 90, true, true);
                        if (img.isError()) return null;
                        return img;
                    } catch (Throwable t) {
                        return null;
                    }
                }
            };

            imgTask.setOnSucceeded(ev -> {
                Image img = imgTask.getValue();
                imageHolder.getChildren().clear();
                if (img != null && !img.isError()) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(140);
                    iv.setFitHeight(90);
                    iv.setPreserveRatio(true);
                    iv.getStyleClass().add("preview-image");
                    imageHolder.getChildren().add(iv);
                } else {
                    Label fail = new Label("Image Preview Unavailable");
                    fail.getStyleClass().add("preview-fail-text");
                    imageHolder.getChildren().add(fail);
                }
            });

            imgTask.setOnFailed(ev -> {
                imageHolder.getChildren().clear();
                Label fail = new Label("Preview Unavailable");
                fail.getStyleClass().add("preview-fail-text");
                imageHolder.getChildren().add(fail);
            });

            Thread th = new Thread(imgTask, "minion-fat32-preview-loader");
            th.setDaemon(true);
            th.start();

            Label metaDetails = new Label(String.format("IMAGE METRICS\nFormat: %s\nCluster: %d\nSize: %s",
                    fmt, entry.getStartCluster(), formatBytes(entry.getFileSize())));
            metaDetails.getStyleClass().add("preview-meta-text");

            previewBox.getChildren().addAll(imageHolder, metaDetails);
            return previewBox;

        } else if ("PDF".equals(fmt)) {
            Label icon = new Label("📄");
            icon.getStyleClass().add("preview-doc-icon");

            Label docInfo = new Label(String.format("PDF DOCUMENT METADATA\nCluster: %d\nFile Size: %s\nStatus: Deleted FAT32 Entry",
                    entry.getStartCluster(), formatBytes(entry.getFileSize())));
            docInfo.getStyleClass().add("preview-meta-text");

            previewBox.getChildren().addAll(icon, docInfo);
            return previewBox;

        } else if ("ZIP".equals(fmt)) {
            Label icon = new Label("🗜");
            icon.getStyleClass().add("preview-doc-icon");

            Label zipInfo = new Label(String.format("ZIP ARCHIVE METADATA\nCluster: %d\nFile Size: %s\nStatus: Deleted FAT32 Entry",
                    entry.getStartCluster(), formatBytes(entry.getFileSize())));
            zipInfo.getStyleClass().add("preview-meta-text");

            previewBox.getChildren().addAll(icon, zipInfo);
            return previewBox;

        } else {
            // Text or binary generic
            String lower = entry.getFileName().toLowerCase();
            if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".csv") || lower.endsWith(".json")) {
                Label icon = new Label("📝");
                icon.getStyleClass().add("preview-doc-icon");

                Label textPreview = new Label(String.format("TEXT DOCUMENT\nCluster: %d\nFile Size: %s",
                        entry.getStartCluster(), formatBytes(entry.getFileSize())));
                textPreview.getStyleClass().add("preview-meta-text");

                previewBox.getChildren().addAll(icon, textPreview);
                return previewBox;
            }

            Label icon = new Label("💾");
            icon.getStyleClass().add("preview-doc-icon");

            Label binInfo = new Label(String.format("BINARY FILE METADATA\nCluster: %d\nFile Size: %s",
                    entry.getStartCluster(), formatBytes(entry.getFileSize())));
            binInfo.getStyleClass().add("preview-meta-text");

            previewBox.getChildren().addAll(icon, binInfo);
            return previewBox;
        }
    }

    private void chooseOutput(FAT32DirectoryEntry entry, Button button) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Recovered FAT32 File");
        String suggestedName = entry.getFileName().replace('?', '_');
        chooser.setInitialFileName(suggestedName);

        File output = chooser.showSaveDialog(stage);
        if (output == null) {
            return;
        }

        recoverFile(entry, output.toPath(), button);
    }

    private void recoverFile(FAT32DirectoryEntry entry, Path output, Button button) {
        button.setDisable(true);
        button.setText("RECOVERING...");
        statusLabel.setText("RECOVERING " + entry.getFileName() + "...");

        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                try (SectorReader reader = new SectorReader(source.getAbsolutePath())) {
                    FAT32BootSector boot = FAT32BootSector.parse(reader);
                    FAT32RobustRecovery recovery = new FAT32RobustRecovery(reader, boot);
                    FAT32RecoveryResult result = recovery.recover(entry);

                    if (result == null || result.getData() == null) {
                        throw new IOException("Recovery failed: " + result.getStatus());
                    }

                    RecoveryWriter writer = new RecoveryWriter();
                    return writer.write(result.getData(), output);
                }
            }
        };

        task.setOnSucceeded(event -> {
            button.setText("RECOVERED");
            recoveryStatusMap.put(entry, "RECOVERED");
            updateSummaryMetrics();
            Path result = task.getValue();
            statusLabel.setText("RECOVERY COMPLETE  •  " + result.getFileName());
            refreshDisplayedResults();
        });

        task.setOnFailed(event -> {
            button.setDisable(false);
            button.setText("RETRY RECOVERY");
            recoveryStatusMap.put(entry, "FAILED");
            updateSummaryMetrics();
            statusLabel.setText("RECOVERY FAILED");

            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
            refreshDisplayedResults();
        });

        Thread thread = new Thread(task, "minion-ure-fat32-recovery");
        thread.setDaemon(true);
        thread.start();
    }

    private void recoverSelectedFiles() {
        if (selectedFiles.isEmpty()) {
            return;
        }

        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Output Directory for Recovered FAT32 Files");
        File targetDir = dirChooser.showDialog(stage);
        if (targetDir == null) {
            return;
        }

        recoverSelectedButton.setDisable(true);
        loadingIndicator.setVisible(true);
        statusLabel.setText("RECOVERING " + selectedFiles.size() + " SELECTED FILES...");

        List<FAT32DirectoryEntry> toRecover = new ArrayList<>(selectedFiles);

        Task<Void> batchTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (SectorReader reader = new SectorReader(source.getAbsolutePath())) {
                    FAT32BootSector boot = FAT32BootSector.parse(reader);
                    FAT32RobustRecovery recovery = new FAT32RobustRecovery(reader, boot);
                    RecoveryWriter writer = new RecoveryWriter();

                    for (FAT32DirectoryEntry entry : toRecover) {
                        String filename = entry.getFileName().replace('?', '_');
                        Path targetFile = targetDir.toPath().resolve(filename);
                        try {
                            FAT32RecoveryResult result = recovery.recover(entry);
                            if (result != null && result.getData() != null) {
                                writer.write(result.getData(), targetFile);
                                recoveryStatusMap.put(entry, "RECOVERED");
                            } else {
                                recoveryStatusMap.put(entry, result != null
                                    ? result.getStatus().name()
                                    : "FAILED");
                            }
                        } catch (Exception ex) {
                            recoveryStatusMap.put(entry, "FAILED");
                            ex.printStackTrace();
                        }
                    }
                }
                return null;
            }
        };

        batchTask.setOnSucceeded(event -> {
            loadingIndicator.setVisible(false);
            recoverSelectedButton.setDisable(false);
            updateSummaryMetrics();
            statusLabel.setText("BATCH RECOVERY COMPLETE  •  Saved to " + targetDir.getName());
            refreshDisplayedResults();
        });

        batchTask.setOnFailed(event -> {
            loadingIndicator.setVisible(false);
            recoverSelectedButton.setDisable(false);
            updateSummaryMetrics();
            statusLabel.setText("BATCH RECOVERY COMPLETED WITH ERRORS");
            refreshDisplayedResults();
        });

        Thread thread = new Thread(batchTask, "minion-ure-fat32-batch-recovery");
        thread.setDaemon(true);
        thread.start();
    }

    private void exportReport() {
        if (allDiscoveredFiles.isEmpty()) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Recovery Report");
        fileChooser.setInitialFileName("minion-ure-fat32-report.txt");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Report (*.txt)", "*.txt"),
                new FileChooser.ExtensionFilter("CSV Report (*.csv)", "*.csv")
        );

        File saveFile = fileChooser.showSaveDialog(stage);
        if (saveFile == null) {
            return;
        }

        List<RecoveryReportItem> reportItems = new ArrayList<>();
        int recovered = 0;
        int failed = 0;
        long totalBytes = 0;

        for (FAT32DirectoryEntry entry : allDiscoveredFiles) {
            String name = entry.getFileName().replace('?', '_');
            String recStatus = recoveryStatusMap.getOrDefault(entry, "DISCOVERED");
            if ("RECOVERED".equals(recStatus)) recovered++;
            if ("FAILED".equals(recStatus)) failed++;
            totalBytes += entry.getFileSize();

            String conf = entry.getPrediction() != null
                    ? String.format("%.0f%% (%s)", entry.getPrediction().getProbability(), entry.getPrediction().getRating())
                    : "N/A";

            String path = entry.getPath() != null ? entry.getPath() : "ROOT";

            reportItems.add(new RecoveryReportItem(
                    name,
                    detectFormat(entry.getFileName()),
                    entry.getStartCluster(),
                    "Cluster " + entry.getStartCluster() + " | Path: " + path,
                    entry.getFileSize(),
                    "FAT32",
                    conf,
                    recStatus
            ));
        }

        RecoveryReport report = new RecoveryReport(
                source.getName(),
                source.getAbsolutePath(),
                "FAT32 FILE RECOVERY",
                allDiscoveredFiles.size(),
                selectedFiles.size(),
                recovered,
                failed,
                totalBytes,
                reportItems
        );

        try {
            report.export(saveFile.toPath());
            statusLabel.setText("REPORT EXPORTED  •  " + saveFile.getName());
        } catch (IOException e) {
            statusLabel.setText("FAILED TO EXPORT REPORT");
            e.printStackTrace();
        }
    }

    private void showEmptyState() {
        Label title = new Label("NO DELETED FAT32 FILES DETECTED");
        title.getStyleClass().add("fat32-empty-title");

        Label description = new Label(
                "MINION URE did not find deleted directory entries in the FAT32 filesystem. " +
                "Scanned root directory and subdirectories for deleted files."
        );
        description.setWrapText(true);
        description.getStyleClass().add("fat32-empty-description");

        VBox empty = new VBox(8, title, description);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(50));

        filesContainer.getChildren().add(empty);
    }

    private void showNoFilterMatchesState() {
        Label title = new Label("NO FILES MATCH ACTIVE FILTER");
        title.getStyleClass().add("fat32-empty-title");

        Label description = new Label(
                "No deleted FAT32 files match the selected format (" + selectedFormatFilter + ") filter."
        );
        description.setWrapText(true);
        description.getStyleClass().add("fat32-empty-description");

        VBox empty = new VBox(8, title, description);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(40));

        filesContainer.getChildren().add(empty);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
