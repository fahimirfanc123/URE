package filesystem.fat32;

import core.disk.SectorReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Recovers deleted FAT32 files from their original data clusters.
 *
 * Initial implementation supports contiguous deleted files.
 */
public class FAT32FileRecovery {

    private final SectorReader reader;
    private final FAT32BootSector bootSector;

    public FAT32FileRecovery(
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

    public byte[] recover(
            FAT32DirectoryEntry entry
    ) throws IOException {

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

        long fileSize =
                entry.getFileSize();

        if (fileSize == 0) {
            return new byte[0];
        }

        long startCluster =
                entry.getStartCluster();

        if (startCluster < 2) {
            throw new IOException(
                    "Invalid FAT32 starting cluster: "
                            + startCluster
            );
        }

        int bytesPerSector =
                bootSector.getBytesPerSector();

        int sectorsPerCluster =
                bootSector.getSectorsPerCluster();

        long bytesPerCluster =
                (long) bytesPerSector
                        * sectorsPerCluster;

        long clustersRequired =
                (fileSize + bytesPerCluster - 1)
                        / bytesPerCluster;

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        long remaining =
                fileSize;

        /*
         * Deleted FAT chains may have been cleared.
         *
         * For this first recovery implementation we assume
         * the deleted file was stored contiguously.
         */
        for (long i = 0;
             i < clustersRequired && remaining > 0;
             i++) {

            long cluster =
                    startCluster + i;

            long firstSector =
                    bootSector.clusterToSector(
                            cluster
                    );

            for (int sectorOffset = 0;
                 sectorOffset < sectorsPerCluster
                         && remaining > 0;
                 sectorOffset++) {

                byte[] sector =
                        reader.readSector(
                                firstSector
                                        + sectorOffset
                        );

                int bytesToWrite =
                        (int) Math.min(
                                remaining,
                                sector.length
                        );

                output.write(
                        sector,
                        0,
                        bytesToWrite
                );

                remaining -=
                        bytesToWrite;
            }
        }

        if (remaining != 0) {
            throw new IOException(
                    "Could not recover complete file"
            );
        }

        return output.toByteArray();
    }
}
