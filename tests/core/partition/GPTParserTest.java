package tests.core.partition;

import core.disk.SectorReader;
import core.partition.GPTParser;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

public class GPTParserTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {

        System.out.println("=== GPTParser Test Suite ===\n");

        File testFile = createGPTTestImage();

        testValidGPT(testFile);
        testGPTHeader(testFile);

        System.out.println();
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);

        if (failed > 0) {
            throw new AssertionError("Some GPTParser tests failed");
        }

        System.out.println("\n=== All GPTParser tests passed ===");
    }

    private static void testValidGPT(File file) throws Exception {

        try (SectorReader reader = new SectorReader(file.getPath())) {

            GPTParser parser = new GPTParser(reader);

            check(
                    parser.isValidGPT(),
                    "Valid GPT signature detected"
            );
        }
    }

    private static void testGPTHeader(File file) throws Exception {

        try (SectorReader reader = new SectorReader(file.getPath())) {

            GPTParser parser = new GPTParser(reader);

            GPTParser.GPTHeader header = parser.readHeader();

            check(
                    header.getHeaderSize() == 92,
                    "GPT header size is 92 bytes"
            );

            check(
                    header.getCurrentLBA() == 1,
                    "Current GPT header is at LBA 1"
            );

            check(
                    header.getBackupLBA() == 99,
                    "Backup GPT header LBA parsed"
            );

            check(
                    header.getFirstUsableLBA() == 34,
                    "First usable LBA parsed"
            );

            check(
                    header.getLastUsableLBA() == 98,
                    "Last usable LBA parsed"
            );

            check(
                    header.getPartitionEntryStartLBA() == 2,
                    "Partition entry table LBA parsed"
            );

            check(
                    header.getNumberOfPartitionEntries() == 128,
                    "Partition entry count parsed"
            );

            check(
                    header.getSizeOfPartitionEntry() == 128,
                    "Partition entry size parsed"
            );
        }
    }

    /**
     * Creates a small fake disk containing a GPT header
     * specifically for testing GPTParser.
     */
    private static File createGPTTestImage() throws Exception {

        File directory = new File("test-images");

        if (!directory.exists()) {
            directory.mkdirs();
        }

        File file = new File(directory, "gpt-test.img");

        try (RandomAccessFile raf =
                     new RandomAccessFile(file, "rw")) {

            // 100 sectors * 512 bytes
            raf.setLength(100L * 512);

            // GPT header starts at sector 1
            raf.seek(512);

            byte[] header = new byte[512];

            byte[] signature =
                    "EFI PART".getBytes(StandardCharsets.US_ASCII);

            System.arraycopy(
                    signature,
                    0,
                    header,
                    0,
                    signature.length
            );

            // Revision 1.0 = 0x00010000
            writeIntLE(header, 8, 0x00010000);

            // GPT header size
            writeIntLE(header, 12, 92);

            // Current LBA
            writeLongLE(header, 24, 1);

            // Backup GPT header
            writeLongLE(header, 32, 99);

            // First usable sector
            writeLongLE(header, 40, 34);

            // Last usable sector
            writeLongLE(header, 48, 98);

            // Partition entries begin at LBA 2
            writeLongLE(header, 72, 2);

            // Number of partition entries
            writeIntLE(header, 80, 128);

            // Size of each partition entry
            writeIntLE(header, 84, 128);

            raf.write(header);
        }

        return file;
    }

    private static void writeIntLE(
            byte[] data,
            int offset,
            long value
    ) {

        for (int i = 0; i < 4; i++) {

            data[offset + i] =
                    (byte) ((value >> (8 * i)) & 0xFF);
        }
    }

    private static void writeLongLE(
            byte[] data,
            int offset,
            long value
    ) {

        for (int i = 0; i < 8; i++) {

            data[offset + i] =
                    (byte) ((value >> (8 * i)) & 0xFF);
        }
    }

    private static void check(
            boolean condition,
            String message
    ) {

        if (condition) {

            passed++;

            System.out.println("[PASS] " + message);

        } else {

            failed++;

            System.out.println("[FAIL] " + message);
        }
    }
}
