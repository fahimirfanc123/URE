package gui;

import core.carving.BoundaryStatus;
import core.carving.CarvedFile;
import core.carving.FileFormat;
import core.carving.SignatureScanner;
import core.carving.boundary.SectorByteReader;
import core.disk.SectorReader;
import core.prediction.RecoveryPrediction;
import gui.report.RecoveryReport;
import gui.report.RecoveryReportItem;

import javafx.application.Platform;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Module 9 — Signature Carving (Raw File Recovery)
 *
 * Scans raw disk sectors for magic-byte file signatures (JPEG, PNG, PDF, ZIP)
 * and recovers files without requiring filesystem metadata.
 *
 * Enhanced with dynamic filtering, sorting, multi-selection batch recovery,
 * safe lightweight preview, live recovery metrics summary, and session reporting.
 */
public class CarvingRecoveryView extends VBox {

    private final Stage stage;
    private final File source;
    private final Runnable onBack;

    // Discovered data & state
    private final List<CarvedFile> allDiscoveredFiles = new ArrayList<>();
    private final Set<CarvedFile> selectedFiles = new LinkedHashSet<>();
    private final Map<CarvedFile, String> recoveryStatusMap = new HashMap<>();
    private final Map<CarvedFile, Button> cardRecoverButtons = new HashMap<>();

    // Active Filters and Sorting
    private String selectedFormatFilter = "ALL";
    private String selectedBoundaryFilter = "ALL";
    private String selectedSort = "CONFIDENCE_DESC";

    // UI Structure
    private final VBox summaryPanel;
    private final Label metricFoundValue;
    private final Label metricSelectedValue;
    private final Label metricRecoveredValue;
    private final Label metricFailedValue;
    private final Label metricSizeValue;

    private final HBox filterBar;
    private final VBox filesContainer;
    private final Label statusLabel;
    private final ProgressIndicator loadingIndicator;
    private final Button scanButton;
    private final Button recoverSelectedButton;
    private final Button exportReportButton;
    private final ComboBox<String> sortComboBox;

