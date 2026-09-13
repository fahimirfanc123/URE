package core.prediction;

/**
 * Stores the characteristics used to estimate
 * whether a deleted file can be recovered successfully.
 *
 * Some values can be unknown. In that case,
 * wrapper types such as Boolean and Double are used
 * so null can represent "not yet determined".
 */
public class RecoveryFeatures {

    private final boolean validHeader;
    private final boolean validFooter;
    private final boolean metadataAvailable;
    private final boolean clustersAvailable;

    // null = unknown
    private final Boolean fragmented;

    // null = unknown
    private final Double overwrittenPercentage;

    public RecoveryFeatures(
            boolean validHeader,
            boolean validFooter,
            boolean metadataAvailable,
            boolean clustersAvailable,
            Boolean fragmented,
            Double overwrittenPercentage) {

        if (overwrittenPercentage != null &&
                (overwrittenPercentage < 0 ||
                 overwrittenPercentage > 100)) {

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

    /**
     * @return true if fragmented,
     *         false if not fragmented,
     *         null if unknown
     */
    public Boolean isFragmented() {
        return fragmented;
    }

    /**
     * @return percentage of overwritten data,
     *         or null if overwrite status is unknown
     */
    public Double getOverwrittenPercentage() {
        return overwrittenPercentage;
    }
}
