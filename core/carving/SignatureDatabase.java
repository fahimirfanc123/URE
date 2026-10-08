package core.carving;

import core.carving.boundary.JpegBoundaryDetector;
import core.carving.boundary.PdfBoundaryDetector;
import core.carving.boundary.PngBoundaryDetector;
import core.carving.boundary.ZipBoundaryDetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of active file signatures and boundary detectors used by the carving engine.
 */
public class SignatureDatabase {

    private final List<FileSignature> signatures = new ArrayList<>();
    private int maxHeaderLength = 0;

    /**
     * Initializes the signature database with default supported formats:
     * JPEG, PNG, PDF, and ZIP.
     */
    public SignatureDatabase() {
        registerDefaults();
    }

    private void registerDefaults() {
        // JPEG: FF D8 FF (50 MB limit)
        register(new FileSignature(
                FileFormat.JPEG,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
                50L * 1024 * 1024,
                new JpegBoundaryDetector()
        ));

        // PNG: 89 50 4E 47 0D 0A 1A 0A (50 MB limit)
        register(new FileSignature(
                FileFormat.PNG,
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
                50L * 1024 * 1024,
                new PngBoundaryDetector()
        ));

        // PDF: %PDF- (100 MB limit)
        register(new FileSignature(
                FileFormat.PDF,
                new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D},
                100L * 1024 * 1024,
                new PdfBoundaryDetector()
        ));

        // ZIP: PK 03 04 (100 MB limit)
        register(new FileSignature(
                FileFormat.ZIP,
                new byte[]{0x50, 0x4B, 0x03, 0x04},
                100L * 1024 * 1024,
                new ZipBoundaryDetector()
        ));
    }

    /**
     * Registers a custom or additional file signature into the database.
     *
     * @param signature FileSignature to register
     */
    public void register(FileSignature signature) {
        if (signature == null) {
            throw new IllegalArgumentException("FileSignature cannot be null");
        }
        signatures.add(signature);
        if (signature.getHeaderLength() > maxHeaderLength) {
            maxHeaderLength = signature.getHeaderLength();
        }
    }

    /**
     * Matches the buffer contents at the given offset against all registered signatures.
     *
     * @param buffer byte buffer containing disk data
     * @param offset offset within the buffer to test
     * @return matching FileSignature, or null if no signature matches
     */
    public FileSignature findMatch(byte[] buffer, int offset) {
        if (buffer == null || offset < 0) {
            return null;
        }

        for (FileSignature sig : signatures) {
            if (sig.matches(buffer, offset)) {
                return sig;
            }
        }
        return null;
    }

    /**
     * Returns an unmodifiable list of all registered signatures.
     *
     * @return list of FileSignatures
     */
    public List<FileSignature> getSignatures() {
        return Collections.unmodifiableList(signatures);
    }

    /**
     * Gets the maximum header length among all registered signatures.
     *
     * @return maximum header length in bytes
     */
    public int getMaxHeaderLength() {
        return maxHeaderLength;
    }
}
