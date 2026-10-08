package filesystem.fat32;

import core.disk.SectorReader;
import core.prediction.RecoveryFeatureExtractor;
import core.prediction.RecoveryFeatures;
import core.prediction.RecoveryPrediction;
import core.prediction.RecoveryPredictor;
import java.io.IOException;
import java.util.List;

/**
 * Robust FAT32 file recovery that supports:
 * - FAT cluster-chain recovery
 * - Fragmented file recovery
 * - Subdirectory traversal
 * - Safe fallback strategies
 * - Partial recovery classification
 * - Overwrite detection
 * - Confidence integration
 */
public class FAT32RobustRecovery {

    private final SectorReader reader;
    private final FAT32BootSector bootSector;
    private final FAT32FileChainValidator chainValidator;
    private final RecoveryFeatureExtractor featureExtractor;
    private final RecoveryPredictor predictor;

    public FAT32RobustRecovery(
            SectorReader reader,
            FAT32BootSector bootSector
    ) {
        if (reader == null) {
            throw new IllegalArgumentException(
                    "SectorReader cannot be null"
            );
        }

        if (bootSector == null) {
            throw new IllegalArgumentException(
                    "FAT32BootSector cannot be null"
            );
        }

        this.reader = reader;
        this.bootSector = bootSector;
        this.chainValidator = new FAT32FileChainValidator(bootSector);
        this.featureExtractor = new RecoveryFeatureExtractor();
        this.predictor = new RecoveryPredictor();
    }

    /**
     * Attempts to recover a deleted file using FAT cluster-chain recovery.
     * This is the primary recovery method that:
     * 1. Reads and validates the FAT cluster chain
     * 2. If valid, recovers data from the chain
     * 3. If invalid/unavailable, falls back to contiguous recovery with warning
     * 4. Analyzes for overwrite evidence
     * 5. Generates confidence based on evidence
     *
     * @param entry the deleted directory entry to recover
     * @return FAT32RecoveryResult containing recovered data and metadata
     */
    public FAT32RecoveryResult recover(FAT32DirectoryEntry entry)
            throws IOException {

        if (entry == null) {
            throw new IllegalArgumentException(
                    "Directory entry cannot be null"
            );
        }

        if (entry.isDirectory()) {
            throw new IllegalArgumentException(
                    "Cannot recover a directory as a file"
            );
        }

        long fileSize = entry.getFileSize();

        // Handle zero-length files
        if (fileSize == 0) {
            return createZeroLengthResult(entry);
        }

        long startCluster = entry.getStartCluster();

        if (startCluster < 2) {
            return createFailedResult(
                    entry,
                    "Invalid starting cluster: " + startCluster,
                    FAT32RecoveryStatus.FAILED
            );
        }

        // Try cluster chain recovery first
        FAT32ChainReadResult chainResult =
                chainValidator.readClusterChainWithValidation(entry, reader);

        byte[] recoveredData;
        FAT32RecoveryStatus status;
        List<Long> clusterChain = chainResult.getChain();
        double overwritePercentage = 0.0;
        boolean fragmented = false;

        if (chainResult.getValidity() == FAT32ChainReadResult.ChainValidity.VALID
                && clusterChain != null) {

            // Valid cluster chain - recover from chain
            recoveredData = chainValidator.recoverDataFromChain(
                    clusterChain,
                    reader,
                    bootSector,
                    fileSize
            );

            if (recoveredData != null && recoveredData.length == fileSize) {
                status = FAT32RecoveryStatus.EXACT;
            } else if (recoveredData != null && recoveredData.length > 0) {
                status = FAT32RecoveryStatus.PARTIAL;
            } else {
                status = FAT32RecoveryStatus.FAILED;
            }

            // Check fragmentation
            fragmented = isFragmented(clusterChain);

            // Analyze overwrite
            if (recoveredData != null) {
                overwritePercentage = analyzeOverwrite(
                        recoveredData,
                        clusterChain,
                        fileSize
                );
            }

        } else if (chainResult.getValidity() == FAT32ChainReadResult.ChainValidity.PARTIAL
                && clusterChain != null) {

            // Partial chain - recover what's available
            recoveredData = chainValidator.recoverDataFromChain(
                    clusterChain,
                    reader,
                    bootSector,
                    fileSize
            );

            if (recoveredData != null && recoveredData.length > 0) {
                status = FAT32RecoveryStatus.PARTIAL;
            } else {
                // Fall back to contiguous assumption
                recoveredData = recoverContiguous(entry);
                if (recoveredData != null && recoveredData.length > 0) {
                    status = FAT32RecoveryStatus.PARTIAL;
                    overwritePercentage = 50.0; // Lower confidence due to fallback
                } else {
                    status = FAT32RecoveryStatus.FAILED;
                }
            }

            fragmented = isFragmented(clusterChain);

        } else {

            // Chain unavailable - use contiguous fallback with lower confidence
            recoveredData = recoverContiguous(entry);

            if (recoveredData != null && recoveredData.length == fileSize) {
                status = FAT32RecoveryStatus.PARTIAL; // Lower confidence
                overwritePercentage = 25.0; // Assumption may be wrong
            } else if (recoveredData != null && recoveredData.length > 0) {
                status = FAT32RecoveryStatus.PARTIAL;
            } else {
                status = FAT32RecoveryStatus.FAILED;
            }
        }

        // Generate confidence prediction
        RecoveryPrediction prediction = generatePrediction(
                entry,
                recoveredData,
                clusterChain,
                status,
                fragmented,
                overwritePercentage
        );

        return new FAT32RecoveryResult(
                entry.getFileName(),
                recoveredData,
                fileSize,
                recoveredData != null ? recoveredData.length : 0,
                status,
                prediction,
                clusterChain,
                fragmented,
                overwritePercentage
        );
    }

