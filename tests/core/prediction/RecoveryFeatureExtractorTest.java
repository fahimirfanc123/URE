package tests.core.prediction;

import core.prediction.RecoveryFeatureExtractor;
import core.prediction.RecoveryFeatures;
import core.prediction.RecoveryPrediction;
import core.prediction.RecoveryPredictor;
import filesystem.fat32.FAT32DirectoryEntry;
import filesystem.ntfs.NTFSFileEntry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RecoveryFeatureExtractorTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        System.out.println("=== RecoveryFeatureExtractor Test Suite ===\n");

        testFullHealthyCandidate();
        testPartiallyOverwrittenCandidate();
        testNTFSResidentCandidate();
        testNTFSNonResidentFragmentedCandidate();

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
            FAT32DirectoryEntry entry =
                    new FAT32DirectoryEntry(
                            "?HOTO.JPG",
                            5,
                            4096,
                            true,
                            0x20
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
                    extractor.extractFAT32(
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
                    overwriteCorrect &&
                    prediction.getProbability() >= 80) {

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
                    "Healthy candidate test threw exception: " + e.getMessage()
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
            FAT32DirectoryEntry entry =
                    new FAT32DirectoryEntry(
                            "?HOTO.JPG",
                            5,
                            4096,
                            true,
                            0x20
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
                    extractor.extractFAT32(
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
                    "Partially overwritten candidate test threw exception: " + e.getMessage()
            );
        }
    }

    private static void testNTFSResidentCandidate() {
        try {
            NTFSFileEntry entry =
                    new NTFSFileEntry(
                            42,
                            "SMALL.TXT",
                            true,
                            false,
                            64,
                            null
                    );

            byte[] textData = "MINION URE RESIDENT DATA TEST\n".getBytes();

            RecoveryFeatureExtractor extractor =
                    new RecoveryFeatureExtractor();

            RecoveryFeatures features =
                    extractor.extractNTFS(
                            entry,
                            textData,
                            true,
                            1,
                            textData.length
                    );

            RecoveryPrediction prediction =
                    new RecoveryPredictor()
                            .predict(features);

            if (Boolean.FALSE.equals(features.isFragmented()) &&
                features.hasMetadata() &&
                features.hasClustersAvailable() &&
                prediction.getProbability() >= 80) {

                pass("NTFS Resident candidate analyzed correctly");
                System.out.println("  " + prediction);
            } else {
                fail("NTFS Resident candidate analysis incorrect");
            }
        } catch (Exception e) {
            fail("NTFS Resident test threw exception: " + e.getMessage());
        }
    }

    private static void testNTFSNonResidentFragmentedCandidate() {
        try {
            NTFSFileEntry entry =
                    new NTFSFileEntry(
                            64,
                            "FRAGMENTED.BIN",
                            true,
                            false,
                            1048576,
                            null
                    );

            byte[] headerData = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF

            RecoveryFeatureExtractor extractor =
                    new RecoveryFeatureExtractor();

            RecoveryFeatures features =
                    extractor.extractNTFS(
                            entry,
                            headerData,
                            false,
                            4, // 4 data runs -> fragmented
                            1048576
                    );

            RecoveryPrediction prediction =
                    new RecoveryPredictor()
                            .predict(features);

            if (Boolean.TRUE.equals(features.isFragmented()) &&
                features.hasValidHeader() &&
                prediction.getRating().equals("Good")) {

                pass("NTFS Non-resident fragmented candidate analyzed correctly");
                System.out.println("  " + prediction);
            } else {
                fail("NTFS Non-resident fragmented candidate analysis incorrect");
            }
        } catch (Exception e) {
            fail("NTFS Non-resident fragmented test threw exception: " + e.getMessage());
        }
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
