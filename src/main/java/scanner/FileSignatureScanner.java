package scanner;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Detects file types by matching magic byte signatures (file carving).
 *
 * <p>Supported formats:
 * <ul>
 *   <li>JPEG  – {@code FF D8 FF}</li>
 *   <li>PNG   – {@code 89 50 4E 47}</li>
 *   <li>PDF   – {@code 25 50 44 46}</li>
 *   <li>ZIP / DOCX / XLSX – {@code 50 4B 03 04}</li>
 * </ul>
 */
public class FileSignatureScanner {

    /**
     * Ordered map of magic-byte signatures → file extension.
     *
     * <p>A {@link LinkedHashMap} is used so that longer / more-specific
     * signatures can be placed before shorter ones if needed in the future,
     * without rewriting lookup logic.
     */
    private static final Map<byte[], String> SIGNATURES = new LinkedHashMap<>();

    static {
        // JPEG  – FF D8 FF
        SIGNATURES.put(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "jpg");
        // PNG   – 89 50 4E 47
        SIGNATURES.put(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}, "png");
        // PDF   – 25 50 44 46  (%PDF)
        SIGNATURES.put(new byte[]{0x25, 0x50, 0x44, 0x46}, "pdf");
        // ZIP / DOCX / XLSX – 50 4B 03 04  (PK\x03\x04)
        SIGNATURES.put(new byte[]{0x50, 0x4B, 0x03, 0x04}, "zip");
    }

    /**
     * Detects the file type of the supplied raw data block by comparing its
     * leading bytes against the known magic-byte signatures.
     *
     * @param block raw bytes read from a storage medium; must not be
     *              {@code null} and should contain at least as many bytes as
     *              the longest signature (4 bytes).
     * @return the lowercase file extension (e.g. {@code "jpg"}, {@code "png"},
     *         {@code "pdf"}, {@code "zip"}), or {@code "UNKNOWN"} when no
     *         signature matches.
     */
    public String detectFileType(byte[] block) {
        if (block == null || block.length == 0) {
            return "UNKNOWN";
        }

        for (Map.Entry<byte[], String> entry : SIGNATURES.entrySet()) {
            byte[] signature = entry.getKey();
            if (startsWith(block, signature)) {
                return entry.getValue();
            }
        }

        return "UNKNOWN";
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when {@code data} begins with every byte in
     * {@code prefix}.
     *
     * @param data   the buffer to inspect; never {@code null}
     * @param prefix the magic-byte sequence; never {@code null}
     * @return {@code true} if {@code data} starts with {@code prefix}
     */
    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
