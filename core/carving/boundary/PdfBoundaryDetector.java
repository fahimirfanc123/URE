package core.carving.boundary;

import core.disk.SectorReader;

import java.io.IOException;

/**
 * Boundary detector for PDF documents.
 *
 * Validates the %PDF- header and scans forward for the terminating %%EOF marker,
 * accommodating trailing whitespace and incremental document updates.
 */
public class PdfBoundaryDetector implements FileBoundaryDetector {

    private static final int CHUNK_SIZE = 65536; // 64 KB
    private static final byte[] PDF_HEADER = {0x25, 0x50, 0x44, 0x46, 0x2D}; // %PDF-
    private static final byte[] PDF_EOF = {0x25, 0x25, 0x45, 0x4F, 0x46};    // %%EOF

    @Override
    public BoundaryResult detectBoundary(SectorReader reader, long startByteOffset, long maxScanBytes) throws IOException {
        if (reader == null) {
            throw new IllegalArgumentException("SectorReader cannot be null");
        }

        // 1. Verify %PDF- header
        byte[] header = SectorByteReader.readBytes(reader, startByteOffset, 5);
        if (header.length < 5) {
            return BoundaryResult.invalid("Truncated header: less than 5 bytes available");
        }

        for (int i = 0; i < 5; i++) {
            if (header[i] != PDF_HEADER[i]) {
                return BoundaryResult.invalid("Invalid PDF header: expected %PDF-");
            }
        }

        long currentOffset = startByteOffset + 5;
        long limitOffset = startByteOffset + maxScanBytes;
        long totalDiskBytes = reader.getTotalSectors() * (long) reader.getSectorSize();
        long actualLimit = Math.min(limitOffset, totalDiskBytes);

        long lastValidEofOffset = -1;
        byte[] overlap = new byte[8];
        int overlapLen = 0;

        // 2. Scan forward for %%EOF markers
        while (currentOffset < actualLimit) {
            int toRead = (int) Math.min(CHUNK_SIZE, actualLimit - currentOffset);
            byte[] chunk = SectorByteReader.readBytes(reader, currentOffset, toRead);
            if (chunk.length == 0) {
                break;
            }

            // Combine overlap with current chunk
            byte[] searchBuffer = new byte[overlapLen + chunk.length];
            if (overlapLen > 0) {
                System.arraycopy(overlap, 0, searchBuffer, 0, overlapLen);
            }
            System.arraycopy(chunk, 0, searchBuffer, overlapLen, chunk.length);

            long bufferBaseOffset = currentOffset - overlapLen;

            // Search for %%EOF in searchBuffer
            for (int i = 0; i <= searchBuffer.length - 5; i++) {
                if (searchBuffer[i] == 0x25 &&
                    searchBuffer[i + 1] == 0x25 &&
                    searchBuffer[i + 2] == 0x45 &&
                    searchBuffer[i + 3] == 0x4F &&
                    searchBuffer[i + 4] == 0x46) {

                    long eofEnd = bufferBaseOffset + i + 5;

                    // Consume trailing whitespace (\r, \n, space)
                    while (eofEnd < actualLimit) {
                        int nextByte = SectorByteReader.readByte(reader, eofEnd);
                        if (nextByte == '\r' || nextByte == '\n' || nextByte == ' ' || nextByte == '\t') {
                            eofEnd++;
                        } else {
                            break;
                        }
                    }

                    lastValidEofOffset = eofEnd;
                }
            }

            // Prepare overlap for next iteration
            overlapLen = Math.min(8, chunk.length);
            System.arraycopy(chunk, chunk.length - overlapLen, overlap, 0, overlapLen);

            currentOffset += chunk.length;
        }

        if (lastValidEofOffset > startByteOffset) {
            long length = lastValidEofOffset - startByteOffset;
            return BoundaryResult.exact(length, "Valid PDF %%EOF marker detected");
        }

        long truncatedLength = actualLimit - startByteOffset;
        return BoundaryResult.truncated(truncatedLength, "PDF %%EOF marker not found within scan limit");
    }
}
