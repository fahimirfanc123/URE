package core.partition;

import core.disk.SectorReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validates MBR signatures and parses primary partition entries.
 */
public class MBRParser {

    private static final int MBR_SIGNATURE_OFFSET = 510;
    private static final int PARTITION_TABLE_OFFSET = 446;
    private static final int PARTITION_ENTRY_SIZE = 16;
    private static final int PARTITION_ENTRY_COUNT = 4;

    private final SectorReader sectorReader;

    public MBRParser(SectorReader sectorReader) {
        if (sectorReader == null) {
            throw new IllegalArgumentException(
                    "SectorReader cannot be null"
            );
        }

        this.sectorReader = sectorReader;
    }

    /**
     * Checks for the standard MBR signature at bytes 510-511.
     */
    public boolean hasValidSignature() throws IOException {
        if (sectorReader.getTotalSectors() < 1
                || sectorReader.getSectorSize() < 512) {
            return false;
        }

        byte[] sector = sectorReader.readSector(0);

        return (sector[510] & 0xFF) == 0x55
                && (sector[511] & 0xFF) == 0xAA;
    }

    /**
     * Parses non-empty primary MBR partition entries.
     *
     * Entries with type 0 or size 0 are skipped. Any non-empty entry
     * extending beyond the visible image is rejected.
     */
    public List<PartitionEntry> readPartitions() throws IOException {
        if (!hasValidSignature()) {
            throw new IOException("Invalid MBR signature");
        }

        byte[] sector = sectorReader.readSector(0);
        List<PartitionEntry> partitions = new ArrayList<>();

        for (int i = 0; i < PARTITION_ENTRY_COUNT; i++) {
            int offset = PARTITION_TABLE_OFFSET
                    + i * PARTITION_ENTRY_SIZE;

            int partitionType = sector[offset + 4] & 0xFF;
            long startLBA = readUnsignedIntLE(sector, offset + 8);
            long sectorCount = readUnsignedIntLE(sector, offset + 12);

            if (partitionType == 0 || sectorCount == 0) {
                continue;
            }

            if (startLBA == 0
                    || startLBA >= sectorReader.getTotalSectors()
                    || sectorCount > sectorReader.getTotalSectors()
                            - startLBA) {
                throw new IOException(
                        "Invalid MBR partition range in entry "
                                + (i + 1)
                );
            }

            partitions.add(new PartitionEntry(
                    i + 1,
                    partitionType,
                    startLBA,
                    sectorCount
            ));
        }

        return Collections.unmodifiableList(partitions);
    }

    private static long readUnsignedIntLE(byte[] data, int offset) {
        return ((long) data[offset] & 0xFF)
                | (((long) data[offset + 1] & 0xFF) << 8)
                | (((long) data[offset + 2] & 0xFF) << 16)
                | (((long) data[offset + 3] & 0xFF) << 24);
    }

    /**
     * Immutable metadata for a primary MBR partition.
     */
    public static final class PartitionEntry {

        private final int partitionNumber;
        private final int partitionType;
        private final long startLBA;
        private final long sectorCount;

        public PartitionEntry(
                int partitionNumber,
                int partitionType,
                long startLBA,
                long sectorCount
        ) {
            this.partitionNumber = partitionNumber;
            this.partitionType = partitionType;
            this.startLBA = startLBA;
            this.sectorCount = sectorCount;
        }

        public int getPartitionNumber() {
            return partitionNumber;
        }

        public int getPartitionType() {
            return partitionType;
        }

        public long getStartLBA() {
            return startLBA;
        }

        public long getSectorCount() {
            return sectorCount;
        }

        @Override
        public String toString() {
            return "PartitionEntry{"
                    + "partitionNumber=" + partitionNumber
                    + ", partitionType=0x"
                    + Integer.toHexString(partitionType)
                    + ", startLBA=" + startLBA
                    + ", sectorCount=" + sectorCount
                    + '}';
        }
    }
}
