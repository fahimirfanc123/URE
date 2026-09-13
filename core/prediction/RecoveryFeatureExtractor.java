package core.prediction;

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

    public RecoveryFeatureExtractor() {
        this.signatureScanner = new FileSignatureScanner();
        this.signatureDatabase = new SignatureDatabase();
    }

    /**
     * Extracts recovery features from a FAT32 directory entry
     * and the raw bytes of a recovery candidate.
     *
     * @param directoryEntry parsed FAT32 directory entry
     * @param fileData raw candidate file bytes
     * @return extracted recovery features
     */
    public RecoveryFeatures extractFat32(
            Fat32Directory directoryEntry,
            byte[] fileData) {

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
         * Detect the file type using its real magic-byte header.
         */
        String detectedType =
                signatureScanner.detectFileType(fileData);

        boolean validHeader =
                !"UNKNOWN".equals(detectedType);

        /*
         * Check whether useful FAT32 metadata still exists.
         *
         * A meaningful file size and starting cluster indicate
         * that useful metadata survived deletion.
         */
        boolean metadataAvailable =
                directoryEntry.getFileSize() > 0 &&
                directoryEntry.getFirstCluster() > 0;

        /*
         * At this stage URE considers a valid starting cluster
         * as evidence that cluster information is available.
         */
        boolean clustersAvailable =
                directoryEntry.getFirstCluster() > 0;

        /*
         * Validate the end of the candidate file against the
         * known footer for the detected file format.
         */
        boolean validFooter =
                hasValidFooter(fileData, detectedType);

        /*
         * URE does not yet perform full FAT cluster-chain
         * fragmentation analysis.
         *
         * null means UNKNOWN.
         */
        Boolean fragmented = null;

        /*
         * URE does not yet determine how much of a deleted
         * file has been overwritten.
         *
         * null means UNKNOWN.
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
     * Checks whether the candidate data ends with the
     * expected footer for its detected file type.
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

        /*
         * Some formats do not have a footer registered.
         * In that situation URE cannot confirm footer validity.
         */
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
