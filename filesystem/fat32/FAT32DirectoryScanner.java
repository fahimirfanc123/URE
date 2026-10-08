package filesystem.fat32;

import core.disk.SectorReader;
import core.prediction.RecoveryFeatureExtractor;
import core.prediction.RecoveryFeatures;
import core.prediction.RecoveryPrediction;
import core.prediction.RecoveryPredictor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Robust FAT32 directory scanner that supports:
 * - Subdirectory traversal (recursive)
 * - Long File Name (LFN) parsing
 * - Deleted file detection
 * - Path tracking
 * - Loop detection for corrupted directory structures
 * - Graceful handling of malformed entries
 */
public class FAT32DirectoryScanner {

    private static final int DIRECTORY_ENTRY_SIZE = 32;
    private static final int MAX_DEPTH = 20; // Prevent infinite recursion
    private static final int MAX_ENTRIES_PER_DIRECTORY = 10000;

    // LFN sequence numbers use these values
    private static final int LFN_ATTRIBUTE = 0x0F;
    private static final int DELETED_MARKER = 0xE5;
    private static final int END_OF_DIRECTORY = 0x00;
    private static final int VOLUME_LABEL_ATTRIBUTE = 0x08;
    private static final int DIRECTORY_ATTRIBUTE = 0x10;

    private final SectorReader reader;
    private final FAT32BootSector bootSector;
    private final RecoveryFeatureExtractor featureExtractor;
    private final RecoveryPredictor predictor;

    // Tracking for loop detection
    private final Set<Long> visitedClusters;

    public FAT32DirectoryScanner(
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
        this.featureExtractor = new RecoveryFeatureExtractor();
        this.predictor = new RecoveryPredictor();
        this.visitedClusters = new HashSet<>();
    }

    /**
     * Scans the root directory for deleted files.
     */
    public List<FAT32DirectoryEntry> scanRootDirectory() throws IOException {
        return scanDirectory(bootSector.getRootCluster(), "");
    }

    /**
     * Scans a specific directory cluster for deleted entries.
     *
     * @param cluster      the starting cluster of the directory
     * @param parentPath   the path to the parent directory
     * @return list of deleted directory entries found
     */
    public List<FAT32DirectoryEntry> scanDirectory(
            long cluster,
            String parentPath
    ) throws IOException {

        // Validate cluster number
        if (cluster < 2) {
            return new ArrayList<>();
        }

        // Check for loop
        if (visitedClusters.contains(cluster)) {
            // Already visited this cluster - potential loop
            return new ArrayList<>();
        }

        // Limit recursion depth
        if (parentPath.split("/").length > MAX_DEPTH) {
            return new ArrayList<>();
        }

        visitedClusters.add(cluster);

        List<FAT32DirectoryEntry> deletedFiles = new ArrayList<>();

        // Calculate the sectors for this cluster
        long firstSector = bootSector.clusterToSector(cluster);
        int sectorsPerCluster = bootSector.getSectorsPerCluster();

        // Check bounds
        if (firstSector >= reader.getTotalSectors()) {
            return deletedFiles;
        }

        int sectorsToRead = sectorsPerCluster;
        if (firstSector + sectorsToRead > reader.getTotalSectors()) {
            sectorsToRead = (int) (reader.getTotalSectors() - firstSector);
        }

        // Read all sectors in the directory cluster
        for (int sectorOffset = 0; sectorOffset < sectorsToRead; sectorOffset++) {
            try {
                byte[] sector = reader.readSector(firstSector + sectorOffset);
                List<FAT32DirectoryEntry> entries = scanSector(
                        sector,
                        parentPath,
                        deletedFiles
                );
                deletedFiles.addAll(entries);

                // Safety limit
                if (deletedFiles.size() >= MAX_ENTRIES_PER_DIRECTORY) {
                    break;
                }
            } catch (IOException e) {
                // Skip unreadable sectors
            }
        }

        // Don't remove from visited - we want to track all visited clusters
        // for the entire scan session

        return deletedFiles;
    }

