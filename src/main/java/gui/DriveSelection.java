package gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;

public class DriveSelection {

    private final Stage dialog;
    private File selectedFolder;

    // Colors
    private final String BACKGROUND = "#0F172A";
    private final String CARD = "#1E293B";
    private final String BORDER = "#334155";
    private final String TEXT = "#F8FAFC";

    public DriveSelection(Stage owner) {

        dialog = new Stage();

        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Select Drive");

        VBox root = new VBox(25);
        root.setPadding(new Insets(35));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle(
                "-fx-background-color: " + BACKGROUND + ";"
        );

        // -----------------------------------------
        // TITLE
        // -----------------------------------------

        Label title = new Label("Select Drive");

        title.setFont(Font.font("Arial", 36));
        title.setTextFill(Color.WHITE);

        // -----------------------------------------
        // DESCRIPTION
        // -----------------------------------------

        Label description = new Label(
                "Choose the drive or folder where you want to search for deleted files."
        );

        description.setFont(Font.font("Arial", 18));
        description.setTextFill(Color.LIGHTGRAY);
        description.setWrapText(true);

        // -----------------------------------------
        // CARD
        // -----------------------------------------

        VBox card = new VBox(20);

        card.setPadding(new Insets(30));
        card.setAlignment(Pos.CENTER);

        card.setMaxWidth(700);

        card.setStyle(
                "-fx-background-color: " + CARD + ";" +
                        "-fx-border-color: " + BORDER + ";" +
                        "-fx-border-width: 2;" +
                        "-fx-background-radius: 20;" +
                        "-fx-border-radius: 20;"
        );

        // -----------------------------------------
        // SELECTED FOLDER LABEL
        // -----------------------------------------

        Label folderLabel = new Label("No folder selected");

        folderLabel.setFont(Font.font("Arial", 20));
        folderLabel.setTextFill(Color.WHITE);

        folderLabel.setWrapText(true);

        // -----------------------------------------
        // BROWSE BUTTON
        // -----------------------------------------

        Button browseButton = new Button("Browse Folder");

        browseButton.setPrefWidth(240);
        browseButton.setPrefHeight(55);

        browseButton.setFont(Font.font("Arial", 16));

        // -----------------------------------------
        // START BUTTON
        // -----------------------------------------

        Button startButton = new Button("Start Recovery");

        startButton.setPrefWidth(240);
        startButton.setPrefHeight(55);

        startButton.setFont(Font.font("Arial", 16));

        // -----------------------------------------
        // CANCEL BUTTON
        // -----------------------------------------

        Button cancelButton = new Button("Cancel");

        cancelButton.setPrefWidth(240);
        cancelButton.setPrefHeight(55);

        cancelButton.setFont(Font.font("Arial", 16));

        // -----------------------------------------
        // BROWSE ACTION
        // -----------------------------------------

        browseButton.setOnAction(event -> {

            DirectoryChooser chooser = new DirectoryChooser();

            chooser.setTitle("Select Drive or Folder");

            File folder = chooser.showDialog(dialog);

            if (folder != null) {

                selectedFolder = folder;

                folderLabel.setText(
                        "Selected:\n" + folder.getAbsolutePath()
                );
            }
        });

        // -----------------------------------------
        // START ACTION
        // -----------------------------------------

        startButton.setOnAction(event -> {

            if (selectedFolder == null) {

                showMessage(
                        "No Folder Selected",
                        "Please select a drive or folder before starting recovery."
                );

                return;
            }

            dialog.close();
        });

        // -----------------------------------------
        // CANCEL ACTION
        // -----------------------------------------

        cancelButton.setOnAction(event -> {

            selectedFolder = null;

            dialog.close();
        });

        // -----------------------------------------
        // ADD CONTROLS
        // -----------------------------------------

        card.getChildren().addAll(
                folderLabel,
                browseButton,
                startButton,
                cancelButton
        );

        root.getChildren().addAll(
                title,
                description,
                card
        );

        Scene scene = new Scene(root, 850, 650);

        dialog.setScene(scene);
    }

    // -----------------------------------------
    // SHOW WINDOW
    // -----------------------------------------

    public File showAndWait() {

        dialog.showAndWait();

        return selectedFolder;
    }

    // -----------------------------------------
    // MESSAGE BOX
    // -----------------------------------------

    private void showMessage(String title, String message) {

        Alert alert = new Alert(Alert.AlertType.INFORMATION);

        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.showAndWait();
    }
}