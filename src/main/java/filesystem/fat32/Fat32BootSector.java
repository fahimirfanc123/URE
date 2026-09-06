package filesystem.fat32;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Fat32BootSector — Universal Recovery Engine (URE)
 *
 * <p>Parses the FAT32 Volume Boot Record (VBR), located at Sector 0 of a FAT32
 * partition. All multi-byte fields are read in little-endian byte order as
 * mandated by the FAT32 specification.
 *
 * <p><b>Reference layout (BPB — BIOS Parameter Block):</b>
 * <pre>
 *  Offset | Size | Field
 *  -------|------|----------------------------
 *  0x0B   |  2   | Bytes Per Sector
 *  0x0D   |  1   | Sectors Per Cluster
 *  0x0E   |  2   | Reserved Sectors Count
 *  0x10   |  1   | Number of FATs
 *  0x24   |  4   | FAT Size (sectors)
 *  0x2C   |  4   | Root Directory First Cluster
 * </pre>
 *
 * @author  Member 2 — Hannad (URE / FAT32 Subsystem)
 * @version 1.0
 */
public class Fat32BootSector {

    // -----------------------------------------------------------------------
    // Constants — BPB field offsets (bytes from sector start)
    // -----------------------------------------------------------------------

    /** Offset of the Bytes Per Sector field inside the boot sector. */
    private static final int OFFSET_BYTES_PER_SECTOR       = 0x0B;

    /** Offset of the Sectors Per Cluster field inside the boot sector. */
    private static final int OFFSET_SECTORS_PER_CLUSTER    = 0x0D;

    /** Offset of the Reserved Sectors Count field inside the boot sector. */
    private static final int OFFSET_RESERVED_SECTORS       = 0x0E;

    /** Offset of the Number of FATs field inside the boot sector. */
    private static final int OFFSET_NUMBER_OF_FATS         = 0x10;

    /** Offset of the FAT Size (in sectors) field inside the boot sector. */
    private static final int OFFSET_FAT_SIZE_SECTORS       = 0x24;

    /** Offset of the Root Directory First Cluster field inside the boot sector. */
    private static final int OFFSET_ROOT_CLUSTER           = 0x2C;

    /** Expected minimum size of a boot sector in bytes. */
    private static final int BOOT_SECTOR_SIZE              = 512;

    // -----------------------------------------------------------------------
    // Parsed BPB fields
    // -----------------------------------------------------------------------

    /**
     * Number of bytes per logical sector (typically 512).
     * Stored as unsigned 16-bit value; held in an {@code int} to avoid sign issues.
     */
    private final int bytesPerSector;

    /**
     * Number of sectors that form one cluster (power of 2: 1, 2, 4 ... 128).
     * Stored as unsigned 8-bit value.
     */
    private final int sectorsPerCluster;

    /**
     * Number of sectors reserved before the first FAT begins (typically 32 for FAT32).
     * Stored as unsigned 16-bit value.
     */
    private final int reservedSectors;

    /**
     * Number of File Allocation Tables on the volume (almost always 2).
     * Stored as unsigned 8-bit value.
     */
    private final int numberOfFats;

    /**
     * Size of each FAT in sectors.
     * Stored as unsigned 32-bit value; held in a {@code long} to avoid sign issues.
     */
    private final long fatSizeSectors;

