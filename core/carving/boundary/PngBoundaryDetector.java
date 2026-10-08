package core.carving.boundary;

import core.disk.SectorReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Structural boundary detector for PNG images.
 *
 * Validates the 8-byte PNG signature (89 50 4E 47 0D 0A 1A 0A) and parses consecutive
 * PNG chunks (4-byte length + 4-byte type + data payload + 4-byte CRC) until the terminating
 * IEND chunk is encountered.
 */
public class PngBoundaryDetector implements FileBoundaryDetector {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Override
    public BoundaryResult detectBoundary(SectorReader reader, long startByteOffset, long maxScanBytes) throws IOException {
        if (reader == null) {
            throw new IllegalArgumentException("SectorReader cannot be null");
        }

        // 1. Verify 8-byte PNG signature
        byte[] sig = SectorByteReader.readBytes(reader, startByteOffset, 8);
        if (sig.length < 8) {
            return BoundaryResult.invalid("Truncated header: less than 8 bytes available");
        }

        for (int i = 0; i < 8; i++) {
            if (sig[i] != PNG_SIGNATURE[i]) {
                return BoundaryResult.invalid("Invalid PNG signature");
            }
        }

        long currentOffset = startByteOffset + 8;
        long limitOffset = startByteOffset + maxScanBytes;
        long totalDiskBytes = reader.getTotalSectors() * (long) reader.getSectorSize();
        long actualLimit = Math.min(limitOffset, totalDiskBytes);

        boolean firstChunk = true;

        // 2. Iterate through PNG chunks
        while (currentOffset + 12 <= actualLimit) {
            // Read 8-byte chunk header (4-byte length + 4-byte type)
            byte[] chunkHeader = SectorByteReader.readBytes(reader, currentOffset, 8);
            if (chunkHeader.length < 8) {
                break;
            }

            long chunkDataLength = ((long) (chunkHeader[0] & 0xFF) << 24)
                    | ((long) (chunkHeader[1] & 0xFF) << 16)
                    | ((long) (chunkHeader[2] & 0xFF) << 8)
                    | ((long) (chunkHeader[3] & 0xFF));

            if (chunkDataLength < 0 || chunkDataLength > maxScanBytes) {
                return BoundaryResult.truncated(currentOffset - startByteOffset,
                        "Corrupted PNG chunk data length: " + chunkDataLength);
            }

            String chunkType = new String(chunkHeader, 4, 4, StandardCharsets.US_ASCII);

            // Validate chunk type characters (must be ASCII letters)
            for (int i = 0; i < 4; i++) {
                char c = chunkType.charAt(i);
                if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) {
                    return BoundaryResult.truncated(currentOffset - startByteOffset,
                            "Corrupted PNG chunk type identifier: " + chunkType);
                }
            }

            if (firstChunk) {
                if (!"IHDR".equals(chunkType)) {
                    return BoundaryResult.invalid("First PNG chunk must be IHDR, found: " + chunkType);
                }
                firstChunk = false;
            }

            long totalChunkLength = chunkDataLength + 12; // Length (4) + Type (4) + Data (L) + CRC (4)
            long nextOffset = currentOffset + totalChunkLength;

            if ("IEND".equals(chunkType)) {
                // Terminating chunk found!
                if (nextOffset > actualLimit) {
                    return BoundaryResult.truncated(actualLimit - startByteOffset,
                            "PNG IEND chunk truncated at end of disk");
                }
                long fileLength = nextOffset - startByteOffset;
                return BoundaryResult.exact(fileLength, "Valid PNG IEND chunk reached");
            }

            if (nextOffset > actualLimit) {
                return BoundaryResult.truncated(actualLimit - startByteOffset,
                        "PNG chunk " + chunkType + " extends beyond scan limit");
            }

            currentOffset = nextOffset;
        }

        long truncatedLength = Math.max(8, currentOffset - startByteOffset);
        return BoundaryResult.truncated(truncatedLength, "PNG IEND chunk not found before scan limit");
    }
}
