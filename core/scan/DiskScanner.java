package core.scan;

import core.disk.SectorReader;

import java.io.IOException;

/**
 * DiskScanner scans sectors from a disk image or storage device.
 *
 * It uses SectorReader to read raw sectors and determines whether
 * each sector contains data or is completely empty.
 */
public class DiskScanner {

    private final SectorReader sectorReader;

    /**
     * Creates a DiskScanner using an existing SectorReader.
     *
     * @param sectorReader reader used to access raw disk sectors
     */
    public DiskScanner(SectorReader sectorReader) {
        if (sectorReader == null) {
            throw new IllegalArgumentException("SectorReader cannot be null");
        }

        this.sectorReader = sectorReader;
    }

    /**
     * Checks whether a sector contains any non-zero byte.
     *
     * @param sectorNumber sector number to check
     * @return true if the sector contains data, otherwise false
     */
    public boolean hasData(long sectorNumber) throws IOException {
        byte[] sector = sectorReader.readSector(sectorNumber);

        for (byte value : sector) {
            if (value != 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * Scans a range of sectors.
     *
     * @param startSector first sector to scan
     * @param sectorCount number of sectors to scan
     * @return ScanResult containing scan statistics
     */
    public ScanResult scan(long startSector, long sectorCount)
            throws IOException {

        if (startSector < 0) {
            throw new IllegalArgumentException(
                    "Start sector cannot be negative"
            );
        }

        if (sectorCount < 0) {
            throw new IllegalArgumentException(
                    "Sector count cannot be negative"
            );
        }

        long sectorsWithData = 0;
        long emptySectors = 0;

        for (long i = 0; i < sectorCount; i++) {

            long currentSector = startSector + i;

            if (hasData(currentSector)) {
                sectorsWithData++;
            } else {
                emptySectors++;
            }
        }

        return new ScanResult(
                startSector,
                sectorCount,
                sectorsWithData,
                emptySectors
        );
    }

    /**
     * Stores the result of a disk scan.
     */
    public static class ScanResult {

        private final long startSector;
        private final long totalScanned;
        private final long sectorsWithData;
        private final long emptySectors;

        public ScanResult(
                long startSector,
                long totalScanned,
                long sectorsWithData,
                long emptySectors) {

            this.startSector = startSector;
            this.totalScanned = totalScanned;
            this.sectorsWithData = sectorsWithData;
            this.emptySectors = emptySectors;
        }

        public long getStartSector() {
            return startSector;
        }

        public long getTotalScanned() {
            return totalScanned;
        }

        public long getSectorsWithData() {
            return sectorsWithData;
        }

        public long getEmptySectors() {
            return emptySectors;
        }

        @Override
        public String toString() {
            return "ScanResult{" +
                    "startSector=" + startSector +
                    ", totalScanned=" + totalScanned +
                    ", sectorsWithData=" + sectorsWithData +
                    ", emptySectors=" + emptySectors +
                    '}';
        }
    }
}
