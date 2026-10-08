package gui.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates structured text and CSV recovery session reports for MINION URE.
 */
public class RecoveryReport {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String applicationName;
    private final String sourceName;
    private final String sourcePath;
    private final String recoveryMode;
    private final String timestamp;
    private final int totalDiscovered;
    private final int totalSelected;
    private final int totalRecovered;
    private final int totalFailed;
    private final long totalBytes;
    private final List<RecoveryReportItem> items;

    public RecoveryReport(
            String sourceName,
            String sourcePath,
            String recoveryMode,
            int totalDiscovered,
            int totalSelected,
            int totalRecovered,
            int totalFailed,
            long totalBytes,
            List<RecoveryReportItem> items
    ) {
        this.applicationName = "MINION URE — UNIVERSAL RECOVERY ENGINE";
        this.sourceName = sourceName != null ? sourceName : "UNKNOWN_SOURCE";
        this.sourcePath = sourcePath != null ? sourcePath : "";
        this.recoveryMode = recoveryMode != null ? recoveryMode : "RECOVERY SESSION";
        this.timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        this.totalDiscovered = totalDiscovered;
        this.totalSelected = totalSelected;
        this.totalRecovered = totalRecovered;
        this.totalFailed = totalFailed;
        this.totalBytes = Math.max(0, totalBytes);
        this.items = items != null ? new ArrayList<>(items) : Collections.emptyList();
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getRecoveryMode() {
        return recoveryMode;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public int getTotalDiscovered() {
        return totalDiscovered;
    }

    public int getTotalSelected() {
        return totalSelected;
    }

    public int getTotalRecovered() {
        return totalRecovered;
    }

    public int getTotalFailed() {
        return totalFailed;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public List<RecoveryReportItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Renders a human-readable plaintext recovery report.
     */
    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\n");
        sb.append("                          MINION URE RECOVERY REPORT                            \n");
        sb.append("================================================================================\n\n");

        sb.append("SESSION METADATA:\n");
        sb.append(String.format("  Application:    %s\n", applicationName));
        sb.append(String.format("  Timestamp:      %s\n", timestamp));
        sb.append(String.format("  Recovery Mode:  %s\n", recoveryMode));
        sb.append(String.format("  Source Name:    %s\n", sourceName));
        sb.append(String.format("  Source Path:    %s\n", sourcePath));
        sb.append("\n");

        sb.append("RECOVERY SUMMARY:\n");
        sb.append(String.format("  Files Discovered: %d\n", totalDiscovered));
        sb.append(String.format("  Files Selected:   %d\n", totalSelected));
        sb.append(String.format("  Files Recovered:  %d\n", totalRecovered));
        sb.append(String.format("  Files Failed:     %d\n", totalFailed));
        sb.append(String.format("  Total Volume:     %s (%d bytes)\n", formatBytes(totalBytes), totalBytes));
        sb.append("\n");

        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(String.format("%-4s  %-28s  %-8s  %-16s  %-12s  %-10s  %-14s  %-10s\n",
                "#", "FILE NAME", "FORMAT", "LOCATION/OFFSET", "SIZE", "BOUNDARY", "CONFIDENCE", "STATUS"));
        sb.append("--------------------------------------------------------------------------------\n");

        if (items.isEmpty()) {
            sb.append("  (No files detected in this session)\n");
        } else {
            for (int i = 0; i < items.size(); i++) {
                RecoveryReportItem item = items.get(i);
                sb.append(String.format("%-4d  %-28s  %-8s  %-16s  %-12s  %-10s  %-14s  %-10s\n",
                        i + 1,
                        truncate(item.getName(), 28),
                        truncate(item.getFormat(), 8),
                        truncate(item.getOffsetLabel(), 16),
                        truncate(formatBytes(item.getSize()), 12),
                        truncate(item.getBoundaryStatus(), 10),
                        truncate(item.getConfidence(), 14),
                        truncate(item.getRecoveryStatus(), 10)
                ));
            }
        }

        sb.append("--------------------------------------------------------------------------------\n");
        sb.append("                       END OF MINION URE RECOVERY REPORT                        \n");
        sb.append("================================================================================\n");

        return sb.toString();
    }

    /**
     * Renders an RFC-4180 compliant CSV recovery report.
     */
    public String toCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Index,File Name,Format,Location,Size Bytes,Size Formatted,Boundary Status,Confidence,Recovery Status,Source,Mode,Timestamp\n");

        for (int i = 0; i < items.size(); i++) {
            RecoveryReportItem item = items.get(i);
            sb.append(i + 1).append(",");
            sb.append(escapeCsv(item.getName())).append(",");
            sb.append(escapeCsv(item.getFormat())).append(",");
            sb.append(escapeCsv(item.getOffsetLabel())).append(",");
            sb.append(item.getSize()).append(",");
            sb.append(escapeCsv(formatBytes(item.getSize()))).append(",");
            sb.append(escapeCsv(item.getBoundaryStatus())).append(",");
            sb.append(escapeCsv(item.getConfidence())).append(",");
            sb.append(escapeCsv(item.getRecoveryStatus())).append(",");
            sb.append(escapeCsv(sourceName)).append(",");
            sb.append(escapeCsv(recoveryMode)).append(",");
            sb.append(escapeCsv(timestamp)).append("\n");
        }

        return sb.toString();
    }

    /**
     * Exports the report to the specified destination path on disk.
     * Selects CSV or plaintext format based on file extension.
     */
    public Path export(Path destinationPath) throws IOException {
        if (destinationPath == null) {
            throw new IllegalArgumentException("Destination path cannot be null");
        }

        Path parent = destinationPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String fileName = destinationPath.getFileName().toString().toLowerCase();
        String content = fileName.endsWith(".csv") ? toCsv() : toPlainText();

        Files.writeString(destinationPath, content, StandardCharsets.UTF_8);
        return destinationPath;
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 1) + "…";
    }
}
