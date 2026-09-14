package gui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class ResultsView extends VBox {

    private final ScanView.ScanSummary summary;
    private final Runnable onBack;
    private final Runnable onRecovery;

    public ResultsView(
            ScanView.ScanSummary summary,
            Runnable onBack,
            Runnable onRecovery
    ) {

        this.summary = summary;
        this.onBack = onBack;
        this.onRecovery = onRecovery;

        buildView();
    }

    private void buildView() {

        getStyleClass().add("results-page");

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


        Label completed =
                new Label("●  SCAN COMPLETE");

        completed
                .getStyleClass()
                .add("results-complete");


        header
                .getChildren()
                .addAll(
                        backButton,
                        spacer,
                        completed
                );


        /* =====================================================
           TITLE
        ===================================================== */

        Label title =
                new Label("SCAN RESULTS");

        title
                .getStyleClass()
                .add("results-title");


        Label subtitle =
                new Label(
                        "MINION URE successfully analysed the selected recovery source."
                );

        subtitle
                .getStyleClass()
                .add("results-subtitle");


        /* =====================================================
           SOURCE
        ===================================================== */

        VBox sourcePanel =
                new VBox(5);

        sourcePanel
                .getStyleClass()
                .add("results-source-panel");

        sourcePanel.setMaxWidth(900);


        Label sourceLabel =
                new Label("ANALYSED SOURCE");

        sourceLabel
                .getStyleClass()
                .add("results-small-label");


        Label sourceName =
                new Label(
                        summary
                                .getSource()
                                .getName()
                );

        sourceName
                .getStyleClass()
                .add("results-source-name");


        Label sourcePath =
                new Label(
                        summary
                                .getSource()
                                .getAbsolutePath()
                );

        sourcePath
                .getStyleClass()
                .add("results-source-path");

        sourcePath.setWrapText(true);


        sourcePanel
                .getChildren()
                .addAll(
                        sourceLabel,
                        sourceName,
                        sourcePath
                );


        /* =====================================================
           MAIN STATISTICS
        ===================================================== */

        HBox stats =
                new HBox(12);

        stats.setMaxWidth(900);


        VBox totalCard =
                createStatCard(
                        "TOTAL SECTORS",
                        format(summary.getTotalSectors()),
                        "Entire source"
                );


        VBox scannedCard =
                createStatCard(
                        "SCANNED",
                        format(summary.getScannedSectors()),
                        "100% analysed"
                );


        VBox dataCard =
                createStatCard(
                        "DATA SECTORS",
                        format(summary.getDataSectors()),
                        percentage(
                                summary.getDataSectors(),
                                summary.getTotalSectors()
                        )
                );


        VBox emptyCard =
                createStatCard(
                        "EMPTY SECTORS",
                        format(summary.getEmptySectors()),
                        percentage(
                                summary.getEmptySectors(),
                                summary.getTotalSectors()
                        )
                );


        HBox.setHgrow(totalCard, Priority.ALWAYS);
        HBox.setHgrow(scannedCard, Priority.ALWAYS);
        HBox.setHgrow(dataCard, Priority.ALWAYS);
        HBox.setHgrow(emptyCard, Priority.ALWAYS);


        stats
                .getChildren()
                .addAll(
                        totalCard,
                        scannedCard,
                        dataCard,
                        emptyCard
                );


        /* =====================================================
           SUMMARY PANEL
        ===================================================== */

        VBox summaryPanel =
                new VBox(14);

        summaryPanel
                .getStyleClass()
                .add("results-summary-panel");

        summaryPanel.setMaxWidth(900);


        Label summaryTitle =
                new Label("ANALYSIS SUMMARY");

        summaryTitle
                .getStyleClass()
                .add("results-section-title");


        HBox statusRow =
                createResultRow(
                        "SCAN STATUS",
                        summary.isCompleted()
                                ? "COMPLETED"
                                : "INCOMPLETE"
                );


        HBox sectorSizeRow =
                createResultRow(
                        "SECTOR SIZE",
                        "512 bytes"
                );


        HBox elapsedRow =
                createResultRow(
                        "SCAN TIME",
                        String.format(
                                "%.2f seconds",
                                summary.getElapsedMilliseconds()
                                        / 1000.0
                        )
                );


        HBox engineRow =
                createResultRow(
                        "ENGINE",
                        "URE SectorReader + DiskScanner"
                );


        summaryPanel
                .getChildren()
                .addAll(
                        summaryTitle,
                        statusRow,
                        sectorSizeRow,
                        elapsedRow,
                        engineRow
                );


        /* =====================================================
           RECOVERY STATUS
        ===================================================== */

        HBox recoveryPanel =
                new HBox(18);

        recoveryPanel.setAlignment(
                Pos.CENTER_LEFT
        );

        recoveryPanel
                .getStyleClass()
                .add("results-recovery-panel");

        recoveryPanel.setMaxWidth(900);


        VBox recoveryText =
                new VBox(5);


        Label recoveryTitle =
                new Label(
                        summary.getDataSectors() > 0
                                ? "DATA DETECTED"
                                : "NO DATA DETECTED"
                );

        recoveryTitle
                .getStyleClass()
                .add("results-recovery-title");


        Label recoveryDescription =
                new Label(
                        summary.getDataSectors() > 0
                                ?
                                "The scan detected sectors containing data. "
                                        +
                                        "Continue to recovery analysis to locate recoverable files."
                                :
                                "No populated sectors were detected in this source."
                );

        recoveryDescription
                .getStyleClass()
                .add("results-recovery-description");

        recoveryDescription.setWrapText(true);


        recoveryText
                .getChildren()
                .addAll(
                        recoveryTitle,
                        recoveryDescription
                );


        HBox.setHgrow(
                recoveryText,
                Priority.ALWAYS
        );


        Button recoveryButton =
                new Button(
                        "CONTINUE TO RECOVERY  ›"
                );

        recoveryButton
                .getStyleClass()
                .add("results-recovery-button");

        recoveryButton.setDisable(
                summary.getDataSectors() <= 0
        );


        recoveryPanel
                .getChildren()
                .addAll(
                        recoveryText,
                        recoveryButton
                );


        /* =====================================================
           FOOTER INFORMATION
        ===================================================== */

        Label information =
                new Label(
                        "The source remains unchanged. MINION URE performs recovery analysis in read-only mode."
                );

        information
                .getStyleClass()
                .add("results-information");

        information.setWrapText(true);
        information.setMaxWidth(900);


        /* =====================================================
           ADD EVERYTHING
        ===================================================== */

        getChildren()
                .addAll(
                        header,
                        title,
                        subtitle,
                        sourcePanel,
                        stats,
                        summaryPanel,
                        recoveryPanel,
                        information
                );


        /* =====================================================
           EVENTS
        ===================================================== */

        backButton.setOnAction(
                event -> onBack.run()
        );


        recoveryButton.setOnAction(
                event -> onRecovery.run()
        );
    }


    private VBox createStatCard(
            String title,
            String value,
            String detail
    ) {

        VBox card =
                new VBox(6);

        card
                .getStyleClass()
                .add("results-stat-card");

        card.setMaxWidth(
                Double.MAX_VALUE
        );


        Label titleLabel =
                new Label(title);

        titleLabel
                .getStyleClass()
                .add("results-stat-title");


        Label valueLabel =
                new Label(value);

        valueLabel
                .getStyleClass()
                .add("results-stat-value");


        Label detailLabel =
                new Label(detail);

        detailLabel
                .getStyleClass()
                .add("results-stat-detail");


        card
                .getChildren()
                .addAll(
                        titleLabel,
                        valueLabel,
                        detailLabel
                );


        return card;
    }


    private HBox createResultRow(
            String name,
            String value
    ) {

        HBox row =
                new HBox();

        row.setAlignment(
                Pos.CENTER_LEFT
        );


        Label nameLabel =
                new Label(name);

        nameLabel
                .getStyleClass()
                .add("results-row-name");


        Region spacer =
                new Region();

        HBox.setHgrow(
                spacer,
                Priority.ALWAYS
        );


        Label valueLabel =
                new Label(value);

        valueLabel
                .getStyleClass()
                .add("results-row-value");


        row
                .getChildren()
                .addAll(
                        nameLabel,
                        spacer,
                        valueLabel
                );


        return row;
    }


    private String format(long number) {

        return String.format(
                "%,d",
                number
        );
    }


    private String percentage(
            long value,
            long total
    ) {

        if (total <= 0) {
            return "0.00%";
        }


        double percent =
                value * 100.0
                        /
                total;


        return String.format(
                "%.2f%%",
                percent
        );
    }
}
