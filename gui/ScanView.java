package gui;

import core.disk.SectorReader;
import core.scan.DiskScanner;
import gui.ScanView.ScanSummary;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.function.Consumer;

public class ScanView extends VBox {

    private static final int UI_UPDATE_INTERVAL = 256;

    private final File source;
    private final Runnable onBack;
    private final Consumer<ScanSummary> onComplete;

    private final Label statusLabel =
            new Label("PREPARING SCAN");

    private final Label percentLabel =
            new Label("0%");

    private final ProgressBar progressBar =
            new ProgressBar(0);

    private final Label currentSectorValue =
            new Label("0");

    private final Label scannedValue =
            new Label("0");

    private final Label dataValue =
            new Label("0");

    private final Label emptyValue =
            new Label("0");

    private final Label elapsedValue =
            new Label("0.0 s");

    private final Button cancelButton =
            new Button("CANCEL SCAN");

    private final Button resultsButton =
            new Button("VIEW RESULTS  ›");

    private volatile boolean cancelled = false;

    private Task<ScanSummary> scanTask;

    private long startTime;

    public ScanView(
            File source,
            Runnable onBack,
            Consumer<ScanSummary> onComplete
    ) {
        setMinWidth(0);
        setMinHeight(0);

        setPrefWidth(Region.USE_COMPUTED_SIZE);
        setPrefHeight(Region.USE_COMPUTED_SIZE);

        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        this.source = source;
        this.onBack = onBack;
        this.onComplete = onComplete;

        buildView();
        startScan();
    }


