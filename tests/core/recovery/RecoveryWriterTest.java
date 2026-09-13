package tests.core.recovery;

import core.recovery.RecoveryWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public class RecoveryWriterTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {

        System.out.println("=== RecoveryWriter Test Suite ===\n");

        testWriteRecoveredData();
        testCreatesParentDirectory();
        testNullData();
        testNullPath();

        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testWriteRecoveredData() throws Exception {

        RecoveryWriter writer = new RecoveryWriter();

        byte[] data = "URE RECOVERED DATA"
                .getBytes(StandardCharsets.UTF_8);

        Path output = Path.of(
                "build/test-output/recovered.txt"
        );

        writer.write(data, output);

        byte[] writtenData = Files.readAllBytes(output);

        if (java.util.Arrays.equals(data, writtenData)) {
            pass("Recovered data written correctly");
        } else {
            fail("Recovered data did not match");
        }

        Files.deleteIfExists(output);
    }

    private static void testCreatesParentDirectory() throws Exception {

        RecoveryWriter writer = new RecoveryWriter();

        byte[] data = "TEST"
                .getBytes(StandardCharsets.UTF_8);

        Path output = Path.of(
                "build/test-output/nested/recovered.bin"
        );

        writer.write(data, output);

        if (Files.exists(output)) {
            pass("Parent directory created automatically");
        } else {
            fail("Parent directory was not created");
        }

        Files.deleteIfExists(output);
    }

    private static void testNullData() {

        RecoveryWriter writer = new RecoveryWriter();

        try {

            writer.write(
                    null,
                    Path.of("build/test-output/test.bin")
            );

            fail("Null data should throw exception");

        } catch (IllegalArgumentException e) {

            pass("Null data throws exception");

        } catch (Exception e) {

            fail("Unexpected exception for null data");
        }
    }

    private static void testNullPath() {

        RecoveryWriter writer = new RecoveryWriter();

        try {

            writer.write(
                    new byte[]{1, 2, 3},
                    null
            );

            fail("Null path should throw exception");

        } catch (IllegalArgumentException e) {

            pass("Null output path throws exception");

        } catch (Exception e) {

            fail("Unexpected exception for null path");
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
