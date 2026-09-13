package core.prediction;

import filesystem.fat32.Fat32ClusterChain;
import filesystem.fat32.Fat32Directory;
import filesystem.fat32.Fat32OverwriteAnalyzer;
import recovery.signature.SignatureDatabase;
import scanner.FileSignatureScanner;

import java.util.List;

/**
 * Extracts recovery information from URE components
 * and converts it into RecoveryFeatures.
 *
 * Currently supports FAT32 recovery candidates.
 */
public class RecoveryFeatureExtractor {

    private final FileSignatureScanner signatureScanner;
    private final SignatureDatabase signatureDatabase;
    private final Fat32ClusterChain clusterChainAnalyzer;
    private final Fat32OverwriteAnalyzer overwriteAnalyzer;

    public RecoveryFeatureExtractor() {
        this.signatureScanner = new FileSignatureScanner();
        this.signatureDatabase = new SignatureDatabase();
        this.clusterChainAnalyzer = new Fat32ClusterChain();
        this.overwriteAnalyzer = new Fat32OverwriteAnalyzer();
    }

    /**
     * Backward-compatible FAT32 extraction.
     *
     * Fragmentation can be determined, but overwrite
     * percentage remains unknown because cluster size
     * was not supplied.
     */
    public RecoveryFeatures extractFat32(
            Fat32Directory directoryEntry,
            byte[] fileData,
            byte[] fatData) {

        return extractFat32(
                directoryEntry,
                fileData,
                fatData,
                null
        );
    }

    /**
     * Full FAT32 recovery feature extraction.
     *
     * @param directoryEntry parsed FAT32 directory entry
     * @param fileData       candidate recovered file bytes
     * @param fatData        raw FAT table bytes
     * @param clusterSize    FAT32 cluster size in bytes
     */
    public RecoveryFeatures extractFat32(
            Fat32Directory directoryEntry,
            byte[] fileData,
            byte[] fatData,
            Long clusterSize) {

        if (directoryEntry == null) {
            throw new IllegalArgumentException(
                    "Directory entry cannot be null"
            );
        }

        if (fileData == null || fileData.length == 0) {
            throw new IllegalArgumentException(
                    "File data cannot be null or empty"
            );
        }

        String detectedType =
                signatureScanner.detectFileType(fileData);

        boolean validHeader =
                !"UNKNOWN".equals(detectedType);

        boolean metadataAvailable =
                directoryEntry.getFileSize() > 0 &&
                directoryEntry.getFirstCluster() > 0;

        boolean clustersAvailable =
                directoryEntry.getFirstCluster() >= 2;

        boolean validFooter =
                hasValidFooter(
                        fileData,
                        detectedType
                );

        Boolean fragmented = null;
        Double overwrittenPercentage = null;

        /*
         * Read the real FAT32 cluster chain.
         */
        if (fatData != null &&
                fatData.length > 0 &&
                directoryEntry.getFirstCluster() >= 2) {

            try {

                List<Long> clusterChain =
                        clusterChainAnalyzer.readChain(
                                fatData,
                                directoryEntry.getFirstCluster()
                        );

                fragmented =
                        clusterChainAnalyzer.isFragmented(
                                clusterChain
                        );

                /*
                 * Calculate overwrite estimate only when
                 * the cluster size is known.
                 */
                if (clusterSize != null &&
                        clusterSize > 0) {

                    overwrittenPercentage =
                            overwriteAnalyzer
                                    .estimateOverwritePercentage(
                                            directoryEntry.getFileSize(),
                                            clusterSize,
                                            clusterChain.size()
                                    );
                }

            } catch (IllegalArgumentException |
                     IllegalStateException e) {

                fragmented = null;
                overwrittenPercentage = null;
            }
        }

        return new RecoveryFeatures(
                validHeader,
                validFooter,
                metadataAvailable,
                clustersAvailable,
                fragmented,
                overwrittenPercentage
        );
    }

    /**
     * Checks whether candidate data ends with
     * the expected signature footer.
     */
    private boolean hasValidFooter(
            byte[] data,
            String extension) {

        if (extension == null ||
                "UNKNOWN".equals(extension)) {

            return false;
        }

        byte[] footer =
                signatureDatabase.getFooter(extension);

        if (footer == null ||
                data.length < footer.length) {

            return false;
        }

        int footerStart =
                data.length - footer.length;

        for (int i = 0; i < footer.length; i++) {

            if (data[footerStart + i] != footer[i]) {
                return false;
            }
        }

        return true;
    }
}
