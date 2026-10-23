package tests.core.disk;

import core.disk.SectorReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class SectorReaderBoundedTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        Path image = Files.createTempFile("ure-bounded-test-", ".img");

        try {
            byte[] data = new byte[4096];

            Arrays.fill(data, 0, 512, (byte) 0x11);
            Arrays.fill(data, 512, 1024, (byte) 0x22);
            Arrays.fill(data, 1024, 1536, (byte) 0x33);
            Arrays.fill(data, 1536, 2048, (byte) 0x44);
            Arrays.fill(data, 2048, 2560, (byte) 0x55);
            Arrays.fill(data, 2560, 3072, (byte) 0x66);
            Arrays.fill(data, 3072, 3584, (byte) 0x77);
            Arrays.fill(data, 3584, 4096, (byte) 0x08);

            Files.write(image, data);

            testPartitionView(image);
            testCannotReadPastPartition(image);
            testRejectsInvalidRegions(image);

            System.out.println(
                    "\n=== Results: " + passed
                            + " passed, " + failed + " failed ==="
            );

            if (failed > 0) {
                throw new AssertionError("Bounded-reader tests failed");
            }
        } finally {
            Files.deleteIfExists(image);
        }
    }

    private static void testPartitionView(Path image) throws Exception {
        try (SectorReader reader =
                     new SectorReader(image.toString(), 512, 1024, 1024)) {

            check("Partition base offset is correct",
                    reader.getBaseOffsetBytes() == 1024);

            check("Visible length is partition length",
                    reader.getTotalBytes() == 1024);

            check("Visible sector count is bounded",
                    reader.getTotalSectors() == 2);

            check("First relative sector reads partition start",
                    (reader.readSector(0)[0] & 0xFF) == 0x33);

            check("Last relative sector stays inside partition",
                    (reader.readSector(1)[0] & 0xFF) == 0x44);

            check("Multi-sector read covers only partition",
                    reader.readSectors(0, 2).length == 1024);
        }
    }

    private static void testCannotReadPastPartition(Path image)
            throws Exception {

        try (SectorReader reader =
                     new SectorReader(image.toString(), 512, 1024, 1024)) {

            try {
                reader.readSector(2);
                check("Read after partition boundary is rejected", false);
            } catch (IllegalArgumentException expected) {
                check("Read after partition boundary is rejected", true);
            }

            try {
                reader.readSectors(1, 2);
                check("Multi-sector read past boundary is rejected", false);
            } catch (IllegalArgumentException expected) {
                check("Multi-sector read past boundary is rejected", true);
            }
        }
    }

    
private static void testRejectsInvalidRegions(Path image)
        throws Exception {

    expectInvalid(image, 512, 4096,
            "Offset at end with nonempty region");

    expectInvalid(image, 512, 3584,
            "Region extending past image", 1024);

    expectInvalid(image, 512, 0,
            "Zero-length region", 0);

    expectInvalid(image, 512, -512,
            "Negative offset");

    expectInvalid(image, 512, 512,
            "Non-sector-aligned length", 513);
}


    private static void expectInvalid(
            Path image,
            int sectorSize,
            long offset,
            String description
    ) throws Exception {
        expectInvalid(image, sectorSize, offset, description, 512);
    }

    private static void expectInvalid(
            Path image,
            int sectorSize,
            long offset,
            String description,
            long length
    ) throws Exception {
        try (SectorReader ignored =
                     new SectorReader(
                             image.toString(),
                             sectorSize,
                             offset,
                             length
                     )) {
            check(description + " is rejected", false);
        } catch (IllegalArgumentException expected) {
            check(description + " is rejected", true);
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + name);
            passed++;
        } else {
            System.out.println("  [FAIL] " + name);
            failed++;
        }
    }
}
