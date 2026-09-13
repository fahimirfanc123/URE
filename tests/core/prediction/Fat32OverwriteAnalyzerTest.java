package tests.core.prediction;

import filesystem.fat32.Fat32OverwriteAnalyzer;

public class Fat32OverwriteAnalyzerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        System.out.println("=== Fat32OverwriteAnalyzer Test Suite ===\n");

        testNoOverwrite();
        testTwentyFivePercentOverwrite();
        testFiftyPercentOverwrite();
        testCompleteOverwrite();
        testInvalidClusterSize();

        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
    }

    private static void testNoOverwrite() {
        try {
            Fat32OverwriteAnalyzer analyzer =
                    new Fat32OverwriteAnalyzer();

            double result =
                    analyzer.estimateOverwritePercentage(
                            4096,
                            1024,
                            4
                    );

            if (result == 0.0) {
                pass("No overwrite detected correctly");
                System.out.println("  Overwrite: " + result + "%");
            } else {
                fail("No-overwrite result incorrect");
            }

        } catch (Exception e) {
            fail("No-overwrite test threw exception");
        }
    }

    private static void testTwentyFivePercentOverwrite() {
        try {
            Fat32OverwriteAnalyzer analyzer =
                    new Fat32OverwriteAnalyzer();

            double result =
                    analyzer.estimateOverwritePercentage(
                            4096,
                            1024,
                            3
                    );

            if (result == 25.0) {
                pass("25% overwrite estimated correctly");
                System.out.println("  Overwrite: " + result + "%");
            } else {
                fail("25% overwrite result incorrect");
            }

        } catch (Exception e) {
            fail("25% overwrite test threw exception");
        }
    }

    private static void testFiftyPercentOverwrite() {
        try {
            Fat32OverwriteAnalyzer analyzer =
                    new Fat32OverwriteAnalyzer();

            double result =
                    analyzer.estimateOverwritePercentage(
                            4096,
                            1024,
                            2
                    );

            if (result == 50.0) {
                pass("50% overwrite estimated correctly");
                System.out.println("  Overwrite: " + result + "%");
            } else {
                fail("50% overwrite result incorrect");
            }

        } catch (Exception e) {
            fail("50% overwrite test threw exception");
        }
    }

    private static void testCompleteOverwrite() {
        try {
            Fat32OverwriteAnalyzer analyzer =
                    new Fat32OverwriteAnalyzer();

            double result =
                    analyzer.estimateOverwritePercentage(
                            4096,
                            1024,
                            0
                    );

            if (result == 100.0) {
                pass("100% overwrite estimated correctly");
                System.out.println("  Overwrite: " + result + "%");
            } else {
                fail("100% overwrite result incorrect");
            }

        } catch (Exception e) {
            fail("100% overwrite test threw exception");
        }
    }

    private static void testInvalidClusterSize() {
        try {
            Fat32OverwriteAnalyzer analyzer =
                    new Fat32OverwriteAnalyzer();

            analyzer.estimateOverwritePercentage(
                    4096,
                    0,
                    4
            );

            fail("Invalid cluster size was accepted");

        } catch (IllegalArgumentException e) {
            pass("Invalid cluster size throws exception");
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
