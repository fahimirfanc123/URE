package core.recovery;

import common.model.RecoveryResult;
import core.scan.DiskScanner;

import java.io.IOException;

/**
 * RecoveryEngine analyzes disk sectors and determines
 * how much data may be recoverable.
 *
 * It uses DiskScanner to inspect sectors and returns
 * the result using the shared RecoveryResult model.
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
            throw new IllegalArgumentException(
                    "DiskScanner cannot be null"
            );
        }

        this.diskScanner = diskScanner;
    }

    /**
     * Analyzes a range of sectors for recoverable data.
     *
     * @param startSector first sector to analyze
     * @param sectorCount number of sectors to analyze
     * @return RecoveryResult containing recovery statistics
     * @throws IOException if disk reading fails
     */
    public RecoveryResult analyze(
            long startSector,
            long sectorCount) throws IOException {

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
}
