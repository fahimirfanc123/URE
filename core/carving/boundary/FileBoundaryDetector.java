package core.carving.boundary;

import core.disk.SectorReader;

import java.io.IOException;

/**
 * Strategy interface for determining file boundaries and structural validity
 * of a carved file candidate starting at a given disk offset.
 */
public interface FileBoundaryDetector {

    /**
     * Determines the boundary and structural validity of a candidate file.
     *
     * @param reader active SectorReader for disk access
     * @param startByteOffset byte offset where the file header begins
     * @param maxScanBytes maximum allowable bytes to scan for boundary
     * @return BoundaryResult indicating status, length, and footer validity
     * @throws IOException if a read error occurs
     */
    BoundaryResult detectBoundary(SectorReader reader, long startByteOffset, long maxScanBytes) throws IOException;
}
