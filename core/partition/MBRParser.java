package core.partition;

import core.disk.SectorReader;

import java.io.IOException;

/**
 * MBRParser is responsible for parsing and validating Master Boot Record (MBR) partition tables.
 *
 * Located at Sector 0 (LBA 0) of an MBR-partitioned storage device, the MBR contains boot code,
 * a 4-entry partition table, and a 2-byte boot signature (0x55, 0xAA) at the end of the sector.
 *
 * This class provides methods to verify the integrity and presence of an MBR on the underlying disk.
 */
public class MBRParser {

    /**
     * Byte offset within Sector 0 where the 2-byte MBR boot signature is located.
     */
    private static final int MBR_SIGNATURE_OFFSET = 510;

    private final SectorReader sectorReader;

    /**
     * Constructs an MBRParser backed by a SectorReader.
     *
     * @param sectorReader The SectorReader providing raw sector access to the disk image or drive
     */
    public MBRParser(SectorReader sectorReader) {
        if (sectorReader == null) {
            throw new IllegalArgumentException("SectorReader cannot be null");
        }
        this.sectorReader = sectorReader;
    }

    /**
     * Validates whether Sector 0 contains a valid MBR boot signature (0x55 0xAA).
     *
     * The signature is stored as a little-endian word 0xAA55, which appears as:
     * - byte 510: 0x55
     * - byte 511: 0xAA
     *
     * @return true if the disk contains a valid MBR signature at Sector 0, false otherwise
     * @throws IOException If reading Sector 0 fails
     */
    public boolean hasValidSignature() throws IOException {
        // Ensure disk has at least 1 sector and sector size is at least 512 bytes
        if (sectorReader.getTotalSectors() < 1 || sectorReader.getSectorSize() < 512) {
            return false;
        }

        // Read Sector 0 (LBA 0) where the MBR resides
        byte[] sector = sectorReader.readSector(0);

        // Convert signed bytes to unsigned ints (0-255) for accurate hex comparison
        int firstByte = sector[MBR_SIGNATURE_OFFSET] & 0xFF;
        int secondByte = sector[MBR_SIGNATURE_OFFSET + 1] & 0xFF;

        // Check for standard MBR boot signature (0x55 0xAA)
        return firstByte == 0x55 && secondByte == 0xAA;
    }
}
