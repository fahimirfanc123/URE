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

        testFullHealthyCandidate();
        testPartiallyOverwrittenCandidate();

        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
    }

    private static void testFullHealthyCandidate() {

        try {
            /*
             * File size = 4096 bytes
             * Cluster size = 1024 bytes
             *
             * Expected clusters = 4
             */
            Fat32Directory entry =
                    createDeletedJpegEntry(
                            5,
                            4096
                    );

            byte[] fileData =
                    createJpegData();

            byte[] fatData = new byte[128];

            /*
             * 5 -> 6 -> 7 -> 8 -> END
             *
             * 4 clusters available.
             * File needs 4 clusters.
             *
             * Estimated overwrite = 0%
             * Fragmented = false
             */
            writeFatEntry(fatData, 5, 6);
            writeFatEntry(fatData, 6, 7);
            writeFatEntry(fatData, 7, 8);
            writeFatEntry(fatData, 8, 0x0FFFFFFF);

            RecoveryFeatureExtractor extractor =
                    new RecoveryFeatureExtractor();

            RecoveryFeatures features =
                    extractor.extractFat32(
                            entry,
                            fileData,
                            fatData,
                            1024L
                    );

            RecoveryPrediction prediction =
                    new RecoveryPredictor()
                            .predict(features);

            boolean fragmentationCorrect =
                    Boolean.FALSE.equals(
                            features.isFragmented()
                    );

            boolean overwriteCorrect =
                    features.getOverwrittenPercentage() != null
                            &&
                    features.getOverwrittenPercentage() == 0.0;

            if (fragmentationCorrect &&
                    overwriteCorrect) {

                pass(
                        "Healthy FAT32 candidate analyzed correctly"
                );

                System.out.println(
                        "  Fragmented: "
                                + features.isFragmented()
                );

                System.out.println(
                        "  Overwrite: "
                                + features.getOverwrittenPercentage()
                                + "%"
                );

                System.out.println(
                        "  " + prediction
                );

            } else {
                fail(
                        "Healthy FAT32 candidate analysis incorrect"
                );
            }

        } catch (Exception e) {

            fail(
                    "Healthy candidate test threw exception"
            );
        }
    }

    private static void testPartiallyOverwrittenCandidate() {

        try {
            /*
             * File size = 4096 bytes
             * Cluster size = 1024 bytes
             *
             * Expected clusters = 4
             */
            Fat32Directory entry =
                    createDeletedJpegEntry(
                            5,
                            4096
                    );

            byte[] fileData =
                    createJpegData();

            byte[] fatData = new byte[128];

            /*
             * Only two clusters remain in the chain:
             *
             * 5 -> 20 -> END
             *
             * Expected clusters = 4
             * Available clusters = 2
             *
             * Estimated overwrite = 50%
             *
             * Because 5 -> 20 is not sequential,
             * the file is also fragmented.
             */
            writeFatEntry(fatData, 5, 20);
            writeFatEntry(
                    fatData,
                    20,
                    0x0FFFFFFF
            );

            RecoveryFeatureExtractor extractor =
                    new RecoveryFeatureExtractor();

            RecoveryFeatures features =
                    extractor.extractFat32(
                            entry,
                            fileData,
                            fatData,
                            1024L
                    );

            RecoveryPrediction prediction =
                    new RecoveryPredictor()
                            .predict(features);

            boolean fragmentationCorrect =
                    Boolean.TRUE.equals(
                            features.isFragmented()
                    );

            boolean overwriteCorrect =
                    features.getOverwrittenPercentage() != null
                            &&
                    features.getOverwrittenPercentage() == 50.0;

            if (fragmentationCorrect &&
                    overwriteCorrect) {

                pass(
                        "Partially overwritten FAT32 candidate analyzed correctly"
                );

                System.out.println(
                        "  Fragmented: "
                                + features.isFragmented()
                );

                System.out.println(
                        "  Overwrite: "
                                + features.getOverwrittenPercentage()
                                + "%"
                );

                System.out.println(
                        "  " + prediction
                );

            } else {
                fail(
                        "Partially overwritten candidate analysis incorrect"
                );
            }

        } catch (Exception e) {

            fail(
                    "Partially overwritten candidate test threw exception"
            );
        }
    }

    private static Fat32Directory createDeletedJpegEntry(
            int firstCluster,
            int fileSize) {

        byte[] entry = new byte[32];

        /*
         * FAT32 deleted-file marker.
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
         * Starting cluster LOW word.
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
