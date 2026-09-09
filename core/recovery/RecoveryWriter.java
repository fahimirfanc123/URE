package core.recovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RecoveryWriter is responsible for writing recovered
 * raw data to an output file.
 */
public class RecoveryWriter {

    /**
     * Writes recovered data to the specified output path.
     *
     * @param data       recovered raw bytes
     * @param outputPath destination file path
     * @return Path of the recovered file
     * @throws IOException              if writing fails
     * @throws IllegalArgumentException if data or path is invalid
     */
    public Path write(byte[] data, Path outputPath) throws IOException {

        if (data == null) {
            throw new IllegalArgumentException("Recovered data cannot be null");
        }

        if (outputPath == null) {
            throw new IllegalArgumentException("Output path cannot be null");
        }

        Path parent = outputPath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.write(outputPath, data);

        return outputPath;
    }
}