    private void buildView() {

        getStyleClass().add("scan-page");

        setAlignment(Pos.TOP_CENTER);
        setSpacing(18);


        /* =====================================================
           HEADER
        ===================================================== */

        HBox header = new HBox();

        header.setAlignment(Pos.CENTER_LEFT);
        header.setMaxWidth(900);


        Button backButton =
                new Button("‹  BACK");

        backButton
                .getStyleClass()
                .add("drive-secondary-button");


        Region spacer =
                new Region();

        HBox.setHgrow(
                spacer,
                Priority.ALWAYS
        );


        Label liveLabel =
                new Label("●  LIVE SCAN");

        liveLabel
                .getStyleClass()
                .add("scan-live");


        header
                .getChildren()
                .addAll(
                        backButton,
                        spacer,
                        liveLabel
                );


        /* =====================================================
           TITLE
        ===================================================== */

        Label title =
                new Label(
                        "SCANNING RECOVERY SOURCE"
                );

        title
                .getStyleClass()
                .add("scan-page-title");


        Label subtitle =
                new Label(
                        "MINION URE is analysing raw sectors and identifying recoverable data."
                );

        subtitle
                .getStyleClass()
                .add("scan-page-subtitle");


        /* =====================================================
           SOURCE PANEL
        ===================================================== */

        VBox sourcePanel =
                new VBox(5);

        sourcePanel
                .getStyleClass()
                .add("scan-source-panel");

        sourcePanel.setMaxWidth(900);


        Label sourceHeader =
                new Label("RECOVERY SOURCE");

        sourceHeader
                .getStyleClass()
                .add("scan-small-title");


        Label sourceName =
                new Label(
                        source.getName()
                );

        sourceName
                .getStyleClass()
                .add("scan-source-name");


        Label sourcePath =
                new Label(
                        source.getAbsolutePath()
                );

        sourcePath
                .getStyleClass()
                .add("scan-source-path");

        sourcePath.setWrapText(true);


        sourcePanel
                .getChildren()
                .addAll(
                        sourceHeader,
                        sourceName,
                        sourcePath
                );


        /* =====================================================
           PROGRESS PANEL
        ===================================================== */

        VBox progressPanel =
                new VBox(14);

        progressPanel
                .getStyleClass()
                .add("scan-progress-panel");

        progressPanel.setMaxWidth(900);


        HBox progressHeader =
                new HBox();

        progressHeader.setAlignment(
                Pos.CENTER_LEFT
        );


        statusLabel
                .getStyleClass()
                .add("scan-status");


        Region progressSpacer =
                new Region();

        HBox.setHgrow(
                progressSpacer,
                Priority.ALWAYS
        );


        percentLabel
                .getStyleClass()
                .add("scan-percent");


        progressHeader
                .getChildren()
                .addAll(
                        statusLabel,
                        progressSpacer,
                        percentLabel
                );


        progressBar
                .getStyleClass()
                .add("scan-progress-bar");

        progressBar.setMaxWidth(
                Double.MAX_VALUE
        );


        HBox sectorLine =
                new HBox(8);

        sectorLine.setAlignment(
                Pos.CENTER_LEFT
        );


        Label currentSectorLabel =
                new Label("CURRENT SECTOR");

        currentSectorLabel
                .getStyleClass()
                .add("scan-detail-label");


        currentSectorValue
                .getStyleClass()
                .add("scan-detail-value");


        sectorLine
                .getChildren()
                .addAll(
                        currentSectorLabel,
                        currentSectorValue
                );


        progressPanel
                .getChildren()
                .addAll(
                        progressHeader,
                        progressBar,
                        sectorLine
                );


        /* =====================================================
           STATISTICS
        ===================================================== */

        HBox statistics =
                new HBox(12);

        statistics.setMaxWidth(900);


        VBox scannedCard =
                createStatCard(
                        "SECTORS SCANNED",
                        scannedValue
                );


        VBox dataCard =
                createStatCard(
                        "DATA SECTORS",
                        dataValue
                );


        VBox emptyCard =
                createStatCard(
                        "EMPTY SECTORS",
                        emptyValue
                );


        VBox elapsedCard =
                createStatCard(
                        "ELAPSED",
                        elapsedValue
                );


        HBox.setHgrow(
                scannedCard,
                Priority.ALWAYS
        );

        HBox.setHgrow(
                dataCard,
                Priority.ALWAYS
        );

        HBox.setHgrow(
                emptyCard,
                Priority.ALWAYS
        );

        HBox.setHgrow(
                elapsedCard,
                Priority.ALWAYS
        );


        statistics
                .getChildren()
                .addAll(
                        scannedCard,
                        dataCard,
                        emptyCard,
                        elapsedCard
                );


        /* =====================================================
           INFORMATION
        ===================================================== */

        Label information =
                new Label(
                        "Read-only analysis • Source data will not be modified • "
                                +
                                "Scanning is powered by the URE SectorReader and DiskScanner core engine."
                );

        information
                .getStyleClass()
                .add("scan-information");

        information.setWrapText(true);
        information.setMaxWidth(900);


        /* =====================================================
           ACTIONS
        ===================================================== */

        HBox actions =
                new HBox(10);

        actions.setAlignment(
                Pos.CENTER_RIGHT
        );

        actions.setMaxWidth(900);


        cancelButton
                .getStyleClass()
                .add("scan-cancel-button");


        resultsButton
                .getStyleClass()
                .add("scan-results-button");

        resultsButton.setDisable(true);


        actions
                .getChildren()
                .addAll(
                        cancelButton,
                        resultsButton
                );


        /* =====================================================
           ADD CONTENT
        ===================================================== */

        getChildren()
                .addAll(
                        header,
                        title,
                        subtitle,
                        sourcePanel,
                        progressPanel,
                        statistics,
                        information,
                        actions
                );


        /* =====================================================
           EVENTS
        ===================================================== */

        backButton.setOnAction(
                event -> {

                    cancelScan();
                    onBack.run();
                }
        );


        cancelButton.setOnAction(
                event -> cancelScan()
        );


        resultsButton.setOnAction(
                event -> {

                    if (scanTask != null
                            &&
                            scanTask.getValue() != null) {

                        onComplete.accept(
                                scanTask.getValue()
                        );
                    }
                }
        );
    }


    private VBox createStatCard(
            String title,
            Label value
    ) {

        VBox card =
                new VBox(7);

        card
                .getStyleClass()
                .add("scan-stat-card");

        card.setAlignment(
                Pos.CENTER_LEFT
        );

        card.setMaxWidth(
                Double.MAX_VALUE
        );


        Label titleLabel =
                new Label(title);

        titleLabel
                .getStyleClass()
                .add("scan-stat-title");


        value
                .getStyleClass()
                .add("scan-stat-value");


        card
                .getChildren()
                .addAll(
                        titleLabel,
                        value
                );

        return card;
    }


