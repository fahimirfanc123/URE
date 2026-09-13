package core.disk;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * SectorReader provides low-level access to disk sectors from raw disk images or physical drives.
 * Supports reading individual sectors at a fixed size (default 512 bytes for standard sectors).
 *
 * This is the foundational I/O layer for URE, enabling all higher-level recovery operations
 * to read raw disk data in a structured manner.
 */
public class SectorReader implements Closeable {

    private static final int DEFAULT_SECTOR_SIZE = 512;

    private final RandomAccessFile file;
    private final int sectorSize;
    private final long totalSectors;

    /**
     * Opens a disk image or device file for sector-level reading.
     *
     * @param path Path to the disk image or device file
     * @throws IOException If the file cannot be opened
     */
    public SectorReader(String path) throws IOException {
        this(path, DEFAULT_SECTOR_SIZE);
    }

    /**
     * Opens a disk image or device file with a custom sector size.
     * Useful for Advanced Format (4K) drives or other non-standard sector sizes.
     *
     * @param path Path to the disk image or device file
     * @param sectorSize Sector size in bytes (typically 512 or 4096)
     * @throws IOException If the file cannot be opened
     * @throws IllegalArgumentException If sector size is invalid
     */
    public SectorReader(String path, int sectorSize) throws IOException {
        if (sectorSize <= 0) {
            throw new IllegalArgumentException("Sector size must be positive");
        }
        this.sectorSize = sectorSize;
        this.file = new RandomAccessFile(path, "r");
        this.totalSectors = file.length() / sectorSize;
    }

    /**
     * Reads a single sector from the disk image.
     *
     * @param sectorNumber The sector index to read (0-based)
     * @return A byte array containing the sector data (size = sectorSize)
     * @throws IOException If a read error occurs
     * @throws IllegalArgumentException If sector number is out of bounds
     */
    public byte[] readSector(long sectorNumber) throws IOException {
        if (sectorNumber < 0 || sectorNumber >= totalSectors) {
            throw new IllegalArgumentException(
                String.format("Sector %d out of bounds (0-%d)", sectorNumber, totalSectors - 1)
            );
        }

        long position = sectorNumber * (long) sectorSize;
        file.seek(position);

        byte[] buffer = new byte[sectorSize];
        file.readFully(buffer);

        return buffer;
    }

    /**
     * Reads multiple consecutive sectors into a single buffer.
     * More efficient than calling readSector() in a loop for large reads.
     *
     * @param startSector The starting sector number (0-based)
     * @param count The number of consecutive sectors to read
     * @return A byte array containing all requested sector data
     * @throws IOException If a read error occurs
     * @throws IllegalArgumentException If the range is invalid
     */
    public byte[] readSectors(long startSector, int count) throws IOException {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }
        if (startSector < 0 || (startSector + count) > totalSectors) {
            throw new IllegalArgumentException(
                String.format("Sector range %d-%d out of bounds (0-%d)",
                    startSector, startSector + count - 1, totalSectors - 1)
            );
        }

        long position = startSector * (long) sectorSize;
        file.seek(position);

        byte[] buffer = new byte[sectorSize * count];
        file.readFully(buffer);

        return buffer;
    }

    /**
     * Gets the total number of sectors in the disk image.
     * Calculated as: file_size / sector_size
     *
     * @return Total sector count
     */
    public long getTotalSectors() {
        return totalSectors;
    }

    /**
     * Gets the sector size in bytes (typically 512 or 4096).
     *
     * @return Sector size
     */
    public int getSectorSize() {
        return sectorSize;
    }

    /**
     * Gets the total file size in bytes.
     *
     * @return File size
     * @throws IOException If file size cannot be determined
     */
    public long getTotalBytes() throws IOException {
        return file.length();
    }

    /**
     * Closes the underlying file handle. Should be called when done reading.
     * Automatically called if used with try-with-resources.
     *
     * @throws IOException If close fails
     */
    @Override
    public void close() throws IOException {
        if (file != null) {
            file.close();
        }
    }
}
