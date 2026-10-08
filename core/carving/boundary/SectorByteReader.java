package core.carving.boundary;

import core.disk.SectorReader;

import java.io.IOException;

/**
 * Utility for reading arbitrary byte ranges from a SectorReader with bounds safety.
 */
public class SectorByteReader {

    /**
     * Reads up to length bytes starting from an arbitrary byte offset on disk.
     *
     * @param reader active SectorReader
     * @param byteOffset absolute byte offset on disk
     * @param length number of bytes to read
     * @return byte array containing the read data (may be smaller than requested if hitting disk boundary)
     * @throws IOException if disk read fails
     */
    public static byte[] readBytes(SectorReader reader, long byteOffset, int length) throws IOException {
        if (reader == null) {
            throw new IllegalArgumentException("SectorReader cannot be null");
        }
        if (length <= 0 || byteOffset < 0) {
            return new byte[0];
        }

        int sectorSize = reader.getSectorSize();
        long totalBytes = reader.getTotalSectors() * (long) sectorSize;

        if (byteOffset >= totalBytes) {
            return new byte[0];
        }

        long available = totalBytes - byteOffset;
        int toRead = (int) Math.min(length, available);
        if (toRead <= 0) {
            return new byte[0];
        }

        long startSector = byteOffset / sectorSize;
        int offsetInStartSector = (int) (byteOffset % sectorSize);

        long endByte = byteOffset + toRead;
        long endSector = (endByte - 1) / sectorSize;
        int sectorCount = (int) (endSector - startSector + 1);

        byte[] rawSectorData = reader.readSectors(startSector, sectorCount);
        byte[] result = new byte[toRead];
        System.arraycopy(rawSectorData, offsetInStartSector, result, 0, toRead);

        return result;
    }

    /**
     * Reads a single byte at the specified absolute byte offset.
     *
     * @param reader active SectorReader
     * @param byteOffset absolute byte offset on disk
     * @return the byte value as an int (0-255), or -1 if EOF
     * @throws IOException if disk read fails
     */
    public static int readByte(SectorReader reader, long byteOffset) throws IOException {
        byte[] b = readBytes(reader, byteOffset, 1);
        if (b.length == 0) {
            return -1;
        }
        return b[0] & 0xFF;
    }
}
