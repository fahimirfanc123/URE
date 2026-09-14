package gui;

import common.model.RecoveryResult;
import core.disk.SectorReader;
import core.recovery.RecoveryEngine;
import core.scan.DiskScanner;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.function.Consumer;

/**
 * Module 5 — Recovery Analysis
 *
 * Uses the real URE backend:
 *
 * SectorReader
 *      ↓
 * DiskScanner
 *      ↓
 * RecoveryEngine
 *      ↓
 * RecoveryResult
 */
public class RecoveryAnalysisView extends VBox {

    private final File source;
    private final Runnable onBack;
    private final Consumer<RecoveryResult> onComplete;

    private final Label statusLabel;
    private final Label recoverableValue;
    private final Label emptyValue;
    private final Label percentageValue;
    private final Label totalValue;
    private final Label messageLabel;

    private final ProgressIndicator progressIndicator;

    private final Button backButton;
    private final Button continueButton;

    private Task<RecoveryResult> analysisTask;

    public RecoveryAnalysisView(
            File source,
            Runnable onBack,
            Consumer<RecoveryResult> onComplete
    ) {

        if (source == null) {
            throw new IllegalArgumentException(
                    "Recovery source cannot be null"
            );
        }

        this.source = source;
        this.onBack = onBack;
        this.onComplete = onComplete;

        setMinWidth(0);
        setMinHeight(0);

        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        getStyleClass().add(
                "recovery-analysis-page"
        );

        setSpacing(22);
        setPadding(
                new Insets(
                        32,
                        45,
                        30,
                        45
                )
        );

        /*
         * =========================================================
         * HEADER
         * =========================================================
         */

        Label moduleLabel =
                new Label(
                        "MODULE 05  /  RECOVERY ENGINE"
                );

        moduleLabel
                .getStyleClass()
                .add(
                        "recovery-module-label"
                );


        Label title =
                new Label(
                        "RECOVERY ANALYSIS"
                );

        title
                .getStyleClass()
                .add(
                        "recovery-analysis-title"
                );


        Label subtitle =
                new Label(
                        "Analyzing the selected source for recoverable data."
                );

        subtitle
                .getStyleClass()
                .add(
                        "recovery-analysis-subtitle"
                );


        VBox header =
                new VBox(
                        7,
                        moduleLabel,
                        title,
                        subtitle
                );


        /*
         * =========================================================
         * SOURCE
         * =========================================================
         */

        Label sourceHeading =
                new Label(
                        "ANALYSIS SOURCE"
                );

        sourceHeading
                .getStyleClass()
                .add(
                        "recovery-small-label"
                );


        Label sourceName =
                new Label(
                        source.getName()
                );

        sourceName
                .getStyleClass()
                .add(
                        "recovery-source-name"
                );


        Label sourcePath =
                new Label(
                        source.getAbsolutePath()
                );

        sourcePath.setWrapText(
                true
        );

        sourcePath
                .getStyleClass()
                .add(
                        "recovery-source-path"
                );


        VBox sourcePanel =
                new VBox(
                        6,
                        sourceHeading,
                        sourceName,
                        sourcePath
                );

        sourcePanel
                .getStyleClass()
                .add(
                        "recovery-source-panel"
                );


        /*
         * =========================================================
         * ANALYSIS STATUS
         * =========================================================
         */

        progressIndicator =
                new ProgressIndicator();

        progressIndicator.setPrefSize(
                44,
                44
        );


        statusLabel =
                new Label(
                        "PREPARING ANALYSIS"
                );

        statusLabel
                .getStyleClass()
                .add(
                        "recovery-status"
                );


        messageLabel =
                new Label(
                        "Initializing URE recovery engine..."
                );

        messageLabel.setWrapText(
                true
        );

        messageLabel
                .getStyleClass()
                .add(
                        "recovery-message"
                );


        VBox statusText =
                new VBox(
                        5,
                        statusLabel,
                        messageLabel
                );


        HBox statusPanel =
                new HBox(
                        18,
                        progressIndicator,
                        statusText
                );

        statusPanel.setAlignment(
                Pos.CENTER_LEFT
        );

        statusPanel
                .getStyleClass()
                .add(
                        "recovery-status-panel"
                );


        /*
         * =========================================================
         * STATISTICS
         * =========================================================
         */

        totalValue =
                new Label("--");

        recoverableValue =
                new Label("--");

        emptyValue =
                new Label("--");

        percentageValue =
                new Label("--");


        HBox statistics =
                new HBox(
                        14,
                        createStatCard(
                                "TOTAL ANALYSED",
                                totalValue
                        ),
                        createStatCard(
                                "RECOVERABLE",
                                recoverableValue
                        ),
                        createStatCard(
                                "EMPTY",
                                emptyValue
                        ),
                        createStatCard(
                                "RECOVERY %",
                                percentageValue
                        )
                );

        statistics.setFillHeight(
                true
        );


        /*
         * =========================================================
         * ENGINE INFORMATION
         * =========================================================
         */

        Label engineTitle =
                new Label(
                        "RECOVERY PIPELINE"
                );

        engineTitle
                .getStyleClass()
                .add(
                        "recovery-small-label"
                );


        Label pipeline =
                new Label(
                        "SectorReader  →  DiskScanner  →  RecoveryEngine  →  RecoveryResult"
                );

        pipeline.setWrapText(
                true
        );

        pipeline
                .getStyleClass()
                .add(
                        "recovery-pipeline"
                );


        Label engineInfo =
                new Label(
                        "URE performs a sector-level analysis and identifies sectors containing data that may be recoverable."
                );

        engineInfo.setWrapText(
                true
        );

        engineInfo
                .getStyleClass()
                .add(
                        "recovery-engine-info"
                );


        VBox enginePanel =
                new VBox(
                        8,
                        engineTitle,
                        pipeline,
                        engineInfo
                );

        enginePanel
                .getStyleClass()
                .add(
                        "recovery-engine-panel"
                );


        /*
         * =========================================================
         * FLEXIBLE SPACE
         * =========================================================
         */

        Region spacer =
                new Region();

        VBox.setVgrow(
                spacer,
                Priority.ALWAYS
        );


        /*
         * =========================================================
         * BUTTONS
         * =========================================================
         */

        backButton =
                new Button(
                        "BACK"
                );

        backButton
                .getStyleClass()
                .add(
                        "recovery-back-button"
                );


        continueButton =
                new Button(
                        "CONTINUE"
                );

        continueButton
                .getStyleClass()
                .add(
                        "recovery-continue-button"
                );

        continueButton.setDisable(
                true
        );


        backButton.setOnAction(
                event -> {

                    cancelAnalysis();

                    if (onBack != null) {
                        onBack.run();
                    }
                }
        );


        continueButton.setOnAction(
                event -> {

                    if (
                            analysisTask != null
                                    &&
                            analysisTask.getValue() != null
                                    &&
                            onComplete != null
                    ) {

                        onComplete.accept(
                                analysisTask.getValue()
                        );
                    }
                }
        );


        Region buttonSpacer =
                new Region();

        HBox.setHgrow(
                buttonSpacer,
                Priority.ALWAYS
        );


        HBox buttons =
                new HBox(
                        12,
                        backButton,
                        buttonSpacer,
                        continueButton
                );

        buttons.setAlignment(
                Pos.CENTER
        );


        /*
         * =========================================================
         * ROOT
         * =========================================================
         */

        getChildren().addAll(
                header,
                sourcePanel,
                statusPanel,
                statistics,
                enginePanel,
                spacer,
                buttons
        );


        /*
         * Start real recovery analysis.
         */

        Platform.runLater(
                this::startAnalysis
        );
    }


