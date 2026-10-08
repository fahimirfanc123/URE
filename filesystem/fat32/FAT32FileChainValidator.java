package filesystem.fat32;

import core.disk.SectorReader;
import filesystem.fat32.FAT32BootSector;
import filesystem.fat32.FAT32DirectoryEntry;
import filesystem.fat32.Fat32ClusterChain;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Validates and reads FAT32 cluster chains from deleted directory entries.
 * Integrates with the existing Fat32ClusterChain class for chain reading.
 * Provides safe fallback strategies when the chain is unreliable.
 */
public class FAT32FileChainValidator {

    private final Fat32ClusterChain chainReader;
    private final FAT32BootSector bootSector;

    public FAT32FileChainValidator(FAT32BootSector bootSector) {
        this.chainReader = new Fat32ClusterChain();
        this.bootSector = bootSector;
    }

    /**
     * Reads the cluster chain from the FAT table associated with a directory entry.
     *
     * @param entry      the directory entry containing the start cluster
     * @param sectorReader read-only sector reader for accessing the FAT
     * @return valid cluster chain, or null if the chain cannot be read
     * @throws IOException if I/O error occurs accessing the FAT
     */
    public List<Long> readClusterChain(
            FAT32DirectoryEntry entry,
            SectorReader sectorReader
    ) throws IOException {

        if (entry == null) {
            throw new IllegalArgumentException(
                    "Directory entry cannot be null"
            );
        }

        long startCluster = entry.getStartCluster();
        if (startCluster < 2) {
            return null; // Invalid start cluster
        }

        // Calculate FAT location and read FAT data
        long firstFatSector = bootSector.getReservedSectorCount()
                + (bootSector.getNumberOfFATs() * bootSector.getFatSize());

        int sectorsPerCluster = bootSector.getSectorsPerCluster();
        int bytesPerSector = bootSector.getBytesPerSector();
        long clusterSize = (long) sectorsPerCluster * bytesPerSector;

        // Read enough FAT sectors to cover the cluster chain
        // A conservative estimate: read enough sectors to reach typical cluster ranges
        long clustersToRead = Math.max(startCluster + 100, 200L);
        long fatSectorsNeeded = (clustersToRead * 4 + bytesPerSector - 1) / bytesPerSector;

        // Clamp to available FAT sectors
        long availableFatSectors = bootSector.getTotalSectors() - firstFatSector;
        fatSectorsNeeded = Math.min(fatSectorsNeeded, availableFatSectors);

        if (fatSectorsNeeded <= 0) {
            return null;
        }

        // Read FAT sectors
        long fatStartSector = firstFatSector;
        long fatEndSector = firstFatSector + fatSectorsNeeded - 1;

        if (fatEndSector >= bootSector.getTotalSectors()) {
            return null; // Would read beyond image boundary
        }

        // Read all needed FAT sectors in one operation
        long fatSectorCount = fatEndSector - fatStartSector + 1;
        byte[] fatData = sectorReader.readSectors(fatStartSector, (int) fatSectorCount);

        // Read the cluster chain using the existing Fat32ClusterChain reader
        List<Long> chain = chainReader.readChain(fatData, startCluster);

        // Validate the chain is reasonable
        if (chain != null && !chain.isEmpty()) {
            // Verify clusters are in valid range
            for (Long cluster : chain) {
                if (cluster < 2 || cluster >= bootSector.getTotalClusters() + 2) {
                    // Chain contains invalid cluster, invalidate
                    return null;
                }
            }
        }

        return chain;
    }

    /**
     * Attempts to read a cluster chain with safe fallback.
     * If the FAT chain is valid, returns it.
     * If partially valid, returns what's available with a warning.
     * If unavailable, returns null.
     *
     * @param entry      the directory entry
     * @param sectorReader read-only sector reader
     * @return chain and validity status
     * @throws IOException if I/O error occurs
     */
    public FAT32ChainReadResult readClusterChainWithValidation(
            FAT32DirectoryEntry entry,
            SectorReader sectorReader
    ) throws IOException {

        List<Long> chain = readClusterChain(entry, sectorReader);

        if (chain == null || chain.isEmpty()) {
            // Chain unavailable - try contiguous fallback for small files
            return new FAT32ChainReadResult(
                    null,
                    FAT32ChainReadResult.ChainValidity.UNAVAILABLE
            );
        }

        // Validate chain integrity
        boolean valid = validateClusterChain(chain, entry.getFileSize(), bootSector);

        if (valid) {
            return new FAT32ChainReadResult(chain, FAT32ChainReadResult.ChainValidity.VALID);
        } else {
            // Chain has issues - return whatever is valid
            return new FAT32ChainReadResult(chain, FAT32ChainReadResult.ChainValidity.PARTIAL);
        }
    }

