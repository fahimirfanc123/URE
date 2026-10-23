package tests.core.partition;

import core.partition.FilesystemDetector;
import core.partition.FilesystemDetector.DetectedPartition;
import core.partition.FilesystemDetector.FilesystemType;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;

public class FilesystemDetectorTest {

    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        testValidFat32Partition();
        testValidNtfsPartition();
        testUnknownFilesystem();
        testCorruptedGptRejected();
        testInvalidPartitionTableRejected();

        System.out.println(
                "\nFilesystemDetectorTest: "
                        + passed + " checks passed."
        );
    }

    private static void testValidFat32Partition() throws Exception {
        Path image = createMbrImage(0x0B, 1, 50);
        try {
            byte[] boot = new byte[512];
            put16(boot, 11, 512);
            boot[13] = 1;
            put16(boot, 14, 1);
            boot[16] = 1;
            put32(boot, 32, 40);
            put32(boot, 36, 1);
            put32(boot, 44, 2);
            boot[510] = 0x55;
            boot[511] = (byte) 0xAA;
            writeSector(image, 1, boot);

            List<DetectedPartition> results =
                    FilesystemDetector.detect(image.toString());

            check(results.size() == 1, "FAT32: one partition found");
            check(results.get(0).getFilesystemType() == FilesystemType.FAT32,
                    "FAT32: filesystem detected");
            check(results.get(0).getStartLBA() == 1,
                    "FAT32: start LBA preserved");
            check(results.get(0).getSectorCount() == 50,
                    "FAT32: partition size preserved");
        } finally {
            Files.deleteIfExists(image);
        }
    }

    private static void testValidNtfsPartition() throws Exception {
        Path image = createMbrImage(0x07, 1, 50);
        try {
            byte[] boot = new byte[512];
            byte[] oem = "NTFS    ".getBytes(
                    java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(oem, 0, boot, 3, oem.length);
            put16(boot, 11, 512);
            boot[13] = 1;
            put64(boot, 40, 40);
            put64(boot, 48, 4);
            put64(boot, 56, 8);
            boot[64] = 1;
            boot[510] = 0x55;
            boot[511] = (byte) 0xAA;
            writeSector(image, 1, boot);

            List<DetectedPartition> results =
                    FilesystemDetector.detect(image.toString());

            check(results.size() == 1, "NTFS: one partition found");
            check(results.get(0).getFilesystemType() == FilesystemType.NTFS,
                    "NTFS: filesystem detected");
        } finally {
            Files.deleteIfExists(image);
        }
    }

    private static void testUnknownFilesystem() throws Exception {
        Path image = createMbrImage(0x83, 1, 50);
        try {
            List<DetectedPartition> results =
                    FilesystemDetector.detect(image.toString());

            check(results.size() == 1, "Unknown: partition retained");
            check(results.get(0).getFilesystemType() == FilesystemType.UNKNOWN,
                    "Unknown: unsupported filesystem reported");
        } finally {
            Files.deleteIfExists(image);
        }
    }

    private static void testCorruptedGptRejected() throws Exception {
        Path image = Files.createTempFile("ure-bad-gpt-", ".img");
        try {
            try (RandomAccessFile file =
                         new RandomAccessFile(image.toFile(), "rw")) {
                file.setLength(100L * 512L);

                byte[] header = new byte[512];
                byte[] signature = "EFI PART".getBytes(
                        java.nio.charset.StandardCharsets.US_ASCII);
                System.arraycopy(signature, 0, header, 0, 8);

                put32(header, 8, 0x00010000);
                put32(header, 12, 92);
                // Header CRC intentionally left invalid (zero).
                put64(header, 24, 1);
                put64(header, 32, 99);
                put64(header, 40, 34);
                put64(header, 48, 90);
                put64(header, 72, 2);
                put32(header, 80, 4);
                put32(header, 84, 128);

                file.seek(512);
                file.write(header);

                // Four empty entries: 4 * 128 bytes at LBA 2.
                byte[] emptyEntries = new byte[4 * 128];
                CRC32 crc = new CRC32();
                crc.update(emptyEntries);
                put32(header, 88, crc.getValue());

                file.seek(2L * 512L);
                file.write(emptyEntries);

                // Re-write the header with a valid entry-array CRC but
                // deliberately incorrect header CRC.
                file.seek(512);
                file.write(header);
            }

            boolean rejected = false;
            try {
                FilesystemDetector.detect(image.toString());
            } catch (IOException expected) {
                rejected = true;
            }

            check(rejected, "GPT with invalid header CRC is rejected");
        } finally {
            Files.deleteIfExists(image);
        }
    }

    private static void testInvalidPartitionTableRejected()
            throws Exception {
        Path image = Files.createTempFile("ure-no-table-", ".img");
        try {
            try (RandomAccessFile file =
                         new RandomAccessFile(image.toFile(), "rw")) {
                file.setLength(100L * 512L);
            }

            boolean rejected = false;
            try {
                FilesystemDetector.detect(image.toString());
            } catch (IOException expected) {
                rejected = true;
            }

            check(rejected, "Image without a partition table is rejected");
        } finally {
            Files.deleteIfExists(image);
        }
    }

    private static Path createMbrImage(
            int partitionType, int startLba, int sectorCount)
            throws Exception {
        Path image = Files.createTempFile("ure-detector-", ".img");

        try (RandomAccessFile file =
                     new RandomAccessFile(image.toFile(), "rw")) {
            file.setLength(100L * 512L);

            byte[] mbr = new byte[512];
            int entry = 446;
            mbr[entry + 4] = (byte) partitionType;
            put32(mbr, entry + 8, startLba);
            put32(mbr, entry + 12, sectorCount);
            mbr[510] = 0x55;
            mbr[511] = (byte) 0xAA;

            file.seek(0);
            file.write(mbr);
        }

        return image;
    }

    private static void writeSector(
            Path image, int sectorNumber, byte[] data) throws Exception {
        try (RandomAccessFile file =
                     new RandomAccessFile(image.toFile(), "rw")) {
            file.seek((long) sectorNumber * 512L);
            file.write(data);
        }
    }

    private static void put16(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }

    private static void put32(byte[] data, int offset, long value) {
        for (int i = 0; i < 4; i++) {
            data[offset + i] = (byte) (value >>> (8 * i));
        }
    }

    private static void put64(byte[] data, int offset, long value) {
        for (int i = 0; i < 8; i++) {
            data[offset + i] = (byte) (value >>> (8 * i));
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("FAIL: " + description);
        }

        passed++;
        System.out.println("PASS: " + description);
    }
}
