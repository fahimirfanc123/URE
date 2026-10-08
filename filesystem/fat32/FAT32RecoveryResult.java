package filesystem.fat32;

import core.prediction.RecoveryPrediction;
import java.util.List;

/**
 * Represents the result of a FAT32 file recovery operation.
 * Contains all metadata about the recovery including:
 * - Recovered data bytes
 * - Original and recovered sizes
 * - Recovery status (EXACT/PARTIAL/FAILED)
 * - Confidence prediction
 * - Cluster chain information
 * - Overwrite analysis
 */
public class FAT32RecoveryResult {

    private final String fileName;
    private final byte[] data;
    private final long originalSize;
    private final long recoveredSize;
    private final FAT32RecoveryStatus status;
    private final RecoveryPrediction prediction;
    private final List<Long> clusterChain;
    private final boolean fragmented;
    private final double overwritePercentage;

    public FAT32RecoveryResult(
            String fileName,
            byte[] data,
            long originalSize,
            long recoveredSize,
            FAT32RecoveryStatus status,
            RecoveryPrediction prediction,
            List<Long> clusterChain,
            boolean fragmented,
            double overwritePercentage
    ) {
        this.fileName = fileName;
        this.data = data;
        this.originalSize = originalSize;
        this.recoveredSize = recoveredSize;
        this.status = status;
        this.prediction = prediction;
        this.clusterChain = clusterChain;
        this.fragmented = fragmented;
        this.overwritePercentage = overwritePercentage;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getData() {
        return data;
    }

    public long getOriginalSize() {
        return originalSize;
    }

    public long getRecoveredSize() {
        return recoveredSize;
    }

    public FAT32RecoveryStatus getStatus() {
        return status;
    }

    public RecoveryPrediction getPrediction() {
        return prediction;
    }

    public List<Long> getClusterChain() {
        return clusterChain;
    }

    public boolean isFragmented() {
        return fragmented;
    }

    public double getOverwritePercentage() {
        return overwritePercentage;
    }

    /**
     * Returns the recovery percentage (recovered/original * 100).
     */
    public double getRecoveryPercentage() {
        if (originalSize == 0) {
            return 100.0;
        }
        return (recoveredSize * 100.0) / originalSize;
    }

    /**
     * Returns true if recovery was successful (EXACT or PARTIAL).
     */
    public boolean isSuccessful() {
        return status == FAT32RecoveryStatus.EXACT
                || status == FAT32RecoveryStatus.PARTIAL;
    }

    @Override
    public String toString() {
        return "FAT32RecoveryResult{" +
                "fileName='" + fileName + '\'' +
                ", originalSize=" + originalSize +
                ", recoveredSize=" + recoveredSize +
                ", status=" + status +
                ", fragmented=" + fragmented +
                ", overwritePercentage=" + overwritePercentage +
                ", prediction=" + prediction +
                '}';
    }
}