package core.prediction;

import filesystem.fat32.Fat32ClusterChain;
import filesystem.fat32.Fat32Directory;
import recovery.signature.SignatureDatabase;
import scanner.FileSignatureScanner;

/**
 * Extracts real recovery information from URE components
 * and converts it into RecoveryFeatures.
 *
 * This class currently supports FAT32 recovery candidates.
 */
public class RecoveryFeatureExtractor {

    private final FileSignatureScanner signatureScanner;
    private final SignatureDatabase signatureDatabase;
    private final Fat32ClusterChain clusterChainAnalyzer;

    public RecoveryFeatureExtractor() {
        this.signatureScanner = new FileSignatureScanner();
        this.signatureDatabase = new SignatureDatabase();
        this.clusterChainAnalyzer = new Fat32ClusterChain();
    }

    /**
     * Extracts recovery features from a FAT32 directory entry,
     * raw candidate file bytes, and FAT table data.
     *
     * @param directoryEntry parsed FAT32 directory entry
     * @param fileData raw candidate file bytes
     * @param fatData raw FAT32 FAT table bytes
     * @return extracted recovery features
     */
    public RecoveryFeatures extractFat32(
            Fat32Directory directoryEntry,
            byte[] fileData,
            byte[] fatData) {

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

        /*
         * Detect file type using real magic-byte signatures.
         */
        String detectedType =
                signatureScanner.detectFileType(fileData);

        boolean validHeader =
                !"UNKNOWN".equals(detectedType);

        /*
         * FAT32 metadata survived if meaningful file size
         * and starting cluster still exist.
         */
        boolean metadataAvailable =
                directoryEntry.getFileSize() > 0 &&
                directoryEntry.getFirstCluster() > 0;

        /*
         * Starting cluster information is available.
         */
        boolean clustersAvailable =
                directoryEntry.getFirstCluster() >= 2;

        /*
         * Validate candidate file footer.
         */
        boolean validFooter =
                hasValidFooter(fileData, detectedType);

        /*
         * Fragmentation analysis.
         *
         * null means URE could not determine fragmentation.
         */
        Boolean fragmented = null;

        if (fatData != null &&
                fatData.length > 0 &&
                directoryEntry.getFirstCluster() >= 2) {

            try {
                fragmented =
                        clusterChainAnalyzer.analyzeFragmentation(
                                fatData,
                                directoryEntry.getFirstCluster()
                        );

            } catch (IllegalArgumentException |
                     IllegalStateException e) {

                fragmented = null;
            }
        }

        /*
         * Overwrite analysis is not implemented yet.
         */
        Double overwrittenPercentage = null;

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
     * Checks whether candidate data ends with the expected
     * footer for its detected file type.
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
