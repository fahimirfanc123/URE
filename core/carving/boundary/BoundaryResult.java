package core.carving.boundary;

import core.carving.BoundaryStatus;

/**
 * Result of boundary detection for a file format signature candidate.
 */
public class BoundaryResult {

    private final BoundaryStatus status;
    private final long length;
    private final boolean validFooter;
    private final String details;

    public BoundaryResult(BoundaryStatus status, long length, boolean validFooter, String details) {
        if (status == null) {
            throw new IllegalArgumentException("BoundaryStatus cannot be null");
        }
        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative: " + length);
        }
        this.status = status;
        this.length = length;
        this.validFooter = validFooter;
        this.details = details != null ? details : "";
    }

    public static BoundaryResult exact(long length, String details) {
        return new BoundaryResult(BoundaryStatus.EXACT, length, true, details);
    }

    public static BoundaryResult truncated(long length, String details) {
        return new BoundaryResult(BoundaryStatus.TRUNCATED, length, false, details);
    }

    public static BoundaryResult invalid(String details) {
        return new BoundaryResult(BoundaryStatus.INVALID, 0, false, details);
    }

    public BoundaryStatus getStatus() {
        return status;
    }

    public long getLength() {
        return length;
    }

    public boolean hasValidFooter() {
        return validFooter;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return "BoundaryResult{" +
                "status=" + status +
                ", length=" + length +
                ", validFooter=" + validFooter +
                ", details='" + details + '\'' +
                '}';
    }
}
