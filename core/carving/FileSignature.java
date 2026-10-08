package core.carving;

import core.carving.boundary.FileBoundaryDetector;

import java.util.Arrays;

/**
 * Encapsulates the magic-byte signature, size constraints, and boundary detector
 * for a specific file format in the signature carving subsystem.
 */
public class FileSignature {

    private final FileFormat format;
    private final byte[] header;
    private final long maxScanBytes;
    private final FileBoundaryDetector boundaryDetector;

    public FileSignature(
            FileFormat format,
            byte[] header,
            long maxScanBytes,
            FileBoundaryDetector boundaryDetector) {

        if (format == null) {
            throw new IllegalArgumentException("FileFormat cannot be null");
        }
        if (header == null || header.length == 0) {
            throw new IllegalArgumentException("Header signature cannot be null or empty");
        }
        if (maxScanBytes <= 0) {
            throw new IllegalArgumentException("maxScanBytes must be positive");
        }
        if (boundaryDetector == null) {
            throw new IllegalArgumentException("boundaryDetector cannot be null");
        }

        this.format = format;
        this.header = Arrays.copyOf(header, header.length);
        this.maxScanBytes = maxScanBytes;
        this.boundaryDetector = boundaryDetector;
    }

    public FileFormat getFormat() {
        return format;
    }

    public byte[] getHeader() {
        return Arrays.copyOf(header, header.length);
    }

    public int getHeaderLength() {
        return header.length;
    }

    public long getMaxScanBytes() {
        return maxScanBytes;
    }

    public FileBoundaryDetector getBoundaryDetector() {
        return boundaryDetector;
    }

    /**
     * Checks if this signature matches the given buffer starting at the specified offset.
     *
     * @param buffer byte buffer to inspect
     * @param offset starting offset within buffer
     * @return true if the buffer matches the signature header
     */
    public boolean matches(byte[] buffer, int offset) {
        if (buffer == null || offset < 0 || offset + header.length > buffer.length) {
            return false;
        }

        for (int i = 0; i < header.length; i++) {
            if (buffer[offset + i] != header[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "FileSignature{" +
                "format=" + format +
                ", headerLength=" + header.length +
                ", maxScanBytes=" + maxScanBytes +
                '}';
    }
}
