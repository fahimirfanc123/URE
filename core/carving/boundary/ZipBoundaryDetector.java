package core.carving.boundary;

import core.disk.SectorReader;

import java.io.IOException;

/**
 * Boundary detector for ZIP archives (including DOCX, XLSX, JAR, APK, etc.).
 *
 * Validates the Local File Header signature (PK 03 04) and scans forward for the
 * End of Central Directory (EOCD) record (PK 05 06), parsing the comment length field
 * to determine the exact archive boundary.
 */
public class ZipBoundaryDetector implements FileBoundaryDetector {

    private static final int CHUNK_SIZE = 65536; // 64 KB
    private static final byte[] ZIP_HEADER = {0x50, 0x4B, 0x03, 0x04}; // PK\x03\x04
    private static final byte[] EOCD_SIG = {0x50, 0x4B, 0x05, 0x06};   // PK\x05\x06

    @Override
    public BoundaryResult detectBoundary(SectorReader reader, long startByteOffset, long maxScanBytes) throws IOException {
        if (reader == null) {
            throw new IllegalArgumentException("SectorReader cannot be null");
        }

        // 1. Verify PK 03 04 header
        byte[] header = SectorByteReader.readBytes(reader, startByteOffset, 4);
        if (header.length < 4) {
            return BoundaryResult.invalid("Truncated header: less than 4 bytes available");
        }

        for (int i = 0; i < 4; i++) {
            if (header[i] != ZIP_HEADER[i]) {
                return BoundaryResult.invalid("Invalid ZIP header: expected PK 03 04");
            }
        }

        long currentOffset = startByteOffset + 4;
        long limitOffset = startByteOffset + maxScanBytes;
        long totalDiskBytes = reader.getTotalSectors() * (long) reader.getSectorSize();
        long actualLimit = Math.min(limitOffset, totalDiskBytes);

        byte[] overlap = new byte[8];
        int overlapLen = 0;

        // 2. Scan forward for EOCD signature (PK 05 06)
        while (currentOffset < actualLimit) {
            int toRead = (int) Math.min(CHUNK_SIZE, actualLimit - currentOffset);
            byte[] chunk = SectorByteReader.readBytes(reader, currentOffset, toRead);
            if (chunk.length == 0) {
                break;
            }

            byte[] searchBuffer = new byte[overlapLen + chunk.length];
            if (overlapLen > 0) {
                System.arraycopy(overlap, 0, searchBuffer, 0, overlapLen);
            }
            System.arraycopy(chunk, 0, searchBuffer, overlapLen, chunk.length);

            long bufferBaseOffset = currentOffset - overlapLen;

            for (int i = 0; i <= searchBuffer.length - 4; i++) {
                if (searchBuffer[i] == EOCD_SIG[0] &&
                    searchBuffer[i + 1] == EOCD_SIG[1] &&
                    searchBuffer[i + 2] == EOCD_SIG[2] &&
                    searchBuffer[i + 3] == EOCD_SIG[3]) {

                    long eocdOffset = bufferBaseOffset + i;

                    // Read EOCD record (22 bytes min)
                    byte[] eocdRecord = SectorByteReader.readBytes(reader, eocdOffset, 22);
                    if (eocdRecord.length == 22) {
                        int commentLength = (eocdRecord[20] & 0xFF) | ((eocdRecord[21] & 0xFF) << 8);
                        long totalFileLength = (eocdOffset - startByteOffset) + 22 + commentLength;

                        if (startByteOffset + totalFileLength <= actualLimit) {
                            return BoundaryResult.exact(totalFileLength,
                                    "Valid ZIP EOCD structure detected (comment length: " + commentLength + ")");
                        }
                    }
                }
            }

            overlapLen = Math.min(8, chunk.length);
            System.arraycopy(chunk, chunk.length - overlapLen, overlap, 0, overlapLen);

            currentOffset += chunk.length;
        }

        long truncatedLength = actualLimit - startByteOffset;
        return BoundaryResult.truncated(truncatedLength, "ZIP EOCD record not found within scan limit");
    }
}
