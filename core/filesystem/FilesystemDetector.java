package core.filesystem;

import core.disk.SectorReader;
import filesystem.fat32.FAT32BootSector;
import filesystem.ntfs.NTFSBootSector;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Filesystem Auto-Detection for MINION URE.
 *
 * Safely inspects boot-sector structures in sector 0 to automatically
 * determine whether a candidate disk or image contains FAT32, NTFS,
 * or an unknown / RAW format.
 *
 * All inspection operations are strictly read-only and fail safely on
 * malformed or I/O-errored inputs.
 */
public class FilesystemDetector {

    /**
     * Detects filesystem type from a file path.
     *
     * @param imagePath absolute or relative path to disk image
     * @return detected FilesystemType (FAT32, NTFS, or UNKNOWN)
     */
    public static FilesystemType detect(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return FilesystemType.UNKNOWN;
        }

        File file = new File(imagePath);
        if (!file.exists() || !file.isFile() || file.length() < 512) {
            return FilesystemType.UNKNOWN;
        }

        try (SectorReader reader = new SectorReader(imagePath)) {
            return detect(reader);
        } catch (Throwable e) {
            return FilesystemType.UNKNOWN;
        }
    }

    /**
     * Detects filesystem type from a File object.
     *
     * @param file File referencing the target disk or image
     * @return detected FilesystemType
     */
    public static FilesystemType detect(File file) {
        if (file == null) {
            return FilesystemType.UNKNOWN;
        }
        return detect(file.getAbsolutePath());
    }

    /**
     * Detects filesystem type using an active SectorReader.
     *
     * @param reader active SectorReader instance
     * @return detected FilesystemType
     */
    public static FilesystemType detect(SectorReader reader) {
        if (reader == null) {
            return FilesystemType.UNKNOWN;
        }

        try {
            if (reader.getTotalSectors() < 1) {
                return FilesystemType.UNKNOWN;
            }

            byte[] sector0 = reader.readSector(0);
            if (sector0 == null || sector0.length < 512) {
                return FilesystemType.UNKNOWN;
            }

            // Check signature 0x55 0xAA at offset 510-511
            if ((sector0[510] & 0xFF) != 0x55 || (sector0[511] & 0xFF) != 0xAA) {
                return FilesystemType.UNKNOWN;
            }

            // 1. Check NTFS
            if (isNTFSSector(sector0)) {
                try {
                    NTFSBootSector.parse(reader);
                    return FilesystemType.NTFS;
                } catch (Exception ignored) {
                }
            }

            // 2. Check FAT32
            if (isFAT32Sector(sector0)) {
                try {
                    FAT32BootSector.parse(reader);
                    return FilesystemType.FAT32;
                } catch (Exception ignored) {
                }
            }

            return FilesystemType.UNKNOWN;
        } catch (Throwable e) {
            return FilesystemType.UNKNOWN;
        }
    }

    /**
     * Detects filesystem type from raw sector 0 byte buffer.
     *
     * @param sector0 raw 512-byte sector data
     * @return detected FilesystemType
     */
    public static FilesystemType detect(byte[] sector0) {
        if (sector0 == null || sector0.length < 512) {
            return FilesystemType.UNKNOWN;
        }

        // Standard boot signature check
        if ((sector0[510] & 0xFF) != 0x55 || (sector0[511] & 0xFF) != 0xAA) {
            return FilesystemType.UNKNOWN;
        }

        if (isNTFSSector(sector0)) {
            return FilesystemType.NTFS;
        }

        if (isFAT32Sector(sector0)) {
            return FilesystemType.FAT32;
        }

        return FilesystemType.UNKNOWN;
    }

    /*
     * =============================================================
     * HEURISTICS & VALIDATION HELPERS
     * =============================================================
     */

    private static boolean isNTFSSector(byte[] boot) {
        if (boot == null || boot.length < 512) {
            return false;
        }

        // OEM ID check: "NTFS    " (offset 3, 8 bytes)
        String oem = new String(boot, 3, 8, StandardCharsets.US_ASCII);
        if (!"NTFS    ".equals(oem)) {
            return false;
        }

        int bytesPerSector = readUInt16LE(boot, 11);
        if (bytesPerSector != 512 && bytesPerSector != 1024 &&
            bytesPerSector != 2048 && bytesPerSector != 4096) {
            return false;
        }

        int sectorsPerCluster = boot[13] & 0xFF;
        if (sectorsPerCluster <= 0) {
            return false;
        }

        long totalSectors = readInt64LE(boot, 40);
        if (totalSectors <= 0) {
            return false;
        }

        long mftCluster = readInt64LE(boot, 48);
        return mftCluster >= 0;
    }

    private static boolean isFAT32Sector(byte[] boot) {
        if (boot == null || boot.length < 512) {
            return false;
        }

        int bytesPerSector = readUInt16LE(boot, 11);
        if (bytesPerSector != 512 && bytesPerSector != 1024 &&
            bytesPerSector != 2048 && bytesPerSector != 4096) {
            return false;
        }

        int sectorsPerCluster = boot[13] & 0xFF;
        if (sectorsPerCluster == 0 || (sectorsPerCluster & (sectorsPerCluster - 1)) != 0) {
            return false;
        }

        int reservedSectorCount = readUInt16LE(boot, 14);
        if (reservedSectorCount <= 0) {
            return false;
        }

        int numberOfFATs = boot[16] & 0xFF;
        if (numberOfFATs <= 0 || numberOfFATs > 2) {
            return false;
        }

        int totalSectors16 = readUInt16LE(boot, 19);
        long totalSectors32 = readUInt32LE(boot, 32);
        long totalSectors = totalSectors16 != 0 ? totalSectors16 : totalSectors32;
        if (totalSectors <= 0) {
            return false;
        }

        int fatSize16 = readUInt16LE(boot, 22);
        long fatSize32 = readUInt32LE(boot, 36);
        long fatSize = fatSize16 != 0 ? fatSize16 : fatSize32;
        if (fatSize <= 0) {
            return false;
        }

        long rootCluster = readUInt32LE(boot, 44);
        return rootCluster >= 2;
    }

    private static int readUInt16LE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static long readUInt32LE(byte[] data, int offset) {
        return ((long) data[offset] & 0xFF)
                | (((long) data[offset + 1] & 0xFF) << 8)
                | (((long) data[offset + 2] & 0xFF) << 16)
                | (((long) data[offset + 3] & 0xFF) << 24);
    }

    private static long readInt64LE(byte[] data, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value |= ((long) data[offset + i] & 0xFF) << (8 * i);
        }
        return value;
    }
}
