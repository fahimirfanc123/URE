package core.partition;

import core.disk.SectorReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * GPTParser reads and validates the GPT header located at LBA 1.
 *
 * GPT disks contain a header whose first 8 bytes contain
 * the signature "EFI PART".
 */
public class GPTParser {

    private static final long GPT_HEADER_LBA = 1;
    private static final String GPT_SIGNATURE = "EFI PART";

    private final SectorReader sectorReader;

    /**
     * Creates a GPT parser using an existing SectorReader.
     *
     * @param sectorReader sector reader used to read raw disk sectors
     */
    public GPTParser(SectorReader sectorReader) {
        if (sectorReader == null) {
            throw new IllegalArgumentException("SectorReader cannot be null");
        }

        this.sectorReader = sectorReader;
    }

    /**
     * Checks whether the disk contains a valid GPT signature.
     *
     * GPT header starts at LBA 1.
     * Bytes 0-7 should contain "EFI PART".
     *
     * @return true if GPT signature exists
     */
    public boolean isValidGPT() throws IOException {

        byte[] sector = sectorReader.readSector(GPT_HEADER_LBA);

        if (sector.length < 92) {
            return false;
        }

        String signature =
                new String(sector, 0, 8, StandardCharsets.US_ASCII);

        return GPT_SIGNATURE.equals(signature);
    }

    /**
     * Reads important information from the GPT header.
     *
     * @return parsed GPTHeader object
     */
    public GPTHeader readHeader() throws IOException {

        byte[] sector = sectorReader.readSector(GPT_HEADER_LBA);

        if (sector.length < 92) {
            throw new IOException("GPT header sector is too small");
        }

        String signature =
                new String(sector, 0, 8, StandardCharsets.US_ASCII);

        if (!GPT_SIGNATURE.equals(signature)) {
            throw new IOException("Invalid GPT signature");
        }

        long revision = readUnsignedIntLE(sector, 8);
        long headerSize = readUnsignedIntLE(sector, 12);

        long currentLBA = readLongLE(sector, 24);
        long backupLBA = readLongLE(sector, 32);

        long firstUsableLBA = readLongLE(sector, 40);
        long lastUsableLBA = readLongLE(sector, 48);

        long partitionEntryStartLBA = readLongLE(sector, 72);

        long numberOfPartitionEntries =
                readUnsignedIntLE(sector, 80);

        long sizeOfPartitionEntry =
                readUnsignedIntLE(sector, 84);

        return new GPTHeader(
                revision,
                headerSize,
                currentLBA,
                backupLBA,
                firstUsableLBA,
                lastUsableLBA,
                partitionEntryStartLBA,
                numberOfPartitionEntries,
                sizeOfPartitionEntry
        );
    }

    /**
     * Reads a 32-bit little-endian unsigned integer.
     */
    private long readUnsignedIntLE(byte[] data, int offset) {

        return ((long) data[offset] & 0xFF)
                | (((long) data[offset + 1] & 0xFF) << 8)
                | (((long) data[offset + 2] & 0xFF) << 16)
                | (((long) data[offset + 3] & 0xFF) << 24);
    }

    /**
     * Reads a 64-bit little-endian value.
     */
    private long readLongLE(byte[] data, int offset) {

        return ((long) data[offset] & 0xFF)
                | (((long) data[offset + 1] & 0xFF) << 8)
                | (((long) data[offset + 2] & 0xFF) << 16)
                | (((long) data[offset + 3] & 0xFF) << 24)
                | (((long) data[offset + 4] & 0xFF) << 32)
                | (((long) data[offset + 5] & 0xFF) << 40)
                | (((long) data[offset + 6] & 0xFF) << 48)
                | (((long) data[offset + 7] & 0xFF) << 56);
    }

    /**
     * Stores important GPT header information.
     */
    public static class GPTHeader {

        private final long revision;
        private final long headerSize;
        private final long currentLBA;
        private final long backupLBA;
        private final long firstUsableLBA;
        private final long lastUsableLBA;
        private final long partitionEntryStartLBA;
        private final long numberOfPartitionEntries;
        private final long sizeOfPartitionEntry;

        public GPTHeader(
                long revision,
                long headerSize,
                long currentLBA,
                long backupLBA,
                long firstUsableLBA,
                long lastUsableLBA,
                long partitionEntryStartLBA,
                long numberOfPartitionEntries,
                long sizeOfPartitionEntry
        ) {

            this.revision = revision;
            this.headerSize = headerSize;
            this.currentLBA = currentLBA;
            this.backupLBA = backupLBA;
            this.firstUsableLBA = firstUsableLBA;
            this.lastUsableLBA = lastUsableLBA;
            this.partitionEntryStartLBA = partitionEntryStartLBA;
            this.numberOfPartitionEntries = numberOfPartitionEntries;
            this.sizeOfPartitionEntry = sizeOfPartitionEntry;
        }

        public long getRevision() {
            return revision;
        }

        public long getHeaderSize() {
            return headerSize;
        }

        public long getCurrentLBA() {
            return currentLBA;
        }

        public long getBackupLBA() {
            return backupLBA;
        }

        public long getFirstUsableLBA() {
            return firstUsableLBA;
        }

        public long getLastUsableLBA() {
            return lastUsableLBA;
        }

        public long getPartitionEntryStartLBA() {
            return partitionEntryStartLBA;
        }

        public long getNumberOfPartitionEntries() {
            return numberOfPartitionEntries;
        }

        public long getSizeOfPartitionEntry() {
            return sizeOfPartitionEntry;
        }
    }
}