    /**
     * Scans a single directory sector for entries.
     * This handles both normal and LFN entries.
     */
    private List<FAT32DirectoryEntry> scanSector(
            byte[] sector,
            String parentPath,
            List<FAT32DirectoryEntry> deletedFiles
    ) {
        List<FAT32DirectoryEntry> results = new ArrayList<>();

        // Buffer for reconstructing LFN names
        List<byte[]> lfnBuffer = new ArrayList<>();
        String currentShortName = null;

        for (int offset = 0;
             offset + DIRECTORY_ENTRY_SIZE <= sector.length;
             offset += DIRECTORY_ENTRY_SIZE) {

            int firstByte = sector[offset] & 0xFF;

            // End of directory
            if (firstByte == END_OF_DIRECTORY) {
                break;
            }

            int attributes = sector[offset + 11] & 0xFF;

            // Handle LFN entries
            if (attributes == LFN_ATTRIBUTE) {
                // This is a Long File Name entry
                byte[] entry = new byte[DIRECTORY_ENTRY_SIZE];
                System.arraycopy(sector, offset, entry, 0, DIRECTORY_ENTRY_SIZE);

                // Check if this LFN is deleted
                boolean isLfnDeleted = (sector[offset] & 0xFF) == DELETED_MARKER;

                lfnBuffer.add(entry);

                // Also capture the short name that follows
                // (we'll use it if LFN is incomplete)
                continue;
            }

            // Skip volume labels
            if ((attributes & VOLUME_LABEL_ATTRIBUTE) != 0) {
                lfnBuffer.clear();
                continue;
            }

            // Check for deleted entry
            boolean isDeleted = (firstByte == DELETED_MARKER);

            // Skip non-deleted files unless we want active files too
            // For now, focus on deleted files
            if (!isDeleted) {
                lfnBuffer.clear();
                continue;
            }

            // Parse the short filename
            String shortName = parseShortName(sector, offset);

            // Try to reconstruct full name from LFN buffer
            String fullName = reconstructName(lfnBuffer, shortName);
            lfnBuffer.clear();

            // Parse directory entry
            long startCluster = readUInt16LE(sector, offset + 20);
            long temp = readUInt16LE(sector, offset + 26);
            startCluster = (startCluster << 16) | (temp & 0xFFFFL);

            long fileSize = readUInt32LE(sector, offset + 28);

            // Build path
            String fullPath = parentPath.isEmpty()
                    ? "/" + fullName
                    : parentPath + "/" + fullName;

            // Determine if this is a directory
            boolean isDirectory = (attributes & DIRECTORY_ATTRIBUTE) != 0;

            // Create the directory entry with extended info
            FAT32DirectoryEntry entry = createEntry(
                    fullName,
                    fullPath,
                    startCluster,
                    fileSize,
                    isDeleted,
                    attributes,
                    isDirectory
            );

            if (entry != null) {
                results.add(entry);

                // If this is a deleted subdirectory, recurse into it
                if (isDirectory && isDeleted && startCluster >= 2) {
                    try {
                        List<FAT32DirectoryEntry> subdirEntries =
                                scanDirectory(startCluster, fullPath);
                        deletedFiles.addAll(subdirEntries);
                    } catch (IOException e) {
                        // Can't read subdirectory - skip
                    }
                }
            }
        }

        return results;
    }

    /**
     * Parses the 8.3 short filename from a directory entry.
     */
    private String parseShortName(byte[] sector, int offset) {
        StringBuilder name = new StringBuilder();

        // First character may be 0x05 (Kanji) or 0xE5 (deleted) - replace with ?
        int firstChar = sector[offset] & 0xFF;
        if (firstChar == DELETED_MARKER || firstChar == 0x05) {
            name.append('?');
        } else if (firstChar != 0) {
            name.append((char) firstChar);
        }

        // Base name (bytes 1-7)
        for (int i = 1; i < 8; i++) {
            int b = sector[offset + i] & 0xFF;
            if (b != ' ' && b != 0) {
                name.append((char) b);
            }
        }

        // Extension (bytes 8-10)
        StringBuilder ext = new StringBuilder();
        for (int i = 8; i < 11; i++) {
            int b = sector[offset + i] & 0xFF;
            if (b != ' ' && b != 0) {
                ext.append((char) b);
            }
        }

        if (ext.length() > 0) {
            name.append('.').append(ext);
        }

        return name.toString();
    }

