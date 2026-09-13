package filesystem.ntfs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * NtfsBootSector — Universal Recovery Engine (URE)
 *
 * <p>Parses the NTFS Volume Boot Record (VBR), located at Sector 0 of an NTFS
 * partition. All multi-byte fields are read in little-endian byte order as
 * mandated by the NTFS on-disk format specification.
 *
 * <p>The first 512 bytes of an NTFS partition contain the BIOS Parameter Block
 * (BPB) plus an Extended BPB that carries NTFS-specific fields such as the
 * starting cluster of the Master File Table ($MFT) and its mirror ($MFTMirr).
 *
 * <p><b>Reference layout (NTFS BPB + Extended BPB):</b>
 * <pre>
 *  Offset | Size | Field
 *  -------|------|--------------------------------------------
 *  0x00   |  3   | Jump boot code (EB 52 90)
 *  0x03   |  8   | OEM ID ("NTFS    ")
 *  0x0B   |  2   | Bytes Per Sector
 *  0x0D   |  1   | Sectors Per Cluster
 *  0x0E   |  2   | Reserved Sectors (always 0 in NTFS)
 *  0x28   |  8   | Total Sectors on Volume
 *  0x30   |  8   | $MFT  — MFT Starting Cluster
 *  0x38   |  8   | $MFTMirr — MFT Mirror Starting Cluster
 *  0x40   |  1   | Clusters Per MFT Record (signed — see spec)
 *  0x41   |  3   | Reserved
 *  0x44   |  1   | Clusters Per Index Buffer (signed — see spec)
 *  0x48   |  8   | Volume Serial Number
 *  0x50   |  4   | Checksum (unused)
 * </pre>
 *
 * <h2>NTFS Signature Bytes</h2>
 * <p>A valid NTFS boot sector contains the OEM ID string {@code "NTFS    "}
 * (8 bytes, with 4 trailing spaces) at offset {@code 0x03}.
 *
 * @author  Member 2 — Hannad (URE / NTFS Subsystem)
 * @version 1.0
 * @see     <a href="https://learn.microsoft.com/en-us/windows/win32/fileio/master-file-table">
 *          Microsoft — Master File Table</a>
 */
public class NtfsBootSector {

    // -----------------------------------------------------------------------
    // Constants — field offsets (bytes from sector start)
    // -----------------------------------------------------------------------

    /** Expected size of a boot sector in bytes. */
    private static final int BOOT_SECTOR_SIZE               = 512;

    /** Byte offset of the OEM Identifier field ("NTFS    "). */
    private static final int OFFSET_OEM_ID                  = 0x03;

    /** Length of the OEM Identifier field in bytes. */
    private static final int OEM_ID_LENGTH                  = 8;

    /** Expected OEM ID string for a valid NTFS partition. */
    private static final String NTFS_OEM_ID                 = "NTFS    ";

    /** Byte offset of the Bytes Per Sector field (2 bytes, unsigned). */
    private static final int OFFSET_BYTES_PER_SECTOR        = 0x0B;

    /** Byte offset of the Sectors Per Cluster field (1 byte, unsigned). */
    private static final int OFFSET_SECTORS_PER_CLUSTER     = 0x0D;

    /** Byte offset of the $MFT starting cluster (8 bytes, unsigned). */
    private static final int OFFSET_MFT_START_CLUSTER       = 0x30;

    /** Byte offset of the $MFTMirr starting cluster (8 bytes, unsigned). */
    private static final int OFFSET_MFT_MIRROR_START_CLUSTER = 0x38;

    // -----------------------------------------------------------------------
    // Parsed BPB / Extended BPB fields
    // -----------------------------------------------------------------------

    /**
     * Number of bytes per logical sector (typically 512 or 4096).
     * Stored as an unsigned 16-bit value in an {@code int} to avoid sign issues.
     */
    private final int bytesPerSector;

    /**
     * Number of sectors that make up one cluster (power of 2: 1, 2, 4 ... 128).
     * Stored as an unsigned 8-bit value.
     */
    private final int sectorsPerCluster;

    /**
     * Logical Cluster Number (LCN) of the first cluster of the Master File Table
     * ({@code $MFT}).
     *
     * <p>This is an 8-byte field in the on-disk format.  NTFS volumes in practice
     * never exceed 2^63 clusters, so a signed {@code long} is safe; however, the
     * value is always expected to be non-negative.
     */
    private final long mftStartCluster;

