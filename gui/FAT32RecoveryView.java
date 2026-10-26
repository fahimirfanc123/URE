package gui;

import core.disk.SectorReader;
import core.recovery.RecoveryWriter;
import filesystem.fat32.FAT32BootSector;
import filesystem.fat32.FAT32DeletedFileScanner;
import filesystem.fat32.FAT32DirectoryEntry;
import filesystem.fat32.FAT32FileRecovery;
import javafx.stage.DirectoryChooser;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
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

public class FAT32RecoveryView extends VBox {

    private final Stage stage;
    private final File source;
    private final Runnable onBack;

    private final long partitionOffsetBytes;
    private final long partitionLengthBytes;
    private final VBox filesContainer;
    private final Label statusLabel;
    private final Button exitButton;
    private final ProgressIndicator loadingIndicator;
    private List<FAT32DirectoryEntry> scannedFiles = List.of();
    private final Button recoverAllButton;

    public FAT32RecoveryView(
            Stage stage,
            File source,
            Runnable onBack
    ) {
        this(stage, source, onBack, 0L, 0L);
    }

    public FAT32RecoveryView(
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

        recoverAllButton = new Button("RECOVER ALL");
        recoverAllButton.getStyleClass().add("fat32-recover-button");
        recoverAllButton.setDisable(true);
        recoverAllButton.setOnAction(event -> chooseBatchOutput());

        setMinWidth(0);
        setMinHeight(0);
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        setSpacing(18);
        setPadding(new Insets(30, 45, 30, 45));

        getStyleClass().add("fat32-recovery-page");


        /*
         * HEADER
         */

        Label module =
                new Label("FAT32 RECOVERY");

        module.getStyleClass().add(
                "fat32-module-label"
        );


        Label title =
                new Label("DELETED FILE RECOVERY");

        title.getStyleClass().add(
                "fat32-recovery-title"
        );


        Label subtitle =
                new Label(
                        "Deleted FAT32 directory entries detected by MINION URE."
                );

        subtitle.getStyleClass().add(
                "fat32-recovery-subtitle"
        );


        VBox header =
                new VBox(
                        5,
                        module,
                        title,
                        subtitle
                );


        /*
         * SOURCE
         */

        Label sourceTitle =
                new Label("SOURCE");

        sourceTitle.getStyleClass().add(
                "fat32-small-label"
        );


        Label sourceName =
                new Label(source.getName());

        sourceName.getStyleClass().add(
                "fat32-source-name"
        );


        Label sourcePath =
                new Label(source.getAbsolutePath());

        sourcePath.getStyleClass().add(
                "fat32-source-path"
        );


        VBox sourcePanel =
                new VBox(
                        5,
                        sourceTitle,
                        sourceName,
                        sourcePath
                );

        sourcePanel.getStyleClass().add(
                "fat32-source-panel"
        );


        /*
         * STATUS
         */

        statusLabel =
                new Label("SCANNING FAT32 DIRECTORY...");

        statusLabel.getStyleClass().add(
                "fat32-status"
        );


        loadingIndicator =
                new ProgressIndicator();

        loadingIndicator.setPrefSize(
                24,
                24
        );


        HBox statusBox =
                new HBox(
                        12,
                        loadingIndicator,
                        statusLabel
                );

        statusBox.setAlignment(
                Pos.CENTER_LEFT
        );


        /*
         * FILE LIST
         */

        filesContainer =
                new VBox(10);

        filesContainer.setFillWidth(true);


        ScrollPane scrollPane =
                new ScrollPane(
                        filesContainer
                );

        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);

        scrollPane.getStyleClass().add(
                "fat32-scroll"
        );

        VBox.setVgrow(
                scrollPane,
                Priority.ALWAYS
        );


        /*
         * BACK
         */

        Button backButton =
                new Button("BACK");

        backButton.getStyleClass().add(
                "fat32-back-button"
        );

