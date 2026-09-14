package gui;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class Main extends Application {

    private double mouseX;
    private double mouseY;

    private double cursorX;
    private double cursorY;

    private double cursorRadius = 8;
    private double targetRadius = 8;

    private double windowDragX;
    private double windowDragY;

    @Override
    public void start(Stage stage) {

        stage.initStyle(StageStyle.UNDECORATED);

        StackPane root = new StackPane();
        root.getStyleClass().add("root");

        BorderPane layout = new BorderPane();
        layout.getStyleClass().add("layout");

        /* =========================================================
           CUSTOM TITLE BAR
        ========================================================= */

        HBox topBar = new HBox(14);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label logo = new Label("M");
        logo.getStyleClass().add("mini-logo");

        VBox brandBox = new VBox(1);

        Label brand = new Label("MINION URE");
        brand.getStyleClass().add("brand");

        Label brandSubtitle =
                new Label("Universal Recovery Engine");

        brandSubtitle.getStyleClass().add(
                "brand-subtitle"
        );

        brandBox.getChildren().addAll(
                brand,
                brandSubtitle
        );

        Region topSpacer = new Region();
        HBox.setHgrow(
                topSpacer,
                Priority.ALWAYS
        );

        HBox navigation = new HBox(18);
        navigation.setAlignment(Pos.CENTER);

        Label recoverNav =
                createNavLabel("RECOVER");

        Label analyseNav =
                createNavLabel("ANALYSE");

        Label predictNav =
                createNavLabel("PREDICT");

        navigation.getChildren().addAll(
                recoverNav,
                createArrow(),
                analyseNav,
                createArrow(),
                predictNav
        );

        HBox windowControls = new HBox(8);
        windowControls.setAlignment(
                Pos.CENTER_RIGHT
        );

        Button minimizeButton =
                new Button("—");

        minimizeButton.getStyleClass()
                .addAll(
                        "window-button",
                        "minimize-button"
                );

        Button maximizeButton =
                new Button("□");

        maximizeButton.getStyleClass()
                .addAll(
                        "window-button",
                        "maximize-button"
                );

        Button closeButton =
                new Button("×");

        closeButton.getStyleClass()
                .addAll(
                        "window-button",
                        "close-button"
                );

        minimizeButton.setTooltip(
                new Tooltip("Minimize")
        );

        maximizeButton.setTooltip(
                new Tooltip("Maximize")
        );

        closeButton.setTooltip(
                new Tooltip("Close")
        );

        minimizeButton.setOnAction(
                event ->
                        stage.setIconified(true)
        );

        maximizeButton.setOnAction(
                event ->
                        stage.setMaximized(
                                !stage.isMaximized()
                        )
        );

        closeButton.setOnAction(
                event ->
                        stage.close()
        );

        windowControls.getChildren().addAll(
                minimizeButton,
                maximizeButton,
                closeButton
        );

        topBar.getChildren().addAll(
                logo,
                brandBox,
                topSpacer,
                navigation,
                windowControls
        );

        topBar.setOnMousePressed(event -> {

            if (!stage.isMaximized()) {

                windowDragX =
                        event.getSceneX();

                windowDragY =
                        event.getSceneY();
            }
        });

        topBar.setOnMouseDragged(event -> {

            if (!stage.isMaximized()) {

                stage.setX(
                        event.getScreenX()
                                - windowDragX
                );

                stage.setY(
                        event.getScreenY()
                                - windowDragY
                );
            }
        });

        topBar.setOnMouseClicked(event -> {

            if (event.getClickCount() == 2) {

                stage.setMaximized(
                        !stage.isMaximized()
                );
            }
        });

        /* =========================================================
           CENTER HERO
        ========================================================= */

        VBox hero = new VBox(18);
        hero.setAlignment(Pos.CENTER);
        hero.getStyleClass().add("hero");

        HBox versionRow = new HBox(0);
        versionRow.setAlignment(Pos.CENTER);

        Label version =
                new Label("v1.0.0");

        version.getStyleClass()
                .add("version");

        Label beta =
                new Label("BETA");

        beta.getStyleClass()
                .add("beta");

        versionRow.getChildren().addAll(
                version,
                beta
        );

        Label heroLogo =
                new Label("M");

        heroLogo.getStyleClass()
                .add("hero-logo");

        Label title =
                new Label("MINION");

        title.getStyleClass()
                .add("title");

        Label subtitle =
                new Label(
                        "UNIVERSAL RECOVERY ENGINE"
                );

        subtitle.getStyleClass()
                .add("subtitle");

        Region divider =
                new Region();

        divider.getStyleClass()
                .add("divider");

        Label tagline =
                new Label(
                        "RECOVER WHAT WAS THOUGHT LOST."
                );

        tagline.getStyleClass()
                .add("tagline");

        /* =========================================================
           FEATURE CARDS
        ========================================================= */

        HBox cards = new HBox(18);
        cards.setAlignment(Pos.CENTER);

        VBox fatCard =
                createCard(
                        "◉",
                        "FAT32",
                        "Scan & Recover"
                );

        VBox ntfsCard =
                createCard(
                        "▦",
                        "NTFS",
                        "Deep Analysis"
                );

        VBox predictionCard =
                createCard(
                        "✦",
                        "AI Prediction",
                        "Recovery Intelligence"
                );

        cards.getChildren().addAll(
                fatCard,
                ntfsCard,
                predictionCard
        );

        /* =========================================================
           START BUTTON
        ========================================================= */

        Button startButton =
                new Button(
                        "➜   BEGIN RECOVERY"
                );

        startButton
                .getStyleClass()
                .add("start-button");

        hero.getChildren().addAll(
                versionRow,
                heroLogo,
                title,
                subtitle,
                divider,
                tagline,
                cards,
                startButton
        );

        /* =========================================================
           BOTTOM BAR
        ========================================================= */

        HBox bottomBar = new HBox();
        bottomBar.getStyleClass()
                .add("bottom-bar");

        bottomBar.setAlignment(
                Pos.CENTER_LEFT
        );

        Label pulse =
                new Label("⌁");

        pulse.getStyleClass()
                .add("pulse");

        Label footerLeft =
                new Label(
                        "FAST   •   SECURE   •   CROSS-PLATFORM"
                );

        footerLeft.getStyleClass()
                .add("footer-text");

        Region bottomSpacer =
                new Region();

        HBox.setHgrow(
                bottomSpacer,
                Priority.ALWAYS
        );

        Label footerRight =
                new Label(
                        "MINION URE   |   DATA LIVES LONGER."
                );

        footerRight.getStyleClass()
                .add("footer-text");

        bottomBar.getChildren().addAll(
                pulse,
                footerLeft,
                bottomSpacer,
                footerRight
        );

        layout.setTop(topBar);
        layout.setCenter(hero);
        layout.setBottom(bottomBar);

        /* =========================================================
           CURSOR
        ========================================================= */

        Circle cursorRing =
                new Circle(8);

        cursorRing.setFill(
                Color.TRANSPARENT
        );

        cursorRing.setStroke(
                Color.web("#E95420")
        );

        cursorRing.setStrokeWidth(
                1.4
        );

        cursorRing.setMouseTransparent(
                true
        );

        root.getChildren().addAll(
                layout,
                cursorRing
        );

        Scene scene =
                new Scene(
                        root,
                        1100,
                        700
                );

        scene.getStylesheets().add(
                getClass()
                        .getResource(
                                "/css/main.css"
                        )
                        .toExternalForm()
        );

        scene.setCursor(
                Cursor.DEFAULT
        );

        cards.maxWidthProperty().bind(
                scene.widthProperty()
                        .multiply(0.78)
        );

        hero.maxWidthProperty().bind(
                scene.widthProperty()
                        .multiply(0.90)
        );

        /* =========================================================
           MOUSE TRACKING
        ========================================================= */

        scene.addEventFilter(
                MouseEvent.MOUSE_MOVED,
                event -> {

                    mouseX =
                            event.getSceneX();

                    mouseY =
                            event.getSceneY();
                }
        );

        scene.addEventFilter(
                MouseEvent.MOUSE_DRAGGED,
                event -> {

                    mouseX =
                            event.getSceneX();

                    mouseY =
                            event.getSceneY();
                }
        );

        AnimationTimer cursorAnimation =
                new AnimationTimer() {

                    @Override
                    public void handle(long now) {

                        double followSpeed =
                                0.30;

                        cursorX +=
                                (mouseX - cursorX)
                                        * followSpeed;

                        cursorY +=
                                (mouseY - cursorY)
                                        * followSpeed;

                        cursorRadius +=
                                (
                                        targetRadius
                                                - cursorRadius
                                )
                                        * 0.20;

                        cursorRing.setRadius(
                                cursorRadius
                        );

                        cursorRing.setTranslateX(
                                cursorX
                                        - scene.getWidth()
                                        / 2
                        );

                        cursorRing.setTranslateY(
                                cursorY
                                        - scene.getHeight()
                                        / 2
                        );
                    }
                };

        cursorAnimation.start();

        addCursorEffect(
                startButton,
                22
        );

        addCursorEffect(
                fatCard,
                14
        );

        addCursorEffect(
                ntfsCard,
                14
        );

        addCursorEffect(
                predictionCard,
                14
        );

        addCursorEffect(
                recoverNav,
                12
        );

        addCursorEffect(
                analyseNav,
                12
        );

        addCursorEffect(
                predictNav,
                12
        );

        addCursorEffect(
                minimizeButton,
                12
        );

        addCursorEffect(
                maximizeButton,
                12
        );

        addCursorEffect(
                closeButton,
                12
        );

        /* =========================================================
           STARTUP ANIMATION
        ========================================================= */

        hero.setOpacity(0);

        FadeTransition fade =
                new FadeTransition(
                        Duration.millis(300),
                        hero
                );

        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();

        /* =========================================================
           WINDOW
        ========================================================= */

        stage.setTitle("MINION URE");
        stage.setScene(scene);

        stage.setMinWidth(850);
        stage.setMinHeight(600);

        stage.show();

        mouseX =
                scene.getWidth() / 2;

        mouseY =
                scene.getHeight() / 2;

        cursorX = mouseX;
        cursorY = mouseY;
    }

    private VBox createCard(
            String icon,
            String title,
            String subtitle
    ) {

        Label iconLabel =
                new Label(icon);

        iconLabel.getStyleClass()
                .add("card-icon");

        Label titleLabel =
                new Label(title);

        titleLabel.getStyleClass()
                .add("card-title");

        Label subtitleLabel =
                new Label(subtitle);

        subtitleLabel.getStyleClass()
                .add("card-subtitle");

        VBox text =
                new VBox(
                        4,
                        titleLabel,
                        subtitleLabel
                );

        HBox row =
                new HBox(
                        16,
                        iconLabel,
                        text
                );

        row.setAlignment(
                Pos.CENTER_LEFT
        );

        VBox card =
                new VBox(row);

        card.getStyleClass()
                .add("feature-card");

        card.setAlignment(
                Pos.CENTER_LEFT
        );

        HBox.setHgrow(
                card,
                Priority.ALWAYS
        );

        return card;
    }

    private Label createNavLabel(
            String text
    ) {

        Label label =
                new Label(text);

        label.getStyleClass()
                .add("nav-label");

        return label;
    }

    private Label createArrow() {

        Label arrow =
                new Label("›");

        arrow.getStyleClass()
                .add("nav-arrow");

        return arrow;
    }

    private void addCursorEffect(
            javafx.scene.Node node,
            double radius
    ) {

        node.setOnMouseEntered(
                event ->
                        targetRadius =
                                radius
        );

        node.setOnMouseExited(
                event ->
                        targetRadius =
                                8
        );
    }

    public static void main(
            String[] args
    ) {

        launch(args);
    }
}
