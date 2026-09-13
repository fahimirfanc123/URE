package tests.core.prediction;

import core.prediction.RecoveryFeatures;
import core.prediction.RecoveryPrediction;
import core.prediction.RecoveryPredictor;

public class RecoveryPredictorTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        System.out.println("=== RecoveryPredictor Test Suite ===\n");

        testExcellentRecovery();
        testModerateRecovery();
        testUnknownFragmentationAndOverwrite();
        testVeryPoorRecovery();
        testInvalidOverwritePercentage();
        testNullFeatures();

        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
    }

    private static void testExcellentRecovery() {
        try {
            RecoveryFeatures features = new RecoveryFeatures(
                    true,
                    true,
                    true,
                    true,
                    false,
                    5.0
            );

            RecoveryPrediction result =
                    new RecoveryPredictor().predict(features);

            if (result.getProbability() >= 80 &&
                    result.getRating().equals("Excellent")) {

                pass("Excellent recovery candidate predicted correctly");
                System.out.println("  " + result);
            } else {
                fail("Excellent recovery prediction incorrect");
            }

        } catch (Exception e) {
            fail("Excellent recovery test threw exception");
        }
    }

    private static void testModerateRecovery() {
        try {
            RecoveryFeatures features = new RecoveryFeatures(
                    true,
                    false,
                    false,
                    true,
                    true,
                    50.0
            );

            RecoveryPrediction result =
                    new RecoveryPredictor().predict(features);

            if (result.getProbability() >= 40 &&
                    result.getProbability() < 60 &&
                    result.getRating().equals("Moderate")) {

                pass("Moderate recovery candidate predicted correctly");
                System.out.println("  " + result);
            } else {
                fail("Moderate recovery prediction incorrect");
            }

        } catch (Exception e) {
            fail("Moderate recovery test threw exception");
        }
    }

    private static void testUnknownFragmentationAndOverwrite() {
        try {
            RecoveryFeatures features = new RecoveryFeatures(
                    true,
                    true,
                    true,
                    true,
                    null,
                    null
            );

            RecoveryPrediction result =
                    new RecoveryPredictor().predict(features);

            if (result.getProbability() == 80.0 &&
                    result.getRating().equals("Excellent")) {

                pass("Unknown evidence does not receive extra score");
                System.out.println("  " + result);
            } else {
                fail("Unknown evidence scoring incorrect");
            }

        } catch (Exception e) {
            fail("Unknown evidence test threw exception");
        }
    }

    private static void testVeryPoorRecovery() {
        try {
            RecoveryFeatures features = new RecoveryFeatures(
                    false,
                    false,
                    false,
                    false,
                    true,
                    90.0
            );

            RecoveryPrediction result =
                    new RecoveryPredictor().predict(features);

            if (result.getProbability() < 20 &&
                    result.getRating().equals("Very Poor")) {

                pass("Very poor recovery candidate predicted correctly");
                System.out.println("  " + result);
            } else {
                fail("Very poor recovery prediction incorrect");
            }

        } catch (Exception e) {
            fail("Very poor recovery test threw exception");
        }
    }

    private static void testInvalidOverwritePercentage() {
        try {
            new RecoveryFeatures(
                    true,
                    true,
                    true,
                    true,
                    false,
                    120.0
            );

            fail("Invalid overwrite percentage was accepted");

        } catch (IllegalArgumentException e) {
            pass("Invalid overwrite percentage throws exception");
        }
    }

    private static void testNullFeatures() {
        try {
            new RecoveryPredictor().predict(null);

            fail("Null features were accepted");

        } catch (IllegalArgumentException e) {
            pass("Null RecoveryFeatures throws exception");
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
