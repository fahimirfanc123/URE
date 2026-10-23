package tests.core.disk;

import core.disk.SectorReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class SectorReaderOffsetTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        Path image = Files.createTempFile("ure-offset-test-", ".img");

        try {
            byte[] data = new byte[2048];

            // Four sectors with distinct byte patterns.
            Arrays.fill(data, 0, 512, (byte) 0x11);
            Arrays.fill(data, 512, 1024, (byte) 0x22);
            Arrays.fill(data, 1024, 1536, (byte) 0x33);
            Arrays.fill(data, 1536, 2048, (byte) 0x44);

            Files.write(image, data);

            testOffsetRead(image);
            testMetadata(image);
            testRelativeBounds(image);
            testInvalidOffset(image);

            System.out.println(
                    "\n=== Results: " + passed
                            + " passed, " + failed + " failed ==="
            );

            if (failed > 0) {
                throw new AssertionError("Offset tests failed");
            }
        } finally {
            Files.deleteIfExists(image);
        }
    }

    private static void testOffsetRead(Path image) throws Exception {
        try (SectorReader reader =
                     new SectorReader(image.toString(), 512, 512)) {

            byte[] sector = reader.readSector(0);

            check("Sector 0 starts at requested offset",
                    (sector[0] & 0xFF) == 0x22);

            byte[] next = reader.readSector(1);

            check("Sector 1 reads the next relative sector",
                    (next[0] & 0xFF) == 0x33);

            byte[] multiple = reader.readSectors(0, 2);

            check("Multi-sector read respects offset",
                    (multiple[0] & 0xFF) == 0x22
                            && (multiple[512] & 0xFF) == 0x33);
        }
    }

    private static void testMetadata(Path image) throws Exception {
        try (SectorReader reader =
                     new SectorReader(image.toString(), 512, 512)) {

            check("Base offset is retained",
                    reader.getBaseOffsetBytes() == 512);

            check("Visible byte count excludes preceding bytes",
                    reader.getTotalBytes() == 1536);

            check("Visible sector count is relative to offset",
                    reader.getTotalSectors() == 3);
        }
    }

    private static void testRelativeBounds(Path image) throws Exception {
        try (SectorReader reader =
                     new SectorReader(image.toString(), 512, 512)) {

            try {
                reader.readSector(3);
                check("Relative out-of-bounds read is rejected", false);
            } catch (IllegalArgumentException expected) {
                check("Relative out-of-bounds read is rejected", true);
            }
        }
    }

    private static void testInvalidOffset(Path image) throws Exception {
        try {
            new SectorReader(image.toString(), 512, 2049).close();
            check("Offset beyond image is rejected", false);
        } catch (IllegalArgumentException expected) {
            check("Offset beyond image is rejected", true);
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