    /**
     * Recovers a file assuming contiguous clusters (fallback strategy).
     */
    private byte[] recoverContiguous(FAT32DirectoryEntry entry)
            throws IOException {

        long fileSize = entry.getFileSize();
        long startCluster = entry.getStartCluster();

        int bytesPerSector = bootSector.getBytesPerSector();
        int sectorsPerCluster = bootSector.getSectorsPerCluster();
        long bytesPerCluster = (long) bytesPerSector * sectorsPerCluster;

        long clustersRequired = (fileSize + bytesPerCluster - 1) / bytesPerCluster;

        java.io.ByteArrayOutputStream output =
                new java.io.ByteArrayOutputStream();

        long remaining = fileSize;

        for (long i = 0; i < clustersRequired && remaining > 0; i++) {
            long cluster = startCluster + i;

            // Check cluster validity
            if (cluster < 2) {
                break;
            }

            long firstSector;
            try {
                firstSector = bootSector.clusterToSector(cluster);
            } catch (IllegalArgumentException e) {
                break;
            }

            // Check bounds
            if (firstSector >= reader.getTotalSectors()) {
                break;
            }

            for (int sectorOffset = 0;
                 sectorOffset < sectorsPerCluster && remaining > 0;
                 sectorOffset++) {

                long sector = firstSector + sectorOffset;
                if (sector >= reader.getTotalSectors()) {
                    return truncateOutput(output, fileSize);
                }

                try {
                    byte[] sectorData = reader.readSector(sector);

                    int bytesToWrite = (int) Math.min(remaining, sectorData.length);
                    output.write(sectorData, 0, bytesToWrite);
                    remaining -= bytesToWrite;
                } catch (IOException e) {
                    return truncateOutput(output, fileSize);
                }
            }
        }

        byte[] data = output.toByteArray();
        if (data.length > fileSize) {
            byte[] truncated = new byte[(int) fileSize];
            System.arraycopy(data, 0, truncated, 0, truncated.length);
            return truncated;
        }

        return data;
    }

    private byte[] truncateOutput(
            java.io.ByteArrayOutputStream output,
            long targetSize
    ) {
        byte[] data = output.toByteArray();
        if (data.length > targetSize) {
            byte[] truncated = new byte[(int) targetSize];
            System.arraycopy(data, 0, truncated, 0, truncated.length);
            return truncated;
        }
        return data;
    }

    private boolean isFragmented(List<Long> chain) {
        if (chain == null || chain.size() <= 1) {
            return false;
        }

        for (int i = 1; i < chain.size(); i++) {
            if (chain.get(i) != chain.get(i - 1) + 1) {
                return true;
            }
        }
        return false;
    }

    private double analyzeOverwrite(
            byte[] data,
            List<Long> chain,
            long expectedSize
    ) {
        if (data == null || data.length == 0) {
            return 100.0;
        }

        // Check for zero-filled data
        boolean allZeros = true;
        int checkLen = Math.min(data.length, 4096);
        for (int i = 0; i < checkLen; i++) {
            if (data[i] != 0) {
                allZeros = false;
                break;
            }
        }

        if (allZeros && expectedSize > 0) {
            return 100.0;
        }

        // Check if recovered size matches expected
        if (data.length < expectedSize) {
            double missing = expectedSize - data.length;
            return (missing / expectedSize) * 100.0;
        }

        return 0.0;
    }

    private RecoveryPrediction generatePrediction(
            FAT32DirectoryEntry entry,
            byte[] recoveredData,
            List<Long> chain,
            FAT32RecoveryStatus status,
            boolean fragmented,
            double overwritePercentage
    ) {
        boolean validHeader = false;
        boolean validFooter = false;

        if (recoveredData != null && recoveredData.length > 0) {
            String detectedType = featureExtractor.detectFileType(
                    recoveredData,
                    entry.getFileSize()
            );
            validHeader = !"UNKNOWN".equals(detectedType);
            validFooter = featureExtractor.hasValidFooter(recoveredData, detectedType);
        }

        RecoveryFeatures features = new RecoveryFeatures(
                validHeader,
                validFooter,
                true, // Metadata available
                chain != null && !chain.isEmpty(),
                fragmented ? Boolean.TRUE : Boolean.FALSE,
                overwritePercentage > 0 ? overwritePercentage : null
        );

        return predictor.predict(features);
    }

    private FAT32RecoveryResult createZeroLengthResult(FAT32DirectoryEntry entry) {
        RecoveryPrediction prediction = predictor.predict(
                new RecoveryFeatures(
                        true, // Zero-length is valid
                        true,
                        true,
                        true,
                        false,
                        0.0
                )
        );

        return new FAT32RecoveryResult(
                entry.getFileName(),
                new byte[0],
                0,
                0,
                FAT32RecoveryStatus.EXACT,
                prediction,
                null,
                false,
                0.0
        );
    }

    private FAT32RecoveryResult createFailedResult(
            FAT32DirectoryEntry entry,
            String reason,
            FAT32RecoveryStatus status
    ) {
        RecoveryPrediction prediction = predictor.predict(
                new RecoveryFeatures(
                        false,
                        false,
                        false,
                        false,
                        null,
                        100.0
                )
        );

        return new FAT32RecoveryResult(
                entry.getFileName(),
                null,
                entry.getFileSize(),
                0,
                status,
                prediction,
                null,
                false,
                100.0
        );
    }
}