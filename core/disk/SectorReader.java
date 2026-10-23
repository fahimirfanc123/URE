
package core.disk;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/**
 * Read-only sector access for disk images and device files.
 *
 * Supports:
 * 1. Reading an entire image.
 * 2. Reading from a byte offset to the end of an image.
 * 3. Reading a bounded region, such as a partition.
 *
 * All sector numbers are relative to this reader's visible region.
 */
public class SectorReader implements Closeable {

    private static final int DEFAULT_SECTOR_SIZE = 512;

    private final RandomAccessFile file;
    private final int sectorSize;
    private final long totalSectors;
    private final long baseOffsetBytes;
    private final long totalBytes;

    public SectorReader(String path) throws IOException {
        this(path, DEFAULT_SECTOR_SIZE, 0L);
    }

    public SectorReader(String path, int sectorSize) throws IOException {
        this(path, sectorSize, 0L);
    }

    /**
     * Opens a read-only view starting at a byte offset and extending
     * to the end of the image.
     */
    public SectorReader(
            String path,
            int sectorSize,
            long baseOffsetBytes
    ) throws IOException {
        this(path, sectorSize, baseOffsetBytes, 0L, false);
    }

    /**
     * Opens a read-only, partition-bounded view.
     *
     * @param path image or device path
     * @param sectorSize sector size in bytes
     * @param baseOffsetBytes starting byte offset
     * @param lengthBytes length of the visible region in bytes
     */
    public SectorReader(
            String path,
            int sectorSize,
            long baseOffsetBytes,
            long lengthBytes
    ) throws IOException {
        this(path, sectorSize, baseOffsetBytes, lengthBytes, true);
    }

    private SectorReader(
            String path,
            int sectorSize,
            long baseOffsetBytes,
            long requestedLengthBytes,
            boolean bounded
    ) throws IOException {

        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "Path cannot be empty"
            );
        }

        if (sectorSize <= 0) {
            throw new IllegalArgumentException(
                    "Sector size must be positive"
            );
        }

        if (baseOffsetBytes < 0) {
            throw new IllegalArgumentException(
                    "Base offset cannot be negative"
            );
        }

        if (bounded && requestedLengthBytes <= 0) {
            throw new IllegalArgumentException(
                    "Bounded region length must be positive"
            );
        }

        if (bounded && requestedLengthBytes % sectorSize != 0) {
            throw new IllegalArgumentException(
                    "Bounded region length must be a multiple "
                            + "of the sector size"
            );
        }

        RandomAccessFile opened = new RandomAccessFile(path, "r");

        long visibleBytes;

        try {
            long imageLength = getDeviceCapacityBytes(path, opened);

            if (baseOffsetBytes > imageLength) {
                throw new IllegalArgumentException(
                        "Base offset exceeds image size"
                );
            }

            long remainingBytes = imageLength - baseOffsetBytes;

            if (bounded) {
                if (requestedLengthBytes > remainingBytes) {
                    throw new IllegalArgumentException(
                            "Bounded region extends beyond image size"
                    );
                }

                visibleBytes = requestedLengthBytes;
            } else {
                visibleBytes = remainingBytes;
            }

        } catch (IOException | RuntimeException e) {
            try {
                opened.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }

        this.file = opened;
        this.sectorSize = sectorSize;
        this.baseOffsetBytes = baseOffsetBytes;
        this.totalBytes = visibleBytes;
        this.totalSectors = visibleBytes / sectorSize;
    }

    /**
     * Returns the actual capacity for a block device, or the file length
     * for a regular disk image. Device capacity lookup fails closed.
     */
    private static long getDeviceCapacityBytes(
            String path,
            RandomAccessFile opened
    ) throws IOException {

        if (Files.isRegularFile(Path.of(path))) {
            return opened.length();
        }

        Process process = new ProcessBuilder(
                "blockdev", "--getsize64", path
        ).redirectErrorStream(true).start();

        byte[] output = process.getInputStream().readAllBytes();

        try {
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(
                        "Timed out determining device capacity: " + path
                );
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Interrupted while determining device capacity: " + path,
                    e
            );
        }

        String result = new String(output, StandardCharsets.UTF_8).trim();

        if (process.exitValue() != 0) {
            throw new IOException(
                    "Could not determine device capacity for " + path
                            + ": " + result
            );
        }

        try {
            long capacity = Long.parseLong(result);
            if (capacity <= 0) {
                throw new IOException("Device capacity is not positive: " + path);
            }
            return capacity;
        } catch (NumberFormatException e) {
            throw new IOException(
                    "Invalid device capacity returned for " + path
                            + ": " + result,
                    e
            );
        }
    }

    /**
     * Reads one sector relative to this reader's visible region.
     */
    public synchronized byte[] readSector(long sectorNumber)
            throws IOException {

        checkRange(sectorNumber, 1);

        byte[] buffer = new byte[sectorSize];
        file.seek(absolutePosition(sectorNumber));
        file.readFully(buffer);

        return buffer;
    }

    /**
     * Reads consecutive sectors relative to this reader's visible region.
     */
    public synchronized byte[] readSectors(
            long startSector,
            int count
    ) throws IOException {

        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Count must be positive"
            );
        }

        checkRange(startSector, count);

        long bytesToRead = (long) sectorSize * count;

        if (bytesToRead > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Requested read is too large"
            );
        }

        byte[] buffer = new byte[(int) bytesToRead];
        file.seek(absolutePosition(startSector));
        file.readFully(buffer);

        return buffer;
    }

    private void checkRange(long startSector, int count) {
        if (startSector < 0
                || startSector >= totalSectors
                || count > totalSectors - startSector) {

            throw new IllegalArgumentException(
                    String.format(
                            "Sector range %d-%d out of bounds "
                                    + "(valid sectors: 0-%d)",
                            startSector,
                            startSector + (long) count - 1,
                            totalSectors - 1
                    )
            );
        }
    }

    private long absolutePosition(long sectorNumber) {
        return baseOffsetBytes + sectorNumber * (long) sectorSize;
    }

    public long getTotalSectors() {
        return totalSectors;
    }

    public int getSectorSize() {
        return sectorSize;
    }

    /**
     * Returns the visible region's byte length.
     */
    public long getTotalBytes() {
        return totalBytes;
    }

    public long getBaseOffsetBytes() {
        return baseOffsetBytes;
    }

    @Override
    public synchronized void close() throws IOException {
        file.close();
    }
}
