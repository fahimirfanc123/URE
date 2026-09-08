package core.recovery;

import core.scan.DiskScanner;

import java.io.IOException;

/**
 * RecoveryEngine coordinates the basic recovery analysis process.
 *
 * It uses DiskScanner to inspect a range of sectors and creates
 * a RecoveryResult containing information about potentially
 * recoverable sectors.
 */
public class RecoveryEngine {

    private final DiskScanner diskScanner;

    /**
     * Creates a RecoveryEngine using the given DiskScanner.
     *
     * @param diskScanner scanner used to inspect disk sectors
     */
    public RecoveryEngine(DiskScanner diskScanner) {
        if (diskScanner == null) {
            throw new IllegalArgumentException("DiskScanner cannot be null");
        }

        this.diskScanner = diskScanner;
    }

    /**
     * Analyzes a range of sectors for potentially recoverable data.
     *
     * @param startSector first sector to scan
     * @param sectorCount number of sectors to scan
     * @return recovery analysis result
     * @throws IOException if disk reading fails
     */
    public RecoveryResult analyze(long startSector, long sectorCount)
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

        DiskScanner.ScanResult scanResult =
                diskScanner.scan(startSector, sectorCount);

        return new RecoveryResult(
                scanResult.getStartSector(),
                scanResult.getTotalScanned(),
                scanResult.getSectorsWithData(),
                scanResult.getEmptySectors()
        );
    }

    /**
     * Stores the result of a recovery analysis.
     */
    public static class RecoveryResult {

        private final long startSector;
        private final long totalScanned;
        private final long recoverableSectors;
        private final long emptySectors;

        public RecoveryResult(
                long startSector,
                long totalScanned,
                long recoverableSectors,
                long emptySectors
        ) {
            this.startSector = startSector;
            this.totalScanned = totalScanned;
            this.recoverableSectors = recoverableSectors;
            this.emptySectors = emptySectors;
        }

        public long getStartSector() {
            return startSector;
        }

        public long getTotalScanned() {
            return totalScanned;
        }

        public long getRecoverableSectors() {
            return recoverableSectors;
        }

        public long getEmptySectors() {
            return emptySectors;
        }

        public boolean hasRecoverableData() {
            return recoverableSectors > 0;
        }

        @Override
        public String toString() {
            return "RecoveryResult{" +
                    "startSector=" + startSector +
                    ", totalScanned=" + totalScanned +
                    ", recoverableSectors=" + recoverableSectors +
                    ", emptySectors=" + emptySectors +
                    '}';
        }
    }
}
