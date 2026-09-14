package tests.core.prediction;

import filesystem.fat32.Fat32ClusterChain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public class Fat32ClusterChainTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        System.out.println("=== Fat32ClusterChain Test Suite ===\n");

        testContiguousChain();
        testFragmentedChain();
        testSingleCluster();
        testInvalidStartCluster();

        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
    }

    private static void testContiguousChain() {
        try {
            byte[] fatData = new byte[64];

            writeFatEntry(fatData, 5, 6);
            writeFatEntry(fatData, 6, 7);
            writeFatEntry(fatData, 7, 0x0FFFFFFF);

            Fat32ClusterChain analyzer =
                    new Fat32ClusterChain();

            List<Long> chain =
                    analyzer.readChain(fatData, 5);

            boolean fragmented =
                    analyzer.isFragmented(chain);

            if (chain.equals(List.of(5L, 6L, 7L))
                    && !fragmented) {

                pass("Contiguous cluster chain detected correctly");
                System.out.println("  Chain: " + chain);
            } else {
                fail("Contiguous chain detection incorrect");
            }

        } catch (Exception e) {
            fail("Contiguous chain test threw exception");
        }
    }

    private static void testFragmentedChain() {
        try {
            byte[] fatData = new byte[128];

            writeFatEntry(fatData, 5, 20);
            writeFatEntry(fatData, 20, 21);
            writeFatEntry(fatData, 21, 0x0FFFFFFF);

            Fat32ClusterChain analyzer =
                    new Fat32ClusterChain();

            List<Long> chain =
                    analyzer.readChain(fatData, 5);

            boolean fragmented =
                    analyzer.isFragmented(chain);

            if (chain.equals(List.of(5L, 20L, 21L))
                    && fragmented) {

                pass("Fragmented cluster chain detected correctly");
                System.out.println("  Chain: " + chain);
            } else {
                fail("Fragmented chain detection incorrect");
            }

        } catch (Exception e) {
            fail("Fragmented chain test threw exception");
        }
    }

    private static void testSingleCluster() {
        try {
            byte[] fatData = new byte[32];

            writeFatEntry(fatData, 5, 0x0FFFFFFF);

            Fat32ClusterChain analyzer =
                    new Fat32ClusterChain();

            List<Long> chain =
                    analyzer.readChain(fatData, 5);

            boolean fragmented =
                    analyzer.isFragmented(chain);

            if (chain.size() == 1 && !fragmented) {
                pass("Single-cluster file detected as not fragmented");
            } else {
                fail("Single-cluster fragmentation result incorrect");
            }

        } catch (Exception e) {
            fail("Single-cluster test threw exception");
        }
    }

    private static void testInvalidStartCluster() {
        try {
            Fat32ClusterChain analyzer =
                    new Fat32ClusterChain();

            analyzer.readChain(new byte[32], 1);

            fail("Invalid start cluster was accepted");

        } catch (IllegalArgumentException e) {
            pass("Invalid start cluster throws exception");
        }
    }

    private static void writeFatEntry(
            byte[] fatData,
            int cluster,
            int value) {

        int offset = cluster * 4;

        ByteBuffer
                .wrap(fatData, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value);
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