        backButton.setOnAction(
                event -> {

                    if (onBack != null) {
                        onBack.run();
                    }
                }
        );


        HBox footer = new HBox(12, backButton, recoverAllButton, exitButton);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(
                header,
                sourcePanel,
                statusBox,
                scrollPane,
                footer
        );


        /*
         * Begin filesystem scan.
         */

        startDeletedFileScan();
    }


    /*
     * =============================================================
     * SCAN DELETED FILES
     * =============================================================
     */

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

        Task<List<FAT32DirectoryEntry>> task =
                new Task<>() {

                    @Override
                    protected List<FAT32DirectoryEntry> call()
                            throws Exception {

                        try (SectorReader reader = openSourceReader()) {

                            FAT32BootSector boot =
                                    FAT32BootSector.parse(
                                            reader
                                    );


                            FAT32DeletedFileScanner scanner =
                                    new FAT32DeletedFileScanner(
                                            reader,
                                            boot
                                    );


                            return scanner.scanRootDirectory();
                        }
                    }
                };


        task.setOnSucceeded(
                event -> {

                    loadingIndicator.setVisible(
                            false
                    );


                    List<FAT32DirectoryEntry> files =
                            task.getValue();
                    scannedFiles = List.copyOf(files);
                    recoverAllButton.setDisable(files.isEmpty());

                    if (files.isEmpty()) {

                        statusLabel.setText(
                                "NO DELETED FILES FOUND"
                        );

                        showEmptyState();

                        return;
                    }


                    statusLabel.setText(
                            files.size()
                                    +
                            (files.size() == 1
                                    ? " DELETED FILE FOUND"
                                    : " DELETED FILES FOUND")
                    );


                    for (
                            FAT32DirectoryEntry entry :
                            files
                    ) {

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

                    statusLabel.setText(
                            "FAT32 SCAN FAILED"
                    );


                    Label error =
                            new Label(
                                    task.getException()
                                            != null
                                            ? task.getException()
                                                .getMessage()
                                            : "Unknown FAT32 error"
                            );

                    error.getStyleClass().add(
                            "fat32-error"
                    );


                    filesContainer
                            .getChildren()
                            .add(
                                    error
                            );


                    if (task.getException() != null) {
                        task.getException()
                                .printStackTrace();
                    }
                }
        );


        Thread thread =
                new Thread(
                        task,
                        "minion-ure-fat32-scan"
                );

        thread.setDaemon(true);
        thread.start();
    }


    /*
     * =============================================================
     * FILE CARD
     * =============================================================
     */

    private VBox createFileCard(
            FAT32DirectoryEntry entry
    ) {

        Label name =
                new Label(
                        entry.getFileName()
                );

        name.getStyleClass().add(
                "fat32-file-name"
        );


        Label deleted =
                new Label("DELETED");

        deleted.getStyleClass().add(
                "fat32-deleted-badge"
        );


        Region spacer =
                new Region();

        HBox.setHgrow(
                spacer,
                Priority.ALWAYS
        );


        HBox heading =
                new HBox(
                        10,
                        name,
                        spacer,
                        deleted
                );

        heading.setAlignment(
                Pos.CENTER_LEFT
        );


        Label size =
                new Label(
                        "SIZE  "
                                +
                        formatBytes(
                                entry.getFileSize()
                        )
                );


        Label cluster =
                new Label(
                        "START CLUSTER  "
                                +
                        entry.getStartCluster()
                );


        size.getStyleClass().add(
                "fat32-file-info"
        );

        cluster.getStyleClass().add(
                "fat32-file-info"
        );


        HBox metadata =
                new HBox(
                        25,
                        size,
                        cluster
                );


        Button recover =
                new Button(
                        "RECOVER FILE"
                );

        recover.getStyleClass().add(
                "fat32-recover-button"
        );


        recover.setOnAction(
                event ->
                        chooseOutput(
                                entry,
                                recover
                        )
        );


        VBox card =
                new VBox(
                        12,
                        heading,
                        metadata,
                        recover
                );

        card.getStyleClass().add(
                "fat32-file-card"
        );

        return card;
    }


    private void chooseBatchOutput() {
        if (scannedFiles.isEmpty()) {
            statusLabel.setText("NO FILES AVAILABLE TO RECOVER");
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Folder for Recovered Files");

        File destination = chooser.showDialog(stage);
        if (destination == null) {
            return;
        }

        if (!destination.isDirectory() || !destination.canWrite()) {
            statusLabel.setText("DESTINATION FOLDER IS NOT WRITABLE");
            return;
        }

        recoverAllFiles(destination.toPath());
    }

    private void recoverAllFiles(Path destination) {
        List<FAT32DirectoryEntry> filesToRecover = List.copyOf(scannedFiles);

        recoverAllButton.setDisable(true);
        loadingIndicator.setVisible(true);
        loadingIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                int recovered = 0;
                int failed = 0;
                Set<String> reservedNames = new HashSet<>();

                for (int i = 0; i < filesToRecover.size(); i++) {
                    FAT32DirectoryEntry entry = filesToRecover.get(i);
                    updateMessage("RECOVERING " + (i + 1) + " OF "
                            + filesToRecover.size() + ": " + entry.getFileName());
                    updateProgress(i, filesToRecover.size());

                    try {
                        String safeName = entry.getFileName()
                                .replace('?', '_')
                                .replace('/', '_')
                                .replace('\\', '_');

                        if (safeName.isBlank() || safeName.equals(".")
                                || safeName.equals("..")) {
                            safeName = "recovered_file_" + (i + 1);
                        }

                        Path output = uniqueOutputPath(
                                destination, safeName, reservedNames);

                        try (SectorReader reader = openSourceReader()) {
                            FAT32BootSector boot = FAT32BootSector.parse(reader);
                            FAT32FileRecovery recovery =
                                    new FAT32FileRecovery(reader, boot);
                            byte[] data = recovery.recover(entry);
                            RecoveryWriter writer = new RecoveryWriter();
                            writer.write(data, output);
                        }

                        recovered++;
                    } catch (Exception ex) {
                        failed++;
                        System.err.println("Failed to recover "
                                + entry.getFileName() + ": " + ex.getMessage());
                    }

                    updateProgress(i + 1, filesToRecover.size());
                }

                return "BATCH COMPLETE — RECOVERED: " + recovered
                        + " | FAILED: " + failed
                        + " | TOTAL: " + filesToRecover.size();
            }
        };

        task.messageProperty().addListener((observable, oldMessage, newMessage) ->
                statusLabel.setText(newMessage));
        loadingIndicator.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(event -> {
            loadingIndicator.progressProperty().unbind();
            loadingIndicator.setVisible(false);
            statusLabel.setText(task.getValue());
            recoverAllButton.setDisable(false);
            exitButton.setManaged(true);
            exitButton.setVisible(true);
        });

        task.setOnFailed(event -> {
            loadingIndicator.progressProperty().unbind();
            loadingIndicator.setVisible(false);
            statusLabel.setText("BATCH RECOVERY STOPPED: "
                    + (task.getException() == null
                    ? "UNKNOWN ERROR"
                    : task.getException().getMessage()));
            recoverAllButton.setDisable(false);
        });

        Thread thread = new Thread(task, "minion-ure-fat32-batch-recovery");
        thread.setDaemon(true);
        thread.start();
    }

    private Path uniqueOutputPath(
            Path destination,
            String fileName,
            Set<String> reservedNames) {

        String baseName = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            baseName = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }

        for (int suffix = 0; ; suffix++) {
            String candidateName = suffix == 0
                    ? baseName + extension
                    : baseName + "_" + suffix + extension;
            Path candidate = destination.resolve(candidateName)
                    .toAbsolutePath().normalize();
            String key = candidate.toString();

            if (!Files.exists(candidate) && reservedNames.add(key)) {
                return candidate;
            }
        }
    }

    /*
     * =============================================================
     * OUTPUT LOCATION
     * =============================================================
     */

    private void chooseOutput(
            FAT32DirectoryEntry entry,
            Button button
    ) {

        FileChooser chooser =
                new FileChooser();

        chooser.setTitle(
                "Save Recovered FAT32 File"
        );


        String suggestedName =
                entry.getFileName()
                        .replace(
                                '?',
                                '_'
                        );


        chooser.setInitialFileName(
                suggestedName
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


    /*
     * =============================================================
     * ACTUAL FAT32 RECOVERY
     * =============================================================
     */

    private void recoverFile(
            FAT32DirectoryEntry entry,
            Path output,
            Button button
    ) {

        button.setDisable(true);
        button.setText("RECOVERING...");

        statusLabel.setText(
                "RECOVERING "
                        +
                entry.getFileName()
                        +
                "..."
        );


        Task<Path> task =
                new Task<>() {

                    @Override
                    protected Path call()
                            throws Exception {

                        try (SectorReader reader = openSourceReader()) {

                            FAT32BootSector boot =
                                    FAT32BootSector.parse(
                                            reader
                                    );


                            FAT32FileRecovery recovery =
                                    new FAT32FileRecovery(
                                            reader,
                                            boot
                                    );


                            byte[] data =
                                    recovery.recover(
                                            entry
                                    );


                            RecoveryWriter writer =
                                    new RecoveryWriter();


                            return writer.write(
                                    data,
                                    output
                            );
                        }
                    }
                };


        task.setOnSucceeded(
                event -> {

                    Path result =
                            task.getValue();


                    button.setText(
                            "RECOVERED"
                    );


                    statusLabel.setText(
                            "RECOVERY COMPLETE  •  "
                                    +
                            result.getFileName()
                    );

                    exitButton.setManaged(true);
                    exitButton.setVisible(true);


                    System.out.println(
                            "=== FAT32 FILE RECOVERED ==="
                    );

                    System.out.println(
                            "File: "
                                    +
                            entry.getFileName()
                    );

                    System.out.println(
                            "Bytes: "
                                    +
                            entry.getFileSize()
                    );

                    System.out.println(
                            "Output: "
                                    +
                            result.toAbsolutePath()
                    );
                }
        );


        task.setOnFailed(
                event -> {

                    button.setDisable(false);

                    button.setText(
                            "RETRY RECOVERY"
                    );

                    statusLabel.setText(
                            "RECOVERY FAILED"
                    );


                    if (task.getException() != null) {
                        task.getException()
                                .printStackTrace();
                    }
                }
        );


        Thread thread =
                new Thread(
                        task,
                        "minion-ure-fat32-recovery"
                );

        thread.setDaemon(true);
        thread.start();
    }


    /*
     * =============================================================
     * EMPTY STATE
     * =============================================================
     */

    private void showEmptyState() {

        Label title =
                new Label(
                        "NO DELETED FAT32 FILES DETECTED"
                );

        title.getStyleClass().add(
                "fat32-empty-title"
        );


        Label description =
                new Label(
                        "MINION URE did not find deleted short-name "
                                +
                        "directory entries in the FAT32 root directory."
                );

        description.setWrapText(true);

        description.getStyleClass().add(
                "fat32-empty-description"
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
                new Insets(50)
        );


        filesContainer
                .getChildren()
                .add(
                        empty
                );
    }


    /*
     * =============================================================
     * SIZE FORMAT
     * =============================================================
     */

    private String formatBytes(
            long bytes
    ) {

        if (bytes < 1024) {
            return bytes + " bytes";
        }

        double kb =
                bytes / 1024.0;

        if (kb < 1024) {
            return String.format(
                    "%.1f KB",
                    kb
            );
        }

        double mb =
                kb / 1024.0;

        return String.format(
                "%.1f MB",
                mb
        );
    }
}
