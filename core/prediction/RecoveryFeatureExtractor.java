package core.prediction;

import core.disk.SectorReader;
import filesystem.fat32.FAT32BootSector;
import filesystem.fat32.FAT32DirectoryEntry;
import filesystem.ntfs.NTFSBootSector;
import filesystem.ntfs.NTFSDataRun;
import filesystem.ntfs.NTFSFileEntry;
import filesystem.ntfs.NTFSMFTRecord;
import filesystem.ntfs.NTFSRunListParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts recovery evidence from active URE filesystem models
 * and converts them into RecoveryFeatures for the RecoveryPredictor.
 *
 * This layer does not perform raw disk parsing itself; it receives
 * facts from the active filesystem layer and evaluates concrete evidence.
 */
public class RecoveryFeatureExtractor {

    private static final long FAT32_MASK = 0x0FFFFFFFL;
    private static final long FAT32_END_MIN = 0x0FFFFFF8L;
    private static final long FAT32_END_MAX = 0x0FFFFFFFL;
    private static final long FAT32_BAD = 0x0FFFFFF7L;

    public RecoveryFeatureExtractor() {
    }

    /*
     * =============================================================
     * ACTIVE FAT32 FEATURE EXTRACTION
     * =============================================================
     */

    /**
     * Extracts recovery features from an active FAT32 directory entry
     * by querying filesystem geometry and sampling starting sector data.
     *
     * @param entry  active FAT32 directory entry
     * @param reader active sector reader (can be null for offline analysis)
     * @param boot   active FAT32 boot sector (can be null for offline analysis)
     * @return populated RecoveryFeatures
     */
    public RecoveryFeatures extractFAT32(
            FAT32DirectoryEntry entry,
            SectorReader reader,
            FAT32BootSector boot
    ) {
        if (entry == null) {
            throw new IllegalArgumentException("FAT32DirectoryEntry cannot be null");
        }

        boolean metadataAvailable = entry.getFileSize() >= 0 &&
                entry.getStartCluster() >= 2 &&
                entry.getFileName() != null &&
                !entry.getFileName().isBlank();

        boolean clustersAvailable = false;
        boolean validHeader = false;
        boolean validFooter = false;
        Boolean fragmented = null;
        Double overwrittenPercentage = null;

        if (boot != null) {
            long clusterSize = (long) boot.getBytesPerSector() * boot.getSectorsPerCluster();
            long totalClusters = (boot.getTotalSectors() - boot.getFirstDataSector())
                    / boot.getSectorsPerCluster();

            clustersAvailable = entry.getStartCluster() >= 2 &&
                    entry.getStartCluster() < (totalClusters + 2);

            // Single cluster files cannot be fragmented
            if (entry.getFileSize() > 0 && clusterSize > 0) {
                if (entry.getFileSize() <= clusterSize) {
                    fragmented = Boolean.FALSE;
                }
            }

            // Inspect the candidate starting sector if reader is available
            if (reader != null && clustersAvailable) {
                try {
                    long sector = boot.clusterToSector(entry.getStartCluster());
                    byte[] firstSector = reader.readSector(sector);

                    boolean allZeros = isAllZeros(firstSector);
                    if (entry.getFileSize() > 0 && allZeros) {
                        overwrittenPercentage = 100.0;
                        validHeader = false;
                    } else if (!allZeros) {
                        overwrittenPercentage = 0.0;
                        String type = detectFileType(firstSector, entry.getFileSize());
                        validHeader = !"UNKNOWN".equals(type);
                    }
                } catch (Exception e) {
                    // Non-fatal: leave undetermined
                }
            }
        } else {
            clustersAvailable = entry.getStartCluster() >= 2;
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
     * Extracts FAT32 features from memory buffers and FAT table data.
     *
     * @param entry       FAT32 directory entry
     * @param fileData    candidate recovered bytes
     * @param fatData     raw FAT table bytes (optional)
     * @param clusterSize FAT32 cluster size in bytes (optional)
     * @return populated RecoveryFeatures
     */
    public RecoveryFeatures extractFAT32(
            FAT32DirectoryEntry entry,
            byte[] fileData,
            byte[] fatData,
            Long clusterSize
    ) {
        if (entry == null) {
            throw new IllegalArgumentException("Directory entry cannot be null");
        }
        if (fileData == null || fileData.length == 0) {
            throw new IllegalArgumentException("File data cannot be null or empty");
        }

        long fileSize = entry.getFileSize() > 0 ? entry.getFileSize() : fileData.length;
        String detectedType = detectFileType(fileData, fileSize);
        boolean validHeader = !"UNKNOWN".equals(detectedType);
        boolean metadataAvailable = entry.getFileSize() > 0 && entry.getStartCluster() >= 2;
        boolean clustersAvailable = entry.getStartCluster() >= 2;
        boolean validFooter = hasValidFooter(fileData, detectedType);

        Boolean fragmented = null;
        Double overwrittenPercentage = null;

        if (clusterSize != null && clusterSize > 0) {
            if (entry.getFileSize() <= clusterSize) {
                fragmented = Boolean.FALSE;
                overwrittenPercentage = 0.0;
            } else if (fatData != null && fatData.length > 0 && entry.getStartCluster() >= 2) {
                try {
                    List<Long> chain = readFatChain(fatData, entry.getStartCluster());
                    if (chain != null && !chain.isEmpty()) {
                        fragmented = isChainFragmented(chain);
                        long expectedClusters =
                                (entry.getFileSize() + clusterSize - 1) / clusterSize;
                        if (chain.size() >= expectedClusters) {
                            overwrittenPercentage = 0.0;
                        } else {
                            long missing = expectedClusters - chain.size();
                            overwrittenPercentage =
                                    ((double) missing / expectedClusters) * 100.0;
                        }
                    }
                } catch (Exception e) {
                    fragmented = null;
                    overwrittenPercentage = null;
                }
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
     * Backward-compatible overloads.
     */
    public RecoveryFeatures extractFat32(
            FAT32DirectoryEntry entry,
            byte[] fileData,
            byte[] fatData,
            Long clusterSize
    ) {
        return extractFAT32(entry, fileData, fatData, clusterSize);
    }

    public RecoveryFeatures extractFat32(
            FAT32DirectoryEntry entry,
            byte[] fileData,
            byte[] fatData
    ) {
        return extractFAT32(entry, fileData, fatData, null);
    }

    /*
     * =============================================================
     * ACTIVE NTFS FEATURE EXTRACTION
     * =============================================================
     */

    /**
     * Extracts recovery features from an active NTFS MFT record
     * by querying attribute structure, resident/non-resident data, and runlists.
     *
     * @param record active MFT record
     * @param reader active sector reader (optional)
     * @param boot   active NTFS boot sector (optional)
     * @return populated RecoveryFeatures
     */
    public RecoveryFeatures extractNTFS(
            NTFSMFTRecord record,
            SectorReader reader,
            NTFSBootSector boot
    ) {
        if (record == null || !record.isValidFileRecord()) {
            return new RecoveryFeatures(false, false, false, false, null, 100.0);
        }

        byte[] recordData = record.getData().clone();
        if (!applyFixup(recordData)) {
            return new RecoveryFeatures(false, false, false, false, null, 100.0);
        }

        int firstAttributeOffset = readUInt16LE(recordData, 20);
        if (firstAttributeOffset <= 0 || firstAttributeOffset >= recordData.length) {
            return new RecoveryFeatures(false, false, false, false, null, 100.0);
        }

        boolean metadataAvailable = true;
        boolean clustersAvailable = false;
        boolean validHeader = false;
        boolean validFooter = false;
        Boolean fragmented = null;
        Double overwrittenPercentage = null;

        int offset = firstAttributeOffset;
        while (offset + 16 <= recordData.length) {
            long type = readUInt32LE(recordData, offset);
            if (type == 0xFFFFFFFFL) {
                break;
            }

            int attributeLength = (int) readUInt32LE(recordData, offset + 4);
            if (attributeLength <= 0 || offset + attributeLength > recordData.length) {
                break;
            }

            int nonResident = recordData[offset + 8] & 0xFF;

            if (type == 0x80L) { // $DATA attribute
                if (nonResident == 0) {
                    // Resident $DATA
                    int contentLength = (int) readUInt32LE(recordData, offset + 16);
                    int contentOffset = readUInt16LE(recordData, offset + 20);
                    int contentStart = offset + contentOffset;

                    if (contentLength >= 0 &&
                        contentStart >= 0 &&
                        contentStart + contentLength <= recordData.length) {

                        clustersAvailable = true;
                        fragmented = Boolean.FALSE; // Resident data is 100% contiguous
                        overwrittenPercentage = 0.0;

                        if (contentLength > 0) {
                            byte[] residentData = new byte[contentLength];
                            System.arraycopy(recordData, contentStart, residentData, 0, contentLength);
                            String detectedType = detectFileType(residentData, contentLength);
                            validHeader = !"UNKNOWN".equals(detectedType);
                            validFooter = hasValidFooter(residentData, detectedType);
                        }
                    }
                    break;
                } else {
                    // Non-resident $DATA
                    int runListOffset = readUInt16LE(recordData, offset + 32);
                    int runListStart = offset + runListOffset;
                    int attributeEnd = offset + attributeLength;
                    long realFileSize = readInt64LE(recordData, offset + 48);

                    if (runListStart > offset &&
                        runListStart < attributeEnd &&
                        realFileSize >= 0) {

                        try {
                            NTFSRunListParser parser = new NTFSRunListParser();
                            List<NTFSDataRun> runs =
                                    parser.parse(recordData, runListStart, attributeEnd);

                            if (!runs.isEmpty()) {
                                clustersAvailable = true;
                                fragmented = runs.size() > 1 ? Boolean.TRUE : Boolean.FALSE;
                                overwrittenPercentage = 0.0;

                                if (reader != null && boot != null) {
                                    for (NTFSDataRun run : runs) {
                                        if (!run.isSparse() && run.getStartCluster() >= 0) {
                                            long startSector =
                                                    run.getStartCluster() * boot.getSectorsPerCluster();
                                            byte[] headerSector = reader.readSector(startSector);
                                            String detectedType = detectFileType(headerSector, realFileSize);
                                            validHeader = !"UNKNOWN".equals(detectedType);
                                            break;
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Non-fatal
                        }
                    }
                    break;
                }
            }

            offset += attributeLength;
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
     * Extracts NTFS features from memory buffers and metadata properties.
     */
    public RecoveryFeatures extractNTFS(
            NTFSFileEntry entry,
            byte[] fileData,
            boolean isResident,
            int runCount,
            long fileSize
    ) {
        if (entry == null) {
            throw new IllegalArgumentException("NTFSFileEntry cannot be null");
        }

        boolean metadataAvailable = true;
        boolean clustersAvailable = fileSize > 0 || (fileData != null && fileData.length > 0);
        String detectedType = fileData != null ? detectFileType(fileData, fileSize) : "UNKNOWN";
        boolean validHeader = !"UNKNOWN".equals(detectedType);
        boolean validFooter = hasValidFooter(fileData, detectedType);
        Boolean fragmented = isResident ? Boolean.FALSE : (runCount > 1 ? Boolean.TRUE : Boolean.FALSE);
        Double overwrittenPercentage = (fileData != null && isAllZeros(fileData) && fileSize > 0)
                ? 100.0
                : 0.0;

        return new RecoveryFeatures(
                validHeader,
                validFooter,
                metadataAvailable,
                clustersAvailable,
                fragmented,
                overwrittenPercentage
        );
    }

    /*
     * =============================================================
     * SIGNATURE & FILE TYPE DETECTION
     * =============================================================
     */

    public String detectFileType(byte[] block) {
        return detectFileType(block, block != null ? block.length : 0);
    }

    public String detectFileType(byte[] block, long fileSize) {
        if (block == null || block.length == 0) {
            return "UNKNOWN";
        }

        // JPEG: FF D8 FF
        if (startsWith(block, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF)) {
            return "jpg";
        }

        // PNG: 89 50 4E 47
        if (startsWith(block, (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47)) {
            return "png";
        }

        // PDF: 25 50 44 46 (%PDF)
        if (startsWith(block, (byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46)) {
            return "pdf";
        }

        // ZIP / Office: 50 4B 03 04 (PK\x03\x04)
        if (startsWith(block, (byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04)) {
            return "zip";
        }

        // GIF: 47 49 46 38 (GIF8)
        if (startsWith(block, (byte) 0x47, (byte) 0x49, (byte) 0x46, (byte) 0x38)) {
            return "gif";
        }

        // BMP: 42 4D (BM)
        if (startsWith(block, (byte) 0x42, (byte) 0x4D)) {
            return "bmp";
        }

        // ELF: 7F 45 4C 46
        if (startsWith(block, (byte) 0x7F, (byte) 0x45, (byte) 0x4C, (byte) 0x46)) {
            return "elf";
        }

        // Java Class: CA FE BA BE
        if (startsWith(block, (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE)) {
            return "class";
        }

        // Windows PE / EXE: 4D 5A (MZ)
        if (startsWith(block, (byte) 0x4D, (byte) 0x5A)) {
            return "exe";
        }

        // Plain Text / ASCII / UTF-8
        if (isPrintableText(block, fileSize)) {
            return "txt";
        }

        return "UNKNOWN";
    }

    public boolean hasValidFooter(byte[] data, String detectedType) {
        if (data == null || data.length == 0 ||
            detectedType == null || "UNKNOWN".equals(detectedType) || "txt".equals(detectedType)) {
            return false;
        }

        if ("jpg".equals(detectedType)) {
            return endsWith(data, (byte) 0xFF, (byte) 0xD9);
        }

        if ("png".equals(detectedType)) {
            return endsWith(data,
                    (byte) 0x49, (byte) 0x45, (byte) 0x4E, (byte) 0x44,
                    (byte) 0xAE, (byte) 0x42, (byte) 0x60, (byte) 0x82);
        }

        if ("pdf".equals(detectedType)) {
            return containsSubsequence(data, Math.max(0, data.length - 64),
                    new byte[]{0x25, 0x25, 0x45, 0x4F, 0x46});
        }

        if ("zip".equals(detectedType)) {
            return containsSubsequence(data, Math.max(0, data.length - 64),
                    new byte[]{0x50, 0x4B, 0x05, 0x06});
        }

        return false;
    }

    /*
     * =============================================================
     * HELPER UTILITIES
     * =============================================================
     */

    private boolean isPrintableText(byte[] block, long fileSize) {
        if (block == null || block.length == 0) {
            return false;
        }
        int maxCheck = 32;
        if (fileSize > 0 && fileSize < maxCheck) {
            maxCheck = (int) fileSize;
        }
        int checkLen = Math.min(block.length, maxCheck);
        if (checkLen <= 0) {
            return false;
        }
        int printableCount = 0;
        for (int i = 0; i < checkLen; i++) {
            int b = block[i] & 0xFF;
            if ((b >= 32 && b <= 126) || b == 9 || b == 10 || b == 13) {
                printableCount++;
            } else {
                return false;
            }
        }
        return printableCount >= Math.min(checkLen, 4);
    }

    private boolean isAllZeros(byte[] data) {
        if (data == null || data.length == 0) {
            return true;
        }
        int checkLen = Math.min(data.length, 512);
        for (int i = 0; i < checkLen; i++) {
            if (data[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWith(byte[] data, byte... prefix) {
        if (data == null || data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean endsWith(byte[] data, byte... suffix) {
        if (data == null || data.length < suffix.length) {
            return false;
        }
        int start = data.length - suffix.length;
        for (int i = 0; i < suffix.length; i++) {
            if (data[start + i] != suffix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean containsSubsequence(byte[] data, int startOffset, byte[] target) {
        if (data == null || target == null || startOffset < 0 || startOffset + target.length > data.length) {
            return false;
        }
        for (int i = startOffset; i <= data.length - target.length; i++) {
            boolean match = true;
            for (int j = 0; j < target.length; j++) {
                if (data[i + j] != target[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private List<Long> readFatChain(byte[] fatData, long startCluster) {
        List<Long> chain = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        long current = startCluster;

        while (current >= 2) {
            if (visited.contains(current)) {
                break;
            }
            visited.add(current);
            chain.add(current);

            long offset = current * 4L;
            if (offset + 4 > fatData.length) {
                break;
            }

            int raw = ByteBuffer.wrap(fatData, (int) offset, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).getInt();
            long next = raw & FAT32_MASK;

            if (next >= FAT32_END_MIN && next <= FAT32_END_MAX) {
                break;
            }
            if (next == FAT32_BAD || next < 2) {
                break;
            }
            current = next;
        }
        return chain;
    }

    private boolean isChainFragmented(List<Long> chain) {
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

    private boolean applyFixup(byte[] data) {
        if (data.length < 8) {
            return false;
        }
        int usaOffset = readUInt16LE(data, 4);
        int usaCount = readUInt16LE(data, 6);
        if (usaOffset <= 0 || usaCount <= 1 || usaOffset + usaCount * 2 > data.length) {
            return false;
        }
        int updateSequenceNumber = readUInt16LE(data, usaOffset);
        for (int i = 1; i < usaCount; i++) {
            int sectorEnd = (i * 512) - 2;
            if (sectorEnd + 1 >= data.length) {
                return false;
            }
            int existing = readUInt16LE(data, sectorEnd);
            if (existing != updateSequenceNumber) {
                return false;
            }
            data[sectorEnd] = data[usaOffset + i * 2];
            data[sectorEnd + 1] = data[usaOffset + i * 2 + 1];
        }
        return true;
    }

    private int readUInt16LE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private long readUInt32LE(byte[] data, int offset) {
        return ((long) data[offset] & 0xFF)
                | (((long) data[offset + 1] & 0xFF) << 8)
                | (((long) data[offset + 2] & 0xFF) << 16)
                | (((long) data[offset + 3] & 0xFF) << 24);
    }

    private long readInt64LE(byte[] data, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value |= ((long) data[offset + i] & 0xFF) << (8 * i);
        }
        return value;
    }
}
