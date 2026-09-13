package recovery.fat32;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles file extraction and recovery for FAT32 file systems.
 *
 * <p>Recovered file data (carved from raw disk sectors) is written to a
 * caller-supplied output directory. The directory is created automatically
 * if it does not yet exist.
 */
public class Fat32Recovery {

    private static final Logger LOGGER =
            Logger.getLogger(Fat32Recovery.class.getName());

    /**
     * Writes {@code fileData} to {@code outputDirectory/filename}.
     *
     * <p>Steps performed:
     * <ol>
     *   <li>Validate arguments (null / empty checks).</li>
     *   <li>Resolve and, if necessary, create the output directory.</li>
     *   <li>Write the raw bytes atomically via {@link FileOutputStream}.</li>
     * </ol>
     *
     * @param fileData        raw bytes to recover; must not be {@code null} or empty
     * @param outputDirectory path of the directory that will receive the file;
     *                        must not be {@code null} or blank
     * @param filename        target filename (no path separators); must not be
     *                        {@code null} or blank
     * @return {@code true} if the file was written successfully,
     *         {@code false} if a validation failure or {@link IOException} occurred
     */
    public boolean recoverFile(byte[] fileData, String outputDirectory, String filename) {

        // ------------------------------------------------------------------
        // 1. Argument validation
        // ------------------------------------------------------------------
        if (fileData == null || fileData.length == 0) {
            LOGGER.warning("recoverFile: fileData is null or empty — nothing to write.");
            return false;
        }
        if (outputDirectory == null || outputDirectory.isBlank()) {
            LOGGER.warning("recoverFile: outputDirectory is null or blank.");
            return false;
        }
        if (filename == null || filename.isBlank()) {
            LOGGER.warning("recoverFile: filename is null or blank.");
            return false;
        }

        // ------------------------------------------------------------------
        // 2. Ensure the output directory exists
        // ------------------------------------------------------------------
        File directory = new File(outputDirectory);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                LOGGER.severe("recoverFile: failed to create output directory: "
                        + directory.getAbsolutePath());
                return false;
            }
            LOGGER.info("recoverFile: created output directory: "
                    + directory.getAbsolutePath());
        }

        // ------------------------------------------------------------------
        // 3. Write file data
        // ------------------------------------------------------------------
        File outputFile = new File(directory, filename);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(fileData);
            fos.flush();
            LOGGER.info(String.format(
                    "recoverFile: successfully wrote %d byte(s) → %s",
                    fileData.length, outputFile.getAbsolutePath()));
            return true;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,
                    "recoverFile: I/O error writing file: " + outputFile.getAbsolutePath(), e);
            return false;
        }
    }
}
