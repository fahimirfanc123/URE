package gui;

import common.model.RecoveryResult;
import core.disk.SectorReader;
import core.recovery.RecoveryWriter;
import core.scan.DiskScanner;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;

/**
 * MODULE 6 — RECOVERY MODE
 *
 * Provides available recovery methods.
 *
 * Currently implemented:
 * - Raw Sector Recovery
 *
 * Future:
 * - FAT32 Deleted File Recovery
 * - NTFS Deleted File Recovery
 */
public class RecoveryModeView extends VBox {

    private final Stage stage;
    private final File source;
    private final RecoveryResult recoveryResult;
    private final Runnable onBack;

    private final Label statusLabel;
    private final ProgressBar progressBar;
    private final Button rawRecoveryButton;

    public RecoveryModeView(
            Stage stage,
            File source,
            RecoveryResult recoveryResult,
            Runnable onBack
    ) {

        this.stage = stage;
        this.source = source;
        this.recoveryResult = recoveryResult;
        this.onBack = onBack;

        setMinWidth(0);
        setMinHeight(0);

        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        setSpacing(22);

        setPadding(
                new Insets(
                        32,
                        45,
                        30,
                        45
                )
        );

        getStyleClass().add(
                "recovery-mode-page"
        );


        /*
         * =========================================================
         * HEADER
         * =========================================================
         */

        Label moduleLabel =
                new Label(
                        "MODULE 06  /  RECOVERY MODE"
                );

        moduleLabel
                .getStyleClass()
                .add(
                        "recovery-mode-module"
                );


        Label title =
                new Label(
                        "SELECT RECOVERY METHOD"
                );

        title
                .getStyleClass()
                .add(
                        "recovery-mode-title"
                );


        Label subtitle =
                new Label(
                        "Choose how MINION URE should recover data from the selected source."
                );

        subtitle
                .getStyleClass()
                .add(
                        "recovery-mode-subtitle"
                );


        VBox header =
                new VBox(
                        6,
                        moduleLabel,
                        title,
                        subtitle
                );


        /*
         * =========================================================
         * SOURCE INFORMATION
         * =========================================================
         */

        Label sourceLabel =
                new Label(
                        "SOURCE"
                );

        sourceLabel
                .getStyleClass()
                .add(
                        "recovery-mode-small-label"
                );


        Label sourceName =
                new Label(
                        source.getName()
                );

        sourceName
                .getStyleClass()
                .add(
                        "recovery-mode-source"
                );


        Label detected =
                new Label(
                        recoveryResult.getRecoverableSectors()
                                +
                        " sectors containing recoverable data detected"
                );

        detected
                .getStyleClass()
                .add(
                        "recovery-mode-detected"
                );


        VBox sourcePanel =
                new VBox(
                        6,
                        sourceLabel,
                        sourceName,
                        detected
                );

        sourcePanel
                .getStyleClass()
                .add(
                        "recovery-mode-source-panel"
                );


        /*
         * =========================================================
         * RAW RECOVERY
         * =========================================================
         */

        Label rawTitle =
                new Label(
                        "RAW SECTOR RECOVERY"
                );

        rawTitle
                .getStyleClass()
                .add(
                        "recovery-option-title"
                );


        Label rawDescription =
                new Label(
                        "Extract sectors containing raw data and save them into a recovery file."
                );

        rawDescription.setWrapText(
                true
        );

        rawDescription
                .getStyleClass()
                .add(
                        "recovery-option-description"
                );


        Label rawStatus =
                new Label(
                        "AVAILABLE"
                );

        rawStatus
                .getStyleClass()
                .add(
                        "recovery-option-available"
                );


        rawRecoveryButton =
                new Button(
                        "START RAW RECOVERY"
                );

        rawRecoveryButton
                .getStyleClass()
                .add(
                        "raw-recovery-button"
                );


        rawRecoveryButton.setOnAction(
                event ->
                        chooseOutputAndRecover()
        );


        Region rawSpacer =
                new Region();

        HBox.setHgrow(
                rawSpacer,
                Priority.ALWAYS
        );


        HBox rawTop =
                new HBox(
                        12,
                        rawTitle,
                        rawSpacer,
                        rawStatus
                );

        rawTop.setAlignment(
                Pos.CENTER_LEFT
        );


        VBox rawCard =
                new VBox(
                        12,
                        rawTop,
                        rawDescription,
                        rawRecoveryButton
                );

        rawCard
                .getStyleClass()
                .add(
                        "recovery-option-card-active"
                );


        /*
 * =========================================================
 * FAT32 — AVAILABLE
 * =========================================================
 */

Label fat32Title =
        new Label(
                "FAT32 FILE RECOVERY"
        );

fat32Title
        .getStyleClass()
        .add(
                "recovery-option-title"
        );


Label fat32Description =
        new Label(
                "Detect and recover deleted files using FAT32 filesystem structures."
        );

fat32Description.setWrapText(
        true
);

fat32Description
        .getStyleClass()
        .add(
                "recovery-option-description"
        );


Label fat32Status =
        new Label(
                "AVAILABLE"
        );

fat32Status
        .getStyleClass()
        .add(
                "recovery-option-available"
        );


Button fat32Button =
        new Button(
                "FIND DELETED FILES"
        );

fat32Button
        .getStyleClass()
        .add(
                "raw-recovery-button"
        );


fat32Button.setOnAction(
        event -> {

            if (getParent()
                    instanceof javafx.scene.layout.BorderPane borderPane) {

                FAT32RecoveryView fat32View =
                        new FAT32RecoveryView(
                                stage,
                                source,
                                () -> borderPane.setCenter(this)
                        );

                borderPane.setCenter(
                        fat32View
                );
            }
        }
);


VBox fat32Card =
        new VBox(
                12,
                fat32Title,
                fat32Description,
                fat32Status,
                fat32Button
        );


fat32Card
        .getStyleClass()
        .add(
                "recovery-option-card-active"
        ); 

        /*
 * =========================================================
 * NTFS — AVAILABLE
 * =========================================================
 */

Label ntfsTitle =
        new Label(
                "NTFS FILE RECOVERY"
        );

ntfsTitle
        .getStyleClass()
        .add(
                "recovery-option-title"
        );


Label ntfsDescription =
        new Label(
                "Detect and recover deleted files using NTFS MFT metadata."
        );

ntfsDescription.setWrapText(
        true
);

ntfsDescription
        .getStyleClass()
        .add(
                "recovery-option-description"
        );


Label ntfsStatus =
        new Label(
                "AVAILABLE"
        );

ntfsStatus
        .getStyleClass()
        .add(
                "recovery-option-available"
        );


Button ntfsButton =
        new Button(
                "FIND DELETED FILES"
        );

ntfsButton
        .getStyleClass()
        .add(
                "raw-recovery-button"
        );


ntfsButton.setOnAction(
        event -> {

            if (getParent()
                    instanceof javafx.scene.layout.BorderPane borderPane) {

                NTFSRecoveryView ntfsView =
                        new NTFSRecoveryView(
                                stage,
                                source,
                                () -> borderPane.setCenter(this)
                        );

                borderPane.setCenter(
                        ntfsView
                );
            }
        }
);


VBox ntfsCard =
        new VBox(
                12,
                ntfsTitle,
                ntfsDescription,
                ntfsStatus,
                ntfsButton
        );


ntfsCard
        .getStyleClass()
        .add(
                "recovery-option-card-active"
        );

        HBox recoveryMethods =
                new HBox(
                        14,
                        rawCard,
                        fat32Card,
                        ntfsCard
                );

        HBox.setHgrow(
                rawCard,
                Priority.ALWAYS
        );

        HBox.setHgrow(
                fat32Card,
                Priority.ALWAYS
        );

        HBox.setHgrow(
                ntfsCard,
                Priority.ALWAYS
        );

        rawCard.setMaxWidth(
                Double.MAX_VALUE
        );

        fat32Card.setMaxWidth(
                Double.MAX_VALUE
        );

        ntfsCard.setMaxWidth(
                Double.MAX_VALUE
        );


        /*
         * =========================================================
         * RECOVERY STATUS
         * =========================================================
         */

        statusLabel =
                new Label(
                        "READY FOR RECOVERY"
                );

        statusLabel
                .getStyleClass()
                .add(
                        "recovery-mode-status"
                );


        progressBar =
                new ProgressBar(
                        0
                );

        progressBar.setMaxWidth(
                Double.MAX_VALUE
        );

        progressBar.setVisible(
                false
        );


        VBox statusPanel =
                new VBox(
                        8,
                        statusLabel,
                        progressBar
                );

        statusPanel
                .getStyleClass()
                .add(
                        "recovery-mode-status-panel"
                );


        /*
         * =========================================================
         * SPACER
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
         * BACK
         * =========================================================
         */

        Button backButton =
                new Button(
                        "BACK"
                );

        backButton
                .getStyleClass()
                .add(
                        "recovery-mode-back"
                );


        backButton.setOnAction(
                event -> {

                    if (onBack != null) {
                        onBack.run();
                    }
                }
        );


        getChildren().addAll(
                header,
                sourcePanel,
                recoveryMethods,
                statusPanel,
                spacer,
                backButton
        );
    }


