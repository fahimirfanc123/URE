
package filesystem.fat32;

import core.disk.SectorReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FAT32DeletedFileScanner {

    private static final int ENTRY_SIZE = 32;
    private static final int ATTR_LONG_NAME = 0x0F;
    private static final int ATTR_VOLUME_LABEL = 0x08;
    private static final int ATTR_DIRECTORY = 0x10;

    private static final long FAT_MASK = 0x0FFFFFFFL;
    private static final long EOC_MIN = 0x0FFFFFF8L;
    private static final long BAD_CLUSTER = 0x0FFFFFF7L;

    private final SectorReader reader;
    private final FAT32BootSector bootSector;

    public FAT32DeletedFileScanner(
            SectorReader reader,
            FAT32BootSector bootSector) {

        if (reader == null || bootSector == null) {
            throw new IllegalArgumentException(
                    "Reader and boot sector are required");
        }

        this.reader = reader;
        this.bootSector = bootSector;
    }

    public List<FAT32DirectoryEntry> scanRootDirectory()
            throws IOException {

        List<FAT32DirectoryEntry> results = new ArrayList<>();
        Set<Long> visitedDirectoryClusters = new HashSet<>();

        scanDirectory(
                bootSector.getRootCluster(),
                false,
                results,
                visitedDirectoryClusters);

        return results;
    }

    private void scanDirectory(
            long startCluster,
            boolean insideDeletedDirectory,
            List<FAT32DirectoryEntry> results,
            Set<Long> visitedDirectoryClusters)
            throws IOException {

        if (startCluster < 2) {
            return;
        }

        long cluster = startCluster;
        Set<Long> chainVisited = new HashSet<>();

        while (cluster >= 2
                && chainVisited.add(cluster)
                && visitedDirectoryClusters.add(cluster)) {

            long firstSector = bootSector.clusterToSector(cluster);

            for (int i = 0;
                 i < bootSector.getSectorsPerCluster();
                 i++) {

                long sectorNumber = firstSector + i;

                if (sectorNumber < 0
                        || sectorNumber >= bootSector.getTotalSectors()) {
                    throw new IOException(
                            "Directory sector is out of bounds: "
                                    + sectorNumber);
                }

                byte[] sector = reader.readSector(sectorNumber);

                boolean endOfDirectory = scanSector(
                        sector,
                        insideDeletedDirectory,
                        results,
                        visitedDirectoryClusters);

                if (endOfDirectory) {
                    return;
                }
            }

            cluster = readNextCluster(cluster);
        }
    }

    private boolean scanSector(
            byte[] sector,
            boolean insideDeletedDirectory,
            List<FAT32DirectoryEntry> results,
            Set<Long> visitedDirectoryClusters)
            throws IOException {

        for (int offset = 0;
             offset + ENTRY_SIZE <= sector.length;
             offset += ENTRY_SIZE) {

            int first = sector[offset] & 0xFF;

            // No entries follow this marker in this directory.
            if (first == 0x00) {
                return true;
            }

            boolean deleted = first == 0xE5;
            int attributes = sector[offset + 11] & 0xFF;

            // Skip long-filename metadata and volume labels.
            if (attributes == ATTR_LONG_NAME
                    || (attributes & ATTR_VOLUME_LABEL) != 0) {
                continue;
            }

            boolean directory = (attributes & ATTR_DIRECTORY) != 0;

            // Skip the special "." and ".." directory entries.
            if (!deleted && sector[offset] == '.') {
                continue;
            }

            long high = readUInt16LE(sector, offset + 20);
            long low = readUInt16LE(sector, offset + 26);
            long startCluster = (high << 16) | low;
            long fileSize = readUInt32LE(sector, offset + 28);

            if (directory) {
                /*
                 * Enter deleted directories and their descendants.
                 * Do not recursively scan ordinary root directories.
                 */
                if ((deleted || insideDeletedDirectory)
                        && startCluster >= 2) {
                    scanDirectory(
                            startCluster,
                            true,
                            results,
                            visitedDirectoryClusters);
                }

                // Directories are not file recovery candidates.
                continue;
            }

            /*
             * Include files whose own entry is deleted, and files
             * still marked allocated inside a deleted directory.
             */
            if (deleted || insideDeletedDirectory) {
                String name = decodeShortName(
                        sector, offset, deleted);

                results.add(new FAT32DirectoryEntry(
                        name,
                        startCluster,
                        fileSize,
                        true,
                        attributes));
            }
        }

        return false;
    }

    private long readNextCluster(long cluster) throws IOException {

        int bytesPerSector = bootSector.getBytesPerSector();
        long fatOffset = cluster * 4L;

        long fatSector = bootSector.getReservedSectorCount()
                + fatOffset / bytesPerSector;

        int fatEntryOffset = (int) (fatOffset % bytesPerSector);

        byte[] fatData = reader.readSector(fatSector);

        if (fatEntryOffset + 4 > fatData.length) {
            throw new IOException("Invalid FAT entry offset");
        }

        long next = readUInt32LE(fatData, fatEntryOffset) & FAT_MASK;

        if (next >= EOC_MIN || next == 0 || next == 1
                || next == BAD_CLUSTER) {
            return EOC_MIN;
        }

        if (next < 2) {
            throw new IOException("Invalid FAT cluster chain: " + next);
        }

        return next;
    }

    private static String decodeShortName(
            byte[] sector,
            int offset,
            boolean deleted) {

        int baseLength = deleted ? 7 : 8;

        String base = new String(
                sector,
                offset + (deleted ? 1 : 0),
                baseLength,
                StandardCharsets.US_ASCII).trim();

        String extension = new String(
                sector,
                offset + 8,
                3,
                StandardCharsets.US_ASCII).trim();

        String name = deleted ? "?" + base : base;

        if (!extension.isEmpty()) {
            name += "." + extension;
        }

        return name;
    }

    private static int readUInt16LE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8);
    }

    private static long readUInt32LE(byte[] data, int offset) {
        return ((long) data[offset] & 0xFF)
                | (((long) data[offset + 1] & 0xFF) << 8)
                | (((long) data[offset + 2] & 0xFF) << 16)
                | (((long) data[offset + 3] & 0xFF) << 24);
    }
}