    /**
     * Logical Cluster Number (LCN) of the first cluster of the MFT Mirror
     * ({@code $MFTMirr}), which holds a backup of the first 4 MFT records.
     *
     * <p>Same 8-byte, non-negative long as {@link #mftStartCluster}.
     */
    private final long mftMirrorStartCluster;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Constructs an {@code NtfsBootSector} by parsing the raw 512-byte sector
     * read from the very start of an NTFS partition.
     *
     * <p>The constructor performs basic input validation (size check) and an
     * OEM ID check to confirm the sector belongs to an NTFS volume.  If either
     * check fails, an {@link IllegalArgumentException} is thrown so that callers
     * can detect misidentified partitions early during recovery.
     *
     * @param sectorData the 512-byte boot sector data (Sector 0 of the partition)
     * @throws IllegalArgumentException if {@code sectorData} is {@code null},
     *         shorter than {@value #BOOT_SECTOR_SIZE} bytes, or does not carry
     *         the expected NTFS OEM ID signature
     */
    public NtfsBootSector(byte[] sectorData) {
        // ------------------------------------------------------------------
        // 1. Size validation
        // ------------------------------------------------------------------
        if (sectorData == null || sectorData.length < BOOT_SECTOR_SIZE) {
            throw new IllegalArgumentException(
                "NTFS boot sector must be at least " + BOOT_SECTOR_SIZE +
                " bytes, got: " + (sectorData == null ? "null" : sectorData.length));
        }

        // ------------------------------------------------------------------
        // 2. OEM ID / Signature validation ("NTFS    ") 
        // ------------------------------------------------------------------
        String oemId = new String(sectorData, OFFSET_OEM_ID, OEM_ID_LENGTH,
                                  java.nio.charset.StandardCharsets.US_ASCII);
        if (!NTFS_OEM_ID.equals(oemId)) {
            throw new IllegalArgumentException(
                "Sector does not appear to be an NTFS partition. " +
                "OEM ID found: \"" + oemId.trim() + "\", expected: \"NTFS\".");
        }

        // ------------------------------------------------------------------
        // 3. Wrap in a LITTLE_ENDIAN ByteBuffer and parse fields
        // ------------------------------------------------------------------
        ByteBuffer buf = ByteBuffer.wrap(sectorData).order(ByteOrder.LITTLE_ENDIAN);

        /*
         * Unsigned masking rules applied consistently throughout:
         *   - 1-byte field  : buf.get(offset)       & 0xFF          -> int  (0..255)
         *   - 2-byte field  : buf.getShort(offset)  & 0xFFFF        -> int  (0..65535)
         *   - 8-byte field  : buf.getLong(offset)                   -> long
         *     (NTFS cluster values are always non-negative in practice;
         *      no masking needed because Java long covers the full 64-bit range.)
         */
        this.bytesPerSector       = buf.getShort(OFFSET_BYTES_PER_SECTOR)       & 0xFFFF;
        this.sectorsPerCluster    = buf.get(OFFSET_SECTORS_PER_CLUSTER)         & 0xFF;
        this.mftStartCluster      = buf.getLong(OFFSET_MFT_START_CLUSTER);
        this.mftMirrorStartCluster = buf.getLong(OFFSET_MFT_MIRROR_START_CLUSTER);

        // ------------------------------------------------------------------
        // 4. Semantic validation — values must be logically sane
        // ------------------------------------------------------------------
        if (bytesPerSector == 0) {
            throw new IllegalArgumentException(
                "Parsed bytesPerSector is 0 — boot sector data appears corrupt.");
        }
        if (sectorsPerCluster == 0) {
            throw new IllegalArgumentException(
                "Parsed sectorsPerCluster is 0 — boot sector data appears corrupt.");
        }
        if (mftStartCluster < 0) {
            throw new IllegalArgumentException(
                "Parsed mftStartCluster is negative — boot sector data appears corrupt.");
        }
    }

    // -----------------------------------------------------------------------
    // Raw field getters
    // -----------------------------------------------------------------------

    /**
     * Returns the number of bytes in one logical sector (typically 512 or 4096).
     *
     * <p>Parsed from offset {@code 0x0B} as an unsigned 16-bit little-endian value.
     *
     * @return bytes per sector as an unsigned 16-bit value (stored in {@code int})
     */
    public int getBytesPerSector() {
        return bytesPerSector;
    }

    /**
     * Returns the number of sectors that make up one cluster.
     *
     * <p>Parsed from offset {@code 0x0D} as an unsigned 8-bit value.
     * Valid values per the NTFS spec are powers of 2 in the range 1–128.
     *
     * @return sectors per cluster as an unsigned 8-bit value (1, 2, 4, 8 ... 128)
     */
    public int getSectorsPerCluster() {
        return sectorsPerCluster;
    }

    /**
     * Returns the Logical Cluster Number (LCN) of the first cluster of the
     * Master File Table ({@code $MFT}).
     *
     * <p>Parsed from offset {@code 0x30} as an 8-byte little-endian integer.
     * The $MFT is the core metadata table of an NTFS volume; its starting
     * cluster is required to locate any file or directory on the volume.
     *
     * @return $MFT starting cluster number (non-negative {@code long})
     */
    public long getMftStartCluster() {
        return mftStartCluster;
    }

    /**
     * Returns the Logical Cluster Number (LCN) of the first cluster of the
     * MFT Mirror ({@code $MFTMirr}).
     *
     * <p>Parsed from offset {@code 0x38} as an 8-byte little-endian integer.
     * The $MFTMirr stores a backup of the first 4 MFT records ($MFT, $MFTMirr,
     * $LogFile, $Volume) and is used during recovery when the primary $MFT
     * is damaged.
     *
     * @return $MFTMirr starting cluster number (non-negative {@code long})
     */
    public long getMftMirrorStartCluster() {
        return mftMirrorStartCluster;
    }

    // -----------------------------------------------------------------------
    // Derived / calculated values
    // -----------------------------------------------------------------------

    /**
     * Calculates the size of one cluster in bytes.
     *
     * <pre>
     *   bytesPerCluster = bytesPerSector x sectorsPerCluster
     * </pre>
     *
     * <p>This value is the fundamental unit of allocation on an NTFS volume.
     * All file data, MFT records, and index buffers are addressed in whole
     * clusters. Typical values: 4096 (512 * 8), 65536 (512 * 128).
     *
     * @return cluster size in bytes (always a power of 2)
     */
    public int getBytesPerCluster() {
        return bytesPerSector * sectorsPerCluster;
    }

    /**
     * Calculates the byte offset of the $MFT from the start of the partition.
     *
     * <pre>
     *   mftByteOffset = mftStartCluster x bytesPerCluster
     * </pre>
     *
     * <p>This is the raw byte address you would seek to on the device in order
     * to read the first MFT record (the self-referential $MFT entry).
     *
     * @return byte offset of the $MFT from the partition start
     */
    public long getMftByteOffset() {
        return mftStartCluster * getBytesPerCluster();
    }

    /**
     * Calculates the byte offset of the $MFTMirr from the start of the partition.
     *
     * <pre>
     *   mftMirrorByteOffset = mftMirrorStartCluster x bytesPerCluster
     * </pre>
     *
     * @return byte offset of the $MFTMirr from the partition start
     */
    public long getMftMirrorByteOffset() {
        return mftMirrorStartCluster * getBytesPerCluster();
    }

    // -----------------------------------------------------------------------
    // Object overrides
    // -----------------------------------------------------------------------

    /**
     * Returns a human-readable diagnostic summary of all parsed NTFS BPB fields
     * and their derived values. Useful for recovery session logs.
     *
     * @return formatted multi-line string of parsed and calculated fields
     */
    @Override
    public String toString() {
        return "NtfsBootSector {" +
               "\n  bytesPerSector          = " + bytesPerSector +
               "\n  sectorsPerCluster       = " + sectorsPerCluster +
               "\n  mftStartCluster         = " + mftStartCluster +
               "\n  mftMirrorStartCluster   = " + mftMirrorStartCluster +
               "\n  [derived]" +
               "\n  bytesPerCluster         = " + getBytesPerCluster() +
               "\n  mftByteOffset           = " + getMftByteOffset() +
               "\n  mftMirrorByteOffset     = " + getMftMirrorByteOffset() +
               "\n}";
    }
}