    /*
     * =============================================================
     * LOCKED CARD
     * =============================================================
     */

    private VBox createLockedCard(
            String title,
            String description
    ) {

        Label titleLabel =
                new Label(
                        title
                );

        titleLabel
                .getStyleClass()
                .add(
                        "recovery-option-title"
                );


        Label descriptionLabel =
                new Label(
                        description
                );

        descriptionLabel.setWrapText(
                true
        );

        descriptionLabel
                .getStyleClass()
                .add(
                        "recovery-option-description"
                );


        Label locked =
                new Label(
                        "LOCKED"
                );

        locked
                .getStyleClass()
                .add(
                        "recovery-option-locked"
                );


        Button button =
                new Button(
                        "NOT AVAILABLE"
                );

        button.setDisable(
                true
        );


        VBox card =
                new VBox(
                        12,
                        titleLabel,
                        descriptionLabel,
                        locked,
                        button
                );

        card
                .getStyleClass()
                .add(
                        "recovery-option-card-locked"
                );

        return card;
    }


    /*
     * =============================================================
     * OUTPUT FILE
     * =============================================================
     */

    private void chooseOutputAndRecover() {

        FileChooser chooser =
                new FileChooser();

        chooser.setTitle(
                "Save Recovered Raw Data"
        );


        chooser.setInitialFileName(
                "minion-recovered-data.bin"
        );


        chooser
                .getExtensionFilters()
                .add(
                        new FileChooser.ExtensionFilter(
                                "Binary Recovery File",
                                "*.bin"
                        )
                );


        File output =
                chooser.showSaveDialog(
                        stage
                );


        if (output == null) {
            return;
        }


        startRawRecovery(
                output.toPath()
        );
    }


