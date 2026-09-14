package tests.core.partition;

import core.disk.SectorReader;
import core.partition.MBRParser;

public class MBRParserTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {

        System.out.println("=== MBRParser Test Suite ===\n");

        testNullSectorReader();
        testValidMBRSignature();
        testInvalidMBRSignature();
        testEmptyDisk();

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");

        if (failed > 0) {
            System.exit(1);
        }
    }

    /** Test 1: Null SectorReader in constructor should throw IllegalArgumentException */
    private static void testNullSectorReader() {
        try {
            new MBRParser(null);
            check("Null SectorReader throws IllegalArgumentException", false);
        } catch (IllegalArgumentException e) {
            check("Null SectorReader throws IllegalArgumentException", true);
        }
    }

    /** Test 2: Valid MBR image should return true for hasValidSignature() */
    private static void testValidMBRSignature() throws Exception {
        try (SectorReader reader = new SectorReader("test-images/valid-mbr.img")) {
            MBRParser parser = new MBRParser(reader);
            check("Valid MBR signature returns true", parser.hasValidSignature());
        }
    }

    /** Test 3: Invalid MBR image (0x00 0x00) should return false for hasValidSignature() */
    private static void testInvalidMBRSignature() throws Exception {
        try (SectorReader reader = new SectorReader("test-images/invalid-mbr.img")) {
            MBRParser parser = new MBRParser(reader);
            check("Invalid MBR signature returns false", !parser.hasValidSignature());
        }
    }

    /** Test 4: Empty disk image (0 sectors) should gracefully return false */
    private static void testEmptyDisk() throws Exception {
        try (SectorReader reader = new SectorReader("test-images/empty.img")) {
            MBRParser parser = new MBRParser(reader);
            check("Empty disk returns false gracefully", !parser.hasValidSignature());
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
