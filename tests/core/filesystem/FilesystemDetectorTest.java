package tests.core.filesystem;

import core.disk.SectorReader;
import core.filesystem.FilesystemDetector;
import core.filesystem.FilesystemType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FilesystemDetectorTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== FilesystemDetector Test Suite ===\n");

        testFAT32ImageDetection();
        testNTFSImageDetection();
        testEmptyImageDetection();
        testNullAndInvalidInputs();
        testSyntheticFAT32Sector();
        testSyntheticNTFSSector();
        testCorruptedSignatureSector();

        System.out.println("\n=== Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testFAT32ImageDetection() {
        String path = "test-images/ure-fat32-deleted-test.img";
        if (!Files.exists(Path.of(path))) {
            System.out.println("[SKIP] FAT32 test image not found: " + path);
            return;
        }

        FilesystemType typePath = FilesystemDetector.detect(path);
        FilesystemType typeFile = FilesystemDetector.detect(new File(path));

        if (typePath == FilesystemType.FAT32 && typeFile == FilesystemType.FAT32) {
            pass("FAT32 image correctly detected as FAT32");
        } else {
            fail("FAT32 detection failed: path=" + typePath + ", file=" + typeFile);
        }
    }

    private static void testNTFSImageDetection() {
        String path = "test-images/ure-ntfs-deleted-test.img";
        if (!Files.exists(Path.of(path))) {
            System.out.println("[SKIP] NTFS test image not found: " + path);
            return;
        }

        FilesystemType typePath = FilesystemDetector.detect(path);
        FilesystemType typeFile = FilesystemDetector.detect(new File(path));

        if (typePath == FilesystemType.NTFS && typeFile == FilesystemType.NTFS) {
            pass("NTFS image correctly detected as NTFS");
        } else {
            fail("NTFS detection failed: path=" + typePath + ", file=" + typeFile);
        }
    }

    private static void testEmptyImageDetection() {
        String path = "test-images/ure-empty.img";
        if (!Files.exists(Path.of(path))) {
            System.out.println("[SKIP] Empty test image not found: " + path);
            return;
        }

        FilesystemType type = FilesystemDetector.detect(path);
        if (type == FilesystemType.UNKNOWN) {
            pass("Empty/RAW image correctly detected as UNKNOWN");
        } else {
            fail("Empty image unexpected detection: " + type);
        }
    }

    private static void testNullAndInvalidInputs() {
        if (FilesystemDetector.detect((String) null) == FilesystemType.UNKNOWN &&
            FilesystemDetector.detect("") == FilesystemType.UNKNOWN &&
            FilesystemDetector.detect("non-existent-file.img") == FilesystemType.UNKNOWN &&
            FilesystemDetector.detect((File) null) == FilesystemType.UNKNOWN &&
            FilesystemDetector.detect((SectorReader) null) == FilesystemType.UNKNOWN &&
            FilesystemDetector.detect((byte[]) null) == FilesystemType.UNKNOWN &&
            FilesystemDetector.detect(new byte[100]) == FilesystemType.UNKNOWN) {
            pass("Null, missing, and truncated inputs safely resolve to UNKNOWN");
        } else {
            fail("Invalid input handling failed");
        }
    }

    private static void testSyntheticFAT32Sector() {
        byte[] sector = new byte[512];
        sector[510] = 0x55;
        sector[511] = (byte) 0xAA;

        // Bytes per sector: 512
        sector[11] = 0x00;
        sector[12] = 0x02;

        // Sectors per cluster: 8
        sector[13] = 8;

        // Reserved sectors: 32
        sector[14] = 32;
        sector[15] = 0;

        // Number of FATs: 2
        sector[16] = 2;

        // Total sectors 32: 65536
        sector[32] = 0;
        sector[33] = 0;
        sector[34] = 1;
        sector[35] = 0;

        // FAT size 32: 512
        sector[36] = 0;
        sector[37] = 2;
        sector[38] = 0;
        sector[39] = 0;

        // Root cluster: 2
        sector[44] = 2;
        sector[45] = 0;
        sector[46] = 0;
        sector[47] = 0;

        FilesystemType type = FilesystemDetector.detect(sector);
        if (type == FilesystemType.FAT32) {
            pass("Synthetic FAT32 boot sector correctly detected as FAT32");
        } else {
            fail("Synthetic FAT32 detection failed: " + type);
        }
    }

    private static void testSyntheticNTFSSector() {
        byte[] sector = new byte[512];
        sector[510] = 0x55;
        sector[511] = (byte) 0xAA;

        // OEM: "NTFS    "
        byte[] oem = "NTFS    ".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(oem, 0, sector, 3, 8);

        // Bytes per sector: 512
        sector[11] = 0x00;
        sector[12] = 0x02;

        // Sectors per cluster: 8
        sector[13] = 8;

        // Total sectors: 131072
        sector[40] = 0;
        sector[41] = 0;
        sector[42] = 2;
        sector[43] = 0;

        // MFT cluster: 4
        sector[48] = 4;
        sector[49] = 0;
        sector[50] = 0;
        sector[51] = 0;

        FilesystemType type = FilesystemDetector.detect(sector);
        if (type == FilesystemType.NTFS) {
            pass("Synthetic NTFS boot sector correctly detected as NTFS");
        } else {
            fail("Synthetic NTFS detection failed: " + type);
        }
    }

    private static void testCorruptedSignatureSector() {
        byte[] sector = new byte[512];
        sector[510] = 0x00;
        sector[511] = 0x00;

        byte[] oem = "NTFS    ".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(oem, 0, sector, 3, 8);

        FilesystemType type = FilesystemDetector.detect(sector);
        if (type == FilesystemType.UNKNOWN) {
            pass("Corrupted boot signature correctly rejected as UNKNOWN");
        } else {
            fail("Corrupted signature should be UNKNOWN but got: " + type);
        }
    }

    private static void pass(String msg) {
        passed++;
        System.out.println("[PASS] " + msg);
    }

    private static void fail(String msg) {
        failed++;
        System.out.println("[FAIL] " + msg);
    }
}