    private void startScan() {

        if (!source.isFile()) {

            statusLabel.setText(
                    "SOURCE NOT SUPPORTED"
            );

            statusLabel
                    .getStyleClass()
                    .add("scan-error");

            cancelButton.setText("BACK");

            cancelButton.setOnAction(
                    event -> onBack.run()
            );

            return;
        }


        cancelled = false;

        startTime =
                System.currentTimeMillis();


        scanTask =
                new Task<>() {

                    @Override
                    protected ScanSummary call()
                            throws Exception {

                        long scannedSectors = 0;

                        long dataSectors = 0;

                        long emptySectors = 0;


                        /*
                         * REAL URE BACKEND
                         */
                        try (
                                SectorReader sectorReader =
                                        new SectorReader(
                                                source.getAbsolutePath()
                                        )
                        ) {

                            DiskScanner scanner =
                                    new DiskScanner(
                                            sectorReader
                                    );


                            long totalSectors =
                                    sectorReader
                                            .getTotalSectors();


                            if (totalSectors <= 0) {

                                updateProgress(
                                        1,
                                        1
                                );

                                return new ScanSummary(
                                        source,
                                        0,
                                        0,
                                        0,
                                        0,
                                        System.currentTimeMillis()
                                                - startTime,
                                        true
                                );
                            }


                            Platform.runLater(
                                    () ->
                                            statusLabel.setText(
                                                    "SCANNING"
                                            )
                            );


                            for (
                                    long sector = 0;
                                    sector < totalSectors;
                                    sector++
                            ) {

                                if (
                                        cancelled
                                                ||
                                        isCancelled()
                                ) {

                                    break;
                                }


                                boolean containsData =
                                        scanner.hasData(
                                                sector
                                        );


                                scannedSectors++;


                                if (containsData) {

                                    dataSectors++;

                                } else {

                                    emptySectors++;
                                }


                                updateProgress(
                                        scannedSectors,
                                        totalSectors
                                );


                                if (
                                        scannedSectors
                                                % UI_UPDATE_INTERVAL
                                                == 0
                                                ||
                                        scannedSectors
                                                == totalSectors
                                ) {

                                    updateUi(
                                            sector,
                                            scannedSectors,
                                            dataSectors,
                                            emptySectors,
                                            totalSectors
                                    );
                                }
                            }


                            long elapsed =
                                    System.currentTimeMillis()
                                            - startTime;


                            boolean completed =
                                    !cancelled
                                            &&
                                    !isCancelled()
                                            &&
                                    scannedSectors
                                            == totalSectors;


                            return new ScanSummary(
                                    source,
                                    totalSectors,
                                    scannedSectors,
                                    dataSectors,
                                    emptySectors,
                                    elapsed,
                                    completed
                            );
                        }
                    }
                };


        progressBar
                .progressProperty()
                .bind(
                        scanTask.progressProperty()
                );


        scanTask.setOnSucceeded(
                event -> {

                    ScanSummary summary =
                            scanTask.getValue();


                    if (summary == null) {

                        statusLabel.setText(
                                "SCAN FAILED"
                        );

                        statusLabel
                                .getStyleClass()
                                .add("scan-error");

                        return;
                    }


                    updateFinalUi(
                            summary
                    );


                    if (summary.isCompleted()) {

                        statusLabel.setText(
                                "SCAN COMPLETE"
                        );

                        percentLabel.setText(
                                "100%"
                        );

                        resultsButton.setDisable(
                                false
                        );

                        cancelButton.setText(
                           "EXIT"
                        );

                        cancelButton.setOnAction(
                          e -> onBack.run()
                        );

                        System.out.println(
                                "=== SCAN COMPLETE ==="
                        );

                        System.out.println(
                                summary
                        );

                    } else {

                        statusLabel.setText(
                                "SCAN CANCELLED"
                        );

                        cancelButton.setText(
                                "BACK"
                        );

                        cancelButton.setOnAction(
                                e -> onBack.run()
                        );
                    }
                }
        );


        scanTask.setOnCancelled(
                event -> {

                    statusLabel.setText(
                            "SCAN CANCELLED"
                    );

                    cancelButton.setText(
                            "BACK"
                    );

                    cancelButton.setOnAction(
                            e -> onBack.run()
                    );
                }
        );


        scanTask.setOnFailed(
                event -> {

                    statusLabel.setText(
                            "SCAN FAILED"
                    );

                    statusLabel
                            .getStyleClass()
                            .add("scan-error");


                    Throwable error =
                            scanTask.getException();


                    if (error != null) {

                        System.err.println(
                                "Scan failed: "
                                        +
                                error.getMessage()
                        );

                        error.printStackTrace();
                    }


                    cancelButton.setText(
                            "BACK"
                    );

                    cancelButton.setOnAction(
                            e -> onBack.run()
                    );
                }
        );


        Thread worker =
                new Thread(
                        scanTask,
                        "minion-ure-core-scanner"
                );

        worker.setDaemon(true);
        worker.start();
    }


