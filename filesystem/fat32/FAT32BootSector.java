package filesystem.fat32;

import core.disk.SectorReader;

import java.io.IOException;

/**
 * Parses important FAT32 boot-sector information.
 */
public class FAT32BootSector {

    private final int bytesPerSector;
    private final int sectorsPerCluster;
    private final int reservedSectorCount;
    private final int numberOfFATs;
    private final long totalSectors;
    private final long fatSize;
    private final long rootCluster;

    private FAT32BootSector(
            int bytesPerSector,
            int sectorsPerCluster,
            int reservedSectorCount,
            int numberOfFATs,
            long totalSectors,
            long fatSize,
            long rootCluster
    ) {
        this.bytesPerSector = bytesPerSector;
        this.sectorsPerCluster = sectorsPerCluster;
        this.reservedSectorCount = reservedSectorCount;
        this.numberOfFATs = numberOfFATs;
        this.totalSectors = totalSectors;
        this.fatSize = fatSize;
        this.rootCluster = rootCluster;
    }

    public static FAT32BootSector parse(SectorReader reader)
            throws IOException {

        if (reader == null) {
            throw new IllegalArgumentException(
                    "SectorReader cannot be null"
            );
        }

        byte[] boot = reader.readSector(0);

        if (boot.length < 512) {
            throw new IOException(
                    "Invalid FAT32 boot sector"
            );
        }

        // Standard boot-sector signature: 0x55 0xAA
        if ((boot[510] & 0xFF) != 0x55 ||
            (boot[511] & 0xFF) != 0xAA) {

            throw new IOException(
                    "Invalid boot sector signature"
            );
        }

        int bytesPerSector =
                readUInt16LE(boot, 11);

        int sectorsPerCluster =
                boot[13] & 0xFF;

        int reservedSectorCount =
                readUInt16LE(boot, 14);

        int numberOfFATs =
                boot[16] & 0xFF;

        int totalSectors16 =
                readUInt16LE(boot, 19);

        long totalSectors32 =
                readUInt32LE(boot, 32);

        long totalSectors =
                totalSectors16 != 0
                        ? totalSectors16
                        : totalSectors32;

        int fatSize16 =
                readUInt16LE(boot, 22);

        long fatSize32 =
                readUInt32LE(boot, 36);

        long fatSize =
                fatSize16 != 0
                        ? fatSize16
                        : fatSize32;

        long rootCluster =
                readUInt32LE(boot, 44);

        validate(
                bytesPerSector,
                sectorsPerCluster,
                reservedSectorCount,
                numberOfFATs,
                totalSectors,
                fatSize,
                rootCluster
        );

        return new FAT32BootSector(
                bytesPerSector,
                sectorsPerCluster,
                reservedSectorCount,
                numberOfFATs,
                totalSectors,
                fatSize,
                rootCluster
        );
    }

    private static void validate(
            int bytesPerSector,
            int sectorsPerCluster,
            int reservedSectorCount,
            int numberOfFATs,
            long totalSectors,
            long fatSize,
            long rootCluster
    ) throws IOException {

        if (bytesPerSector != 512 &&
            bytesPerSector != 1024 &&
            bytesPerSector != 2048 &&
            bytesPerSector != 4096) {

            throw new IOException(
                    "Invalid FAT bytes-per-sector value: "
                            + bytesPerSector
            );
        }

        if (sectorsPerCluster == 0 ||
            (sectorsPerCluster &
                    (sectorsPerCluster - 1)) != 0) {

            throw new IOException(
                    "Invalid sectors-per-cluster value"
            );
        }

        if (reservedSectorCount <= 0) {
            throw new IOException(
                    "Invalid reserved-sector count"
            );
        }

        if (numberOfFATs <= 0) {
            throw new IOException(
                    "Invalid FAT count"
            );
        }

        if (totalSectors <= 0) {
            throw new IOException(
                    "Invalid total-sector count"
            );
        }

        if (fatSize <= 0) {
            throw new IOException(
                    "Invalid FAT size"
            );
        }

        if (rootCluster < 2) {
            throw new IOException(
                    "Invalid FAT32 root cluster"
            );
        }
    }

    private static int readUInt16LE(
            byte[] data,
            int offset
    ) {

        return (data[offset] & 0xFF)
                |
                ((data[offset + 1] & 0xFF) << 8);
    }

    private static long readUInt32LE(
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

    public int getBytesPerSector() {
        return bytesPerSector;
    }

    public int getSectorsPerCluster() {
        return sectorsPerCluster;
    }

    public int getReservedSectorCount() {
        return reservedSectorCount;
    }

    public int getNumberOfFATs() {
        return numberOfFATs;
    }

    public long getTotalSectors() {
        return totalSectors;
    }

    public long getFatSize() {
        return fatSize;
    }

    public long getRootCluster() {
        return rootCluster;
    }

    public long getFirstDataSector() {

        return reservedSectorCount
                +
                ((long) numberOfFATs * fatSize);
    }

    public long clusterToSector(long cluster) {

        if (cluster < 2) {
            throw new IllegalArgumentException(
                    "Cluster must be >= 2"
            );
        }

        return getFirstDataSector()
                +
                ((cluster - 2)
                        * sectorsPerCluster);
    }

    @Override
    public String toString() {

        return "FAT32BootSector{" +
                "bytesPerSector=" + bytesPerSector +
                ", sectorsPerCluster=" + sectorsPerCluster +
                ", reservedSectorCount=" + reservedSectorCount +
                ", numberOfFATs=" + numberOfFATs +
                ", totalSectors=" + totalSectors +
                ", fatSize=" + fatSize +
                ", rootCluster=" + rootCluster +
                ", firstDataSector=" + getFirstDataSector() +
                '}';
    }
}
