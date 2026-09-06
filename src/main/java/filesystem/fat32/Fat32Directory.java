package filesystem.fat32;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Fat32Directory — Universal Recovery Engine (URE)
 *
 * <p>Parses a single 32-byte FAT32 directory entry as described in the
 * Microsoft FAT32 File System Specification. Each entry in a directory
 * cluster is exactly 32 bytes long and follows this layout:
 *
 * <pre>
 *  Offset | Size | Field
 *  -------|------|-------------------------------------------
 *  0x00   |  8   | Filename (space-padded, OEM charset)
 *  0x08   |  3   | Extension (space-padded, OEM charset)
 *  0x0B   |  1   | Attributes
 *  0x0C   |  1   | Reserved (Windows NT flags — ignored)
 *  0x0D   |  1   | Creation time, 10ms increments — ignored
 *  0x0E   |  2   | Creation time — ignored
 *  0x10   |  2   | Creation date — ignored
 *  0x12   |  2   | Last access date — ignored
 *  0x14   |  2   | First cluster HIGH word
 *  0x16   |  2   | Last write time — ignored
 *  0x18   |  2   | Last write date — ignored
 *  0x1A   |  2   | First cluster LOW word
 *  0x1C   |  4   | File size in bytes
 * </pre>
 *
 * <h2>Attribute flags (Byte 0x0B)</h2>
 * <pre>
 *  0x01 — ATTR_READ_ONLY
 *  0x02 — ATTR_HIDDEN
 *  0x04 — ATTR_SYSTEM
 *  0x08 — ATTR_VOLUME_ID
 *  0x10 — ATTR_DIRECTORY
 *  0x20 — ATTR_ARCHIVE
 *  0x0F — ATTR_LONG_NAME  (combination of READ_ONLY|HIDDEN|SYSTEM|VOLUME_ID)
 * </pre>
 *
 * <h2>Deleted-entry marker</h2>
 * <p>If byte 0 of the entry equals {@code 0xE5}, the entry was previously
 * deleted and its cluster chain may still be recoverable.
 *
 * @author  Member 2 — Hannad (URE / FAT32 Subsystem)
 * @version 1.0
 */
public class Fat32Directory {

    // -----------------------------------------------------------------------
    // Constants — directory entry field offsets and sizes
    // -----------------------------------------------------------------------

    /** Size of a FAT32 directory entry in bytes. */
    private static final int ENTRY_SIZE                    = 32;

    /** Number of bytes used for the base filename (excluding extension). */
    private static final int FILENAME_LENGTH               = 8;

    /** Number of bytes used for the file extension. */
    private static final int EXTENSION_LENGTH              = 3;

    /** Offset of the filename field. */
    private static final int OFFSET_FILENAME               = 0x00;

    /** Offset of the attribute byte. */
    private static final int OFFSET_ATTRIBUTES             = 0x0B;

    /** Offset of the first-cluster HIGH word (2 bytes). */
    private static final int OFFSET_FIRST_CLUSTER_HIGH     = 0x14;

    /** Offset of the first-cluster LOW word (2 bytes). */
    private static final int OFFSET_FIRST_CLUSTER_LOW      = 0x1A;

    /** Offset of the file size field (4 bytes). */
    private static final int OFFSET_FILE_SIZE              = 0x1C;

    // -----------------------------------------------------------------------
    // Attribute flag masks
    // -----------------------------------------------------------------------

    /** Attribute bit: entry represents a sub-directory. */
    private static final int ATTR_DIRECTORY                = 0x10;

    /** Attribute bit: entry is a volume label (disk name). */
    private static final int ATTR_VOLUME_ID                = 0x08;

    /**
     * Combined mask that identifies a Long File Name (LFN) entry.
     * An LFN entry has all four of READ_ONLY | HIDDEN | SYSTEM | VOLUME_ID set.
     */
    private static final int ATTR_LONG_NAME                = 0x0F;

    /** Magic value in byte 0 that flags the entry as deleted. */
    private static final int DELETED_MARKER                = 0xE5;

    // -----------------------------------------------------------------------
    // Parsed fields
    // -----------------------------------------------------------------------

    /**
     * Raw first byte of the entry; preserved so {@link #isDeleted()} can
     * inspect the {@code 0xE5} marker without re-parsing.
     */
    private final int firstByte;

    /**
     * The 8.3 filename without trailing spaces, built by combining
     * the 8-byte name and 3-byte extension fields.
     * Format: {@code "FILENAME.EXT"} or {@code "FILENAME"} when no extension.
     */
    private final String filename;

