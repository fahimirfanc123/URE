
package core.disk;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

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
    private final NativeReadTransport nativeTransport;
    private final long sourceCapacityBytes;
    private static final int NATIVE_CHUNK_BYTES = 1024 * 1024;
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

        Path source = Path.of(path);
        long capacity;
        long visibleBytes;
        RandomAccessFile openedFile = null;
        NativeReadTransport openedTransport = null;

        if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            openedFile = new RandomAccessFile(path, "r");
            try {
                capacity = openedFile.length();
                if (capacity <= 0) {
                    throw new IOException("Disk image is empty");
                }
                if (baseOffsetBytes > capacity) {
                    throw new IllegalArgumentException(
                            "Base offset exceeds image size");
                }
                long remaining = capacity - baseOffsetBytes;
                if (bounded && requestedLengthBytes > remaining) {
                    throw new IllegalArgumentException(
                            "Bounded region extends beyond image size");
                }
                visibleBytes = bounded ? requestedLengthBytes : remaining;
            } catch (IOException | RuntimeException e) {
                try {
                    openedFile.close();
                } catch (IOException closeError) {
                    e.addSuppressed(closeError);
                }
                throw e;
            }
        } else {
            if (!path.startsWith("/dev/")) {
                throw new IOException(
                        "Only regular disk images or eligible /dev devices are supported");
            }
            if (!PhysicalDriveDiscovery.isSelectableDevice(path)) {
                throw new IOException(
                        "Physical device is not eligible for scanning: " + path);
            }

            String helper = System.getProperty("ure.native.bridge");
            if (helper == null || helper.isBlank()) {
                helper = System.getenv("URE_NATIVE_BRIDGE");
            }
            if (helper == null || helper.isBlank()) {
                Path builtHelper = Path.of("build/native/ure-udisks-readonly-bridge");
                Path repoHelper = Path.of("native/ure-disk-helper/udisks_readonly_bridge");
                if (Files.isExecutable(builtHelper)) {
                    helper = builtHelper.toAbsolutePath().toString();
                } else if (Files.isExecutable(repoHelper)) {
                    helper = repoHelper.toAbsolutePath().toString();
                } else {
                    throw new IOException(
                            "Native bridge not found. Build it or set "
                            + "-Dure.native.bridge=/absolute/path");
                }
            }

            openedTransport = new NativeReadTransport(
                    new ProcessBuilder(helper, path));
            try {
                capacity = openedTransport.size();
                if (baseOffsetBytes > capacity) {
                    throw new IllegalArgumentException(
                            "Base offset exceeds device size");
                }
                long remaining = capacity - baseOffsetBytes;
                if (bounded && requestedLengthBytes > remaining) {
                    throw new IllegalArgumentException(
                            "Bounded region extends beyond device size");
                }
                visibleBytes = bounded ? requestedLengthBytes : remaining;
            } catch (IOException | RuntimeException e) {
                try {
                    openedTransport.close();
                } catch (IOException closeError) {
                    e.addSuppressed(closeError);
                }
                throw e;
            }
        }

        this.file = openedFile;
        this.nativeTransport = openedTransport;
        this.sourceCapacityBytes = capacity;
        this.sectorSize = sectorSize;
        this.baseOffsetBytes = baseOffsetBytes;
        this.totalBytes = visibleBytes;
        this.totalSectors = visibleBytes / sectorSize;
        if (this.totalSectors <= 0) {
            try {
                close();
            } catch (IOException ignored) {
            }
            throw new IOException(
                    "Visible source region contains no complete sectors");
        }
    }

    /**
     * Reads one sector relative to this reader's visible region.
     */
    public synchronized byte[] readSector(long sectorNumber)
            throws IOException {

        checkRange(sectorNumber, 1);

        return readBytes(absolutePosition(sectorNumber), sectorSize);
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

        return readBytes(absolutePosition(startSector), (int) bytesToRead);
    }

    private byte[] readBytes(long absoluteOffset, int length)
            throws IOException {
        if (file != null) {
            byte[] buffer = new byte[length];
            file.seek(absoluteOffset);
            file.readFully(buffer);
            return buffer;
        }

        byte[] result = new byte[length];
        int copied = 0;
        while (copied < length) {
            int chunk = Math.min(NATIVE_CHUNK_BYTES, length - copied);
            byte[] part = nativeTransport.read(
                    absoluteOffset + copied, chunk, sourceCapacityBytes);
            if (part.length != chunk) {
                throw new IOException("Native helper returned an unexpected byte count");
            }
            System.arraycopy(part, 0, result, copied, chunk);
            copied += chunk;
        }
        return result;
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
        if (nativeTransport != null) {
            nativeTransport.close();
        } else if (file != null) {
            file.close();
        }
    }
}
