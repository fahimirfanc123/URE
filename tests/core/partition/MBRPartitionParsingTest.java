package tests.core.partition;

import core.disk.SectorReader;
import core.partition.MBRParser;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MBRPartitionParsingTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        testSinglePartition();
        testMultiplePartitions();
        testNoPartitions();
        testOutOfBoundsPartition();
        testInvalidSignature();
        testReturnedListIsUnmodifiable();

        System.out.println("\n=== MBR Partition Results: "
                + passed + " passed, " + failed + " failed ===");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testSinglePartition() throws Exception {
        Path image = createImage(100);
        try {
            writePartition(image, 0, 0x0B, 10, 20);

            try (SectorReader reader = new SectorReader(image.toString())) {
                List<MBRParser.PartitionEntry> partitions =
                        new MBRParser(reader).readPartitions();

                check("Single partition is parsed", partitions.size() == 1);

                if (partitions.size() == 1) {
                    MBRParser.PartitionEntry p = partitions.get(0);
                    check("Partition number is correct",
                            p.getPartitionNumber() == 1);
                    check("Partition type is correct",
                            p.getPartitionType() == 0x0B);
                    check("Partition start LBA is correct",
                            p.getStartLBA() == 10);
                    check("Partition sector count is correct",
                            p.getSectorCount() == 20);
                }
            }
        } finally {
            Files.deleteIfExists(image);
        }
    }

    private static void testMultiplePartitions() throws Exception {
        Path image = createImage(200);
        try {
            writePartition(image, 0, 0x0B, 10, 20);
            writePartition(image, 1, 0x07, 40, 30);

            try (SectorReader reader = new SectorReader(image.toString())) {
                List<MBRParser.PartitionEntry> partitions =
                        new MBRParser(reader).readPartitions();

                check("Multiple partitions are parsed",
                        partitions.size() == 2);

                if (partitions.size() == 2) {
                    check("Second partition number is correct",
                            partitions.get(1).getPartitionNumber() == 2);
                    check("Second partition type is correct",
                            partitions.get(1).getPartitionType() == 0x07);
                    check("Second partition start is correct",
                            partitions.get(1).getStartLBA() == 40);
                }
            }
        } finally {
            Files.deleteIfExists(image);
        }
    }

    private static void testNoPartitions() throws Exception {
        Path image = createImage(100);
        try {
            try (SectorReader reader = new SectorReader(image.toString())) {
                List<MBRParser.PartitionEntry> partitions =
                        new MBRParser(reader).readPartitions();

                check("Valid MBR with no partitions returns empty list",
                        partitions.isEmpty());
            }
        } finally {
            Files.deleteIfExists(image);
        }
    }

    private static void testOutOfBoundsPartition() throws Exception {
        Path image = createImage(100);
        try {
            // Starts at LBA 90 and claims 20 sectors; image ends at LBA 99.
            writePartition(image, 0, 0x0B, 90, 20);

            try (SectorReader reader = new SectorReader(image.toString())) {
                expectIOException("Out-of-bounds partition is rejected",
                        () -> new MBRParser(reader).readPartitions());
            }
        } finally {
            Files.deleteIfExists(image);
        }
    }

    private static void testInvalidSignature() throws Exception {
        Path image = createImage(100);
        try {
            // Override the valid signature created by createImage().
            try (RandomAccessFile file =
                         new RandomAccessFile(image.toFile(), "rw")) {
                file.seek(510);
                file.write(0x00);
                file.write(0x00);
            }

            try (SectorReader reader = new SectorReader(image.toString())) {
                expectIOException("Invalid MBR signature is rejected",
                        () -> new MBRParser(reader).readPartitions());
            }
        } finally {
            Files.deleteIfExists(image);
        }
    }

    private static void testReturnedListIsUnmodifiable() throws Exception {
        Path image = createImage(100);
        try {
            writePartition(image, 0, 0x0B, 10, 20);

            try (SectorReader reader = new SectorReader(image.toString())) {
                List<MBRParser.PartitionEntry> partitions =
                        new MBRParser(reader).readPartitions();

                try {
                    partitions.clear();
                    check("Partition list is unmodifiable", false);
                } catch (UnsupportedOperationException expected) {
                    check("Partition list is unmodifiable", true);
                }
            }
        } finally {
            Files.deleteIfExists(image);
        }
    }

    private static Path createImage(int sectors) throws IOException {
        Path image = Files.createTempFile("mbr-partition-test-", ".img");

        try (RandomAccessFile file =
                     new RandomAccessFile(image.toFile(), "rw")) {
            file.setLength((long) sectors * 512);

            // Standard MBR signature.
            file.seek(510);
            file.write(0x55);
            file.write(0xAA);
        }

        return image;
    }

    private static void writePartition(
            Path image,
            int index,
            int type,
            long startLBA,
            long sectorCount
    ) throws IOException {
        try (RandomAccessFile file =
                     new RandomAccessFile(image.toFile(), "rw")) {
            long entryOffset = 446L + index * 16L;

            // Partition type is at byte 4 of the 16-byte entry.
            file.seek(entryOffset + 4);
            file.write(type);

            // Start LBA and sector count are at bytes 8 and 12.
            file.seek(entryOffset + 8);
            writeUInt32LE(file, startLBA);
            writeUInt32LE(file, sectorCount);
        }
    }

    private static void writeUInt32LE(
            RandomAccessFile file, long value
    ) throws IOException {
        for (int i = 0; i < 4; i++) {
            file.write((int) (value & 0xFF));
            value >>>= 8;
        }
    }

    private static void expectIOException(
            String name, ThrowingAction action
    ) throws Exception {
        try {
            action.run();
            check(name, false);
        } catch (IOException expected) {
            check(name, true);
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("[PASS] " + name);
            passed++;
        } else {
            System.out.println("[FAIL] " + name);
            failed++;
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