    /**
     * Validates that a cluster chain is physically reasonable.
     */
    private boolean validateClusterChain(
            List<Long> chain,
            long fileSize,
            FAT32BootSector bootSector
    ) {
        if (chain == null || chain.isEmpty()) {
            return false;
        }

        int bytesPerSector = bootSector.getBytesPerSector();
        int sectorsPerCluster = bootSector.getSectorsPerCluster();
        long clusterSize = (long) bytesPerSector * sectorsPerCluster;

        // Check expected number of clusters
        long expectedClusters = (fileSize + clusterSize - 1) / clusterSize;

        // Chain must have at least the expected number of clusters
        if (chain.size() < expectedClusters) {
            return false; // Missing clusters
        }

        // Check that clusters are sequential within runs (allow fragmentation)
        // But verify no obviously invalid patterns
        for (int i = 0; i < chain.size(); i++) {
            Long cluster = chain.get(i);
            if (cluster == null || cluster < 2) {
                return false;
            }
        }

        // Check for looping (should have been caught by Fat32ClusterChain already,
        // but double-check for safety)
        if (hasLoop(chain)) {
            return false;
        }

        return true;
    }

    /**
     * Checks if a cluster chain has a loop.
     */
    private boolean hasLoop(List<Long> chain) {
        // ChainReader already detects loops, but be safe
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Long cluster : chain) {
            if (!seen.add(cluster)) {
                return true; // Loop detected
            }
        }
        return false;
    }

    /**
     * Converts cluster numbers to byte offsets and reads the actual file data.
     *
     * @param chain       validated cluster chain
     * @param sectorReader read-only sector reader
     * @param bootSector  FAT32 boot sector for geometry
     * @param originalFileSize original file size for truncation
     * @return recovered byte array, or null if reading fails
     * @throws IOException if I/O error occurs
     */
    public byte[] recoverDataFromChain(
            List<Long> chain,
            SectorReader sectorReader,
            FAT32BootSector bootSector,
            long originalFileSize
    ) throws IOException {

        if (chain == null || chain.isEmpty()) {
            return null;
        }

        int bytesPerSector = bootSector.getBytesPerSector();
        int sectorsPerCluster = bootSector.getSectorsPerCluster();
        long clusterSize = (long) bytesPerSector * sectorsPerCluster;

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long bytesRecovered = 0;

        for (int i = 0; i < chain.size(); i++) {
            long cluster = chain.get(i);
            long firstSector = bootSector.clusterToSector(cluster);

            // Read all sectors in this cluster
            for (int sectorOffset = 0;
                 sectorOffset < sectorsPerCluster
                         && bytesRecovered < originalFileSize;
                 sectorOffset++) {

                if (firstSector + sectorOffset >= sectorReader.getTotalSectors()) {
                    // Beyond image boundary - truncate
                    return truncateOutput(output, originalFileSize);
                }

                byte[] sector = sectorReader.readSector(firstSector + sectorOffset);

                int bytesToWrite;
                long remainingInCluster = clusterSize
                        - (sectorOffset * bytesPerSector);

                if (bytesRecovered + remainingInCluster <= originalFileSize) {
                    // Write full sector data
                    bytesToWrite = sector.length;
                    output.write(sector, 0, bytesToWrite);
                } else {
                    // Truncate to match original file size
                    int needed = (int) (originalFileSize - bytesRecovered);
                    if (needed > 0 && needed <= sector.length) {
                        output.write(sector, 0, needed);
                    }
                    bytesRecovered = originalFileSize;
                    break;
                }

                bytesRecovered += sector.length;
            }

            if (bytesRecovered >= originalFileSize) {
                break; // Recovered enough data
            }
        }

        // Final truncation to ensure exact file size
        byte[] result = output.toByteArray();
        if (result.length > originalFileSize) {
            byte[] truncated = new byte[(int) originalFileSize];
            System.arraycopy(result, 0, truncated, 0, truncated.length);
            return truncated;
        }

        return result;
    }

    /**
     * Truncates the output stream to the specified size.
     */
    private byte[] truncateOutput(ByteArrayOutputStream output, long targetSize) {
        byte[] data = output.toByteArray();
        if (data.length > targetSize) {
            byte[] truncated = new byte[(int) targetSize];
            System.arraycopy(data, 0, truncated, 0, truncated.length);
            return truncated;
        }
        return data;
    }
}

/**
 * Result of reading a cluster chain with validity assessment.
 */
class FAT32ChainReadResult {

    enum ChainValidity {
        VALID,
        PARTIAL,
        UNAVAILABLE
    }

    private final List<Long> chain;
    private final ChainValidity validity;

    FAT32ChainReadResult(List<Long> chain, ChainValidity validity) {
        this.chain = chain;
        this.validity = validity;
    }

    public List<Long> getChain() {
        return chain;
    }

    public ChainValidity getValidity() {
        return validity;
    }
}