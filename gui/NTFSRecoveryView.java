package gui;

import core.disk.SectorReader;
import core.prediction.RecoveryPrediction;
import core.recovery.RecoveryWriter;
import filesystem.ntfs.NTFSBootSector;
import filesystem.ntfs.NTFSDeletedFileScanner;
import filesystem.ntfs.NTFSFileEntry;
import filesystem.ntfs.NTFSFileRecovery;
import filesystem.ntfs.NTFSMFTReader;
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
 * Module 8 — NTFS Recovery
 *
 * Scans NTFS $MFT records and recovers deleted files.
 * Enhanced with dynamic filtering, sorting, batch recovery, safe preview,
 * summary metrics, and report export.
 */
public class NTFSRecoveryView extends VBox {

    private final Stage stage;
    private final File source;
    private final Runnable onBack;

    // Discovered data & state
    private final List<NTFSFileEntry> allDiscoveredFiles = new ArrayList<>();
    private final Set<NTFSFileEntry> selectedFiles = new LinkedHashSet<>();
    private final Map<NTFSFileEntry, String> recoveryStatusMap = new HashMap<>();
    private final Map<NTFSFileEntry, Button> cardRecoverButtons = new HashMap<>();

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

    public NTFSRecoveryView(
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

        getStyleClass().add("ntfs-recovery-page");

        /*
         * HEADER
         */
        Label moduleLabel = new Label("MODULE 08  /  NTFS RECOVERY");
        moduleLabel.getStyleClass().add("ntfs-module-label");

        Label title = new Label("DELETED FILE RECOVERY");
        title.getStyleClass().add("ntfs-recovery-title");

        Label subtitle = new Label("Scan NTFS $MFT records and recover deleted files.");
        subtitle.getStyleClass().add("ntfs-recovery-subtitle");

        VBox header = new VBox(4, moduleLabel, title, subtitle);

        /*
         * SOURCE PANEL
         */
        VBox sourcePanel = createSourcePanel();

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
        statusLabel = new Label("SCANNING NTFS MFT...");
        statusLabel.getStyleClass().add("ntfs-status");

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(20, 20);

        scanButton = new Button("RE-SCAN MFT");
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
                "Record: Low → High"
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
                } else if (val.startsWith("Record")) {
                    selectedSort = "RECORD_ASC";
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
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("ntfs-scroll");

        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        /*
         * FOOTER
         */
        Button backButton = new Button("BACK");
        backButton.getStyleClass().add("ntfs-back-button");
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

    private VBox createSourcePanel() {
        Label sourceLabel = new Label("SOURCE");
        sourceLabel.getStyleClass().add("ntfs-small-label");

        Label sourceName = new Label(source.getName());
        sourceName.getStyleClass().add("ntfs-source-name");

        Label sourcePath = new Label(source.getAbsolutePath());
        sourcePath.setWrapText(true);
        sourcePath.getStyleClass().add("ntfs-source-path");

        VBox panel = new VBox(4, sourceLabel, sourceName, sourcePath);
        panel.getStyleClass().add("ntfs-source-panel");
        return panel;
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
        statusLabel.setText("SCANNING NTFS MFT...");
        filesContainer.getChildren().clear();
        allDiscoveredFiles.clear();
        selectedFiles.clear();
        recoveryStatusMap.clear();
        cardRecoverButtons.clear();
        updateSummaryMetrics();

        Task<List<NTFSFileEntry>> task = new Task<>() {
            @Override
            protected List<NTFSFileEntry> call() throws Exception {
                try (SectorReader reader = new SectorReader(source.getAbsolutePath())) {
                    NTFSBootSector boot = NTFSBootSector.parse(reader);
                    NTFSMFTReader mftReader = new NTFSMFTReader(reader, boot);
                    NTFSDeletedFileScanner scanner = new NTFSDeletedFileScanner(mftReader, reader, boot);
                    return scanner.scan(4096);
                }
            }
        };

        task.setOnSucceeded(event -> {
            loadingIndicator.setVisible(false);
            scanButton.setDisable(false);

            List<NTFSFileEntry> files = task.getValue();
            if (files != null) {
                allDiscoveredFiles.addAll(files);
            }

            if (allDiscoveredFiles.isEmpty()) {
                statusLabel.setText("NO DELETED FILES FOUND");
                exportReportButton.setDisable(true);
                showEmptyState();
                updateSummaryMetrics();
                return;
            }

            exportReportButton.setDisable(false);
            statusLabel.setText(
                    allDiscoveredFiles.size() + (allDiscoveredFiles.size() == 1 ? " DELETED FILE FOUND" : " DELETED FILES FOUND")
            );

            refreshDisplayedResults();
        });

        task.setOnFailed(event -> {
            loadingIndicator.setVisible(false);
            scanButton.setDisable(false);
            exportReportButton.setDisable(true);
            statusLabel.setText("NTFS SCAN FAILED");

            Throwable error = task.getException();
            Label errorLabel = new Label(error == null ? "Unknown error" : error.getMessage());
            errorLabel.setWrapText(true);
            errorLabel.getStyleClass().add("ntfs-error");
            filesContainer.getChildren().add(errorLabel);

            if (error != null) {
                error.printStackTrace();
            }
        });

        Thread thread = new Thread(task, "minion-ure-ntfs-scanner");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshDisplayedResults() {
        filesContainer.getChildren().clear();
        cardRecoverButtons.clear();

        List<NTFSFileEntry> filtered = allDiscoveredFiles.stream()
                .filter(this::matchesFilter)
                .collect(Collectors.toCollection(ArrayList::new));

        Comparator<NTFSFileEntry> comparator;
        switch (selectedSort) {
            case "SIZE_DESC":
                comparator = Comparator.comparingLong(NTFSFileEntry::getFileSize).reversed();
                break;
            case "RECORD_ASC":
                comparator = Comparator.comparingLong(NTFSFileEntry::getRecordNumber);
                break;
            case "CONFIDENCE_DESC":
            default:
                comparator = Comparator.comparingDouble((NTFSFileEntry e) ->
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
            for (NTFSFileEntry entry : filtered) {
                filesContainer.getChildren().add(createFileCard(entry));
            }
        }

        updateSummaryMetrics();
    }

    private boolean matchesFilter(NTFSFileEntry entry) {
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
        for (NTFSFileEntry entry : allDiscoveredFiles) {
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

        for (NTFSFileEntry entry : allDiscoveredFiles) {
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

    private VBox createFileCard(NTFSFileEntry entry) {
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

        Label fileName = new Label(entry.getFileName());
        fileName.getStyleClass().add("ntfs-file-name");

        Label deletedBadge = new Label("DELETED");
        deletedBadge.getStyleClass().add("ntfs-deleted-badge");

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

        HBox header = new HBox(10, selectBox, formatBadge, fileName, spacer, deletedBadge);
        if (!currentStatus.equals("PENDING")) {
            header.getChildren().add(recoveryStatusBadge);
        }
        header.setAlignment(Pos.CENTER_LEFT);

        Label recordInfo = new Label("MFT RECORD  " + entry.getRecordNumber());
        recordInfo.getStyleClass().add("ntfs-file-info");

        HBox metadata = new HBox(22);
        if (entry.getFileSize() > 0) {
            Label sizeInfo = new Label("SIZE  " + formatBytes(entry.getFileSize()));
            sizeInfo.getStyleClass().add("ntfs-file-info");
            metadata.getChildren().add(sizeInfo);
        }
        metadata.getChildren().add(recordInfo);

        if (entry.getPrediction() != null) {
            RecoveryPrediction prediction = entry.getPrediction();
            Label confidenceLabel = new Label(String.format(
                    "CONFIDENCE  %.0f%% (%s)",
                    prediction.getProbability(),
                    prediction.getRating()
            ));
            confidenceLabel.getStyleClass().add("ntfs-file-info");
            metadata.getChildren().add(confidenceLabel);
        }

        javafx.scene.Node previewNode = createSafePreview(entry);

        Button recoverButton = new Button("RECOVER FILE");
        recoverButton.getStyleClass().add("ntfs-recover-button");
        if ("RECOVERED".equals(currentStatus)) {
            recoverButton.setText("RECOVERED");
        }
        recoverButton.setOnAction(event -> chooseOutput(entry, recoverButton));
        cardRecoverButtons.put(entry, recoverButton);

        VBox card = new VBox(10, header, metadata, previewNode, recoverButton);
        card.getStyleClass().add("ntfs-file-card");

        return card;
    }

    private javafx.scene.Node createSafePreview(NTFSFileEntry entry) {
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
                        NTFSBootSector boot = NTFSBootSector.parse(reader);
                        NTFSMFTReader mftReader = new NTFSMFTReader(reader, boot);
                        NTFSFileRecovery recovery = new NTFSFileRecovery(reader, boot, mftReader);
                        byte[] data = recovery.recover(entry);
                        if (data == null || data.length == 0) return null;
                        int maxLen = Math.min(data.length, 2 * 1024 * 1024);
                        Image img = new Image(new ByteArrayInputStream(data, 0, maxLen), 140, 90, true, true);
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

            Thread th = new Thread(imgTask, "minion-ntfs-preview-loader");
            th.setDaemon(true);
            th.start();

            Label metaDetails = new Label(String.format("IMAGE METRICS\nFormat: %s\nMFT Record: %d\nSize: %s",
                    fmt, entry.getRecordNumber(), formatBytes(entry.getFileSize())));
            metaDetails.getStyleClass().add("preview-meta-text");

            previewBox.getChildren().addAll(imageHolder, metaDetails);
            return previewBox;

        } else if ("PDF".equals(fmt)) {
            Label icon = new Label("📄");
            icon.getStyleClass().add("preview-doc-icon");

            Label docInfo = new Label(String.format("PDF DOCUMENT METADATA\nMFT Record: %d\nFile Size: %s\nStatus: Deleted NTFS Entry",
                    entry.getRecordNumber(), formatBytes(entry.getFileSize())));
            docInfo.getStyleClass().add("preview-meta-text");

            previewBox.getChildren().addAll(icon, docInfo);
            return previewBox;

        } else if ("ZIP".equals(fmt)) {
            Label icon = new Label("🗜");
            icon.getStyleClass().add("preview-doc-icon");

            Label zipInfo = new Label(String.format("ZIP ARCHIVE METADATA\nMFT Record: %d\nFile Size: %s\nStatus: Deleted NTFS Entry",
                    entry.getRecordNumber(), formatBytes(entry.getFileSize())));
            zipInfo.getStyleClass().add("preview-meta-text");

            previewBox.getChildren().addAll(icon, zipInfo);
            return previewBox;

        } else {
            String lower = entry.getFileName().toLowerCase();
            if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".csv") || lower.endsWith(".json")) {
                Label icon = new Label("📝");
                icon.getStyleClass().add("preview-doc-icon");

                Label textPreview = new Label(String.format("TEXT DOCUMENT\nMFT Record: %d\nFile Size: %s",
                        entry.getRecordNumber(), formatBytes(entry.getFileSize())));
                textPreview.getStyleClass().add("preview-meta-text");

                previewBox.getChildren().addAll(icon, textPreview);
                return previewBox;
            }

            Label icon = new Label("💾");
            icon.getStyleClass().add("preview-doc-icon");

            Label binInfo = new Label(String.format("BINARY FILE METADATA\nMFT Record: %d\nFile Size: %s",
                    entry.getRecordNumber(), formatBytes(entry.getFileSize())));
            binInfo.getStyleClass().add("preview-meta-text");

            previewBox.getChildren().addAll(icon, binInfo);
            return previewBox;
        }
    }

    private void chooseOutput(NTFSFileEntry entry, Button button) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Recovered NTFS File");
        chooser.setInitialFileName(entry.getFileName());

        File output = chooser.showSaveDialog(stage);
        if (output == null) {
            return;
        }

        recoverFile(entry, output.toPath(), button);
    }

    private void recoverFile(NTFSFileEntry entry, Path output, Button button) {
        button.setDisable(true);
        button.setText("RECOVERING...");
        statusLabel.setText("RECOVERING " + entry.getFileName());

        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                try (SectorReader reader = new SectorReader(source.getAbsolutePath())) {
                    NTFSBootSector boot = NTFSBootSector.parse(reader);
                    NTFSMFTReader mftReader = new NTFSMFTReader(reader, boot);
                    NTFSFileRecovery recovery = new NTFSFileRecovery(reader, boot, mftReader);
                    byte[] recovered = recovery.recover(entry);
                    RecoveryWriter writer = new RecoveryWriter();
                    return writer.write(recovered, output);
                }
            }
        };

        task.setOnSucceeded(event -> {
            button.setText("RECOVERED");
            recoveryStatusMap.put(entry, "RECOVERED");
            updateSummaryMetrics();
            statusLabel.setText("RECOVERY COMPLETE • " + entry.getFileName());
            refreshDisplayedResults();
        });

        task.setOnFailed(event -> {
            button.setDisable(false);
            button.setText("RETRY RECOVERY");
            recoveryStatusMap.put(entry, "FAILED");
            updateSummaryMetrics();
            Throwable error = task.getException();
            statusLabel.setText("RECOVERY FAILED • " + (error == null ? "UNKNOWN ERROR" : error.getMessage()));
            refreshDisplayedResults();
        });

        Thread thread = new Thread(task, "minion-ure-ntfs-recovery");
        thread.setDaemon(true);
        thread.start();
    }

