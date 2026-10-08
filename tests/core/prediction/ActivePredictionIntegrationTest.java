package tests.core.prediction;

import core.disk.SectorReader;
import core.prediction.RecoveryPrediction;
import filesystem.fat32.FAT32BootSector;
import filesystem.fat32.FAT32DeletedFileScanner;
import filesystem.fat32.FAT32DirectoryEntry;
import filesystem.ntfs.NTFSBootSector;
import filesystem.ntfs.NTFSDeletedFileScanner;
import filesystem.ntfs.NTFSFileEntry;
import filesystem.ntfs.NTFSMFTReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ActivePredictionIntegrationTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        System.out.println("=== Active Prediction Integration Test Suite ===\n");

        testFAT32ActivePrediction();
        testNTFSActivePrediction();

        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testFAT32ActivePrediction() {
        String imagePath = "test-images/ure-fat32-deleted-test.img";
        if (!Files.exists(Path.of(imagePath))) {
            System.out.println("[SKIP] FAT32 test image not found: " + imagePath);
            return;
        }

        try (SectorReader reader = new SectorReader(imagePath)) {
            FAT32BootSector boot = FAT32BootSector.parse(reader);
            FAT32DeletedFileScanner scanner = new FAT32DeletedFileScanner(reader, boot);

            List<FAT32DirectoryEntry> deletedFiles = scanner.scanRootDirectory();

            if (deletedFiles.isEmpty()) {
                fail("FAT32: No deleted files detected");
                return;
            }

            FAT32DirectoryEntry entry = deletedFiles.get(0);
            RecoveryPrediction prediction = entry.getPrediction();

            if (prediction == null) {
                fail("FAT32: Entry has no attached RecoveryPrediction");
                return;
            }

            System.out.println("FAT32 Deleted File: " + entry.getFileName());
            System.out.println("  Size: " + entry.getFileSize() + " bytes");
            System.out.println("  Cluster: " + entry.getStartCluster());
            System.out.println("  Confidence: " + prediction.getProbability() + "% (" + prediction.getRating() + ")");

            if (prediction.getProbability() >= 80.0 && "Excellent".equals(prediction.getRating())) {
                pass("FAT32 deleted file active prediction is Excellent (confidence >= 80%)");
            } else {
                fail("FAT32 prediction unexpected score: " + prediction);
            }

        } catch (Exception e) {
            fail("FAT32 Active Prediction failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testNTFSActivePrediction() {
        String imagePath = "test-images/ure-ntfs-deleted-test.img";
        if (!Files.exists(Path.of(imagePath))) {
            System.out.println("[SKIP] NTFS test image not found: " + imagePath);
            return;
        }

        try (SectorReader reader = new SectorReader(imagePath)) {
            NTFSBootSector boot = NTFSBootSector.parse(reader);
            NTFSMFTReader mftReader = new NTFSMFTReader(reader, boot);
            NTFSDeletedFileScanner scanner = new NTFSDeletedFileScanner(mftReader, reader, boot);

            List<NTFSFileEntry> deletedFiles = scanner.scan(1024);

            if (deletedFiles.isEmpty()) {
                fail("NTFS: No deleted files detected");
                return;
            }

            NTFSFileEntry entry = deletedFiles.get(0);
            RecoveryPrediction prediction = entry.getPrediction();

            if (prediction == null) {
                fail("NTFS: Entry has no attached RecoveryPrediction");
                return;
            }

            System.out.println("NTFS Deleted File: " + entry.getFileName());
            System.out.println("  Record: " + entry.getRecordNumber());
            System.out.println("  Size: " + entry.getFileSize() + " bytes");
            System.out.println("  Confidence: " + prediction.getProbability() + "% (" + prediction.getRating() + ")");

            if (prediction.getProbability() >= 60.0 && ("Good".equals(prediction.getRating()) || "Excellent".equals(prediction.getRating()))) {
                pass("NTFS deleted file active prediction is Good/Excellent (confidence >= 60%)");
            } else {
                fail("NTFS prediction unexpected score: " + prediction);
            }

        } catch (Exception e) {
            fail("NTFS Active Prediction failed: " + e.getMessage());
            e.printStackTrace();
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
