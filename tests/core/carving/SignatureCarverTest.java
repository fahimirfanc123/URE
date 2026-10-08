package tests.core.carving;

import core.carving.*;
import core.carving.boundary.*;
import core.disk.SectorReader;
import core.prediction.RecoveryPrediction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class SignatureCarverTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Signature Carving Engine Test Suite ===\n");

        try {
            testSyntheticControlledMultiImage();
            testSectorBoundaryAndSplitSignatures();
            testJpegBoundaryDetector();
            testPngBoundaryDetector();
            testPdfBoundaryDetector();
            testZipBoundaryDetector();
            testFalsePositiveRejection();
            testEmptyAndTruncatedImages();
            testDeterminism();
            testFileRecoveryExtraction();
        } catch (Exception e) {
            System.out.println("[ERROR] Unexpected exception during test execution: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n=== Carving Test Summary ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);

        if (failed > 0) {
            System.exit(1);
        }
    }

    /**
     * Test 1: Controlled image with JPEG, PNG, PDF, and ZIP laid out contiguously.
     */
    private static void testSyntheticControlledMultiImage() throws IOException {
        Path tempImage = Files.createTempFile("ure-carve-controlled-", ".img");
        try {
            byte[] jpeg = createValidJpeg();
            byte[] png = createValidPng();
            byte[] pdf = createValidPdf();
            byte[] zip = createValidZip();

            // Total image: 16 sectors (8192 bytes)
            byte[] disk = new byte[8192];
            int offsetJpeg = 512;    // Sector 1
            int offsetPng = 2048;    // Sector 4
            int offsetPdf = 4096;    // Sector 8
            int offsetZip = 6144;    // Sector 12

            System.arraycopy(jpeg, 0, disk, offsetJpeg, jpeg.length);
            System.arraycopy(png, 0, disk, offsetPng, png.length);
            System.arraycopy(pdf, 0, disk, offsetPdf, pdf.length);
            System.arraycopy(zip, 0, disk, offsetZip, zip.length);

            Files.write(tempImage, disk);

            try (SectorReader reader = new SectorReader(tempImage.toString())) {
                SignatureScanner scanner = new SignatureScanner(reader);
                List<CarvedFile> results = scanner.scan();

                check("Controlled image found exactly 4 carved files", results.size() == 4);

                if (results.size() == 4) {
                    CarvedFile cJpeg = results.get(0);
                    CarvedFile cPng = results.get(1);
                    CarvedFile cPdf = results.get(2);
                    CarvedFile cZip = results.get(3);

                    check("JPEG format detected", cJpeg.getFormat() == FileFormat.JPEG);
                    check("JPEG start offset matches", cJpeg.getStartByteOffset() == offsetJpeg);
                    check("JPEG exact length matches", cJpeg.getLength() == jpeg.length);
                    check("JPEG boundary is EXACT", cJpeg.getBoundaryStatus() == BoundaryStatus.EXACT);
                    check("JPEG confidence score >= 60%", cJpeg.getPrediction().getProbability() >= 60.0);

                    check("PNG format detected", cPng.getFormat() == FileFormat.PNG);
                    check("PNG start offset matches", cPng.getStartByteOffset() == offsetPng);
                    check("PNG exact length matches", cPng.getLength() == png.length);
                    check("PNG boundary is EXACT", cPng.getBoundaryStatus() == BoundaryStatus.EXACT);

                    check("PDF format detected", cPdf.getFormat() == FileFormat.PDF);
                    check("PDF start offset matches", cPdf.getStartByteOffset() == offsetPdf);
                    check("PDF exact length matches", cPdf.getLength() == pdf.length);
                    check("PDF boundary is EXACT", cPdf.getBoundaryStatus() == BoundaryStatus.EXACT);

                    check("ZIP format detected", cZip.getFormat() == FileFormat.ZIP);
                    check("ZIP start offset matches", cZip.getStartByteOffset() == offsetZip);
                    check("ZIP exact length matches", cZip.getLength() == zip.length);
                    check("ZIP boundary is EXACT", cZip.getBoundaryStatus() == BoundaryStatus.EXACT);
                }
            }
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }

    /**
     * Test 2: Signatures crossing sector and chunk boundaries.
     */
    private static void testSectorBoundaryAndSplitSignatures() throws IOException {
        Path tempImage = Files.createTempFile("ure-carve-split-", ".img");
        try {
            byte[] disk = new byte[4096]; // 8 sectors

            // Place PNG signature split across Sector 0 and Sector 1
            // Sector 0 ends at 511. Put PNG starting at offset 510:
            // 89 50 in sector 0, 4E 47 0D 0A 1A 0A in sector 1
            byte[] png = createValidPng();
            System.arraycopy(png, 0, disk, 510, png.length);

            Files.write(tempImage, disk);

            // Test with 512-byte small chunk size to force cross-chunk boundary inspection
            try (SectorReader reader = new SectorReader(tempImage.toString())) {
                SignatureScanner scanner = new SignatureScanner(reader, new SignatureDatabase(), 512);
                List<CarvedFile> results = scanner.scan();

                check("Split signature across sector boundary detected", results.size() == 1);
                if (results.size() == 1) {
                    CarvedFile f = results.get(0);
                    check("Split signature identified at byte 510", f.getStartByteOffset() == 510);
                    check("Split PNG length matches", f.getLength() == png.length);
                    check("Split PNG status is EXACT", f.getBoundaryStatus() == BoundaryStatus.EXACT);
                }
            }
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }

    /**
     * Test 3: JPEG boundary detector specific cases.
     */
    private static void testJpegBoundaryDetector() throws IOException {
        Path tempImage = Files.createTempFile("ure-jpeg-test-", ".img");
        try {
            byte[] validJpeg = createValidJpeg();
            byte[] truncatedJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46};

            byte[] disk = new byte[2048];
            System.arraycopy(validJpeg, 0, disk, 0, validJpeg.length);
            System.arraycopy(truncatedJpeg, 0, disk, 1024, truncatedJpeg.length);

            Files.write(tempImage, disk);

            try (SectorReader reader = new SectorReader(tempImage.toString())) {
                JpegBoundaryDetector detector = new JpegBoundaryDetector();

                BoundaryResult validResult = detector.detectBoundary(reader, 0, 50000);
                check("Valid JPEG boundary EXACT", validResult.getStatus() == BoundaryStatus.EXACT);
                check("Valid JPEG length exact", validResult.getLength() == validJpeg.length);
                check("Valid JPEG has valid footer", validResult.hasValidFooter());

                BoundaryResult truncResult = detector.detectBoundary(reader, 1024, 500);
                check("Truncated JPEG boundary TRUNCATED", truncResult.getStatus() == BoundaryStatus.TRUNCATED);
                check("Truncated JPEG has no valid footer", !truncResult.hasValidFooter());

                BoundaryResult invalidResult = detector.detectBoundary(reader, 512, 500);
                check("Zero sector invalid JPEG", invalidResult.getStatus() == BoundaryStatus.INVALID);
            }
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }

    /**
     * Test 4: PNG boundary detector specific cases.
     */
    private static void testPngBoundaryDetector() throws IOException {
        Path tempImage = Files.createTempFile("ure-png-test-", ".img");
        try {
            byte[] validPng = createValidPng();
            // Corrupt chunk type PNG
            byte[] corruptPng = Arrays.copyOf(validPng, validPng.length);
            corruptPng[12] = (byte) 0xFF; // corrupt chunk type characters

            byte[] disk = new byte[2048];
            System.arraycopy(validPng, 0, disk, 0, validPng.length);
            System.arraycopy(corruptPng, 0, disk, 1024, corruptPng.length);

            Files.write(tempImage, disk);

            try (SectorReader reader = new SectorReader(tempImage.toString())) {
                PngBoundaryDetector detector = new PngBoundaryDetector();

                BoundaryResult validResult = detector.detectBoundary(reader, 0, 50000);
                check("Valid PNG boundary EXACT", validResult.getStatus() == BoundaryStatus.EXACT);
                check("Valid PNG length exact", validResult.getLength() == validPng.length);

                BoundaryResult corruptResult = detector.detectBoundary(reader, 1024, 50000);
                check("Corrupted PNG chunk detected as TRUNCATED/bounded", corruptResult.getStatus() == BoundaryStatus.TRUNCATED);
            }
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }

    /**
     * Test 5: PDF boundary detector with multiple EOF markers.
     */
    private static void testPdfBoundaryDetector() throws IOException {
        Path tempImage = Files.createTempFile("ure-pdf-test-", ".img");
        try {
            // PDF with incremental update (2 %%EOF markers)
            String pdfContent = "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n%%EOF\n2 0 obj<</Type/Pages>>endobj\n%%EOF\n";
            byte[] pdfBytes = pdfContent.getBytes(StandardCharsets.US_ASCII);

            byte[] disk = new byte[2048];
            System.arraycopy(pdfBytes, 0, disk, 0, pdfBytes.length);

            Files.write(tempImage, disk);

            try (SectorReader reader = new SectorReader(tempImage.toString())) {
                PdfBoundaryDetector detector = new PdfBoundaryDetector();

                BoundaryResult result = detector.detectBoundary(reader, 0, 50000);
                check("Multi-EOF PDF boundary EXACT", result.getStatus() == BoundaryStatus.EXACT);
                check("Multi-EOF PDF encompasses full document", result.getLength() == pdfBytes.length);
                check("Multi-EOF PDF footer is valid", result.hasValidFooter());
            }
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }

    /**
     * Test 6: ZIP boundary detector with comment length.
     */
    private static void testZipBoundaryDetector() throws IOException {
        Path tempImage = Files.createTempFile("ure-zip-test-", ".img");
        try {
            byte[] zip = createValidZipWithComment("MINION URE ZIP TEST");

            byte[] disk = new byte[2048];
            System.arraycopy(zip, 0, disk, 0, zip.length);

            Files.write(tempImage, disk);

            try (SectorReader reader = new SectorReader(tempImage.toString())) {
                ZipBoundaryDetector detector = new ZipBoundaryDetector();

                BoundaryResult result = detector.detectBoundary(reader, 0, 50000);
                check("ZIP with comment boundary EXACT", result.getStatus() == BoundaryStatus.EXACT);
                check("ZIP with comment length exact", result.getLength() == zip.length);
                check("ZIP has valid footer", result.hasValidFooter());
            }
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }

    /**
     * Test 7: False positive rejection.
     */
    private static void testFalsePositiveRejection() throws IOException {
        Path tempImage = Files.createTempFile("ure-false-positive-", ".img");
        try {
            byte[] disk = new byte[2048];
            // Random occurrences of FF D8 00 (not JPEG), PK 01 02 (central dir without local header), %PDF- (truncated at EOF)
            disk[100] = (byte) 0xFF;
            disk[101] = (byte) 0xD8;
            disk[102] = 0x00; // not 0xFF

            disk[300] = 0x50; // 'P'
            disk[301] = 0x4B; // 'K'
            disk[302] = 0x01; // not 0x03 0x04

            Files.write(tempImage, disk);

            try (SectorReader reader = new SectorReader(tempImage.toString())) {
                SignatureScanner scanner = new SignatureScanner(reader);
                List<CarvedFile> results = scanner.scan();

                check("False signatures rejected (0 files found)", results.isEmpty());
            }
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }

    /**
     * Test 8: Empty and truncated images.
     */
    private static void testEmptyAndTruncatedImages() throws IOException {
        Path emptyImage = Files.createTempFile("ure-empty-carve-", ".img");
        try {
            byte[] disk = new byte[2048];
            Files.write(emptyImage, disk);

            try (SectorReader reader = new SectorReader(emptyImage.toString())) {
                SignatureScanner scanner = new SignatureScanner(reader);
                List<CarvedFile> results = scanner.scan();
                check("Empty image produces 0 carved files", results.isEmpty());
            }
        } finally {
            Files.deleteIfExists(emptyImage);
        }
    }

    /**
     * Test 9: Determinism (scanning twice yields identical results).
     */
    private static void testDeterminism() throws IOException {
        Path tempImage = Files.createTempFile("ure-determinism-", ".img");
        try {
            byte[] jpeg = createValidJpeg();
            byte[] png = createValidPng();

            byte[] disk = new byte[4096];
            System.arraycopy(jpeg, 0, disk, 512, jpeg.length);
            System.arraycopy(png, 0, disk, 2048, png.length);

            Files.write(tempImage, disk);

            try (SectorReader reader = new SectorReader(tempImage.toString())) {
                SignatureScanner scanner = new SignatureScanner(reader);
                List<CarvedFile> run1 = scanner.scan();
                List<CarvedFile> run2 = scanner.scan();

                check("Deterministic run count matches", run1.size() == run2.size());
                if (run1.size() == run2.size() && run1.size() == 2) {
                    check("File 1 offset matches across runs", run1.get(0).getStartByteOffset() == run2.get(0).getStartByteOffset());
                    check("File 1 length matches across runs", run1.get(0).getLength() == run2.get(0).getLength());
                    check("File 2 offset matches across runs", run1.get(1).getStartByteOffset() == run2.get(1).getStartByteOffset());
                    check("File 2 length matches across runs", run1.get(1).getLength() == run2.get(1).getLength());
                }
            }
        } finally {
            Files.deleteIfExists(tempImage);
        }
    }

    /**
     * Test 10: File recovery extraction.
     */
    private static void testFileRecoveryExtraction() throws IOException {
        Path tempImage = Files.createTempFile("ure-extract-", ".img");
        Path recoveredFile = Files.createTempFile("ure-recovered-", ".jpg");
        try {
            byte[] jpeg = createValidJpeg();
            byte[] disk = new byte[2048];
            System.arraycopy(jpeg, 0, disk, 512, jpeg.length);

            Files.write(tempImage, disk);

            try (SectorReader reader = new SectorReader(tempImage.toString())) {
                SignatureScanner scanner = new SignatureScanner(reader);
                List<CarvedFile> results = scanner.scan();

                check("One JPEG found for extraction", results.size() == 1);
                if (results.size() == 1) {
                    CarvedFile file = results.get(0);

                    // Test streaming recovery to destination path
                    file.recoverTo(reader, recoveredFile);

                    byte[] writtenBytes = Files.readAllBytes(recoveredFile);
                    check("Extracted file byte size matches original", writtenBytes.length == jpeg.length);
                    check("Extracted file content matches original", Arrays.equals(writtenBytes, jpeg));

                    // Test readAllBytes helper
                    byte[] inMemoryBytes = file.readAllBytes(reader);
                    check("readAllBytes matches original", Arrays.equals(inMemoryBytes, jpeg));
                }
            }
        } finally {
            Files.deleteIfExists(tempImage);
            Files.deleteIfExists(recoveredFile);
        }
    }

    // =========================================================================
    // Synthetic File Generators
    // =========================================================================

    private static byte[] createValidJpeg() {
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, // SOI + APP0
                0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                (byte) 0xFF, (byte) 0xDB, 0x00, 0x43, 0x00, // DQT marker
                0x01, 0x01, 0x01, 0x01, // dummy quantization table
                (byte) 0xFF, (byte) 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, // SOS marker
                0x12, 0x34, (byte) 0xFF, 0x00, 0x56, 0x78, // compressed payload with byte-stuffing
                (byte) 0xFF, (byte) 0xD9 // EOI
        };
    }

    private static byte[] createValidPng() {
        // PNG Header + IHDR chunk (13 bytes payload) + IEND chunk (0 bytes payload)
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        byte[] ihdrChunk = new byte[]{
                0x00, 0x00, 0x00, 0x0D, // Length: 13
                'I', 'H', 'D', 'R',     // Type: IHDR
                0x00, 0x00, 0x00, 0x01, // Width: 1
                0x00, 0x00, 0x00, 0x01, // Height: 1
                0x08, 0x02, 0x00, 0x00, 0x00, // Bit depth, Color type, etc.
                0x4A, 0x3C, (byte) 0x9B, 0x5E // CRC
        };

        byte[] iendChunk = new byte[]{
                0x00, 0x00, 0x00, 0x00, // Length: 0
                'I', 'E', 'N', 'D',     // Type: IEND
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82 // CRC
        };

        byte[] result = new byte[pngHeader.length + ihdrChunk.length + iendChunk.length];
        System.arraycopy(pngHeader, 0, result, 0, pngHeader.length);
        System.arraycopy(ihdrChunk, 0, result, pngHeader.length, ihdrChunk.length);
        System.arraycopy(iendChunk, 0, result, pngHeader.length + ihdrChunk.length, iendChunk.length);
        return result;
    }

    private static byte[] createValidPdf() {
        String pdf = "%PDF-1.4\n" +
                "1 0 obj\n<</Type/Catalog/Pages 2 0 R>>\nendobj\n" +
                "2 0 obj\n<</Type/Pages/Kids[3 0 R]/Count 1>>\nendobj\n" +
                "3 0 obj\n<</Type/Page/MediaBox[0 0 612 792]>>\nendobj\n" +
                "xref\n0 4\n0000000000 65535 f \n" +
                "trailer\n<</Size 4/Root 1 0 R>>\nstartxref\n180\n%%EOF\n";
        return pdf.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] createValidZip() {
        return createValidZipWithComment("");
    }

    private static byte[] createValidZipWithComment(String comment) {
        byte[] commentBytes = comment.getBytes(StandardCharsets.US_ASCII);
        int commentLen = commentBytes.length;

        // Local file header (PK 03 04)
        byte[] localHeader = new byte[]{
                0x50, 0x4B, 0x03, 0x04, // signature
                0x14, 0x00,             // version needed: 2.0
                0x00, 0x00,             // general purpose bit flag
                0x00, 0x00,             // compression method: none
                0x00, 0x00, 0x00, 0x00, // time/date
                0x00, 0x00, 0x00, 0x00, // crc32
                0x00, 0x00, 0x00, 0x00, // compressed size
                0x00, 0x00, 0x00, 0x00, // uncompressed size
                0x04, 0x00,             // filename length: 4 ("test")
                0x00, 0x00,             // extra field length: 0
                't', 'e', 's', 't'      // filename
        };

        // End of central directory record (PK 05 06)
        byte[] eocd = new byte[]{
                0x50, 0x4B, 0x05, 0x06, // signature
                0x00, 0x00,             // disk number
                0x00, 0x00,             // disk with central dir
                0x00, 0x00,             // entries on disk
                0x00, 0x00,             // total entries
                0x00, 0x00, 0x00, 0x00, // size of central dir
                0x00, 0x00, 0x00, 0x00, // offset of central dir
                (byte) (commentLen & 0xFF), (byte) ((commentLen >> 8) & 0xFF) // comment length
        };

        byte[] zip = new byte[localHeader.length + eocd.length + commentLen];
        System.arraycopy(localHeader, 0, zip, 0, localHeader.length);
        System.arraycopy(eocd, 0, zip, localHeader.length, eocd.length);
        if (commentLen > 0) {
            System.arraycopy(commentBytes, 0, zip, localHeader.length + eocd.length, commentLen);
        }
        return zip;
    }

    private static void check(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + description);
        } else {
            failed++;
            System.out.println("[FAIL] " + description);
        }
    }
}