    /*
     * =============================================================
     * RAW RECOVERY
     * =============================================================
     */

    private void startRawRecovery(
            Path outputPath
    ) {

        rawRecoveryButton.setDisable(
                true
        );

        progressBar.setVisible(
                true
        );

        progressBar.setProgress(
                0
        );

        statusLabel.setText(
                "RECOVERING RAW DATA..."
        );


        Task<Path> recoveryTask =
                new Task<>() {

                    @Override
                    protected Path call()
                            throws Exception {

                        try (
                                SectorReader reader =
                                        new SectorReader(
                                                source.getAbsolutePath()
                                        );

                                ByteArrayOutputStream recovered =
                                        new ByteArrayOutputStream()
                        ) {

                            DiskScanner scanner =
                                    new DiskScanner(
                                            reader
                                    );


                            long totalSectors =
                                    reader.getTotalSectors();


                            for (
                                    long sector = 0;
                                    sector < totalSectors;
                                    sector++
                            ) {

                                if (isCancelled()) {
                                    break;
                                }


                                if (
                                        scanner.hasData(
                                                sector
                                        )
                                ) {

                                    byte[] data =
                                            reader.readSector(
                                                    sector
                                            );

                                    recovered.write(
                                            data
                                    );
                                }


                                if (
                                        sector % 256 == 0
                                                ||
                                        sector ==
                                                totalSectors - 1
                                ) {

                                    updateProgress(
                                            sector + 1,
                                            totalSectors
                                    );
                                }
                            }


                            RecoveryWriter writer =
                                    new RecoveryWriter();


                            return writer.write(
                                    recovered.toByteArray(),
                                    outputPath
                            );
                        }
                    }
                };


        progressBar
                .progressProperty()
                .bind(
                        recoveryTask
                                .progressProperty()
                );


        recoveryTask.setOnSucceeded(
                event -> {

                    progressBar
                            .progressProperty()
                            .unbind();


                    progressBar.setProgress(
                            1
                    );


                    Path result =
                            recoveryTask.getValue();


                    statusLabel.setText(
                            "RECOVERY COMPLETE  •  "
                                    +
                            result.toAbsolutePath()
                    );


                    rawRecoveryButton.setDisable(
                            false
                    );


                    System.out.println(
                            "=== RAW RECOVERY COMPLETE ==="
                    );

                    System.out.println(
                            "Output: "
                                    +
                            result.toAbsolutePath()
                    );
                }
        );


        recoveryTask.setOnFailed(
                event -> {

                    progressBar
                            .progressProperty()
                            .unbind();


                    statusLabel.setText(
                            "RAW RECOVERY FAILED"
                    );


                    rawRecoveryButton.setDisable(
                            false
                    );


                    Throwable error =
                            recoveryTask.getException();


                    if (error != null) {
                        error.printStackTrace();
                    }
                }
        );


        Thread worker =
                new Thread(
                        recoveryTask,
                        "minion-ure-raw-recovery"
                );

        worker.setDaemon(
                true
        );

        worker.start();
    }
}