    public CarvingRecoveryView(
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

        getStyleClass().add("carving-recovery-page");

        /*
         * =========================================================
         * HEADER
         * =========================================================
         */

        Label module = new Label("MODULE 09  /  SIGNATURE CARVING");
        module.getStyleClass().add("carving-module-label");

        Label title = new Label("RAW FILE CARVING");
        title.getStyleClass().add("carving-recovery-title");

        Label subtitle = new Label(
                "Scan raw disk sectors for contiguous files using magic-byte headers and footers."
        );
        subtitle.getStyleClass().add("carving-recovery-subtitle");

        VBox header = new VBox(4, module, title, subtitle);

        /*
         * =========================================================
         * SOURCE PANEL
         * =========================================================
         */

        Label sourceTitle = new Label("SOURCE");
        sourceTitle.getStyleClass().add("carving-small-label");

        Label sourceName = new Label(source.getName());
        sourceName.getStyleClass().add("carving-source-name");

        Label sourcePath = new Label(source.getAbsolutePath());
        sourcePath.getStyleClass().add("carving-source-path");

        VBox sourcePanel = new VBox(4, sourceTitle, sourceName, sourcePath);
        sourcePanel.getStyleClass().add("carving-source-panel");

        /*
         * =========================================================
         * RECOVERY SUMMARY PANEL
         * =========================================================
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

        summaryPanel = new VBox(8, summaryMetrics);
        summaryPanel.getStyleClass().add("recovery-summary-panel");

        /*
         * =========================================================
         * STATUS & SCAN ACTION BAR
         * =========================================================
         */

        statusLabel = new Label("READY TO SCAN");
        statusLabel.getStyleClass().add("carving-status");

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(20, 20);
        loadingIndicator.setVisible(false);

        scanButton = new Button("RE-SCAN DISK");
        scanButton.getStyleClass().add("carving-scan-button");
        scanButton.setOnAction(event -> startCarvingScan());

        exportReportButton = new Button("EXPORT REPORT");
        exportReportButton.getStyleClass().add("report-export-button");
        exportReportButton.setDisable(true);
        exportReportButton.setOnAction(event -> exportReport());

        Region statusSpacer = new Region();
        HBox.setHgrow(statusSpacer, Priority.ALWAYS);

        HBox statusBox = new HBox(10, loadingIndicator, statusLabel, statusSpacer, exportReportButton, scanButton);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        /*
         * =========================================================
         * FILTERING & SORTING TOOLBAR
         * =========================================================
         */

        filterBar = buildFilterToolbar();

        recoverSelectedButton = new Button("RECOVER SELECTED (0)");
        recoverSelectedButton.getStyleClass().add("batch-recover-button");
        recoverSelectedButton.setDisable(true);
        recoverSelectedButton.setOnAction(event -> recoverSelectedFiles());

        sortComboBox = new ComboBox<>();
        sortComboBox.getItems().addAll(
                "Confidence: High → Low",
                "Size: Large → Small",
                "Offset: Low → High"
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
                } else if (val.startsWith("Offset")) {
                    selectedSort = "OFFSET_ASC";
                }
                refreshDisplayedResults();
            }
        });

        /*
         * =========================================================
         * RESULTS CONTAINER
         * =========================================================
         */

        filesContainer = new VBox(10);
        filesContainer.setFillWidth(true);

        ScrollPane scrollPane = new ScrollPane(filesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("carving-scroll");

        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        /*
         * =========================================================
         * BACK BUTTON & FOOTER
         * =========================================================
         */

        Button backButton = new Button("BACK");
        backButton.getStyleClass().add("carving-back-button");
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

        // Automatically begin initial carving scan
        startCarvingScan();
    }

    /*
     * =============================================================
     * FILTER & TOOLBAR BUILDER
     * =============================================================
     */

    private HBox buildFilterToolbar() {
        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("filter-bar");

        Label formatFilterLabel = new Label("FORMAT:");
        formatFilterLabel.getStyleClass().add("filter-section-label");

        HBox formatPills = new HBox(4);
        String[] formats = {"ALL", "JPEG", "PNG", "PDF", "ZIP"};
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

        Label boundaryFilterLabel = new Label("BOUNDARY:");
        boundaryFilterLabel.getStyleClass().add("filter-section-label");

        HBox boundaryPills = new HBox(4);
        String[] boundaries = {"ALL", "EXACT", "TRUNCATED"};
        for (String b : boundaries) {
            Button pill = new Button(b);
            pill.getStyleClass().add(b.equals(selectedBoundaryFilter) ? "filter-pill-active" : "filter-pill");
            pill.setOnAction(event -> {
                selectedBoundaryFilter = b;
                updatePillStyles(boundaryPills, selectedBoundaryFilter);
                refreshDisplayedResults();
            });
            boundaryPills.getChildren().add(pill);
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
                boundaryFilterLabel,
                boundaryPills,
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

    /*
     * =============================================================
     * SCANNING ENGINE
     * =============================================================
     */

    private void startCarvingScan() {
        scanButton.setDisable(true);
        exportReportButton.setDisable(true);
        loadingIndicator.setVisible(true);
        statusLabel.setText("SCANNING DISK SECTORS FOR FILE SIGNATURES...");
        filesContainer.getChildren().clear();
        allDiscoveredFiles.clear();
        selectedFiles.clear();
        recoveryStatusMap.clear();
        cardRecoverButtons.clear();
        updateSummaryMetrics();

        Task<List<CarvedFile>> scanTask = new Task<>() {
            @Override
            protected List<CarvedFile> call() throws Exception {
                try (SectorReader reader = new SectorReader(source.getAbsolutePath())) {
                    SignatureScanner scanner = new SignatureScanner(reader);
                    return scanner.scan();
                }
            }
        };

        scanTask.setOnSucceeded(event -> {
            loadingIndicator.setVisible(false);
            scanButton.setDisable(false);

            List<CarvedFile> files = scanTask.getValue();
            allDiscoveredFiles.addAll(files);

            if (files.isEmpty()) {
                statusLabel.setText("NO CARVED FILES FOUND");
                exportReportButton.setDisable(true);
                showEmptyState();
                updateSummaryMetrics();
                return;
            }

            exportReportButton.setDisable(false);
            statusLabel.setText(
                    files.size() + (files.size() == 1 ? " CARVED FILE DETECTED" : " CARVED FILES DETECTED")
            );

            refreshDisplayedResults();
        });

        scanTask.setOnFailed(event -> {
            loadingIndicator.setVisible(false);
            scanButton.setDisable(false);
            exportReportButton.setDisable(true);
            statusLabel.setText("CARVING SCAN FAILED");

            String errorMsg = scanTask.getException() != null
                    ? scanTask.getException().getMessage()
                    : "Unknown carving scan error";

            Label errorLabel = new Label("Scan error: " + errorMsg);
            errorLabel.getStyleClass().add("carving-error");
            filesContainer.getChildren().add(errorLabel);

            if (scanTask.getException() != null) {
                scanTask.getException().printStackTrace();
            }
        });

        Thread thread = new Thread(scanTask, "minion-ure-carving-scan");
        thread.setDaemon(true);
        thread.start();
    }

    /*
     * =============================================================
     * FILTERING, SORTING & RENDERING
     * =============================================================
     */

    private void refreshDisplayedResults() {
        filesContainer.getChildren().clear();
        cardRecoverButtons.clear();

        // 1. Filter
        List<CarvedFile> filtered = allDiscoveredFiles.stream()
                .filter(file -> {
                    if (!"ALL".equalsIgnoreCase(selectedFormatFilter)) {
                        if (!file.getFormat().name().equalsIgnoreCase(selectedFormatFilter)) {
                            return false;
                        }
                    }
                    if (!"ALL".equalsIgnoreCase(selectedBoundaryFilter)) {
                        if (!file.getBoundaryStatus().name().equalsIgnoreCase(selectedBoundaryFilter)) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toCollection(ArrayList::new));

        // 2. Sort
        Comparator<CarvedFile> comparator;
        switch (selectedSort) {
            case "SIZE_DESC":
                comparator = Comparator.comparingLong(CarvedFile::getLength).reversed();
                break;
            case "OFFSET_ASC":
                comparator = Comparator.comparingLong(CarvedFile::getStartByteOffset);
                break;
            case "CONFIDENCE_DESC":
            default:
                comparator = Comparator.comparingDouble((CarvedFile f) ->
                        f.getPrediction() != null ? f.getPrediction().getProbability() : 0.0).reversed();
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
            for (CarvedFile file : filtered) {
                filesContainer.getChildren().add(createFileCard(file));
            }
        }

        updateSummaryMetrics();
    }

    private void selectAllFiltered() {
        for (CarvedFile file : allDiscoveredFiles) {
            if (matchesFilter(file)) {
                selectedFiles.add(file);
            }
        }
        refreshDisplayedResults();
    }

    private void clearSelection() {
        selectedFiles.clear();
        refreshDisplayedResults();
    }

    private boolean matchesFilter(CarvedFile file) {
        if (!"ALL".equalsIgnoreCase(selectedFormatFilter)) {
            if (!file.getFormat().name().equalsIgnoreCase(selectedFormatFilter)) {
                return false;
            }
        }
        if (!"ALL".equalsIgnoreCase(selectedBoundaryFilter)) {
            if (!file.getBoundaryStatus().name().equalsIgnoreCase(selectedBoundaryFilter)) {
                return false;
            }
        }
        return true;
    }

    private void updateSummaryMetrics() {
        int found = allDiscoveredFiles.size();
        int selected = selectedFiles.size();
        int recovered = 0;
        int failed = 0;
        long totalBytes = 0;

        for (CarvedFile file : allDiscoveredFiles) {
            String st = recoveryStatusMap.get(file);
            if ("RECOVERED".equals(st)) {
                recovered++;
            } else if ("FAILED".equals(st)) {
                failed++;
            }
            totalBytes += file.getLength();
        }

        metricFoundValue.setText(String.valueOf(found));
        metricSelectedValue.setText(String.valueOf(selected));
        metricRecoveredValue.setText(String.valueOf(recovered));
        metricFailedValue.setText(String.valueOf(failed));
        metricSizeValue.setText(formatBytes(totalBytes));

        recoverSelectedButton.setText("RECOVER SELECTED (" + selected + ")");
        recoverSelectedButton.setDisable(selected == 0);
    }

    /*
     * =============================================================
     * FILE RESULT CARD WITH PREVIEW & SELECTION
     * =============================================================
     */

    private VBox createFileCard(CarvedFile file) {
        // Selection CheckBox
        CheckBox selectBox = new CheckBox();
        selectBox.getStyleClass().add("recovery-checkbox");
        selectBox.setSelected(selectedFiles.contains(file));
        selectBox.setOnAction(event -> {
            if (selectBox.isSelected()) {
                selectedFiles.add(file);
            } else {
                selectedFiles.remove(file);
            }
            updateSummaryMetrics();
        });

        // Format badge
        Label formatBadge = new Label(file.getFormat().getDisplayName());
        formatBadge.getStyleClass().add("carving-format-badge");

        // Generated file name
        String suggestedName = String.format("carved_0x%08X.%s",
                file.getStartByteOffset(),
                file.getFormat().getExtension());

        Label fileName = new Label(suggestedName);
        fileName.getStyleClass().add("carving-file-name");

        // Boundary status badge
        Label boundaryBadge = new Label(file.getBoundaryStatus().name());
        boundaryBadge.getStyleClass().add(
                file.getBoundaryStatus() == BoundaryStatus.EXACT
                        ? "carving-boundary-exact"
                        : "carving-boundary-truncated"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Recovery status badge if recovered or failed
        Label recoveryStatusBadge = new Label();
        String currentStatus = recoveryStatusMap.getOrDefault(file, "PENDING");
        if ("RECOVERED".equals(currentStatus)) {
            recoveryStatusBadge.setText("● RECOVERED");
            recoveryStatusBadge.getStyleClass().add("recovery-status-recovered");
        } else if ("FAILED".equals(currentStatus)) {
            recoveryStatusBadge.setText("● FAILED");
            recoveryStatusBadge.getStyleClass().add("recovery-status-failed");
        }

        HBox heading = new HBox(10, selectBox, formatBadge, fileName, spacer, boundaryBadge);
        if (!currentStatus.equals("PENDING")) {
            heading.getChildren().add(recoveryStatusBadge);
        }
        heading.setAlignment(Pos.CENTER_LEFT);

        // Metadata row
        Label sizeLabel = new Label("SIZE  " + formatBytes(file.getLength()));
        sizeLabel.getStyleClass().add("carving-file-info");

        Label offsetLabel = new Label(String.format("OFFSET  %,d (Sector %,d)",
                file.getStartByteOffset(), file.getStartSector()));
        offsetLabel.getStyleClass().add("carving-file-info");

        HBox metadata = new HBox(22, sizeLabel, offsetLabel);

        if (file.getPrediction() != null) {
            RecoveryPrediction prediction = file.getPrediction();
            Label confidenceLabel = new Label(String.format(
                    "CONFIDENCE  %.0f%% (%s)",
                    prediction.getProbability(),
                    prediction.getRating()
            ));
            confidenceLabel.getStyleClass().add("carving-file-info");
            metadata.getChildren().add(confidenceLabel);
        }

        // Lightweight Safe Preview Container
        javafx.scene.Node previewNode = createSafePreview(file);

        // Action button
        Button recoverButton = new Button("RECOVER FILE");
        recoverButton.getStyleClass().add("carving-recover-button");
        if ("RECOVERED".equals(currentStatus)) {
            recoverButton.setText("RECOVERED");
        }
        recoverButton.setOnAction(event -> chooseOutputAndRecover(file, suggestedName, recoverButton));
        cardRecoverButtons.put(file, recoverButton);

        VBox cardContent = new VBox(10, heading, metadata, previewNode, recoverButton);
        cardContent.getStyleClass().add("carving-file-card");

        return cardContent;
    }

    /*
     * =============================================================
     * SAFE LIGHTWEIGHT PREVIEW GENERATOR
     * =============================================================
     */

    private javafx.scene.Node createSafePreview(CarvedFile file) {
        HBox previewBox = new HBox(12);
        previewBox.setAlignment(Pos.CENTER_LEFT);
        previewBox.getStyleClass().add("preview-container");

        FileFormat fmt = file.getFormat();

        if (fmt == FileFormat.JPEG || fmt == FileFormat.PNG) {
            StackPane imageHolder = new StackPane();
            imageHolder.setPrefSize(140, 90);
            imageHolder.setMinSize(140, 90);
            imageHolder.setMaxSize(140, 90);
            imageHolder.getStyleClass().add("preview-image-frame");

            Label loadingLabel = new Label("Loading Preview...");
            loadingLabel.getStyleClass().add("preview-loading-text");
            imageHolder.getChildren().add(loadingLabel);

            // Safe async image decode bounded to at most 2MB
            Task<Image> imageTask = new Task<>() {
                @Override
                protected Image call() {
                    try (SectorReader reader = new SectorReader(source.getAbsolutePath())) {
                        int readLen = (int) Math.min(file.getLength(), 2 * 1024 * 1024L);
                        byte[] bytes = SectorByteReader.readBytes(reader, file.getStartByteOffset(), readLen);
                        if (bytes == null || bytes.length == 0) {
                            return null;
                        }
                        // Decode safely bounded to 140x90
                        Image img = new Image(new ByteArrayInputStream(bytes), 140, 90, true, true);
                        if (img.isError()) {
                            return null;
                        }
                        return img;
                    } catch (Throwable t) {
                        return null;
                    }
                }
            };

            imageTask.setOnSucceeded(ev -> {
                Image img = imageTask.getValue();
                imageHolder.getChildren().clear();
                if (img != null && !img.isError()) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(140);
                    iv.setFitHeight(90);
                    iv.setPreserveRatio(true);
                    iv.getStyleClass().add("preview-image");
                    imageHolder.getChildren().add(iv);
                } else {
                    Label failLabel = new Label("Image Preview Unavailable");
                    failLabel.getStyleClass().add("preview-fail-text");
                    imageHolder.getChildren().add(failLabel);
                }
            });

            imageTask.setOnFailed(ev -> {
                imageHolder.getChildren().clear();
                Label failLabel = new Label("Preview Unavailable");
                failLabel.getStyleClass().add("preview-fail-text");
                imageHolder.getChildren().add(failLabel);
            });

            Thread imgThread = new Thread(imageTask, "minion-ure-preview-loader");
            imgThread.setDaemon(true);
            imgThread.start();

            Label metaDetails = new Label(String.format("IMAGE METRICS\nFormat: %s\nBoundary: %s\nDimensions: Auto-detected",
                    fmt.getDisplayName(), file.getBoundaryStatus()));
            metaDetails.getStyleClass().add("preview-meta-text");

            previewBox.getChildren().addAll(imageHolder, metaDetails);
            return previewBox;

        } else if (fmt == FileFormat.PDF) {
            Label pdfIcon = new Label("📄");
            pdfIcon.getStyleClass().add("preview-doc-icon");

            Label docInfo = new Label(String.format("PDF DOCUMENT METADATA\nHeader: %%PDF- compliant signature\nTrailer: %s\nPayload Length: %,d bytes",
                    file.getBoundaryStatus() == BoundaryStatus.EXACT ? "Valid %%EOF marker verified" : "Truncated (No valid %%EOF)",
                    file.getLength()));
            docInfo.getStyleClass().add("preview-meta-text");

            previewBox.getChildren().addAll(pdfIcon, docInfo);
            return previewBox;

        } else if (fmt == FileFormat.ZIP) {
            Label zipIcon = new Label("🗜");
            zipIcon.getStyleClass().add("preview-doc-icon");

            Label zipInfo = new Label(String.format("ZIP ARCHIVE METADATA\nHeader: PK\\x03\\x04 Local File Header\nStructure: %s\nPayload Length: %,d bytes",
                    file.getBoundaryStatus() == BoundaryStatus.EXACT ? "Valid EOCD record parsed" : "Truncated / Bounded span",
                    file.getLength()));
            zipInfo.getStyleClass().add("preview-meta-text");

            previewBox.getChildren().addAll(zipIcon, zipInfo);
            return previewBox;
        }

        return new Region();
    }

    /*
     * =============================================================
     * INDIVIDUAL FILE RECOVERY
     * =============================================================
     */

    private void chooseOutputAndRecover(CarvedFile file, String suggestedName, Button button) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Recovered Carved File");
        chooser.setInitialFileName(suggestedName);

        String ext = file.getFormat().getExtension();
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(file.getFormat().getDisplayName() + " File (*." + ext + ")", "*." + ext)
        );
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );

        File output = chooser.showSaveDialog(stage);
        if (output == null) {
            return;
        }

        button.setDisable(true);
        button.setText("RECOVERING...");
        statusLabel.setText("RECOVERING " + suggestedName + "...");

        Task<Path> recoverTask = new Task<>() {
            @Override
            protected Path call() throws Exception {
                try (SectorReader reader = new SectorReader(source.getAbsolutePath())) {
                    return file.recoverTo(reader, output.toPath());
                }
            }
        };

        recoverTask.setOnSucceeded(event -> {
            button.setText("RECOVERED");
            recoveryStatusMap.put(file, "RECOVERED");
            updateSummaryMetrics();
            Path result = recoverTask.getValue();
            statusLabel.setText("RECOVERY COMPLETE  •  " + result.getFileName());
            refreshDisplayedResults();
        });

        recoverTask.setOnFailed(event -> {
            button.setDisable(false);
            button.setText("RETRY RECOVERY");
            recoveryStatusMap.put(file, "FAILED");
            updateSummaryMetrics();
            statusLabel.setText("RECOVERY FAILED");

            if (recoverTask.getException() != null) {
                recoverTask.getException().printStackTrace();
            }
            refreshDisplayedResults();
        });

        Thread thread = new Thread(recoverTask, "minion-ure-carving-recovery");
        thread.setDaemon(true);
        thread.start();
    }

    /*
     * =============================================================
     * BATCH RECOVERY (RECOVER SELECTED)
     * =============================================================
     */

    private void recoverSelectedFiles() {
        if (selectedFiles.isEmpty()) {
            return;
        }

        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Output Directory for Carved Files");
        File targetDir = dirChooser.showDialog(stage);
        if (targetDir == null) {
            return;
        }

        recoverSelectedButton.setDisable(true);
        loadingIndicator.setVisible(true);
        statusLabel.setText("RECOVERING " + selectedFiles.size() + " SELECTED FILES...");

        List<CarvedFile> toRecover = new ArrayList<>(selectedFiles);

        Task<Void> batchTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (SectorReader reader = new SectorReader(source.getAbsolutePath())) {
                    for (int i = 0; i < toRecover.size(); i++) {
                        CarvedFile file = toRecover.get(i);
                        String filename = String.format("carved_0x%08X.%s",
                                file.getStartByteOffset(),
                                file.getFormat().getExtension());
                        Path targetFile = targetDir.toPath().resolve(filename);
                        try {
                            file.recoverTo(reader, targetFile);
                            recoveryStatusMap.put(file, "RECOVERED");
                        } catch (Exception ex) {
                            recoveryStatusMap.put(file, "FAILED");
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

        Thread thread = new Thread(batchTask, "minion-ure-batch-carving-recovery");
        thread.setDaemon(true);
        thread.start();
    }

    /*
     * =============================================================
     * EXPORT REPORT
     * =============================================================
     */

    private void exportReport() {
        if (allDiscoveredFiles.isEmpty()) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Recovery Report");
        fileChooser.setInitialFileName("minion-ure-carving-report.txt");
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

        for (CarvedFile file : allDiscoveredFiles) {
            String name = String.format("carved_0x%08X.%s",
                    file.getStartByteOffset(),
                    file.getFormat().getExtension());
            String recStatus = recoveryStatusMap.getOrDefault(file, "DISCOVERED");
            if ("RECOVERED".equals(recStatus)) recovered++;
            if ("FAILED".equals(recStatus)) failed++;
            totalBytes += file.getLength();

            String conf = file.getPrediction() != null
                    ? String.format("%.0f%% (%s)", file.getPrediction().getProbability(), file.getPrediction().getRating())
                    : "N/A";

            reportItems.add(new RecoveryReportItem(
                    name,
                    file.getFormat().getDisplayName(),
                    file.getStartByteOffset(),
                    String.format("Offset %,d (Sector %,d)", file.getStartByteOffset(), file.getStartSector()),
                    file.getLength(),
                    file.getBoundaryStatus().name(),
                    conf,
                    recStatus
            ));
        }

        RecoveryReport report = new RecoveryReport(
                source.getName(),
                source.getAbsolutePath(),
                "SIGNATURE CARVING",
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

    /*
     * =============================================================
     * EMPTY & NO-MATCH STATES
     * =============================================================
     */

    private void showEmptyState() {
        Label title = new Label("NO RECOVERABLE FILE SIGNATURES DETECTED");
        title.getStyleClass().add("carving-empty-title");

        Label description = new Label(
                "MINION URE did not find valid JPEG, PNG, PDF, or ZIP file signatures in the scanned sectors."
        );
        description.setWrapText(true);
        description.getStyleClass().add("carving-empty-description");

        VBox empty = new VBox(8, title, description);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(50));

        filesContainer.getChildren().add(empty);
    }

    private void showNoFilterMatchesState() {
        Label title = new Label("NO FILES MATCH ACTIVE FILTER");
        title.getStyleClass().add("carving-empty-title");

        Label description = new Label(
                "No carved files match the selected format (" + selectedFormatFilter +
                ") or boundary (" + selectedBoundaryFilter + ") criteria."
        );
        description.setWrapText(true);
        description.getStyleClass().add("carving-empty-description");

        VBox empty = new VBox(8, title, description);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(40));

        filesContainer.getChildren().add(empty);
    }

    /*
     * =============================================================
     * HELPER FORMATTING
     * =============================================================
     */

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
