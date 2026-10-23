package gui;

import core.disk.SectorReader;
import core.recovery.RecoveryWriter;
import filesystem.ntfs.NTFSBootSector;
import filesystem.ntfs.NTFSDeletedFileScanner;
import filesystem.ntfs.NTFSFileEntry;
import filesystem.ntfs.NTFSFileRecovery;
import filesystem.ntfs.NTFSMFTReader;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class NTFSRecoveryView extends VBox {

    private final Stage stage;
    private final File source;
    private final Runnable onBack;

    private final long partitionOffsetBytes;
    private final long partitionLengthBytes;
private final VBox filesContainer;
    private final Label statusLabel;
    private final Button exitButton;
    private final ProgressIndicator loadingIndicator;


    public NTFSRecoveryView(
            Stage stage,
            File source,
            Runnable onBack
    ) {
        this(stage, source, onBack, 0L, 0L);
    }

    public NTFSRecoveryView(
            Stage stage,
            File source,
            Runnable onBack,
            long partitionOffsetBytes,
            long partitionLengthBytes
    ) {
        if (stage == null || source == null) {
            throw new IllegalArgumentException(
                    "Stage and source cannot be null"
            );
        }
        if (partitionOffsetBytes < 0 || partitionLengthBytes < 0) {
            throw new IllegalArgumentException(
                    "Partition offset and length cannot be negative"
            );
        }
        if (partitionLengthBytes == 0 && partitionOffsetBytes != 0) {
            throw new IllegalArgumentException(
                    "A nonzero partition offset requires a positive length"
            );
        }

        this.stage = stage;
        this.source = source;
        this.onBack = onBack;
        this.partitionOffsetBytes = partitionOffsetBytes;
        this.partitionLengthBytes = partitionLengthBytes;

        exitButton = new Button("EXIT");
        exitButton.getStyleClass().add("ure-exit-button");
        exitButton.setVisible(false);
        exitButton.setManaged(false);
        exitButton.setOnAction(event -> stage.close());

        setMinWidth(0);
        setMinHeight(0);

        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        setSpacing(18);

        setPadding(
                new Insets(
                        28,
                        34,
                        28,
                        34
                )
        );

        getStyleClass()
                .add(
                        "ntfs-recovery-page"
                );


        Label moduleLabel =
                new Label(
                        "NTFS RECOVERY"
                );

        moduleLabel
                .getStyleClass()
                .add(
                        "ntfs-module-label"
                );


        Label title =
                new Label(
                        "DELETED FILE RECOVERY"
                );

        title
                .getStyleClass()
                .add(
                        "ntfs-recovery-title"
                );


        Label subtitle =
                new Label(
                        "Scan NTFS $MFT records and recover deleted files."
                );

        subtitle
                .getStyleClass()
                .add(
                        "ntfs-recovery-subtitle"
                );


        VBox sourcePanel =
                createSourcePanel();


        loadingIndicator =
                new ProgressIndicator();

        loadingIndicator.setPrefSize(
                24,
                24
        );


        statusLabel =
                new Label(
                        "SCANNING NTFS MFT..."
                );

        statusLabel
                .getStyleClass()
                .add(
                        "ntfs-status"
                );


        HBox statusBox =
                new HBox(
                        10,
                        loadingIndicator,
                        statusLabel
                );

        statusBox.setAlignment(
                Pos.CENTER_LEFT
        );


        filesContainer =
                new VBox(
                        10
                );

        filesContainer.setFillWidth(
                true
        );


        ScrollPane scrollPane =
                new ScrollPane(
                        filesContainer
                );

        scrollPane.setFitToWidth(
                true
        );

        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        scrollPane
                .getStyleClass()
                .add(
                        "ntfs-scroll"
                );

        VBox.setVgrow(
                scrollPane,
                Priority.ALWAYS
        );


        Button backButton =
                new Button(
                        "BACK"
                );

        backButton
                .getStyleClass()
                .add(
                        "ntfs-back-button"
                );

        backButton.setOnAction(
                event -> onBack.run()
        );


        HBox footer = new HBox(12, backButton, exitButton);

        footer.setAlignment(
                Pos.CENTER_LEFT
        );


        getChildren().addAll(
                moduleLabel,
                title,
                subtitle,
                sourcePanel,
                statusBox,
                scrollPane,
                footer
        );


        startDeletedFileScan();
    }


    private VBox createSourcePanel() {

        Label sourceLabel =
                new Label(
                        "SOURCE"
                );

        sourceLabel
                .getStyleClass()
                .add(
                        "ntfs-small-label"
                );


        Label sourceName =
                new Label(
                        source.getName()
                );

        sourceName
                .getStyleClass()
                .add(
                        "ntfs-source-name"
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
                        "ntfs-source-path"
                );


        VBox panel =
                new VBox(
                        5,
                        sourceLabel,
                        sourceName,
                        sourcePath
                );

        panel
                .getStyleClass()
                .add(
                        "ntfs-source-panel"
                );

        return panel;
    }


    private SectorReader openSourceReader()
            throws java.io.IOException {
        if (partitionLengthBytes > 0) {
            return new SectorReader(
                    source.getAbsolutePath(),
                    512,
                    partitionOffsetBytes,
                    partitionLengthBytes
            );
        }
        return new SectorReader(source.getAbsolutePath());
    }

    private void startDeletedFileScan() {

        Task<List<NTFSFileEntry>> task =
                new Task<>() {

                    @Override
                    protected List<NTFSFileEntry> call()
                            throws Exception {

                        try (SectorReader reader = openSourceReader()) {

                            NTFSBootSector boot =
                                    NTFSBootSector.parse(
                                            reader
                                    );

                            NTFSMFTReader mftReader =
                                    new NTFSMFTReader(
                                            reader,
                                            boot
                                    );

                            NTFSDeletedFileScanner scanner =
                                    new NTFSDeletedFileScanner(
                                            mftReader
                                    );

                            return scanner.scan(
                                    4096
                            );
                        }
                    }
                };


        task.setOnSucceeded(
                event -> {

                    loadingIndicator.setVisible(
                            false
                    );

                    List<NTFSFileEntry> files =
                            task.getValue();

                    if (files == null ||
                        files.isEmpty()) {

                        statusLabel.setText(
                                "NO DELETED FILES FOUND"
                        );

                        showEmptyState();

                        return;
                    }


                    statusLabel.setText(
                            files.size()
                                    + " DELETED FILE(S) FOUND"
                    );


                    for (NTFSFileEntry entry : files) {

                        filesContainer
                                .getChildren()
                                .add(
                                        createFileCard(
                                                entry
                                        )
                                );
                    }
                }
        );


        task.setOnFailed(
                event -> {

                    loadingIndicator.setVisible(
                            false
                    );

                    Throwable error =
                            task.getException();

                    statusLabel.setText(
                            "NTFS SCAN FAILED"
                    );


                    Label errorLabel =
                            new Label(
                                    error == null
                                            ? "Unknown error"
                                            : error.getMessage()
                            );

                    errorLabel.setWrapText(
                            true
                    );

                    errorLabel
                            .getStyleClass()
                            .add(
                                    "ntfs-error"
                            );


                    filesContainer
                            .getChildren()
                            .add(
                                    errorLabel
                            );
                }
        );


        Thread thread =
                new Thread(
                        task,
                        "minion-ure-ntfs-scanner"
                );

        thread.setDaemon(
                true
        );

        thread.start();
    }


    private VBox createFileCard(
            NTFSFileEntry entry
    ) {

        Label fileName =
                new Label(
                        entry.getFileName()
                );

        fileName
                .getStyleClass()
                .add(
                        "ntfs-file-name"
                );


        Label deletedBadge =
                new Label(
                        "DELETED"
                );

        deletedBadge
                .getStyleClass()
                .add(
                        "ntfs-deleted-badge"
                );


        Region spacer =
                new Region();

        HBox.setHgrow(
                spacer,
                Priority.ALWAYS
        );


        HBox header =
                new HBox(
                        10,
                        fileName,
                        spacer,
                        deletedBadge
                );

        header.setAlignment(
                Pos.CENTER_LEFT
        );


        Label recordInfo =
                new Label(
                        "MFT RECORD  "
                                + entry.getRecordNumber()
                );

        recordInfo
                .getStyleClass()
                .add(
                        "ntfs-file-info"
                );


        Button recoverButton =
                new Button(
                        "RECOVER FILE"
                );

        recoverButton
                .getStyleClass()
                .add(
                        "ntfs-recover-button"
                );


        recoverButton.setOnAction(
                event ->
                        chooseOutput(
                                entry,
                                recoverButton
                        )
        );


        VBox card =
                new VBox(
                        10,
                        header,
                        recordInfo,
                        recoverButton
                );

        card
                .getStyleClass()
                .add(
                        "ntfs-file-card"
                );

        return card;
    }


    private void chooseOutput(
            NTFSFileEntry entry,
            Button button
    ) {

        FileChooser chooser =
                new FileChooser();

        chooser.setTitle(
                "Save Recovered NTFS File"
        );

        chooser.setInitialFileName(
                entry.getFileName()
        );


        File output =
                chooser.showSaveDialog(
                        stage
                );


        if (output == null) {
            return;
        }


        recoverFile(
                entry,
                output.toPath(),
                button
        );
    }


    private void recoverFile(
            NTFSFileEntry entry,
            Path output,
            Button button
    ) {

        button.setDisable(
                true
        );

        button.setText(
                "RECOVERING..."
        );


        statusLabel.setText(
                "RECOVERING "
                        + entry.getFileName()
        );


        Task<Path> task =
                new Task<>() {

                    @Override
                    protected Path call()
                            throws Exception {

                        try (SectorReader reader = openSourceReader()) {

                            NTFSBootSector boot =
                                    NTFSBootSector.parse(
                                            reader
                                    );


                            NTFSMFTReader mftReader =
                                    new NTFSMFTReader(
                                            reader,
                                            boot
                                    );


                            NTFSFileRecovery recovery =
                                    new NTFSFileRecovery(
                                            reader,
                                            boot,
                                            mftReader
                                    );


                            byte[] recovered =
                                    recovery.recover(
                                            entry
                                    );


                            RecoveryWriter writer =
                                    new RecoveryWriter();


                            Path result =
                                    writer.write(
                                            recovered,
                                            output
                                    );


                            System.out.println(
                                    "=== NTFS FILE RECOVERED ==="
                            );

                            System.out.println(
                                    "File: "
                                            + entry.getFileName()
                            );

                            System.out.println(
                                    "Bytes: "
                                            + recovered.length
                            );

                            System.out.println(
                                    "Output: "
                                            + result.toAbsolutePath()
                            );


                            return result;
                        }
                    }
                };


        task.setOnSucceeded(
                event -> {

                    button.setText(
                            "RECOVERED"
                    );

                    statusLabel.setText(
                            "RECOVERY COMPLETE • "
                                    + entry.getFileName()
                    );

                    exitButton.setManaged(true);
                    exitButton.setVisible(true);
                }
        );


        task.setOnFailed(
                event -> {

                    button.setDisable(
                            false
                    );

                    button.setText(
                            "RETRY RECOVERY"
                    );

                    Throwable error =
                            task.getException();

                    statusLabel.setText(
                            "RECOVERY FAILED • "
                                    +
                                    (
                                            error == null
                                                    ? "UNKNOWN ERROR"
                                                    : error.getMessage()
                                    )
                    );
                }
        );


        Thread thread =
                new Thread(
                        task,
                        "minion-ure-ntfs-recovery"
                );

        thread.setDaemon(
                true
        );

        thread.start();
    }


    private void showEmptyState() {

        Label title =
                new Label(
                        "NO DELETED NTFS FILES"
                );

        title
                .getStyleClass()
                .add(
                        "ntfs-empty-title"
                );


        Label description =
                new Label(
                        "No deleted file records were discovered in the scanned MFT range."
                );

        description.setWrapText(
                true
        );

        description
                .getStyleClass()
                .add(
                        "ntfs-empty-description"
                );


        VBox empty =
                new VBox(
                        8,
                        title,
                        description
                );

        empty.setAlignment(
                Pos.CENTER
        );

        empty.setPadding(
                new Insets(
                        50
                )
        );


        filesContainer
                .getChildren()
                .add(
                        empty
                );
    }
}
