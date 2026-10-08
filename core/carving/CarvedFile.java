package core.carving;

import core.carving.boundary.SectorByteReader;
import core.disk.SectorReader;
import core.prediction.RecoveryFeatures;
import core.prediction.RecoveryPrediction;
import core.prediction.RecoveryPredictor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Immutable model representing a raw carved file candidate discovered on disk.
 */
public class CarvedFile {

    private final FileFormat format;
    private final long startByteOffset;
    private final long startSector;
    private final long length;
    private final BoundaryStatus boundaryStatus;
    private final String details;
    private final RecoveryFeatures recoveryFeatures;
    private final RecoveryPrediction prediction;

    public CarvedFile(
            FileFormat format,
            long startByteOffset,
            int sectorSize,
            long length,
            BoundaryStatus boundaryStatus,
            String details) {

        if (format == null) {
            throw new IllegalArgumentException("FileFormat cannot be null");
        }
        if (startByteOffset < 0) {
            throw new IllegalArgumentException("startByteOffset cannot be negative");
        }
        if (sectorSize <= 0) {
            throw new IllegalArgumentException("sectorSize must be positive");
        }
        if (length < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }
        if (boundaryStatus == null) {
            throw new IllegalArgumentException("BoundaryStatus cannot be null");
        }

        this.format = format;
        this.startByteOffset = startByteOffset;
        this.startSector = startByteOffset / sectorSize;
        this.length = length;
        this.boundaryStatus = boundaryStatus;
        this.details = details != null ? details : "";

        // Build real evidence-based recovery features
        boolean validHeader = (boundaryStatus != BoundaryStatus.INVALID);
        boolean validFooter = (boundaryStatus == BoundaryStatus.EXACT);
        boolean metadataAvailable = false; // Carved files do not have filesystem metadata
        boolean clustersAvailable = (length > 0);
        Boolean fragmented = null; // Unknown: cannot be proven contiguous or fragmented without filesystem cluster tables
        Double overwrittenPercentage = null; // Unknown: cannot be determined without cluster allocation maps

        this.recoveryFeatures = new RecoveryFeatures(
                validHeader,
                validFooter,
                metadataAvailable,
                clustersAvailable,
                fragmented,
                overwrittenPercentage
        );

        RecoveryPredictor predictor = new RecoveryPredictor();
        this.prediction = predictor.predict(this.recoveryFeatures);
    }

    public FileFormat getFormat() {
        return format;
    }

    public long getStartByteOffset() {
        return startByteOffset;
    }

    public long getStartSector() {
        return startSector;
    }

    public long getLength() {
        return length;
    }

    public BoundaryStatus getBoundaryStatus() {
        return boundaryStatus;
    }

    public String getDetails() {
        return details;
    }

    public RecoveryFeatures getRecoveryFeatures() {
        return recoveryFeatures;
    }

    public RecoveryPrediction getPrediction() {
        return prediction;
    }

    /**
     * Reads the carved file's content into a byte array.
     * Note: for large files, prefer {@link #recoverTo(SectorReader, Path)} to stream directly to disk.
     *
     * @param reader active SectorReader
     * @return byte array containing the file's data
     * @throws IOException if disk reading fails
     * @throws IllegalStateException if file length exceeds maximum allowable in-memory buffer (64 MB)
     */
    public byte[] readAllBytes(SectorReader reader) throws IOException {
        if (length > 64L * 1024 * 1024) {
            throw new IllegalStateException("File size (" + length + " bytes) exceeds in-memory buffer limit (64 MB). Use recoverTo() instead.");
        }
        return SectorByteReader.readBytes(reader, startByteOffset, (int) length);
    }

    /**
     * Streams the carved file directly from the SectorReader to the target output path.
     * Memory-efficient and suitable for files of any size.
     *
     * @param reader active SectorReader
     * @param destinationPath target file path on disk
     * @return Path to the written file
     * @throws IOException if writing or reading fails
     */
    public Path recoverTo(SectorReader reader, Path destinationPath) throws IOException {
        if (reader == null) {
            throw new IllegalArgumentException("SectorReader cannot be null");
        }
        if (destinationPath == null) {
            throw new IllegalArgumentException("Destination path cannot be null");
        }

        Path parent = destinationPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        final int bufferSize = 65536; // 64 KB streaming buffer
        long remaining = length;
        long currentOffset = startByteOffset;

        try (OutputStream out = Files.newOutputStream(destinationPath)) {
            while (remaining > 0) {
                int toRead = (int) Math.min(bufferSize, remaining);
                byte[] chunk = SectorByteReader.readBytes(reader, currentOffset, toRead);
                if (chunk.length == 0) {
                    break;
                }
                out.write(chunk);
                remaining -= chunk.length;
                currentOffset += chunk.length;
            }
        }

        return destinationPath;
    }

    @Override
    public String toString() {
        return "CarvedFile{" +
                "format=" + format +
                ", startByteOffset=" + startByteOffset +
                ", startSector=" + startSector +
                ", length=" + length +
                ", boundaryStatus=" + boundaryStatus +
                ", probability=" + prediction.getProbability() + "% (" + prediction.getRating() + ")" +
                '}';
    }
}