    /**
     * Cluster number of the first cluster of the root directory (usually 2).
     * Stored as unsigned 32-bit value.
     */
    private final long rootDirFirstCluster;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Constructs a {@code Fat32BootSector} by parsing a raw 512-byte sector array.
     *
     * <p>The array must be exactly {@value #BOOT_SECTOR_SIZE} bytes — the raw
     * content of Sector 0 read directly from the device or image file.
     *
     * @param sectorData the 512-byte boot sector data
     * @throws IllegalArgumentException if {@code sectorData} is {@code null} or
     *                                  shorter than {@value #BOOT_SECTOR_SIZE} bytes
     */
    public Fat32BootSector(byte[] sectorData) {
        if (sectorData == null || sectorData.length < BOOT_SECTOR_SIZE) {
            throw new IllegalArgumentException(
                "Boot sector data must be at least " + BOOT_SECTOR_SIZE +
                " bytes, got: " + (sectorData == null ? "null" : sectorData.length));
        }

        /*
         * Wrap the raw byte array in a ByteBuffer configured for LITTLE_ENDIAN
         * access. All getShort() / getInt() calls then automatically apply
         * the correct byte-swap for x86/FAT32.
         */
        ByteBuffer buf = ByteBuffer.wrap(sectorData).order(ByteOrder.LITTLE_ENDIAN);

        /*
         * Read each BPB field.
         *   - Single-byte fields : get()      masked with 0xFF      -> unsigned int
         *   - Two-byte fields    : getShort() masked with 0xFFFF    -> unsigned int
         *   - Four-byte fields   : getInt()   masked with 0xFFFFFFFFL -> unsigned long
         */
        this.bytesPerSector      = buf.getShort(OFFSET_BYTES_PER_SECTOR)    & 0xFFFF;
        this.sectorsPerCluster   = buf.get(OFFSET_SECTORS_PER_CLUSTER)      & 0xFF;
        this.reservedSectors     = buf.getShort(OFFSET_RESERVED_SECTORS)    & 0xFFFF;
        this.numberOfFats        = buf.get(OFFSET_NUMBER_OF_FATS)           & 0xFF;
        this.fatSizeSectors      = buf.getInt(OFFSET_FAT_SIZE_SECTORS)      & 0xFFFFFFFFL;
        this.rootDirFirstCluster = buf.getInt(OFFSET_ROOT_CLUSTER)          & 0xFFFFFFFFL;
    }

    // -----------------------------------------------------------------------
    // Raw field getters
    // -----------------------------------------------------------------------

    /**
     * Returns the number of bytes in one logical sector (typically 512).
     *
     * @return bytes per sector as an unsigned 16-bit value (stored in {@code int})
     */
    public int getBytesPerSector() {
        return bytesPerSector;
    }

    /**
     * Returns the number of sectors that make up one cluster.
     *
     * @return sectors per cluster as an unsigned 8-bit value
     */
    public int getSectorsPerCluster() {
        return sectorsPerCluster;
    }

    /**
     * Returns the count of sectors reserved at the start of the volume
     * (before the first FAT).
     *
     * @return reserved sector count as an unsigned 16-bit value
     */
    public int getReservedSectors() {
        return reservedSectors;
    }

    /**
     * Returns the number of File Allocation Tables on the volume (typically 2).
     *
     * @return number of FATs as an unsigned 8-bit value
     */
    public int getNumberOfFats() {
        return numberOfFats;
    }

    /**
     * Returns the size of a single FAT in sectors.
     *
     * @return FAT size in sectors as an unsigned 32-bit value (stored in {@code long})
     */
    public long getFatSizeSectors() {
        return fatSizeSectors;
    }

    /**
     * Returns the cluster number of the first cluster of the root directory.
     *
     * @return root directory first cluster as an unsigned 32-bit value
     */
    public long getRootDirFirstCluster() {
        return rootDirFirstCluster;
    }

    // -----------------------------------------------------------------------
    // Derived / calculated values
    // -----------------------------------------------------------------------

    /**
     * Calculates the total size of one cluster in bytes.
     *
     * <pre>
     *   bytesPerCluster = bytesPerSector x sectorsPerCluster
     * </pre>
     *
     * @return cluster size in bytes
     */
    public int getBytesPerCluster() {
        return bytesPerSector * sectorsPerCluster;
    }

    /**
     * Returns the logical sector number at which the first FAT begins.
     *
     * <p>Per the FAT32 spec, the FAT region immediately follows the reserved
     * sectors region:
     * <pre>
     *   fatStartSector = reservedSectors
     * </pre>
     *
     * @return the sector index of FAT number 1
     */
    public int getFatStartSector() {
        return reservedSectors;
    }

    /**
     * Returns the logical sector number at which the data region (clusters) begins.
     *
     * <p>The data region follows the reserved sectors and both FAT copies:
     * <pre>
     *   dataStartSector = reservedSectors + (numberOfFats x fatSizeSectors)
     * </pre>
     *
     * @return the sector index of cluster 2 (the first usable data cluster)
     */
    public long getDataStartSector() {
        return reservedSectors + ((long) numberOfFats * fatSizeSectors);
    }

    // -----------------------------------------------------------------------
    // Object overrides
    // -----------------------------------------------------------------------

    /**
     * Returns a human-readable summary of the parsed BPB fields,
     * useful for logging and debugging during recovery sessions.
     *
     * @return formatted multi-line string of all parsed fields
     */
    @Override
    public String toString() {
        return "Fat32BootSector {" +
               "\n  bytesPerSector       = " + bytesPerSector +
               "\n  sectorsPerCluster    = " + sectorsPerCluster +
               "\n  reservedSectors      = " + reservedSectors +
               "\n  numberOfFats         = " + numberOfFats +
               "\n  fatSizeSectors       = " + fatSizeSectors +
               "\n  rootDirFirstCluster  = " + rootDirFirstCluster +
               "\n  [derived]" +
               "\n  bytesPerCluster      = " + getBytesPerCluster() +
               "\n  fatStartSector       = " + getFatStartSector() +
               "\n  dataStartSector      = " + getDataStartSector() +
               "\n}";
    }
}