    /**
     * Raw attribute byte (unsigned, 0–255).
     * Use the {@code ATTR_*} constants or {@link #isDirectory()} /
     * {@link #isNormalFile()} helpers to interpret individual flags.
     */
    private final int attributes;

    /**
     * First cluster of the entry's data, assembled from the HIGH (bytes 20-21)
     * and LOW (bytes 26-27) 16-bit words stored in the directory entry.
     *
     * <pre>
     *   firstCluster = (clusterHigh << 16) | clusterLow
     * </pre>
     *
     * Stored as a {@code long} (unsigned 32-bit) to handle cluster numbers
     * above {@code 0x7FFF_FFFF} without becoming negative.
     */
    private final long firstCluster;

    /**
     * Size of the file in bytes, as recorded in the directory entry.
     * Stored as unsigned 32-bit value in a {@code long}.
     * Always 0 for directories.
     */
    private final long fileSize;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Constructs a {@code Fat32Directory} by parsing a single raw 32-byte
     * directory entry.
     *
     * @param entryData exactly 32 bytes representing one FAT32 directory entry
     * @throws IllegalArgumentException if {@code entryData} is {@code null} or
     *                                  shorter than {@value #ENTRY_SIZE} bytes
     */
    public Fat32Directory(byte[] entryData) {
        if (entryData == null || entryData.length < ENTRY_SIZE) {
            throw new IllegalArgumentException(
                "Directory entry data must be at least " + ENTRY_SIZE +
                " bytes, got: " + (entryData == null ? "null" : entryData.length));
        }

        ByteBuffer buf = ByteBuffer.wrap(entryData).order(ByteOrder.LITTLE_ENDIAN);

        // Preserve first byte for the deleted-marker check (0xE5).
        this.firstByte = buf.get(OFFSET_FILENAME) & 0xFF;

        // Parse attribute byte as unsigned.
        this.attributes = buf.get(OFFSET_ATTRIBUTES) & 0xFF;

        // Read and combine HIGH and LOW cluster words into a 32-bit cluster number.
        int clusterHigh = buf.getShort(OFFSET_FIRST_CLUSTER_HIGH) & 0xFFFF;
        int clusterLow  = buf.getShort(OFFSET_FIRST_CLUSTER_LOW)  & 0xFFFF;
        this.firstCluster = ((long) clusterHigh << 16) | clusterLow;

        // File size as unsigned 32-bit.
        this.fileSize = buf.getInt(OFFSET_FILE_SIZE) & 0xFFFFFFFFL;

        // Build the 8.3 filename string.
        this.filename = buildFilename(entryData);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Constructs the 8.3 filename string from the raw entry bytes.
     *
     * <p>The name occupies bytes 0–7 and the extension bytes 8–10, both
     * space-padded. If the first byte is {@code 0xE5} (deleted marker) it is
     * replaced with {@code '?'} for display purposes; a byte of {@code 0x05}
     * represents the actual character {@code 0xE5} (Japanese Kanji lead byte
     * on some OEM code pages) and is similarly replaced with {@code '?'}.
     *
     * @param raw the 32-byte directory entry
     * @return the trimmed 8.3 filename, e.g. {@code "README.TXT"} or {@code "MYFOLDER"}
     */
    private static String buildFilename(byte[] raw) {
        // Copy name bytes; handle special first-byte cases.
        byte[] nameBytes = new byte[FILENAME_LENGTH];
        for (int i = 0; i < FILENAME_LENGTH; i++) {
            nameBytes[i] = raw[OFFSET_FILENAME + i];
        }

        // FAT spec: 0x05 in position 0 is actually the Kanji lead byte 0xE5.
        // For recovery display we normalise both 0xE5 and 0x05 to '?'.
        if ((nameBytes[0] & 0xFF) == DELETED_MARKER || (nameBytes[0] & 0xFF) == 0x05) {
            nameBytes[0] = (byte) '?';
        }

        String name = new String(nameBytes, StandardCharsets.US_ASCII).stripTrailing();

        // Extension bytes.
        byte[] extBytes = new byte[EXTENSION_LENGTH];
        for (int i = 0; i < EXTENSION_LENGTH; i++) {
            extBytes[i] = raw[OFFSET_FILENAME + FILENAME_LENGTH + i];
        }
        String ext = new String(extBytes, StandardCharsets.US_ASCII).stripTrailing();

        return ext.isEmpty() ? name : name + "." + ext;
    }

    // -----------------------------------------------------------------------
    // Field getters
    // -----------------------------------------------------------------------

    /**
     * Returns the 8.3 filename derived from the directory entry, with trailing
     * spaces stripped and extension appended (e.g. {@code "README.TXT"}).
     *
     * <p>If the entry is deleted, the first character is shown as {@code '?'}.
     *
     * @return the 8.3 filename string
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Returns the raw attribute byte of the directory entry.
     *
     * <p>Bit layout:
     * <ul>
     *   <li>Bit 0 (0x01) — Read-only</li>
     *   <li>Bit 1 (0x02) — Hidden</li>
     *   <li>Bit 2 (0x04) — System</li>
     *   <li>Bit 3 (0x08) — Volume ID</li>
     *   <li>Bit 4 (0x10) — Directory</li>
     *   <li>Bit 5 (0x20) — Archive</li>
     * </ul>
     *
     * @return attribute byte as an unsigned integer (0–63 in practice)
     */
    public int getAttributes() {
        return attributes;
    }

    /**
     * Returns the starting cluster number of this entry's data chain,
     * combined from the HIGH and LOW cluster words in the directory entry:
     * <pre>
     *   firstCluster = (clusterHigh &lt;&lt; 16) | clusterLow
     * </pre>
     *
     * @return first cluster as an unsigned 32-bit value stored in a {@code long}
     */
    public long getFirstCluster() {
        return firstCluster;
    }

    /**
     * Returns the size of the file in bytes, as recorded in the entry.
     * This is always {@code 0} for directory entries.
     *
     * @return file size as an unsigned 32-bit value stored in a {@code long}
     */
    public long getFileSize() {
        return fileSize;
    }

    // -----------------------------------------------------------------------
    // Helper / classification methods
    // -----------------------------------------------------------------------

    /**
     * Determines whether this directory entry has been marked as deleted.
     *
     * <p>A deleted entry has {@code 0xE5} (decimal 229) stored in its very
     * first byte. Its cluster chain may still be intact and recoverable.
     *
     * @return {@code true} if byte 0 of the entry equals {@code 0xE5}
     */
    public boolean isDeleted() {
        return firstByte == DELETED_MARKER;
    }

    /**
     * Determines whether this directory entry represents a sub-directory.
     *
     * <p>The ATTR_DIRECTORY bit ({@code 0x10}) in the attribute byte is set
     * for all sub-directory entries, including the dot ({@code .}) and
     * double-dot ({@code ..}) self/parent references.
     *
     * @return {@code true} if bit 4 of the attribute byte is set
     */
    public boolean isDirectory() {
        return (attributes & ATTR_DIRECTORY) != 0;
    }

    /**
     * Determines whether this entry represents a normal, recoverable file.
     *
     * <p>An entry is considered a "normal file" when ALL of the following
     * conditions hold:
     * <ol>
     *   <li>It is <b>not</b> deleted ({@code firstByte != 0xE5}).</li>
     *   <li>It is <b>not</b> a sub-directory ({@code ATTR_DIRECTORY} clear).</li>
     *   <li>It is <b>not</b> a volume label ({@code ATTR_VOLUME_ID} clear).</li>
     *   <li>It is <b>not</b> a Long File Name (LFN) entry
     *       ({@code attributes != 0x0F}).</li>
     *   <li>It is <b>not</b> a free/unused slot ({@code firstByte != 0x00}).</li>
     * </ol>
     *
     * @return {@code true} if the entry is a regular, non-deleted file
     */
    public boolean isNormalFile() {
        if (isDeleted()) {
            return false;
        }
        if (firstByte == 0x00) {
            // Free (never-used) slot — not a file.
            return false;
        }
        if (isDirectory()) {
            return false;
        }
        if ((attributes & ATTR_VOLUME_ID) != 0) {
            return false;
        }
        if (attributes == ATTR_LONG_NAME) {
            // LFN continuation entry — not a real file entry.
            return false;
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Object overrides
    // -----------------------------------------------------------------------

    /**
     * Returns a human-readable summary of the parsed directory entry fields,
     * useful for recovery log output and debugging.
     *
     * @return formatted multi-line string of all parsed fields
     */
    @Override
    public String toString() {
        return "Fat32Directory {" +
               "\n  filename      = \"" + filename + "\"" +
               "\n  attributes    = 0x" + String.format("%02X", attributes) +
               "\n  firstCluster  = " + firstCluster +
               "\n  fileSize      = " + fileSize + " bytes" +
               "\n  isDeleted     = " + isDeleted() +
               "\n  isDirectory   = " + isDirectory() +
               "\n  isNormalFile  = " + isNormalFile() +
               "\n}";
    }
}
