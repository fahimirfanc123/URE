package gui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.function.Consumer;

public class DriveSelectionView
        extends VBox {

    private final Stage stage;

    private final Runnable onBack;

    private final Consumer<File> onScan;

    private final VBox driveContainer =
            new VBox(10);

    private File selectedSource;

    private final Label selectedName =
            new Label(
                    "No source selected"
            );

    private final Label selectedPath =
            new Label(
                    "Choose a drive or disk image"
            );

    private final Label selectedSize =
            new Label("");

    private final Button scanButton =
            new Button(
                    "➜   CONTINUE TO SCAN"
            );

    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public DriveSelectionView(
            Stage stage,
            Runnable onBack,
            Consumer<File> onScan
    ) {

        this.stage =
                stage;

        this.onBack =
                onBack;

        this.onScan =
                onScan;

        buildView();

        loadDrives();
    }

    /*
     * =========================================================
     * BUILD SCREEN
     * =========================================================
     */

    private void buildView() {

        getStyleClass()
                .add(
                        "drive-page"
                );

        setAlignment(
                Pos.TOP_CENTER
        );

        setSpacing(
                18
        );

        /*
         * =====================================================
         * PAGE HEADER
         * =====================================================
         */

        HBox header =
                new HBox();

        header.setAlignment(
                Pos.CENTER_LEFT
        );

        header.setMaxWidth(
                900
        );

        Button backButton =
                new Button(
                        "‹  BACK"
                );

        backButton
                .getStyleClass()
                .add(
                        "drive-secondary-button"
                );

        backButton.setFocusTraversable(
                false
        );

        Label stepLabel =
                new Label(
                        "RECOVERY / SOURCE"
                );

        stepLabel
                .getStyleClass()
                .add(
                        "drive-step-label"
                );

        javafx.scene.layout.Region headerSpacer =
                new javafx.scene.layout.Region();

        HBox.setHgrow(
                headerSpacer,
                Priority.ALWAYS
        );

        Button refreshButton =
                new Button(
                        "↻  REFRESH"
                );

        refreshButton
                .getStyleClass()
                .add(
                        "drive-secondary-button"
                );

        refreshButton.setFocusTraversable(
                false
        );

        header
                .getChildren()
                .addAll(
                        backButton,
                        headerSpacer,
                        stepLabel,
                        refreshButton
                );

        /*
         * =====================================================
         * TITLE
         * =====================================================
         */

        Label title =
                new Label(
                        "SELECT RECOVERY SOURCE"
                );

        title
                .getStyleClass()
                .add(
                        "drive-page-title"
                );

        Label subtitle =
                new Label(
                        "Choose the drive or disk image you want MINION URE to analyse."
                );

        subtitle
                .getStyleClass()
                .add(
                        "drive-page-subtitle"
                );

        /*
         * =====================================================
         * SOURCE AREA
         * =====================================================
         */

        HBox sourceArea =
                new HBox(18);

        sourceArea.setAlignment(
                Pos.TOP_CENTER
        );

        sourceArea.setMaxWidth(
                900
        );

        /*
         * =====================================================
         * LEFT PANEL — AVAILABLE DRIVES
         * =====================================================
         */

        VBox drivesPanel =
                new VBox(14);

        drivesPanel
                .getStyleClass()
                .add(
                        "drive-panel"
                );

        HBox drivesHeader =
                new HBox();

        drivesHeader.setAlignment(
                Pos.CENTER_LEFT
        );

        Label drivesTitle =
                new Label(
                        "AVAILABLE VOLUMES"
                );

        drivesTitle
                .getStyleClass()
                .add(
                        "drive-panel-title"
                );

        javafx.scene.layout.Region drivesSpacer =
                new javafx.scene.layout.Region();

        HBox.setHgrow(
                drivesSpacer,
                Priority.ALWAYS
        );

        Label driveStatus =
                new Label(
                        "LOCAL"
                );

        driveStatus
                .getStyleClass()
                .add(
                        "drive-status"
                );

        drivesHeader
                .getChildren()
                .addAll(
                        drivesTitle,
                        drivesSpacer,
                        driveStatus
                );

        driveContainer
                .getStyleClass()
                .add(
                        "drive-list"
                );

        drivesPanel
                .getChildren()
                .addAll(
                        drivesHeader,
                        driveContainer
                );

        HBox.setHgrow(
                drivesPanel,
                Priority.ALWAYS
        );

        /*
         * =====================================================
         * RIGHT PANEL — SELECTED SOURCE
         * =====================================================
         */

        VBox selectionPanel =
                new VBox(14);

        selectionPanel
                .getStyleClass()
                .add(
                        "drive-panel"
                );

        selectionPanel.setPrefWidth(
                330
        );

        Label selectionTitle =
                new Label(
                        "SELECTED SOURCE"
                );

        selectionTitle
                .getStyleClass()
                .add(
                        "drive-panel-title"
                );

        Label sourceIcon =
                new Label(
                        "◉"
                );

        sourceIcon
                .getStyleClass()
                .add(
                        "selected-drive-icon"
                );

        selectedName
                .getStyleClass()
                .add(
                        "selected-drive-name"
                );

        selectedPath
                .getStyleClass()
                .add(
                        "selected-drive-path"
                );

        selectedPath.setWrapText(
                true
        );

        selectedSize
                .getStyleClass()
                .add(
                        "selected-drive-size"
                );

        /*
         * Divider.
         */
        javafx.scene.layout.Region divider =
                new javafx.scene.layout.Region();

        divider
                .getStyleClass()
                .add(
                        "drive-divider"
                );

        Label imageText =
                new Label(
                        "Or analyse an existing disk image"
                );

        imageText
                .getStyleClass()
                .add(
                        "drive-help-text"
                );

        Button imageButton =
                new Button(
                        "＋  OPEN DISK IMAGE"
                );

        imageButton
                .getStyleClass()
                .add(
                        "image-button"
                );

        imageButton.setFocusTraversable(
                false
        );

        /*
         * Continue button.
         */
        scanButton
                .getStyleClass()
                .add(
                        "scan-button"
                );

        scanButton.setMaxWidth(
                Double.MAX_VALUE
        );

        scanButton.setDisable(
                true
        );

        scanButton.setFocusTraversable(
                false
        );

        selectionPanel
                .getChildren()
                .addAll(
                        selectionTitle,
                        sourceIcon,
                        selectedName,
                        selectedPath,
                        selectedSize,
                        divider,
                        imageText,
                        imageButton,
                        scanButton
                );

        sourceArea
                .getChildren()
                .addAll(
                        drivesPanel,
                        selectionPanel
                );

        /*
         * =====================================================
         * WARNING
         * =====================================================
         */

        Label warning =
                new Label(

                        "MINION URE will analyse the selected source in read-only mode. "
                                +
                                "Recovered files should be written to another drive."
                );

        warning
                .getStyleClass()
                .add(
                        "drive-warning"
                );

        warning.setWrapText(
                true
        );

        warning.setMaxWidth(
                900
        );

        /*
         * =====================================================
         * ADD EVERYTHING
         * =====================================================
         */

        getChildren()
                .addAll(
                        header,
                        title,
                        subtitle,
                        sourceArea,
                        warning
                );

        /*
         * =====================================================
         * EVENTS
         * =====================================================
         */

        backButton.setOnAction(
                event ->
                        onBack.run()
        );

        refreshButton.setOnAction(
                event ->
                        loadDrives()
        );

        imageButton.setOnAction(
                event ->
                        chooseDiskImage()
        );

        scanButton.setOnAction(
                event -> {

                    if (
                            selectedSource
                                    != null
                    ) {

                        onScan.accept(
                                selectedSource
                        );
                    }
                }
        );
    }

    /*
     * =========================================================
     * LOAD DRIVES
     * =========================================================
     */

    private void loadDrives() {

        driveContainer
                .getChildren()
                .clear();

        File[] roots =
                File.listRoots();

        if (
                roots == null
                        || roots.length == 0
        ) {

            Label empty =
                    new Label(
                            "No mounted volumes detected."
                    );

            empty
                    .getStyleClass()
                    .add(
                            "drive-help-text"
                    );

            driveContainer
                    .getChildren()
                    .add(
                            empty
                    );

            return;
        }

        for (
                File root :
                roots
        ) {

            driveContainer
                    .getChildren()
                    .add(
                            createDriveCard(
                                    root
                            )
                    );
        }
    }

    /*
     * =========================================================
     * DRIVE CARD
     * =========================================================
     */

    private HBox createDriveCard(
            File drive
    ) {

        HBox card =
                new HBox(15);

        card
                .getStyleClass()
                .add(
                        "drive-card"
                );

        card.setAlignment(
                Pos.CENTER_LEFT
        );

        /*
         * Drive icon.
         */
        Label icon =
                new Label(
                        "▣"
                );

        icon
                .getStyleClass()
                .add(
                        "drive-card-icon"
                );

        /*
         * Drive information.
         */
        VBox information =
                new VBox(3);

        Label name =
                new Label(
                        getDriveName(
                                drive
                        )
                );

        name
                .getStyleClass()
                .add(
                        "drive-card-name"
                );

        Label path =
                new Label(
                        drive
                                .getAbsolutePath()
                );

        path
                .getStyleClass()
                .add(
                        "drive-card-path"
                );

        long total =
                drive.getTotalSpace();

        long free =
                drive.getFreeSpace();

        Label storage =
                new Label(

                        formatBytes(total)
                                +
                                " total   •   "
                                +
                                formatBytes(free)
                                +
                                " free"
                );

        storage
                .getStyleClass()
                .add(
                        "drive-card-storage"
                );

        information
                .getChildren()
                .addAll(
                        name,
                        path,
                        storage
                );

        /*
         * Spacer.
         */
        javafx.scene.layout.Region spacer =
                new javafx.scene.layout.Region();

        HBox.setHgrow(
                spacer,
                Priority.ALWAYS
        );

        Label select =
                new Label(
                        "SELECT  ›"
                );

        select
                .getStyleClass()
                .add(
                        "drive-select-label"
                );

        card
                .getChildren()
                .addAll(
                        icon,
                        information,
                        spacer,
                        select
                );

        /*
         * Select source.
         */
        card.setOnMouseClicked(
                event ->
                        selectSource(
                                drive
                        )
        );

        return card;
    }

    /*
     * =========================================================
     * SELECT SOURCE
     * =========================================================
     */

    private void selectSource(
            File source
    ) {

        selectedSource =
                source;

        selectedName.setText(
                getDriveName(
                        source
                )
        );

        selectedPath.setText(
                source
                        .getAbsolutePath()
        );

        long size;

        if (source.isDirectory()) {

            size =
                    source.getTotalSpace();

        } else {

            size =
                    source.length();
        }

        selectedSize.setText(
                "SIZE   "
                        +
                        formatBytes(size)
        );

        /*
         * Enable scan button.
         */
        scanButton.setDisable(
                false
        );
    }

    /*
     * =========================================================
     * DISK IMAGE PICKER
     * =========================================================
     */

    private void chooseDiskImage() {

        FileChooser chooser =
                new FileChooser();

        chooser.setTitle(
                "Select Disk Image"
        );

        chooser
                .getExtensionFilters()
                .addAll(

                        new FileChooser.ExtensionFilter(
                                "Disk Images",
                                "*.img",
                                "*.raw",
                                "*.dd",
                                "*.bin"
                        ),

                        new FileChooser.ExtensionFilter(
                                "All Files",
                                "*.*"
                        )
                );

        File file =
                chooser.showOpenDialog(
                        stage
                );

        if (file != null) {

            selectSource(
                    file
            );
        }
    }

    /*
     * =========================================================
     * DRIVE NAME
     * =========================================================
     */

    private String getDriveName(
            File drive
    ) {

        if (drive.isFile()) {

            return drive.getName();
        }

        String path =
                drive.getAbsolutePath();

        if (
                path.equals("/")
        ) {

            return "System Volume";
        }

        return "Volume "
                + path;
    }

    /*
     * =========================================================
     * BYTE FORMATTER
     * =========================================================
     */

    private String formatBytes(
            long bytes
    ) {

        if (bytes <= 0) {

            return "Unknown";
        }

        double value =
                bytes;

        String[] units = {
                "B",
                "KB",
                "MB",
                "GB",
                "TB"
        };

        int unit = 0;

        while (
                value >= 1024
                        &&
                        unit
                                <
                                units.length - 1
        ) {

            value /= 1024;

            unit++;
        }

        return String.format(
                "%.1f %s",
                value,
                units[unit]
        );
    }
}