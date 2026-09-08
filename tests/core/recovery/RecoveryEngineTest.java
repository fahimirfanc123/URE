package tests.core.recovery;

import core.disk.SectorReader;
import core.scan.DiskScanner;
import core.recovery.RecoveryEngine;

import java.io.File;
import java.io.RandomAccessFile;

public class RecoveryEngineTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        System.out.println("=== RecoveryEngine Test Suite ===\n");

        try {

            File testFile = new File("test-images/recovery-test.img");
            testFile.getParentFile().mkdirs();

            createTestDisk(testFile);

            try (SectorReader reader = new SectorReader(testFile.getPath())) {

                DiskScanner scanner = new DiskScanner(reader);

                RecoveryEngine engine =
                        new RecoveryEngine(scanner);

                testRecoveryAnalysis(engine);

                testRecoverableDataDetection(engine);

                testNegativeStartSector(engine);

                testNegativeSectorCount(engine);
            }

            testNullScanner();

            testFile.delete();

        } catch (Exception e) {

            failed++;

            System.out.println(
                    "[FAIL] Unexpected exception: "
                            + e.getMessage()
            );

            e.printStackTrace();
        }

        System.out.println("\n=== Test Summary ===");

        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void createTestDisk(File file)
            throws Exception {

        try (RandomAccessFile raf =
                     new RandomAccessFile(file, "rw")) {

            raf.setLength(512L * 4);

            // Sector 0 contains data
            raf.seek(0);
            raf.write("URE RECOVERY TEST".getBytes());

            // Sector 1 remains empty

            // Sector 2 contains data
            raf.seek(512L * 2);
            raf.write("RECOVERABLE DATA".getBytes());

            // Sector 3 remains empty
        }
    }

    private static void testRecoveryAnalysis(
            RecoveryEngine engine
    ) throws Exception {

        RecoveryEngine.RecoveryResult result =
                engine.analyze(0, 4);

        boolean success =
                result.getTotalScanned() == 4 &&
                result.getRecoverableSectors() == 2 &&
                result.getEmptySectors() == 2;

        check(
                success,
                "Recovery analysis returned correct statistics"
        );

        System.out.println("  " + result);
    }

    private static void testRecoverableDataDetection(
            RecoveryEngine engine
    ) throws Exception {

        RecoveryEngine.RecoveryResult result =
                engine.analyze(0, 4);

        check(
                result.hasRecoverableData(),
                "Recoverable data detected successfully"
        );
    }

    private static void testNegativeStartSector(
            RecoveryEngine engine
    ) {

        try {

            engine.analyze(-1, 1);

            check(
                    false,
                    "Negative start sector throws exception"
            );

        } catch (IllegalArgumentException e) {

            check(
                    true,
                    "Negative start sector throws exception"
            );

        } catch (Exception e) {

            check(
                    false,
                    "Negative start sector throws exception"
            );
        }
    }

    private static void testNegativeSectorCount(
            RecoveryEngine engine
    ) {

        try {

            engine.analyze(0, -1);

            check(
                    false,
                    "Negative sector count throws exception"
            );

        } catch (IllegalArgumentException e) {

            check(
                    true,
                    "Negative sector count throws exception"
            );

        } catch (Exception e) {

            check(
                    false,
                    "Negative sector count throws exception"
            );
        }
    }

    private static void testNullScanner() {

        try {

            new RecoveryEngine(null);

            check(
                    false,
                    "Null DiskScanner throws exception"
            );

        } catch (IllegalArgumentException e) {

            check(
                    true,
                    "Null DiskScanner throws exception"
            );
        }
    }

    private static void check(
            boolean condition,
            String message
    ) {

        if (condition) {

            passed++;

            System.out.println("[PASS] " + message);

        } else {

            failed++;

            System.out.println("[FAIL] " + message);
        }
    }
}
