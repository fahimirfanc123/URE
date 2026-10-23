package core.partition;

import core.disk.SectorReader;
import filesystem.fat32.FAT32BootSector;
import filesystem.ntfs.NTFSBootSector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detects filesystems inside MBR or GPT partitions.
 *
 * All source access is read-only. Filesystem parsers receive a bounded
 * SectorReader whose sector zero is the beginning of the partition.
 *
 * This initial implementation uses 512-byte disk sectors, matching the
 * current MBRParser, GPTParser and SectorReader defaults.
 */
public final class FilesystemDetector {

    private static final int DISK_SECTOR_SIZE = 512;

    public enum FilesystemType {
        FAT32,
        NTFS,
        UNKNOWN
    }

    public enum PartitionScheme {
        MBR,
        GPT
    }

    private FilesystemDetector() {
    }

    /**
     * Detects the partition scheme and inspects each allocated partition.
     *
     * @throws IOException if the image cannot be read or its recognized
     *                     partition table is invalid
     */
    public static List<DetectedPartition> detect(String imagePath)
            throws IOException {

        if (imagePath == null || imagePath.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Image path cannot be null or empty"
            );
        }

        try (SectorReader disk = new SectorReader(
                imagePath, DISK_SECTOR_SIZE)) {

            if (disk.getTotalSectors() < 2) {
                throw new IOException(
                        "Image is too small to contain a partition table"
                );
            }

            List<PartitionInfo> partitions;
            PartitionScheme scheme;

            GPTParser gpt = new GPTParser(disk);

            if (gpt.isValidGPT()) {
                // A recognized GPT must pass integrity validation.
                // Do not silently fall back to MBR when its CRC is invalid.
                if (!gpt.validateIntegrity()) {
                    throw new IOException(
                            "GPT integrity validation failed"
                    );
                }

                List<GPTParser.GPTPartitionEntry> entries =
                        gpt.readPartitions();

                partitions = new ArrayList<>();

                for (GPTParser.GPTPartitionEntry entry : entries) {
                    partitions.add(new PartitionInfo(
                            entry.getPartitionNumber(),
                            entry.getTypeGuid(),
                            entry.getFirstLBA(),
                            entry.getSectorCount()
                    ));
                }

                scheme = PartitionScheme.GPT;
            } else {
                MBRParser mbr = new MBRParser(disk);

                if (!mbr.hasValidSignature()) {
                    throw new IOException(
                            "Neither a valid GPT header nor an MBR "
                                    + "signature was found"
                    );
                }

                List<MBRParser.PartitionEntry> entries =
                        mbr.readPartitions();

                partitions = new ArrayList<>();

                for (MBRParser.PartitionEntry entry : entries) {
                    partitions.add(new PartitionInfo(
                            entry.getPartitionNumber(),
                            String.format(
                                    "0x%02X",
                                    entry.getPartitionType()
                            ),
                            entry.getStartLBA(),
                            entry.getSectorCount()
                    ));
                }

                scheme = PartitionScheme.MBR;
            }

            List<DetectedPartition> results = new ArrayList<>();

            for (PartitionInfo partition : partitions) {
                results.add(inspectPartition(
                        imagePath,
                        scheme,
                        partition
                ));
            }

            return Collections.unmodifiableList(results);
        }
    }

    private static DetectedPartition inspectPartition(
            String imagePath,
            PartitionScheme scheme,
            PartitionInfo partition) {

        long offsetBytes;
        long lengthBytes;

        try {
            offsetBytes = Math.multiplyExact(
                    partition.startLBA,
                    (long) DISK_SECTOR_SIZE
            );

            lengthBytes = Math.multiplyExact(
                    partition.sectorCount,
                    (long) DISK_SECTOR_SIZE
            );
        } catch (ArithmeticException ex) {
            return unknown(
                    scheme,
                    partition,
                    "Partition byte range overflows"
            );
        }

        try (SectorReader bounded = new SectorReader(
                imagePath,
                DISK_SECTOR_SIZE,
                offsetBytes,
                lengthBytes)) {

            // Check NTFS first because the NTFS OEM identifier is explicit.
            try {
                NTFSBootSector ntfs = NTFSBootSector.parse(bounded);

                if (!volumeFitsPartition(
                        ntfs.getTotalSectors(),
                        ntfs.getBytesPerSector(),
                        lengthBytes)) {

                    return unknown(
                            scheme,
                            partition,
                            "NTFS volume size exceeds partition capacity"
                    );
                }

                return new DetectedPartition(
                        scheme,
                        partition.number,
                        partition.type,
                        partition.startLBA,
                        partition.sectorCount,
                        FilesystemType.NTFS,
                        "NTFS boot sector validated"
                );
            } catch (IOException | IllegalArgumentException ignored) {
                // Try the next supported filesystem.
            }

            try {
                FAT32BootSector fat32 = FAT32BootSector.parse(bounded);

                if (!volumeFitsPartition(
                        fat32.getTotalSectors(),
                        fat32.getBytesPerSector(),
                        lengthBytes)) {

                    return unknown(
                            scheme,
                            partition,
                            "FAT32 volume size exceeds partition capacity"
                    );
                }

                return new DetectedPartition(
                        scheme,
                        partition.number,
                        partition.type,
                        partition.startLBA,
                        partition.sectorCount,
                        FilesystemType.FAT32,
                        "FAT32 boot sector validated"
                );
            } catch (IOException | IllegalArgumentException ignored) {
                // An unrecognized or damaged filesystem is not a table error.
            }

            return unknown(
                    scheme,
                    partition,
                    "No supported valid FAT32 or NTFS boot sector found"
            );

        } catch (IOException | IllegalArgumentException ex) {
            return unknown(
                    scheme,
                    partition,
                    "Could not inspect partition: " + safeMessage(ex)
            );
        }
    }

    /**
     * Compares byte capacities, since a filesystem's logical sector size
     * may differ from the 512-byte sectors used by the partition table.
     */
    private static boolean volumeFitsPartition(
            long volumeSectors,
            int bytesPerSector,
            long partitionLengthBytes) {

        if (volumeSectors <= 0 || bytesPerSector <= 0) {
            return false;
        }

        try {
            long volumeBytes = Math.multiplyExact(
                    volumeSectors,
                    (long) bytesPerSector
            );

            return volumeBytes <= partitionLengthBytes;
        } catch (ArithmeticException ex) {
            return false;
        }
    }

    private static DetectedPartition unknown(
            PartitionScheme scheme,
            PartitionInfo partition,
            String message) {

        return new DetectedPartition(
                scheme,
                partition.number,
                partition.type,
                partition.startLBA,
                partition.sectorCount,
                FilesystemType.UNKNOWN,
                message
        );
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.trim().isEmpty()
                ? ex.getClass().getSimpleName()
                : message;
    }

    private static final class PartitionInfo {
        private final int number;
        private final String type;
        private final long startLBA;
        private final long sectorCount;

        private PartitionInfo(
                int number,
                String type,
                long startLBA,
                long sectorCount) {
            this.number = number;
            this.type = type;
            this.startLBA = startLBA;
            this.sectorCount = sectorCount;
        }
    }

    public static final class DetectedPartition {
        private final PartitionScheme scheme;
        private final int partitionNumber;
        private final String partitionType;
        private final long startLBA;
        private final long sectorCount;
        private final FilesystemType filesystemType;
        private final String diagnostic;

        private DetectedPartition(
                PartitionScheme scheme,
                int partitionNumber,
                String partitionType,
                long startLBA,
                long sectorCount,
                FilesystemType filesystemType,
                String diagnostic) {
            this.scheme = scheme;
            this.partitionNumber = partitionNumber;
            this.partitionType = partitionType;
            this.startLBA = startLBA;
            this.sectorCount = sectorCount;
            this.filesystemType = filesystemType;
            this.diagnostic = diagnostic;
        }

        public PartitionScheme getScheme() {
            return scheme;
        }

        public int getPartitionNumber() {
            return partitionNumber;
        }

        public String getPartitionType() {
            return partitionType;
        }

        public long getStartLBA() {
            return startLBA;
        }

        public long getSectorCount() {
            return sectorCount;
        }

        public FilesystemType getFilesystemType() {
            return filesystemType;
        }

        public String getDiagnostic() {
            return diagnostic;
        }

        @Override
        public String toString() {
            return "DetectedPartition{" +
                    "scheme=" + scheme +
                    ", partitionNumber=" + partitionNumber +
                    ", partitionType='" + partitionType + '\'' +
                    ", startLBA=" + startLBA +
                    ", sectorCount=" + sectorCount +
                    ", filesystemType=" + filesystemType +
                    ", diagnostic='" + diagnostic + '\'' +
                    '}';
        }
    }
}
