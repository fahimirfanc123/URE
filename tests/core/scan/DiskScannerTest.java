package tests.core.scan;

import core.disk.SectorReader;
import core.scan.DiskScanner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DiskScannerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        System.out.println("=== DiskScanner Test Suite ===");
        System.out.println();

        File testFile = new File("test-images/scanner-test.img");

        try {

            createTestImage(testFile);

            try (SectorReader reader = new SectorReader(
                    testFile.getPath())) {

                DiskScanner scanner = new DiskScanner(reader);

                testDataSector(scanner);
                testEmptySector(scanner);
                testScanRange(scanner);
                testNegativeStartSector(scanner);
                testNegativeSectorCount(scanner);
            }

        } catch (Exception e) {

            failed++;

            System.out.println(
                    "[FAIL] Unexpected error: " + e.getMessage()
            );

            e.printStackTrace();

        } finally {

            if (testFile.exists()) {
                testFile.delete();
            }
        }

        System.out.println();
        System.out.println("=== Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void createTestImage(File file)
            throws IOException {

        File parent = file.getParentFile();

        if (parent != null) {
            parent.mkdirs();
        }

        byte[] image = new byte[512 * 4];

        byte[] sector0Data =
                "URE SCANNER TEST - SECTOR 0"
                        .getBytes(StandardCharsets.UTF_8);

        System.arraycopy(
                sector0Data,
                0,
                image,
                0,
                sector0Data.length
        );

        byte[] sector2Data =
                "RECOVERABLE FILE DATA"
                        .getBytes(StandardCharsets.UTF_8);

        System.arraycopy(
                sector2Data,
                0,
                image,
                512 * 2,
                sector2Data.length
        );

        try (FileOutputStream output =
                     new FileOutputStream(file)) {

            output.write(image);
        }
    }

    private static void testDataSector(DiskScanner scanner)
            throws IOException {

        if (scanner.hasData(0)) {
            pass("Sector 0 detected as containing data");
        } else {
            fail("Sector 0 should contain data");
        }
    }

    private static void testEmptySector(DiskScanner scanner)
            throws IOException {

        if (!scanner.hasData(1)) {
            pass("Sector 1 detected as empty");
        } else {
            fail("Sector 1 should be empty");
        }
    }

    private static void testScanRange(DiskScanner scanner)
            throws IOException {

        DiskScanner.ScanResult result =
                scanner.scan(0, 4);

        if (result.getTotalScanned() == 4 &&
                result.getSectorsWithData() == 2 &&
                result.getEmptySectors() == 2) {

            pass("Four-sector scan returned correct statistics");

        } else {

            fail(
                    "Incorrect scan statistics: " + result
            );
        }

        System.out.println("  " + result);
    }

    private static void testNegativeStartSector(
            DiskScanner scanner) {

        try {

            scanner.scan(-1, 1);

            fail("Negative start sector should throw exception");

        } catch (IllegalArgumentException e) {

            pass("Negative start sector throws exception");

        } catch (IOException e) {

            fail("Unexpected IOException");
        }
    }

    private static void testNegativeSectorCount(
            DiskScanner scanner) {

        try {

            scanner.scan(0, -1);

            fail("Negative sector count should throw exception");

        } catch (IllegalArgumentException e) {

            pass("Negative sector count throws exception");

        } catch (IOException e) {

            fail("Unexpected IOException");
        }
    }

    private static void pass(String message) {

        passed++;

        System.out.println("[PASS] " + message);
    }

    private static void fail(String message) {

        failed++;

        System.out.println("[FAIL] " + message);
    }
}