    private void updateUi(
            long currentSector,
            long scanned,
            long data,
            long empty,
            long total
    ) {

        long elapsed =
                System.currentTimeMillis()
                        - startTime;


        double percentage =
                total == 0
                        ? 0
                        :
                        (
                                scanned
                                        * 100.0
                                        /
                                total
                        );


        Platform.runLater(
                () -> {

                    currentSectorValue.setText(
                            String.format(
                                    "%,d / %,d",
                                    currentSector,
                                    total - 1
                            )
                    );


                    scannedValue.setText(
                            String.format(
                                    "%,d",
                                    scanned
                            )
                    );


                    dataValue.setText(
                            String.format(
                                    "%,d",
                                    data
                            )
                    );


                    emptyValue.setText(
                            String.format(
                                    "%,d",
                                    empty
                            )
                    );


                    elapsedValue.setText(
                            String.format(
                                    "%.1f s",
                                    elapsed / 1000.0
                            )
                    );


                    percentLabel.setText(
                            String.format(
                                    "%.1f%%",
                                    percentage
                            )
                    );
                }
        );
    }


    private void updateFinalUi(
            ScanSummary summary
    ) {

        scannedValue.setText(
                String.format(
                        "%,d",
                        summary.getScannedSectors()
                )
        );


        dataValue.setText(
                String.format(
                        "%,d",
                        summary.getDataSectors()
                )
        );


        emptyValue.setText(
                String.format(
                        "%,d",
                        summary.getEmptySectors()
                )
        );


        elapsedValue.setText(
                String.format(
                        "%.2f s",
                        summary.getElapsedMilliseconds()
                                / 1000.0
                )
        );


        if (
                summary.getScannedSectors() > 0
        ) {

            currentSectorValue.setText(
                    String.format(
                            "%,d / %,d",
                            summary.getScannedSectors()
                                    - 1,
                            summary.getTotalSectors()
                                    - 1
                    )
            );
        }
    }


    private void cancelScan() {

        cancelled = true;


        if (
                scanTask != null
                        &&
                scanTask.isRunning()
        ) {

            scanTask.cancel();
        }
    }


    public static class ScanSummary {

        private final File source;

        private final long totalSectors;

        private final long scannedSectors;

        private final long dataSectors;

        private final long emptySectors;

        private final long elapsedMilliseconds;

        private final boolean completed;


        public ScanSummary(
                File source,
                long totalSectors,
                long scannedSectors,
                long dataSectors,
                long emptySectors,
                long elapsedMilliseconds,
                boolean completed
        ) {

            this.source =
                    source;

            this.totalSectors =
                    totalSectors;

            this.scannedSectors =
                    scannedSectors;

            this.dataSectors =
                    dataSectors;

            this.emptySectors =
                    emptySectors;

            this.elapsedMilliseconds =
                    elapsedMilliseconds;

            this.completed =
                    completed;
        }


        public File getSource() {
            return source;
        }


        public long getTotalSectors() {
            return totalSectors;
        }


        public long getScannedSectors() {
            return scannedSectors;
        }


        public long getDataSectors() {
            return dataSectors;
        }


        public long getEmptySectors() {
            return emptySectors;
        }


        public long getElapsedMilliseconds() {
            return elapsedMilliseconds;
        }


        public boolean isCompleted() {
            return completed;
        }


        @Override
        public String toString() {

            return "ScanSummary{" +
                    "source=" + source +
                    ", totalSectors=" + totalSectors +
                    ", scannedSectors=" + scannedSectors +
                    ", dataSectors=" + dataSectors +
                    ", emptySectors=" + emptySectors +
                    ", elapsedMilliseconds=" + elapsedMilliseconds +
                    ", completed=" + completed +
                    '}';
        }
    }
}