    /*
     * =============================================================
     * STAT CARD
     * =============================================================
     */

    private VBox createStatCard(
            String title,
            Label value
    ) {

        Label heading =
                new Label(
                        title
                );

        heading
                .getStyleClass()
                .add(
                        "recovery-stat-title"
                );


        value
                .getStyleClass()
                .add(
                        "recovery-stat-value"
                );


        VBox card =
                new VBox(
                        8,
                        heading,
                        value
                );

        card.setAlignment(
                Pos.CENTER_LEFT
        );

        card.setMaxWidth(
                Double.MAX_VALUE
        );

        HBox.setHgrow(
                card,
                Priority.ALWAYS
        );

        card
                .getStyleClass()
                .add(
                        "recovery-stat-card"
                );

        return card;
    }


    /*
     * =============================================================
     * START ANALYSIS
     * =============================================================
     */

    private void startAnalysis() {

        statusLabel.setText(
                "ANALYZING"
        );

        messageLabel.setText(
                "Scanning sectors with the URE Recovery Engine..."
        );

        progressIndicator.setVisible(
                true
        );

        backButton.setDisable(
                false
        );

        continueButton.setDisable(
                true
        );


        analysisTask =
                new Task<>() {

                    @Override
                    protected RecoveryResult call()
                            throws Exception {

                        try (
                                SectorReader reader =
                                        new SectorReader(
                                                source.getAbsolutePath()
                                        )
                        ) {

                            DiskScanner scanner =
                                    new DiskScanner(
                                            reader
                                    );


                            RecoveryEngine engine =
                                    new RecoveryEngine(
                                            scanner
                                    );


                            long totalSectors =
                                    reader.getTotalSectors();


                            return engine.analyze(
                                    0,
                                    totalSectors
                            );
                        }
                    }
                };


        analysisTask.setOnSucceeded(
                event -> {

                    RecoveryResult result =
                            analysisTask.getValue();

                    showResult(
                            result
                    );
                }
        );


        analysisTask.setOnFailed(
                event -> {

                    Throwable error =
                            analysisTask.getException();

                    progressIndicator.setVisible(
                            false
                    );

                    statusLabel.setText(
                            "ANALYSIS FAILED"
                    );

                    messageLabel.setText(
                            error != null
                                    ? error.getMessage()
                                    : "Unknown recovery analysis error."
                    );

                    continueButton.setDisable(
                            true
                    );


                    System.err.println(
                            "=== RECOVERY ANALYSIS FAILED ==="
                    );

                    if (error != null) {
                        error.printStackTrace();
                    }
                }
        );


        analysisTask.setOnCancelled(
                event -> {

                    progressIndicator.setVisible(
                            false
                    );

                    statusLabel.setText(
                            "ANALYSIS CANCELLED"
                    );

                    messageLabel.setText(
                            "Recovery analysis was cancelled."
                    );
                }
        );


        Thread worker =
                new Thread(
                        analysisTask,
                        "minion-ure-recovery-analysis"
                );

        worker.setDaemon(
                true
        );

        worker.start();
    }