    /**
     * Reconstructs the full filename from LFN entries.
     */
    private String reconstructName(List<byte[]> lfnBuffer, String fallbackName) {
        if (lfnBuffer.isEmpty()) {
            return fallbackName;
        }

        // LFN entries are stored in reverse order (last entry first)
        // Each entry contains 13 UTF-16LE characters
        StringBuilder unicodeName = new StringBuilder();

        // Process entries in reverse (they're stored last-to-first)
        for (int i = lfnBuffer.size() - 1; i >= 0; i--) {
            byte[] entry = lfnBuffer.get(i);

            // Characters 1-5 (bytes 1-10, UTF-16LE)
            for (int j = 1; j <= 10; j += 2) {
                char c = (char) ((entry[j + 1] & 0xFF) << 8 | (entry[j] & 0xFF));
                if (c != 0 && c != 0xFFFF) {
                    unicodeName.append(c);
                }
            }

            // Characters 6-11 (bytes 14-25, UTF-16LE)
            for (int j = 14; j <= 25; j += 2) {
                char c = (char) ((entry[j + 1] & 0xFF) << 8 | (entry[j] & 0xFF));
                if (c != 0 && c != 0xFFFF) {
                    unicodeName.append(c);
                }
            }

            // Characters 12-13 (bytes 28-31, UTF-16LE)
            for (int j = 28; j <= 31; j += 2) {
                char c = (char) ((entry[j + 1] & 0xFF) << 8 | (entry[j] & 0xFF));
                if (c != 0 && c != 0xFFFF) {
                    unicodeName.append(c);
                }
            }
        }

        String result = unicodeName.toString().trim();

        // If we got a valid name, use it; otherwise fall back to short name
        return result.isEmpty() ? fallbackName : result;
    }

    /**
     * Creates a FAT32DirectoryEntry with all metadata.
     */
    private FAT32DirectoryEntry createEntry(
            String fileName,
            String path,
            long startCluster,
            long fileSize,
            boolean deleted,
            int attributes,
            boolean isDirectory
    ) {
        // Validate the entry has minimum required data
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }

        // Skip invalid cluster numbers
        if (startCluster < 2 && !isDirectory) {
            return null;
        }

        // Create a basic entry - we'll enhance it with prediction
        FAT32DirectoryEntry entry = new FAT32DirectoryEntry(
                fileName,
                startCluster,
                fileSize,
                deleted,
                attributes,
                null // Prediction will be added by scanner
        );

        // Extract features and generate prediction
        try {
            RecoveryFeatures features = featureExtractor.extractFAT32(
                    entry,
                    reader,
                    bootSector
            );

            RecoveryPrediction prediction = predictor.predict(features);

            // Create entry with prediction
            return new FAT32DirectoryEntry(
                    fileName,
                    startCluster,
                    fileSize,
                    deleted,
                    attributes,
                    prediction
            );
        } catch (Exception e) {
            // If feature extraction fails, return entry without prediction
            return entry;
        }
    }

    private int readUInt16LE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                |
                ((data[offset + 1] & 0xFF) << 8);
    }

    private long readUInt32LE(byte[] data, int offset) {
        return ((long) data[offset] & 0xFF)
                |
                (((long) data[offset + 1] & 0xFF) << 8)
                |
                (((long) data[offset + 2] & 0xFF) << 16)
                |
                (((long) data[offset + 3] & 0xFF) << 24);
    }

    /**
     * Clears the visited cluster set for a fresh scan.
     */
    public void reset() {
        visitedClusters.clear();
    }
}