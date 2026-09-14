package filesystem.ntfs;

import core.disk.SectorReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class NTFSBootSector {

    private final int bytesPerSector;
    private final int sectorsPerCluster;
    private final long totalSectors;
    private final long mftCluster;
    private final long mftMirrorCluster;
    private final int clustersPerFileRecord;

    private NTFSBootSector(
            int bytesPerSector,
            int sectorsPerCluster,
            long totalSectors,
            long mftCluster,
            long mftMirrorCluster,
            int clustersPerFileRecord
    ) {
        this.bytesPerSector = bytesPerSector;
        this.sectorsPerCluster = sectorsPerCluster;
        this.totalSectors = totalSectors;
        this.mftCluster = mftCluster;
        this.mftMirrorCluster = mftMirrorCluster;
        this.clustersPerFileRecord = clustersPerFileRecord;
    }

    public static NTFSBootSector parse(SectorReader reader) throws IOException {

        if (reader == null) {
            throw new IllegalArgumentException(
                    "SectorReader cannot be null"
            );
        }

        byte[] boot = reader.readSector(0);

        if (boot.length < 512) {
            throw new IOException(
                    "Invalid NTFS boot sector"
            );
        }

        String oem =
                new String(
                        boot,
                        3,
                        8,
                        StandardCharsets.US_ASCII
                );

        if (!"NTFS    ".equals(oem)) {
            throw new IOException(
                    "Not an NTFS filesystem"
            );
        }

        if ((boot[510] & 0xFF) != 0x55 ||
            (boot[511] & 0xFF) != 0xAA) {

            throw new IOException(
                    "Invalid NTFS boot signature"
            );
        }

        int bytesPerSector =
                readUInt16LE(
                        boot,
                        11
                );

        int sectorsPerCluster =
                boot[13] & 0xFF;

        long totalSectors =
                readInt64LE(
                        boot,
                        40
                );

        long mftCluster =
                readInt64LE(
                        boot,
                        48
                );

        long mftMirrorCluster =
                readInt64LE(
                        boot,
                        56
                );

        int clustersPerFileRecord =
                boot[64];

        validate(
                bytesPerSector,
                sectorsPerCluster,
                totalSectors,
                mftCluster
        );

        return new NTFSBootSector(
                bytesPerSector,
                sectorsPerCluster,
                totalSectors,
                mftCluster,
                mftMirrorCluster,
                clustersPerFileRecord
        );
    }

    private static void validate(
            int bytesPerSector,
            int sectorsPerCluster,
            long totalSectors,
            long mftCluster
    ) throws IOException {

        if (bytesPerSector != 512 &&
            bytesPerSector != 1024 &&
            bytesPerSector != 2048 &&
            bytesPerSector != 4096) {

            throw new IOException(
                    "Invalid NTFS bytes-per-sector: "
                            + bytesPerSector
            );
        }

        if (sectorsPerCluster <= 0) {
            throw new IOException(
                    "Invalid NTFS sectors-per-cluster"
            );
        }

        if (totalSectors <= 0) {
            throw new IOException(
                    "Invalid NTFS total sectors"
            );
        }

        if (mftCluster < 0) {
            throw new IOException(
                    "Invalid NTFS MFT cluster"
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

    private static long readInt64LE(
            byte[] data,
            int offset
    ) {

        long value = 0;

        for (int i = 0; i < 8; i++) {
            value |=
                    ((long) data[offset + i] & 0xFF)
                            << (8 * i);
        }

        return value;
    }

    public int getBytesPerSector() {
        return bytesPerSector;
    }

    public int getSectorsPerCluster() {
        return sectorsPerCluster;
    }

    public long getBytesPerCluster() {
        return (long) bytesPerSector
                * sectorsPerCluster;
    }

    public long getTotalSectors() {
        return totalSectors;
    }

    public long getMftCluster() {
        return mftCluster;
    }

    public long getMftMirrorCluster() {
        return mftMirrorCluster;
    }

    public int getClustersPerFileRecord() {
        return clustersPerFileRecord;
    }

    public long getMftStartSector() {
        return mftCluster
                * sectorsPerCluster;
    }

    @Override
    public String toString() {

        return "NTFSBootSector{" +
                "bytesPerSector=" + bytesPerSector +
                ", sectorsPerCluster=" + sectorsPerCluster +
                ", totalSectors=" + totalSectors +
                ", mftCluster=" + mftCluster +
                ", mftMirrorCluster=" + mftMirrorCluster +
                ", mftStartSector=" + getMftStartSector() +
                '}';
    }
}
