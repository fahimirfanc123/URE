package gui;

import gui.DriveSelection;

import javafx.application.Application;
import javafx.application.Platform;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import javafx.scene.paint.Color;

import javafx.scene.text.Font;

import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main extends Application {

    // ============================================================
    // COLORS
    // ============================================================

    private final String BACKGROUND = "#0F172A";
    private final String CARD = "#1E293B";
    private final String BORDER = "#334155";
    private final String TEXT = "#F8FAFC";

    // ============================================================
    // UI COMPONENTS
    // ============================================================

    private ProgressBar progressBar;

    private Label statusLabel;

    private Label percentageLabel;

    private Button recoveryButton;

    private File selectedFolder;

    // ============================================================
    // MAIN WINDOW
    // ============================================================

    @Override
    public void start(Stage stage) {

        stage.setTitle("Recovery App");

        // ========================================================
        // ROOT
        // ========================================================

        VBox root = new VBox(25);

        root.setPadding(new Insets(40));

        root.setStyle(
                "-fx-background-color: " + BACKGROUND + ";"
        );

        // ========================================================
        // HEADER
        // ========================================================

        VBox header = new VBox(5);

        Label title = new Label("Recovery App");

        title.setFont(Font.font("Arial", 48));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label(
                "File Recovery & Management"
        );

        subtitle.setFont(Font.font("Arial", 24));
        subtitle.setTextFill(Color.LIGHTGRAY);

        header.getChildren().addAll(
                title,
                subtitle
        );

        // ========================================================
        // TOP CARDS
        // ========================================================

        HBox cards = new HBox(25);

        cards.setAlignment(Pos.CENTER);

        VBox recoveryCard = createCard(
                "↻  Recovery",
                "Recover deleted or lost files",
                "Start Recovery"
        );

        VBox progressCard = createCard(
                "▥  Progress",
                "Monitor recovery progress",
                "View Progress"
        );

        VBox filesCard = createCard(
                "□  Files",
                "Preview recovered files",
                "Open Files"
        );

        VBox settingsCard = createCard(
                "⚙  Settings",
                "Configure application settings",
                "Settings"
        );

        cards.getChildren().addAll(
                recoveryCard,
                progressCard,
                filesCard,
                settingsCard
        );

        // ========================================================
        // RECOVERY STATUS
        // ========================================================

        VBox statusBox = new VBox(15);

        statusBox.setAlignment(Pos.CENTER);

        Label statusTitle = new Label(
                "Recovery Status"
        );

        statusTitle.setFont(Font.font("Arial", 38));
        statusTitle.setTextFill(Color.WHITE);

        // ========================================================
        // PROGRESS BAR
        // ========================================================

        progressBar = new ProgressBar(0);

        progressBar.setPrefWidth(1100);
        progressBar.setPrefHeight(30);

        // ========================================================
        // STATUS LABEL
        // ========================================================

        statusLabel = new Label(
                "Ready to recover files"
        );

        statusLabel.setFont(Font.font("Arial", 24));
        statusLabel.setTextFill(Color.LIGHTGRAY);

        // ========================================================
        // PERCENTAGE
        // ========================================================

        percentageLabel = new Label("0%");

        percentageLabel.setFont(Font.font("Arial", 28));
        percentageLabel.setTextFill(Color.WHITE);

        // ========================================================
        // MAIN RECOVERY BUTTON
        // ========================================================

        recoveryButton = new Button(
                "Start Recovery"
        );

        recoveryButton.setPrefWidth(260);
        recoveryButton.setPrefHeight(65);

        recoveryButton.setFont(
                Font.font("Arial", 18)
        );

        recoveryButton.setOnAction(
                event -> openDriveSelection()
        );

        statusBox.getChildren().addAll(
                statusTitle,
                progressBar,
                statusLabel,
                percentageLabel,
                recoveryButton
        );

        // ========================================================
        // ADD EVERYTHING
        // ========================================================

        root.getChildren().addAll(
                header,
                cards,
                statusBox
        );

        // ========================================================
        // SCENE
        // ========================================================

        Scene scene = new Scene(
                root,
                1450,
                850
        );

        stage.setScene(scene);

        stage.setMinWidth(1000);
        stage.setMinHeight(700);

        stage.show();
    }

    // ============================================================
    // CREATE CARD
    // ============================================================

    private VBox createCard(
            String titleText,
            String descriptionText,
            String buttonText
    ) {

        VBox card = new VBox(15);

        card.setAlignment(Pos.CENTER);

        card.setPadding(
                new Insets(25)
        );

        card.setPrefWidth(300);
        card.setPrefHeight(250);

        card.setStyle(
                "-fx-background-color: " + CARD + ";" +
                        "-fx-border-color: " + BORDER + ";" +
                        "-fx-border-width: 2;" +
                        "-fx-background-radius: 25;" +
                        "-fx-border-radius: 25;"
        );

        // ========================================================
        // TITLE
        // ========================================================

        Label title = new Label(titleText);

        title.setFont(
                Font.font("Arial", 30)
        );

        title.setTextFill(
                Color.WHITE
        );

        // ========================================================
        // DESCRIPTION
        // ========================================================

        Label description = new Label(
                descriptionText
        );

        description.setFont(
                Font.font("Arial", 18)
        );

        description.setTextFill(
                Color.LIGHTGRAY
        );

        description.setWrapText(true);

        description.setAlignment(
                Pos.CENTER
        );

        // ========================================================
        // BUTTON
        // ========================================================

        Button button = new Button(
                buttonText
        );

        button.setPrefWidth(200);
        button.setPrefHeight(55);

        button.setFont(
                Font.font("Arial", 17)
        );

        // ========================================================
        // BUTTON ACTIONS
        // ========================================================

        if (buttonText.equals("Start Recovery")) {

            button.setOnAction(
                    event -> openDriveSelection()
            );
        }

        else if (buttonText.equals("View Progress")) {

            button.setOnAction(
                    event -> showProgress()
            );
        }

        else if (buttonText.equals("Open Files")) {

            button.setOnAction(
                    event -> openFiles()
            );
        }

        else if (buttonText.equals("Settings")) {

            button.setOnAction(
                    event -> showSettings()
            );
        }

        card.getChildren().addAll(
                title,
                description,
                button
        );

        return card;
    }

    // ============================================================
    // DRIVE SELECTION
    // ============================================================

    private void openDriveSelection() {

        Stage owner =
                (Stage) recoveryButton.getScene().getWindow();

        DriveSelection selection =
                new DriveSelection(owner);

        File folder =
                selection.showAndWait();

        if (folder != null) {

            selectedFolder = folder;

            startRecovery(folder);
        }
    }

    // ============================================================
    // START RECOVERY
    // ============================================================

    private void startRecovery(File folder) {

        recoveryButton.setDisable(true);

        progressBar.setProgress(0);

        percentageLabel.setText("0%");

        statusLabel.setText(
                "Scanning: " + folder.getAbsolutePath()
        );

        Thread recoveryThread = new Thread(() -> {

            List<File> files =
                    new ArrayList<>();

            scanFolder(folder, files);

            int total = files.size();

            if (total == 0) {

                Platform.runLater(() -> {

                    progressBar.setProgress(1);

                    percentageLabel.setText("100%");

                    statusLabel.setText(
                            "No files found in selected folder."
                    );

                    recoveryButton.setDisable(false);
                });

                return;
            }

            for (int i = 0; i < total; i++) {

                try {

                    Thread.sleep(30);

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                    return;
                }

                final int current = i + 1;

                final double progress =
                        (double) current / total;

                Platform.runLater(() -> {

                    progressBar.setProgress(
                            progress
                    );

                    int percent =
                            (int) (progress * 100);

                    percentageLabel.setText(
                            percent + "%"
                    );

                    statusLabel.setText(
                            "Scanning files... " +
                                    current +
                                    " / " +
                                    total
                    );
                });
            }

            Platform.runLater(() -> {

                progressBar.setProgress(1);

                percentageLabel.setText("100%");

                statusLabel.setText(
                        "Recovery scan completed. " +
                                total +
                                " files found."
                );

                recoveryButton.setDisable(false);

                showMessage(
                        "Recovery Complete",
                        "Scan completed successfully.\n\n" +
                                "Files found: " + total
                );
            });

        });

        recoveryThread.setDaemon(true);

        recoveryThread.start();
    }

    // ============================================================
    // SCAN FOLDER
    // ============================================================

    private void scanFolder(
            File folder,
            List<File> files
    ) {

        if (folder == null ||
                !folder.exists()) {

            return;
        }

        File[] list =
                folder.listFiles();

        if (list == null) {

            return;
        }

        for (File file : list) {

            if (file.isFile()) {

                files.add(file);

            } else if (file.isDirectory()) {

                scanFolder(
                        file,
                        files
                );
            }
        }
    }

    // ============================================================
    // VIEW PROGRESS
    // ============================================================

    private void showProgress() {

        String message;

        if (selectedFolder == null) {

            message =
                    "No recovery has been started yet.";

        } else {

            message =
                    "Current recovery folder:\n\n" +
                            selectedFolder.getAbsolutePath();
        }

        showMessage(
                "Recovery Progress",
                message
        );
    }

    // ============================================================
    // OPEN FILES
    // ============================================================

    private void openFiles() {

        if (selectedFolder == null) {

            showMessage(
                    "Files",
                    "No recovery folder has been selected yet."
            );

            return;
        }

        try {

            java.awt.Desktop.getDesktop()
                    .open(selectedFolder);

        } catch (Exception e) {

            showMessage(
                    "Error",
                    "Unable to open the folder."
            );
        }
    }

    // ============================================================
    // SETTINGS
    // ============================================================

    private void showSettings() {

        showMessage(
                "Settings",
                "Recovery App Settings\n\n" +
                        "• Scan folders\n" +
                        "• File recovery\n" +
                        "• Progress monitoring\n" +
                        "• Recovered file management"
        );
    }

    // ============================================================
    // MESSAGE
    // ============================================================

    private void showMessage(
            String title,
            String message
    ) {

        Alert alert =
                new Alert(
                        Alert.AlertType.INFORMATION
                );

        alert.setTitle(title);

        alert.setHeaderText(null);

        alert.setContentText(message);

        alert.showAndWait();
    }

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(
            String[] args
    ) {

        launch(args);
    }
}