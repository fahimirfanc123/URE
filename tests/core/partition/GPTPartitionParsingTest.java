package tests.core.partition;

import core.disk.SectorReader;
import core.partition.GPTParser;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

public class GPTPartitionParsingTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        File image = File.createTempFile("gpt-partitions-", ".img");

        try {
            createImage(image);
            testPartitions(image);
            testCorruptedPartitionArray(image);
        } finally {
            if (!image.delete()) {
                System.err.println(
                        "Warning: could not delete temporary image: "
                                + image.getAbsolutePath());
            }
        }

        System.out.println("\nPassed: " + passed);
        System.out.println("Failed: " + failed);

        if (failed > 0) {
            throw new AssertionError("GPT partition tests failed");
        }
    }

    private static void testPartitions(File image) throws Exception {
        try (SectorReader reader = new SectorReader(image.getPath())) {
            GPTParser parser = new GPTParser(reader);

            check(parser.validateIntegrity(),
        "GPT header and partition-array CRCs are valid");

            List<GPTParser.GPTPartitionEntry> entries =
                    parser.readPartitions();

            check(entries.size() == 2, "Two used partitions detected");

            GPTParser.GPTPartitionEntry first = entries.get(0);
            check(first.getPartitionNumber() == 1,
                    "First partition number is 1");
            check(first.getFirstLBA() == 34,
                    "First partition starting LBA parsed");
            check(first.getLastLBA() == 49,
                    "First partition ending LBA parsed");
            check(first.getSectorCount() == 16,
                    "First partition sector count calculated");
            check("System".equals(first.getName()),
                    "First partition name parsed");

            GPTParser.GPTPartitionEntry second = entries.get(1);
            check(second.getPartitionNumber() == 3,
                    "Unused entry is skipped without renumbering entries");
            check(second.getFirstLBA() == 60,
                    "Second partition starting LBA parsed");
            check(second.getLastLBA() == 79,
                    "Second partition ending LBA parsed");
            check(second.getSectorCount() == 20,
                    "Second partition sector count calculated");
            check("Data".equals(second.getName()),
                    "Second partition name parsed");

            check(
                    "c12a7328-f81f-11d2-ba4b-00a0c93ec93b"
                            .equals(first.getTypeGuid()),
                    "EFI System Partition type GUID decoded");

            boolean immutable = false;
            try {
                entries.clear();
            } catch (UnsupportedOperationException expected) {
                immutable = true;
            }
            check(immutable, "Partition list is immutable");
        }
    }

    private static void testCorruptedPartitionArray(File image)
        throws Exception {

    // Corrupt a partition-array byte without updating either CRC.
    try (RandomAccessFile raf = new RandomAccessFile(image, "rw")) {
        raf.seek(2L * 512 + 5);
        int original = raf.readUnsignedByte();

        raf.seek(2L * 512 + 5);
        raf.writeByte(original ^ 0x01);
    }

    try (SectorReader reader = new SectorReader(image.getPath())) {
        GPTParser parser = new GPTParser(reader);

        check(
                !parser.validateIntegrity(),
                "Corrupted partition array fails CRC validation"
        );
    }
}

    private static void createImage(File image) throws Exception {
        final int sectorSize = 512;

        try (RandomAccessFile raf = new RandomAccessFile(image, "rw")) {
            raf.setLength(100L * sectorSize);

            byte[] header = new byte[sectorSize];
            System.arraycopy(
                    "EFI PART".getBytes(StandardCharsets.US_ASCII),
                    0, header, 0, 8);

            writeIntLE(header, 8, 0x00010000);
            writeIntLE(header, 12, 92);
            writeLongLE(header, 24, 1);
            writeLongLE(header, 32, 99);
            writeLongLE(header, 40, 34);
            writeLongLE(header, 48, 98);
            writeLongLE(header, 72, 2);
            writeIntLE(header, 80, 4);
            writeIntLE(header, 84, 128);

            raf.seek(sectorSize);
            raf.write(header);

            // Entry 1: EFI System Partition, LBA 34–49.
            byte[] efiType = guidBytes(
                    "c12a7328-f81f-11d2-ba4b-00a0c93ec93b");
            writeEntry(raf, 2L * sectorSize, efiType,
                    guidBytes("11111111-2222-3333-4444-555555555555"),
                    34, 49, "System");

            // Entry 2 is intentionally unused (all-zero type GUID).

            // Entry 3: Data partition, LBA 60–79.
            writeEntry(raf, 2L * sectorSize + 2L * 128,
                    guidBytes("ebd0a0a2-b9e5-4433-87c0-68b6b72699c7"),
                    guidBytes("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                    60, 79, "Data");

            // Update the partition-array CRC and then the header CRC.
            byte[] partitionArray = new byte[4 * 128];
            raf.seek(2L * sectorSize);
            raf.readFully(partitionArray);

            CRC32 arrayCrc = new CRC32();
            arrayCrc.update(partitionArray);

            byte[] updatedHeader = new byte[sectorSize];
            raf.seek(sectorSize);
            raf.readFully(updatedHeader);

            writeIntLE(updatedHeader, 88, arrayCrc.getValue());
            writeIntLE(updatedHeader, 16, 0);

            CRC32 headerCrc = new CRC32();
            headerCrc.update(updatedHeader, 0, 92);
            writeIntLE(updatedHeader, 16, headerCrc.getValue());

            raf.seek(sectorSize);
            raf.write(updatedHeader);
        }
    }

    private static void writeEntry(
            RandomAccessFile raf,
            long offset,
            byte[] typeGuid,
            byte[] uniqueGuid,
            long firstLBA,
            long lastLBA,
            String name) throws Exception {

        byte[] entry = new byte[128];
        System.arraycopy(typeGuid, 0, entry, 0, 16);
        System.arraycopy(uniqueGuid, 0, entry, 16, 16);
        writeLongLE(entry, 32, firstLBA);
        writeLongLE(entry, 40, lastLBA);
        writeLongLE(entry, 48, 0);

        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_16LE);
        System.arraycopy(nameBytes, 0, entry, 56,
                Math.min(nameBytes.length, 72));

        raf.seek(offset);
        raf.write(entry);
    }

    private static byte[] guidBytes(String guid) {
        String hex = guid.replace("-", "");
        byte[] standard = new byte[16];

        for (int i = 0; i < 16; i++) {
            standard[i] = (byte) Integer.parseInt(
                    hex.substring(i * 2, i * 2 + 2), 16);
        }

        // GPT stores the first 4-, 2-, and 2-byte GUID fields little-endian.
        byte[] disk = standard.clone();
        disk[0] = standard[3];
        disk[1] = standard[2];
        disk[2] = standard[1];
        disk[3] = standard[0];
        disk[4] = standard[5];
        disk[5] = standard[4];
        disk[6] = standard[7];
        disk[7] = standard[6];

        return disk;
    }

    private static void writeIntLE(byte[] data, int offset, long value) {
        for (int i = 0; i < 4; i++) {
            data[offset + i] = (byte) ((value >>> (8 * i)) & 0xFF);
        }
    }

    private static void writeLongLE(byte[] data, int offset, long value) {
        for (int i = 0; i < 8; i++) {
            data[offset + i] = (byte) ((value >>> (8 * i)) & 0xFF);
        }
    }

    private static void check(boolean condition, String message) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + message);
        } else {
            failed++;
            System.out.println("[FAIL] " + message);
        }
    }
}
