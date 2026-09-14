package filesystem.fat32;

import core.disk.SectorReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FAT32DeletedFileScanner {

    private static final int DIRECTORY_ENTRY_SIZE = 32;

    private final SectorReader reader;
    private final FAT32BootSector bootSector;

    public FAT32DeletedFileScanner(
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
    }

    public List<FAT32DirectoryEntry> scanRootDirectory()
            throws IOException {

        List<FAT32DirectoryEntry> deletedFiles =
                new ArrayList<>();

        long rootCluster =
                bootSector.getRootCluster();

        long rootSector =
                bootSector.clusterToSector(
                        rootCluster
                );

        int sectorsPerCluster =
                bootSector.getSectorsPerCluster();

        for (int i = 0;
             i < sectorsPerCluster;
             i++) {

            byte[] sector =
                    reader.readSector(
                            rootSector + i
                    );

            scanDirectorySector(
                    sector,
                    deletedFiles
            );
        }

        return deletedFiles;
    }

    private void scanDirectorySector(
            byte[] sector,
            List<FAT32DirectoryEntry> deletedFiles
    ) {

        for (int offset = 0;
             offset + DIRECTORY_ENTRY_SIZE <= sector.length;
             offset += DIRECTORY_ENTRY_SIZE) {

            int firstByte =
                    sector[offset] & 0xFF;

            /*
             * 0x00 means no more directory entries.
             */
            if (firstByte == 0x00) {
                break;
            }

            /*
             * Deleted FAT directory entry starts with E5.
             */
            if (firstByte != 0xE5) {
                continue;
            }

            int attributes =
                    sector[offset + 11] & 0xFF;

            /*
             * Skip Long File Name entries.
             */
            if (attributes == 0x0F) {
                continue;
            }

            /*
             * Skip volume labels.
             */
            if ((attributes & 0x08) != 0) {
                continue;
            }

            String fileName =
                    parseDeletedShortName(
                            sector,
                            offset
                    );

            int highCluster =
                    readUInt16LE(
                            sector,
                            offset + 20
                    );

            int lowCluster =
                    readUInt16LE(
                            sector,
                            offset + 26
                    );

            long startCluster =
                    ((long) highCluster << 16)
                            |
                    (lowCluster & 0xFFFFL);

            long fileSize =
                    readUInt32LE(
                            sector,
                            offset + 28
                    );

            FAT32DirectoryEntry entry =
                    new FAT32DirectoryEntry(
                            fileName,
                            startCluster,
                            fileSize,
                            true,
                            attributes
                    );

            deletedFiles.add(
                    entry
            );
        }
    }

    private String parseDeletedShortName(
            byte[] data,
            int offset
    ) {

        /*
         * First character has been destroyed by FAT deletion,
         * so represent it with '?'.
         */

        String base =
                new String(
                        data,
                        offset + 1,
                        7,
                        StandardCharsets.US_ASCII
                ).trim();

        String extension =
                new String(
                        data,
                        offset + 8,
                        3,
                        StandardCharsets.US_ASCII
                ).trim();

        String name =
                "?" + base;

        if (!extension.isEmpty()) {
            name += "." + extension;
        }

        return name;
    }

    private int readUInt16LE(
            byte[] data,
            int offset
    ) {

        return (data[offset] & 0xFF)
                |
                ((data[offset + 1] & 0xFF) << 8);
    }

    private long readUInt32LE(
            byte[] data,
            int offset
    ) {

        return ((long) data[offset] & 0xFF)
                |
                (((long) data[offset + 1] & 0xFF) << 8)
                |
                (((long) data[offset + 2] & 0xFF) << 16)
                |
                (((long) data[offset + 3] & 0xFF) << 24);
    }
}
