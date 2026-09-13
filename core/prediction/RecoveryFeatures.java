package core.prediction;

/**
 * Stores the characteristics used to estimate
 * whether a deleted file can be recovered successfully.
 */
public class RecoveryFeatures {

    private final boolean validHeader;
    private final boolean validFooter;
    private final boolean metadataAvailable;
    private final boolean clustersAvailable;
    private final boolean fragmented;
    private final double overwrittenPercentage;

    public RecoveryFeatures(
            boolean validHeader,
            boolean validFooter,
            boolean metadataAvailable,
            boolean clustersAvailable,
            boolean fragmented,
            double overwrittenPercentage) {

        if (overwrittenPercentage < 0 || overwrittenPercentage > 100) {
            throw new IllegalArgumentException(
                    "Overwritten percentage must be between 0 and 100"
            );
        }

        this.validHeader = validHeader;
        this.validFooter = validFooter;
        this.metadataAvailable = metadataAvailable;
        this.clustersAvailable = clustersAvailable;
        this.fragmented = fragmented;
        this.overwrittenPercentage = overwrittenPercentage;
    }

    public boolean hasValidHeader() {
        return validHeader;
    }

    public boolean hasValidFooter() {
        return validFooter;
    }

    public boolean hasMetadata() {
        return metadataAvailable;
    }

    public boolean hasClustersAvailable() {
        return clustersAvailable;
    }

    public boolean isFragmented() {
        return fragmented;
    }

    public double getOverwrittenPercentage() {
        return overwrittenPercentage;
    }
}