    private void recoverSelectedFiles() {
        if (selectedFiles.isEmpty()) {
            return;
        }

        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Output Directory for Recovered NTFS Files");
        File targetDir = dirChooser.showDialog(stage);
        if (targetDir == null) {
            return;
        }

        recoverSelectedButton.setDisable(true);
        loadingIndicator.setVisible(true);
        statusLabel.setText("RECOVERING " + selectedFiles.size() + " SELECTED FILES...");

        List<NTFSFileEntry> toRecover = new ArrayList<>(selectedFiles);

        Task<Void> batchTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (SectorReader reader = new SectorReader(source.getAbsolutePath())) {
                    NTFSBootSector boot = NTFSBootSector.parse(reader);
                    NTFSMFTReader mftReader = new NTFSMFTReader(reader, boot);
                    NTFSFileRecovery recovery = new NTFSFileRecovery(reader, boot, mftReader);
                    RecoveryWriter writer = new RecoveryWriter();

                    for (NTFSFileEntry entry : toRecover) {
                        Path targetFile = targetDir.toPath().resolve(entry.getFileName());
                        try {
                            byte[] data = recovery.recover(entry);
                            writer.write(data, targetFile);
                            recoveryStatusMap.put(entry, "RECOVERED");
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

        Thread thread = new Thread(batchTask, "minion-ure-ntfs-batch-recovery");
        thread.setDaemon(true);
        thread.start();
    }

    private void exportReport() {
        if (allDiscoveredFiles.isEmpty()) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Recovery Report");
        fileChooser.setInitialFileName("minion-ure-ntfs-report.txt");
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

        for (NTFSFileEntry entry : allDiscoveredFiles) {
            String name = entry.getFileName();
            String recStatus = recoveryStatusMap.getOrDefault(entry, "DISCOVERED");
            if ("RECOVERED".equals(recStatus)) recovered++;
            if ("FAILED".equals(recStatus)) failed++;
            totalBytes += entry.getFileSize();

            String conf = entry.getPrediction() != null
                    ? String.format("%.0f%% (%s)", entry.getPrediction().getProbability(), entry.getPrediction().getRating())
                    : "N/A";

            reportItems.add(new RecoveryReportItem(
                    name,
                    detectFormat(entry.getFileName()),
                    entry.getRecordNumber(),
                    "MFT Record " + entry.getRecordNumber(),
                    entry.getFileSize(),
                    "N/A",
                    conf,
                    recStatus
            ));
        }

        RecoveryReport report = new RecoveryReport(
                source.getName(),
                source.getAbsolutePath(),
                "NTFS FILE RECOVERY",
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
        Label title = new Label("NO DELETED NTFS FILES");
        title.getStyleClass().add("ntfs-empty-title");

        Label description = new Label("No deleted file records were discovered in the scanned MFT range.");
        description.setWrapText(true);
        description.getStyleClass().add("ntfs-empty-description");

        VBox empty = new VBox(8, title, description);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(50));

        filesContainer.getChildren().add(empty);
    }

    private void showNoFilterMatchesState() {
        Label title = new Label("NO FILES MATCH ACTIVE FILTER");
        title.getStyleClass().add("ntfs-empty-title");

        Label description = new Label(
                "No deleted NTFS files match the selected format (" + selectedFormatFilter + ") filter."
        );
        description.setWrapText(true);
        description.getStyleClass().add("ntfs-empty-description");

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
