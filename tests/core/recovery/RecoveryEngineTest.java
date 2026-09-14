package tests.core.recovery;

import common.model.RecoveryResult;
import core.disk.SectorReader;
import core.recovery.RecoveryEngine;
import core.scan.DiskScanner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class RecoveryEngineTest {
    private static final int SECTOR_SIZE = 512;
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {

        System.out.println("=== RecoveryEngine Test Suite ===\n");

        File testFile = createTestDisk();

        try (
                SectorReader sectorReader =
                        new SectorReader(testFile.getPath())
        ) {

            DiskScanner diskScanner =
                    new DiskScanner(sectorReader);

            RecoveryEngine recoveryEngine =
                    new RecoveryEngine(diskScanner);

            testRecoveryAnalysis(recoveryEngine);
            testRecoverableData(recoveryEngine);
            testNegativeStartSector(recoveryEngine);
            testNegativeSectorCount(recoveryEngine);
            testNullDiskScanner();

        } finally {

            if (testFile.exists()) {
                testFile.delete();
            }
        }

        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static File createTestDisk() throws IOException {

        File file =
                File.createTempFile("ure-recovery-test", ".img");

        byte[] diskData =
                new byte[4 * SECTOR_SIZE];

        /*
         * Sector 0 -> contains data
         */
        byte[] sector0Data =
                "URE TEST - SECTOR 0".getBytes();

        System.arraycopy(
                sector0Data,
                0,
                diskData,
                0,
                sector0Data.length
        );

        /*
         * Sector 1 -> empty
         */

        /*
         * Sector 2 -> contains data
         */
        byte[] sector2Data =
                "RECOVERABLE DATA".getBytes();

        int sector2Offset =
                2 * SECTOR_SIZE;

        System.arraycopy(
                sector2Data,
                0,
                diskData,
                sector2Offset,
                sector2Data.length
        );

        /*
         * Sector 3 -> empty
         */

        try (
                FileOutputStream output =
                        new FileOutputStream(file)
        ) {

            output.write(diskData);
        }

        return file;
    }

    private static void testRecoveryAnalysis(
            RecoveryEngine recoveryEngine)
            throws IOException {

        RecoveryResult result =
                recoveryEngine.analyze(0, 4);

        boolean correct =
                result.getStartSector() == 0 &&
                result.getTotalScanned() == 4 &&
                result.getRecoverableSectors() == 2 &&
                result.getEmptySectors() == 2;

        if (correct) {

            pass(
                    "Recovery analysis returned correct statistics"
            );

            System.out.println("  " + result);

        } else {

            fail(
                    "Recovery analysis returned incorrect statistics"
            );
        }
    }

    private static void testRecoverableData(
            RecoveryEngine recoveryEngine)
            throws IOException {

        RecoveryResult result =
                recoveryEngine.analyze(0, 4);

        if (result.hasRecoverableData()) {

            pass(
                    "Recoverable data detected successfully"
            );

        } else {

            fail(
                    "Recoverable data was not detected"
            );
        }
    }

    private static void testNegativeStartSector(
            RecoveryEngine recoveryEngine) {

        try {

            recoveryEngine.analyze(-1, 1);

            fail(
                    "Negative start sector should throw exception"
            );

        } catch (IllegalArgumentException e) {

            pass(
                    "Negative start sector throws exception"
            );

        } catch (IOException e) {

            fail(
                    "Unexpected IOException"
            );
        }
    }

    private static void testNegativeSectorCount(
            RecoveryEngine recoveryEngine) {

        try {

            recoveryEngine.analyze(0, -1);

            fail(
                    "Negative sector count should throw exception"
            );

        } catch (IllegalArgumentException e) {

            pass(
                    "Negative sector count throws exception"
            );

        } catch (IOException e) {

            fail(
                    "Unexpected IOException"
            );
        }
    }

    private static void testNullDiskScanner() {

        try {

            new RecoveryEngine(null);

            fail(
                    "Null DiskScanner should throw exception"
            );

        } catch (IllegalArgumentException e) {

            pass(
                    "Null DiskScanner throws exception"
            );
        }
    }

    private static void pass(String message) {

        passed++;

        System.out.println(
                "[PASS] " + message
        );
    }

    private static void fail(String message) {

        failed++;

        System.out.println(
                "[FAIL] " + message
        );
    }
}
