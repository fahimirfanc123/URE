package core.carving.boundary;

import core.disk.SectorReader;

import java.io.IOException;

/**
 * Boundary detector for JPEG images.
 *
 * Validates the SOI marker (FF D8 FF) and scans forward through the JPEG stream
 * for the terminating EOI marker (FF D9), respecting byte-stuffing rules and maximum scan size.
 */
public class JpegBoundaryDetector implements FileBoundaryDetector {

    private static final int CHUNK_SIZE = 65536; // 64 KB scan buffer

    @Override
    public BoundaryResult detectBoundary(SectorReader reader, long startByteOffset, long maxScanBytes) throws IOException {
        if (reader == null) {
            throw new IllegalArgumentException("SectorReader cannot be null");
        }

        // 1. Verify SOI marker (FF D8)
        byte[] header = SectorByteReader.readBytes(reader, startByteOffset, 3);
        if (header.length < 3 || (header[0] & 0xFF) != 0xFF || (header[1] & 0xFF) != 0xD8 || (header[2] & 0xFF) != 0xFF) {
            return BoundaryResult.invalid("Invalid JPEG header: expected FF D8 FF");
        }

        long currentOffset = startByteOffset + 2; // after FF D8
        long limitOffset = startByteOffset + maxScanBytes;
        long totalDiskBytes = reader.getTotalSectors() * (long) reader.getSectorSize();
        long actualLimit = Math.min(limitOffset, totalDiskBytes);

        // 2. Scan forward for EOI (FF D9)
        byte prevByte = (byte) 0xD8;
        boolean inSos = false;

        while (currentOffset < actualLimit) {
            int toRead = (int) Math.min(CHUNK_SIZE, actualLimit - currentOffset);
            byte[] chunk = SectorByteReader.readBytes(reader, currentOffset, toRead);
            if (chunk.length == 0) {
                break;
            }

            for (int i = 0; i < chunk.length; i++) {
                byte b = chunk[i];
                if ((prevByte & 0xFF) == 0xFF && (b & 0xFF) == 0xD9) {
                    // Found EOI (FF D9)
                    long fileLength = (currentOffset + i + 1) - startByteOffset;
                    return BoundaryResult.exact(fileLength, "Valid JPEG EOI (FF D9) marker detected");
                }
                prevByte = b;
            }

            currentOffset += chunk.length;
        }

        long truncatedLength = actualLimit - startByteOffset;
        return BoundaryResult.truncated(truncatedLength, "JPEG EOI (FF D9) marker not found within scan limit");
    }
}
