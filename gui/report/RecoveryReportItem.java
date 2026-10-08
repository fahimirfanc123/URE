package gui.report;

/**
 * Immutable data transfer object representing a single discovered file in a recovery report.
 */
public class RecoveryReportItem {

    private final String name;
    private final String format;
    private final long offset;
    private final String offsetLabel;
    private final long size;
    private final String boundaryStatus;
    private final String confidence;
    private final String recoveryStatus;

    public RecoveryReportItem(
            String name,
            String format,
            long offset,
            String offsetLabel,
            long size,
            String boundaryStatus,
            String confidence,
            String recoveryStatus
    ) {
        this.name = name != null ? name : "UNKNOWN";
        this.format = format != null ? format : "UNKNOWN";
        this.offset = offset;
        this.offsetLabel = offsetLabel != null ? offsetLabel : String.valueOf(offset);
        this.size = Math.max(0, size);
        this.boundaryStatus = boundaryStatus != null ? boundaryStatus : "N/A";
        this.confidence = confidence != null ? confidence : "N/A";
        this.recoveryStatus = recoveryStatus != null ? recoveryStatus : "DISCOVERED";
    }

    public String getName() {
        return name;
    }

    public String getFormat() {
        return format;
    }

    public long getOffset() {
        return offset;
    }

    public String getOffsetLabel() {
        return offsetLabel;
    }

    public long getSize() {
        return size;
    }

    public String getBoundaryStatus() {
        return boundaryStatus;
    }

    public String getConfidence() {
        return confidence;
    }

    public String getRecoveryStatus() {
        return recoveryStatus;
    }
}
