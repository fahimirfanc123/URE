package filesystem.fat32;

/**
 * Estimates how much of a deleted FAT32 file may have been overwritten.
 *
 * This first version compares:
 *
 * expected clusters required by the file
 * versus
 * clusters still available in the recovered chain.
 *
 * Example:
 *
 * File requires 4 clusters.
 * Only 2 clusters are still usable.
 *
 * Estimated overwrite = 50%.
 */
public class Fat32OverwriteAnalyzer {

    /**
     * Estimates overwrite percentage.
     *
     * @param fileSize      original file size in bytes
     * @param clusterSize   FAT32 cluster size in bytes
     * @param availableClusters number of usable clusters found
     *
     * @return estimated overwrite percentage from 0 to 100
     */
    public double estimateOverwritePercentage(
            long fileSize,
            long clusterSize,
            int availableClusters) {

        if (fileSize < 0) {
            throw new IllegalArgumentException(
                    "File size cannot be negative"
            );
        }

        if (clusterSize <= 0) {
            throw new IllegalArgumentException(
                    "Cluster size must be greater than zero"
            );
        }

        if (availableClusters < 0) {
            throw new IllegalArgumentException(
                    "Available cluster count cannot be negative"
            );
        }

        if (fileSize == 0) {
            return 0.0;
        }

        long expectedClusters =
                (fileSize + clusterSize - 1)
                        / clusterSize;

        if (availableClusters >= expectedClusters) {
            return 0.0;
        }

        long missingClusters =
                expectedClusters - availableClusters;

        double percentage =
                ((double) missingClusters
                        / expectedClusters) * 100.0;

        return Math.max(
                0.0,
                Math.min(percentage, 100.0)
        );
    }
}
