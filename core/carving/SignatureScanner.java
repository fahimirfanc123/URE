package core.carving;

import core.carving.boundary.BoundaryResult;
import core.disk.SectorReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * High-performance streaming signature scanner for raw disk carving.
 *
 * Scans disk sectors in buffered chunks, maintains sliding-window overlap
 * across sector/chunk boundaries to prevent missed signatures, and evaluates
 * format-specific boundary detectors.
 */
public class SignatureScanner {

    private static final int DEFAULT_CHUNK_SIZE_BYTES = 131072; // 128 KB chunk buffer

    private final SectorReader sectorReader;
    private final SignatureDatabase signatureDatabase;
    private final int chunkSize;

    public SignatureScanner(SectorReader sectorReader) {
        this(sectorReader, new SignatureDatabase(), DEFAULT_CHUNK_SIZE_BYTES);
    }

    public SignatureScanner(SectorReader sectorReader, SignatureDatabase signatureDatabase) {
        this(sectorReader, signatureDatabase, DEFAULT_CHUNK_SIZE_BYTES);
    }

    public SignatureScanner(SectorReader sectorReader, SignatureDatabase signatureDatabase, int chunkSize) {
        if (sectorReader == null) {
            throw new IllegalArgumentException("SectorReader cannot be null");
        }
        if (signatureDatabase == null) {
            throw new IllegalArgumentException("SignatureDatabase cannot be null");
        }
        if (chunkSize < 512) {
            throw new IllegalArgumentException("Chunk size must be at least 512 bytes");
        }

        this.sectorReader = sectorReader;
        this.signatureDatabase = signatureDatabase;
        this.chunkSize = chunkSize;
    }

    /**
     * Scans the entire disk image for supported file signatures and carved files.
     *
     * @return List of carved file candidates
     * @throws IOException if disk reading fails
     */
    public List<CarvedFile> scan() throws IOException {
        return scan(0, sectorReader.getTotalSectors());
    }

    /**
     * Scans a specific sector range of the disk image.
     *
     * @param startSector starting sector index (0-based)
     * @param sectorCount number of sectors to scan
     * @return List of carved file candidates
     * @throws IOException if disk reading fails
     */
    public List<CarvedFile> scan(long startSector, long sectorCount) throws IOException {
        if (startSector < 0 || sectorCount < 0 || (startSector + sectorCount) > sectorReader.getTotalSectors()) {
            throw new IllegalArgumentException("Invalid sector range: " + startSector + " + " + sectorCount);
        }

        List<CarvedFile> carvedFiles = new ArrayList<>();
        int sectorSize = sectorReader.getSectorSize();

        long startByteOffset = startSector * (long) sectorSize;
        long totalBytesToScan = sectorCount * (long) sectorSize;
        long endByteOffset = startByteOffset + totalBytesToScan;

        int maxHeaderLen = signatureDatabase.getMaxHeaderLength();
        int overlapSize = Math.max(16, maxHeaderLen);

        long currentDiskByte = startByteOffset;
        byte[] overlap = new byte[0];

        while (currentDiskByte < endByteOffset) {
            int bytesToRead = (int) Math.min(chunkSize, endByteOffset - currentDiskByte);
            long startSectorForChunk = currentDiskByte / sectorSize;
            int offsetInStartSector = (int) (currentDiskByte % sectorSize);

            long endByteForChunk = currentDiskByte + bytesToRead;
            long endSectorForChunk = (endByteForChunk - 1) / sectorSize;
            int sectorsToRead = (int) (endSectorForChunk - startSectorForChunk + 1);

            byte[] sectorBuffer = sectorReader.readSectors(startSectorForChunk, sectorsToRead);
            byte[] chunkData = new byte[bytesToRead];
            System.arraycopy(sectorBuffer, offsetInStartSector, chunkData, 0, bytesToRead);

            // Combine previous overlap with current chunk
            byte[] buffer = new byte[overlap.length + chunkData.length];
            if (overlap.length > 0) {
                System.arraycopy(overlap, 0, buffer, 0, overlap.length);
            }
            System.arraycopy(chunkData, 0, buffer, overlap.length, chunkData.length);

            long bufferBaseOffset = currentDiskByte - overlap.length;

            int i = 0;
            while (i < buffer.length) {
                long absoluteMatchOffset = bufferBaseOffset + i;
                if (absoluteMatchOffset >= endByteOffset) {
                    break;
                }

                FileSignature matchedSig = signatureDatabase.findMatch(buffer, i);

                if (matchedSig != null) {
                    // Evaluate boundary detector starting at absolute offset
                    BoundaryResult boundary = matchedSig.getBoundaryDetector().detectBoundary(
                            sectorReader,
                            absoluteMatchOffset,
                            matchedSig.getMaxScanBytes()
                    );

                    if (boundary.getStatus() != BoundaryStatus.INVALID && boundary.getLength() > 0) {
                        CarvedFile carvedFile = new CarvedFile(
                                matchedSig.getFormat(),
                                absoluteMatchOffset,
                                sectorSize,
                                boundary.getLength(),
                                boundary.getStatus(),
                                boundary.getDetails()
                        );
                        carvedFiles.add(carvedFile);

                        // Advance scan past this file if exact boundary, otherwise advance past header
                        if (boundary.getStatus() == BoundaryStatus.EXACT) {
                            long nextOffset = absoluteMatchOffset + boundary.getLength();
                            long jumpDelta = nextOffset - bufferBaseOffset;
                            if (jumpDelta > i && jumpDelta < buffer.length) {
                                i = (int) jumpDelta;
                                continue;
                            } else if (jumpDelta >= buffer.length) {
                                // Jump beyond current buffer
                                currentDiskByte = nextOffset;
                                overlap = new byte[0];
                                break;
                            }
                        }
                    }
                }

                i++;
            }

            if (currentDiskByte == bufferBaseOffset + overlap.length) {
                // Advance standard chunk
                currentDiskByte += chunkData.length;
                int newOverlapLen = Math.min(overlapSize, chunkData.length);
                overlap = new byte[newOverlapLen];
                System.arraycopy(chunkData, chunkData.length - newOverlapLen, overlap, 0, newOverlapLen);
            }
        }

        return Collections.unmodifiableList(carvedFiles);
    }
}