    /*
     * =============================================================
     * SHOW RESULT
     * =============================================================
     */

    private void showResult(
            RecoveryResult result
    ) {

        progressIndicator.setVisible(
                false
        );


        totalValue.setText(
                String.valueOf(
                        result.getTotalScanned()
                )
        );


        recoverableValue.setText(
                String.valueOf(
                        result.getRecoverableSectors()
                )
        );


        emptyValue.setText(
                String.valueOf(
                        result.getEmptySectors()
                )
        );


        percentageValue.setText(
                String.format(
                        "%.4f%%",
                        result.getRecoveryPercentage()
                )
        );


        if (
                result.hasRecoverableData()
        ) {

            statusLabel.setText(
                    "RECOVERABLE DATA DETECTED"
            );

            messageLabel.setText(
                    result.getRecoverableSectors()
                            +
                    " sectors contain data that may be recoverable."
            );

            continueButton.setDisable(
                    false
            );

        } else {

            statusLabel.setText(
                    "NO RECOVERABLE DATA"
            );

            messageLabel.setText(
                    "No sectors containing recoverable data were detected."
            );

            continueButton.setDisable(
                    true
            );
        }


        System.out.println(
                "=== RECOVERY ANALYSIS COMPLETE ==="
        );

        System.out.println(
                result
        );
    }


    /*
     * =============================================================
     * CANCEL
     * =============================================================
     */

    private void cancelAnalysis() {

        if (
                analysisTask != null
                        &&
                analysisTask.isRunning()
        ) {

            analysisTask.cancel(
                    true
            );
        }
    }
}
