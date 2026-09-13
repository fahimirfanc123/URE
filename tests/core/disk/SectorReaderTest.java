package tests.core.disk;

import core.disk.SectorReader;

import java.nio.charset.StandardCharsets;

public class SectorReaderTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {

        System.out.println("=== SectorReader Test Suite ===\n");

        testBasicRead();
        testMetadata();
        testMultiSectorRead();
        testInvalidCount();
        testBoundsCheck();
        testCustomSectorSize();
        testAutoCloseable();

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
    }

    /** Test 1: Read sector 0 and verify it returns 512 bytes */
    private static void testBasicRead() throws Exception {
        try (SectorReader reader = new SectorReader("test-images/test.img")) {
            byte[] sector = reader.readSector(0);
            check("Basic read returns 512 bytes", sector.length == 512);

            String data = new String(sector, StandardCharsets.US_ASCII);
            System.out.println("  Sector 0 data: " + data.trim());
        }
    }

    /** Test 2: Verify metadata (total sectors, sector size, total bytes) */
    private static void testMetadata() throws Exception {
        try (SectorReader reader = new SectorReader("test-images/test.img")) {
            check("Sector size is 512", reader.getSectorSize() == 512);
            check("Total sectors > 0", reader.getTotalSectors() > 0);
            check("Total bytes = sectors * 512",
                reader.getTotalBytes() == reader.getTotalSectors() * 512);

            System.out.println("  Total sectors: " + reader.getTotalSectors());
            System.out.println("  Total bytes: " + reader.getTotalBytes());
        }
    }

    /** Test 3: Read multiple consecutive sectors */
    private static void testMultiSectorRead() throws Exception {
        try (SectorReader reader = new SectorReader("test-images/test.img")) {
            if (reader.getTotalSectors() >= 2) {
                byte[] sectors = reader.readSectors(0, 2);
                check("Multi-sector read returns 1024 bytes", sectors.length == 1024);
            } else {
                System.out.println("  [SKIP] Not enough sectors for multi-read test");
            }
        }
    }

    /** Test 4: Invalid count (<= 0) should throw IllegalArgumentException */
    private static void testInvalidCount() throws Exception {
        try (SectorReader reader = new SectorReader("test-images/test.img")) {
            try {
                reader.readSectors(0, 0);
                check("Zero count in readSectors throws exception", false);
            } catch (IllegalArgumentException e) {
                check("Zero count in readSectors throws exception", true);
            }

            try {
                reader.readSectors(0, -1);
                check("Negative count in readSectors throws exception", false);
            } catch (IllegalArgumentException e) {
                check("Negative count in readSectors throws exception", true);
            }
        }
    }

    /** Test 5: Out-of-bounds sector should throw IllegalArgumentException */
    private static void testBoundsCheck() throws Exception {
        try (SectorReader reader = new SectorReader("test-images/test.img")) {
            try {
                reader.readSector(-1);
                check("Negative sector throws exception", false);
            } catch (IllegalArgumentException e) {
                check("Negative sector throws exception", true);
            }

            try {
                reader.readSector(reader.getTotalSectors());
                check("Out-of-bounds sector throws exception", false);
            } catch (IllegalArgumentException e) {
                check("Out-of-bounds sector throws exception", true);
            }
        }
    }

    /** Test 6: Custom sector size (4096 bytes) */
    private static void testCustomSectorSize() throws Exception {
        try (SectorReader reader = new SectorReader("test-images/test.img", 4096)) {
            check("Custom sector size is 4096", reader.getSectorSize() == 4096);
            if (reader.getTotalSectors() > 0) {
                byte[] sector = reader.readSector(0);
                check("4K sector read returns 4096 bytes", sector.length == 4096);
            }
        }
    }

    /** Test 7: AutoCloseable works with try-with-resources (no exception on close) */
    private static void testAutoCloseable() {
        try {
            try (SectorReader reader = new SectorReader("test-images/test.img")) {
                reader.readSector(0);
            }
            check("AutoCloseable / try-with-resources works", true);
        } catch (Exception e) {
            check("AutoCloseable / try-with-resources works", false);
        }
    }

    /** Simple assertion helper */
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
