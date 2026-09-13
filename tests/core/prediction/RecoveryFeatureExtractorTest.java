package tests.core.prediction;

import core.prediction.RecoveryFeatureExtractor;
import core.prediction.RecoveryFeatures;
import core.prediction.RecoveryPrediction;
import core.prediction.RecoveryPredictor;
import filesystem.fat32.Fat32Directory;

public class RecoveryFeatureExtractorTest {

    public static void main(String[] args) {

        System.out.println("=== RecoveryFeatureExtractor Test Suite ===\n");

        /*
         * Create a fake 32-byte FAT32 directory entry.
         *
         * Byte 0 = 0xE5  -> deleted file
         * Extension = JPG
         * First cluster = 5
         * File size = 6 bytes
         */
        byte[] directoryEntry = new byte[32];

        directoryEntry[0] = (byte) 0xE5;

        directoryEntry[1] = 'H';
        directoryEntry[2] = 'O';
        directoryEntry[3] = 'T';
        directoryEntry[4] = 'O';

        directoryEntry[8] = 'J';
        directoryEntry[9] = 'P';
        directoryEntry[10] = 'G';

        // First cluster LOW word = 5
        directoryEntry[26] = 0x05;
        directoryEntry[27] = 0x00;

        // File size = 6 bytes
        directoryEntry[28] = 0x06;
        directoryEntry[29] = 0x00;
        directoryEntry[30] = 0x00;
        directoryEntry[31] = 0x00;

        Fat32Directory fat32Entry =
                new Fat32Directory(directoryEntry);

        /*
         * Minimal JPEG-style byte sequence:
         *
         * Header = FF D8 FF
         * Footer = FF D9
         */
        byte[] fileData = new byte[] {
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                0x00,
                (byte) 0xFF,
                (byte) 0xD9
        };

        RecoveryFeatureExtractor extractor =
                new RecoveryFeatureExtractor();

        RecoveryFeatures features =
                extractor.extractFat32(
                        fat32Entry,
                        fileData
                );

        RecoveryPredictor predictor =
                new RecoveryPredictor();

        RecoveryPrediction prediction =
                predictor.predict(features);

        System.out.println("Deleted: "
                + fat32Entry.isDeleted());

        System.out.println("Filename: "
                + fat32Entry.getFilename());

        System.out.println("First Cluster: "
                + fat32Entry.getFirstCluster());

        System.out.println("File Size: "
                + fat32Entry.getFileSize());

        System.out.println();

        System.out.println("Valid Header: "
                + features.hasValidHeader());

        System.out.println("Valid Footer: "
                + features.hasValidFooter());

        System.out.println("Metadata Available: "
                + features.hasMetadata());

        System.out.println("Clusters Available: "
                + features.hasClustersAvailable());

        System.out.println();

        System.out.println(prediction);
    }
}
