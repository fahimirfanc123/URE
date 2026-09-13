package tests.core.prediction;

import core.prediction.RecoveryFeatureExtractor;
import core.prediction.RecoveryFeatures;
import core.prediction.RecoveryPrediction;
import core.prediction.RecoveryPredictor;
import filesystem.fat32.Fat32Directory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RecoveryFeatureExtractorTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        System.out.println("=== RecoveryFeatureExtractor Test Suite ===\n");

        testContiguousFile();
        testFragmentedFile();

        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
    }

    private static void testContiguousFile() {

        try {
            Fat32Directory entry =
                    createDeletedJpegEntry(5, 6);

            byte[] fileData =
                    createJpegData();

            byte[] fatData = new byte[64];

            /*
             * Cluster chain:
             *
             * 5 -> 6 -> 7 -> END
             *
             * This is contiguous.
             */
            writeFatEntry(fatData, 5, 6);
            writeFatEntry(fatData, 6, 7);
            writeFatEntry(fatData, 7, 0x0FFFFFFF);

            RecoveryFeatureExtractor extractor =
                    new RecoveryFeatureExtractor();

            RecoveryFeatures features =
                    extractor.extractFat32(
                            entry,
                            fileData,
                            fatData
                    );

            RecoveryPrediction prediction =
                    new RecoveryPredictor()
                            .predict(features);

            if (Boolean.FALSE.equals(
                    features.isFragmented())) {

                pass("Contiguous FAT32 file detected correctly");

                System.out.println(
                        "  Fragmented: "
                                + features.isFragmented()
                );

                System.out.println(
                        "  " + prediction
                );

            } else {
                fail("Contiguous file marked as fragmented");
            }

        } catch (Exception e) {
            fail("Contiguous file test threw exception");
        }
    }

    private static void testFragmentedFile() {

        try {
            Fat32Directory entry =
                    createDeletedJpegEntry(5, 6);

            byte[] fileData =
                    createJpegData();

            byte[] fatData = new byte[128];

            /*
             * Cluster chain:
             *
             * 5 -> 20 -> 21 -> END
             *
             * This is fragmented.
             */
            writeFatEntry(fatData, 5, 20);
            writeFatEntry(fatData, 20, 21);
            writeFatEntry(fatData, 21, 0x0FFFFFFF);

            RecoveryFeatureExtractor extractor =
                    new RecoveryFeatureExtractor();

            RecoveryFeatures features =
                    extractor.extractFat32(
                            entry,
                            fileData,
                            fatData
                    );

            RecoveryPrediction prediction =
                    new RecoveryPredictor()
                            .predict(features);

            if (Boolean.TRUE.equals(
                    features.isFragmented())) {

                pass("Fragmented FAT32 file detected correctly");

                System.out.println(
                        "  Fragmented: "
                                + features.isFragmented()
                );

                System.out.println(
                        "  " + prediction
                );

            } else {
                fail("Fragmented file was not detected");
            }

        } catch (Exception e) {
            fail("Fragmented file test threw exception");
        }
    }

    private static Fat32Directory createDeletedJpegEntry(
            int firstCluster,
            int fileSize) {

        byte[] entry = new byte[32];

        /*
         * Deleted marker.
         */
        entry[0] = (byte) 0xE5;

        entry[1] = 'H';
        entry[2] = 'O';
        entry[3] = 'T';
        entry[4] = 'O';

        /*
         * JPG extension.
         */
        entry[8] = 'J';
        entry[9] = 'P';
        entry[10] = 'G';

        /*
         * First cluster LOW word.
         */
        entry[26] =
                (byte) (firstCluster & 0xFF);

        entry[27] =
                (byte) ((firstCluster >> 8) & 0xFF);

        /*
         * File size.
         */
        ByteBuffer
                .wrap(entry, 28, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(fileSize);

        return new Fat32Directory(entry);
    }

    private static byte[] createJpegData() {

        return new byte[] {
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                0x00,
                (byte) 0xFF,
                (byte) 0xD9
        };
    }

    private static void writeFatEntry(
            byte[] fatData,
            int cluster,
            int value) {

        int offset =
                cluster * 4;

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
