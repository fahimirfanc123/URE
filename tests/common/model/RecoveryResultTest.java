package tests.common.model;

import common.model.RecoveryResult;

public class RecoveryResultTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        System.out.println("=== RecoveryResult Test Suite ===\n");

        testBasicResult();
        testRecoverableDataDetection();
        testRecoveryPercentage();
        testZeroSectorPercentage();
        testInvalidStatistics();

        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testBasicResult() {

        RecoveryResult result =
                new RecoveryResult(0, 4, 2, 2);

        if (result.getStartSector() == 0 &&
                result.getTotalScanned() == 4 &&
                result.getRecoverableSectors() == 2 &&
                result.getEmptySectors() == 2) {

            pass("Recovery statistics stored correctly");

        } else {

            fail("Recovery statistics are incorrect");
        }
    }

    private static void testRecoverableDataDetection() {

        RecoveryResult result =
                new RecoveryResult(0, 4, 2, 2);

        if (result.hasRecoverableData()) {

            pass("Recoverable data detected correctly");

        } else {

            fail("Recoverable data detection failed");
        }
    }

    private static void testRecoveryPercentage() {

        RecoveryResult result =
                new RecoveryResult(0, 4, 2, 2);

        if (result.getRecoveryPercentage() == 50.0) {

            pass("Recovery percentage calculated correctly");

        } else {

            fail("Recovery percentage calculation failed");
        }
    }

    private static void testZeroSectorPercentage() {

        RecoveryResult result =
                new RecoveryResult(0, 0, 0, 0);

        if (result.getRecoveryPercentage() == 0.0) {

            pass("Zero-sector recovery percentage handled correctly");

        } else {

            fail("Zero-sector percentage handling failed");
        }
    }

    private static void testInvalidStatistics() {

        try {

            new RecoveryResult(0, 4, 3, 2);

            fail("Invalid statistics should throw exception");

        } catch (IllegalArgumentException e) {

            pass("Invalid recovery statistics throw exception");
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
