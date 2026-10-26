package gui;

import core.disk.PhysicalDriveDiscovery;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
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
        driveContainer.getChildren().clear();

        Label mountedHeading = new Label("MOUNTED VOLUMES");
        mountedHeading.getStyleClass().add("drive-help-text");
        driveContainer.getChildren().add(mountedHeading);

        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                driveContainer.getChildren().add(createDriveCard(root));
            }
        }

        Label physicalHeading = new Label("PHYSICAL DISKS");
        physicalHeading.getStyleClass().add("drive-help-text");
        driveContainer.getChildren().add(physicalHeading);

        try {
            java.util.List<PhysicalDriveDiscovery.PhysicalDrive> disks =
                    PhysicalDriveDiscovery.discover();

            if (disks.isEmpty()) {
                Label empty = new Label("No physical disks detected.");
                empty.getStyleClass().add("drive-help-text");
                driveContainer.getChildren().add(empty);
            } else {
                for (PhysicalDriveDiscovery.PhysicalDrive disk : disks) {
                    driveContainer.getChildren().add(
                            createPhysicalDriveCard(disk)
                    );
                }
            }
        } catch (IOException ex) {
            Label warning = new Label(
                    "Physical disk discovery failed. Device selection is disabled."
            );
            warning.getStyleClass().add("drive-help-text");
            warning.setWrapText(true);
            driveContainer.getChildren().add(warning);
        }
    }

    private HBox createPhysicalDriveCard(
            PhysicalDriveDiscovery.PhysicalDrive disk
    ) {
        HBox card = new HBox(15);
        card.getStyleClass().add("drive-card");
        card.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("▣");
        icon.getStyleClass().add("drive-card-icon");

        VBox information = new VBox(3);

        String displayName = disk.getModel().isBlank()
                ? disk.getName()
                : disk.getModel();

        Label name = new Label(displayName);
        name.getStyleClass().add("drive-card-name");

        Label path = new Label(disk.getPath());
        path.getStyleClass().add("drive-card-path");

        String transport = disk.getTransport().isBlank()
                ? "unknown transport"
                : disk.getTransport();

        Label storage = new Label(
                formatBytes(disk.getSizeBytes())
                        + " total  •  " + transport
                        + (disk.isRemovable() ? "  •  removable" : "")
        );
        storage.getStyleClass().add("drive-card-storage");

        information.getChildren().addAll(name, path, storage);

        javafx.scene.layout.Region spacer =
                new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String unavailableReason = getUnavailableReason(disk);
        boolean selectable = disk.isRemovable()
                && disk.isSelectable();

        Label status = new Label(
                selectable ? "SELECT DRIVE"
                        : (disk.isRemovable()
                                ? unavailableReason
                                : "NOT REMOVABLE")
        );
        status.getStyleClass().add("drive-select-label");

        card.getChildren().addAll(icon, information, spacer, status);
        card.setDisable(!selectable);

        if (selectable) {
            card.setOnMouseClicked(event ->
                    selectPhysicalDrive(disk, displayName));
        }

        return card;
    }

    private void selectPhysicalDrive(
            PhysicalDriveDiscovery.PhysicalDrive disk,
            String displayName
    ) {
        if (disk == null
                || !disk.isRemovable()
                || !disk.isSelectable()) {
            selectedSource = null;
            scanButton.setDisable(true);
            selectedName.setText("Drive unavailable");
            selectedPath.setText(disk == null ? "" : disk.getPath());
            return;
        }

        selectedSource = new File(disk.getPath());
        scanButton.setDisable(false);
        selectedName.setText(displayName);
        selectedPath.setText(disk.getPath());
        selectedSize.setText(
                "SIZE   " + formatBytes(disk.getSizeBytes())
        );
    }

    private void showMountedDriveWarning(
            PhysicalDriveDiscovery.PhysicalDrive disk,
            Runnable onContinue
    ) {
        if (getScene() == null || getScene().getWindow() == null) {
            return;
        }

        javafx.geometry.Bounds bounds = localToScreen(getBoundsInLocal());
        if (bounds == null || bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            return;
        }

        // Anchor the popup to this page, not Main's root.
        // This keeps the title bar and footer outside the warning overlay.
        Popup popup = new Popup();
        popup.setAutoFix(false);
        popup.setAutoHide(false);
        popup.setHideOnEscape(false);

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("mounted-warning-overlay");
        overlay.setPrefSize(bounds.getWidth(), bounds.getHeight());
        overlay.setMinSize(bounds.getWidth(), bounds.getHeight());
        overlay.setMaxSize(bounds.getWidth(), bounds.getHeight());
        overlay.setFocusTraversable(true);

        VBox dialog = new VBox(16);
        dialog.getStyleClass().add("mounted-warning-card");
        dialog.setMaxWidth(460);
        dialog.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(dialog, Pos.CENTER);

        HBox heading = new HBox(12);
        heading.setAlignment(Pos.CENTER_LEFT);

        Label warningIcon = new Label("!");
        warningIcon.getStyleClass().add("mounted-warning-icon");

        VBox headingText = new VBox(4);

        Label eyebrow = new Label("MINION URE  /  SAFETY CHECK");
        eyebrow.getStyleClass().add("mounted-warning-eyebrow");

        Label title = new Label("Mounted drive detected");
        title.getStyleClass().add("mounted-warning-title");
        title.setWrapText(true);

        headingText.getChildren().addAll(eyebrow, title);
        heading.getChildren().addAll(warningIcon, headingText);

        Label drivePath = new Label(disk.getPath());
        drivePath.getStyleClass().add("mounted-warning-path");
        drivePath.setMaxWidth(Double.MAX_VALUE);
        drivePath.setWrapText(true);

        Label message = new Label(
                "This drive has mounted filesystems. If its contents "
                + "change during scanning, recovery results may be "
                + "inconsistent. An unmounted source is recommended "
                + "for the best results."
        );
        message.getStyleClass().add("mounted-warning-message");
        message.setWrapText(true);

        Label safetyNote = new Label(
                "SOURCE PROTECTION\\n"
                + "MINION will not automatically unmount this drive. "
                + "Scanning uses read-only access, but the operating "
                + "system may still change mounted data."
        );
        safetyNote.getStyleClass().add("mounted-warning-note");
        safetyNote.setWrapText(true);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button cancel = new Button("CANCEL");
        cancel.getStyleClass().addAll(
                "drive-secondary-button",
                "mounted-warning-button"
        );
        cancel.setOnAction(event -> popup.hide());

        Button proceed = new Button("CONTINUE  ›");
        proceed.getStyleClass().addAll(
                "start-button",
                "mounted-warning-button"
        );
        proceed.setOnAction(event -> {
            popup.hide();
            onContinue.run();
        });

        actions.getChildren().addAll(cancel, proceed);
        dialog.getChildren().addAll(
                heading,
                drivePath,
                message,
                safetyNote,
                actions
        );

        overlay.getChildren().add(dialog);

        // Consume clicks on the overlay background to block the page below.
        overlay.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                event -> {
                    if (event.getTarget() == overlay) {
                        event.consume();
                    }
                }
        );

        overlay.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                popup.hide();
                event.consume();
            }
        });

        popup.getContent().add(overlay);
        popup.show(
                getScene().getWindow(),
                bounds.getMinX(),
                bounds.getMinY()
        );

        // Popup has its own Scene, so copy the page's stylesheets.
        if (popup.getScene() != null) {
            popup.getScene().getStylesheets().setAll(
                    getScene().getStylesheets()
            );
        }

        popup.setWidth(bounds.getWidth());
        popup.setHeight(bounds.getHeight());
        overlay.requestFocus();
    }

    private String getUnavailableReason(
            PhysicalDriveDiscovery.PhysicalDrive disk
    ) {
        if (disk.containsRootFilesystem()) {
            return "SYSTEM DISK";
        }
        if (disk.hasMountedFilesystem()) {
            return "MOUNTED";
        }
        if (disk.isReadOnly()) {
            return "READ-ONLY";
        }
        if (disk.getSizeBytes() <= 0) {
            return "UNKNOWN SIZE";
        }
        return "UNAVAILABLE";
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