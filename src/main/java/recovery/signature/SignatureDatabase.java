package recovery.signature;

import java.util.HashMap;
import java.util.Map;

/**
 * Central repository of magic-byte signatures used for deep raw file carving.
 *
 * <p>Each entry maps a lowercase file extension to:
 * <ul>
 *   <li><b>Header</b> – the byte sequence that marks the start of a file.</li>
 *   <li><b>Footer</b> – the byte sequence that marks the end of a file
 *       ({@code null} for formats with no defined EOF marker).</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 * SignatureDatabase db = new SignatureDatabase();
 * byte[] header = db.getHeader("jpg");  // [FF, D8, FF]
 * byte[] footer = db.getFooter("jpg");  // [FF, D9]
 * byte[] pngEnd = db.getFooter("png"); // null — no standard footer
 * }</pre>
 */
public class SignatureDatabase {

    /** Immutable record pairing a header with an optional footer. */
    private record SignatureEntry(byte[] header, byte[] footer) {}

    /**
     * Primary lookup table.
     * Keys are lowercase file extensions (e.g. {@code "jpg"}, {@code "png"}).
     */
    private final Map<String, SignatureEntry> database = new HashMap<>();

    /**
     * Populates the database with all built-in signature definitions.
     */
    public SignatureDatabase() {
        registerBuiltins();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the header (magic bytes / SOI marker) for the given extension.
     *
     * @param extension lowercase file extension (e.g. {@code "jpg"})
     * @return the header byte array, or {@code null} if the extension is
     *         unknown or {@code extension} itself is {@code null}
     */
    public byte[] getHeader(String extension) {
        if (extension == null) {
            return null;
        }
        SignatureEntry entry = database.get(extension.toLowerCase());
        return entry != null ? entry.header() : null;
    }

    /**
     * Returns the footer (EOF marker) for the given extension.
     *
     * <p>Not all formats have a defined footer. When no footer is registered
     * the method returns {@code null}; callers should fall back to carving a
     * fixed-size block or scanning for the next header in that case.
     *
     * @param extension lowercase file extension (e.g. {@code "jpg"})
     * @return the footer byte array, or {@code null} when no footer is defined
     *         or the extension is unknown
     */
    public byte[] getFooter(String extension) {
        if (extension == null) {
            return null;
        }
        SignatureEntry entry = database.get(extension.toLowerCase());
        return entry != null ? entry.footer() : null;
    }

    /**
     * Returns {@code true} if the database contains an entry for the given
     * extension.
     *
     * @param extension lowercase file extension
     * @return {@code true} if a signature definition exists
     */
    public boolean isSupported(String extension) {
        if (extension == null) {
            return false;
        }
        return database.containsKey(extension.toLowerCase());
    }

    // -------------------------------------------------------------------------
    // Registration helpers
    // -------------------------------------------------------------------------

    /**
     * Registers all built-in signature definitions.
     *
     * <p>To add a new format, call {@link #register(String, byte[], byte[])}
     * here with the appropriate header and footer (pass {@code null} for
     * formats that have no defined EOF marker).
     */
    private void registerBuiltins() {

        // JPEG
        // Header : FF D8 FF        (Start Of Image)
        // Footer : FF D9           (End Of Image)
        register("jpg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
                new byte[]{(byte) 0xFF, (byte) 0xD9});

        // PNG
        // Header : 89 50 4E 47 0D 0A 1A 0A  (full 8-byte PNG signature)
        // Footer : none — PNG does not have a universally reliable EOF marker;
        //          carvers typically use the IEND chunk (00 00 00 00 49 45 4E 44 AE 42 60 82)
        //          but it is omitted here because its length varies with chunk ordering.
        register("png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
                null);
    }

    /**
     * Adds or replaces a signature entry in the database.
     *
     * <p>This method is intentionally package-private to allow extension in
     * tests or subclasses without exposing mutation to arbitrary callers.
     *
     * @param extension lowercase file extension; must not be {@code null}
     * @param header    magic bytes marking the file start; must not be {@code null}
     * @param footer    magic bytes marking the file end, or {@code null} if absent
     */
    void register(String extension, byte[] header, byte[] footer) {
        if (extension == null || header == null) {
            throw new IllegalArgumentException(
                    "extension and header must not be null");
        }
        database.put(extension.toLowerCase(), new SignatureEntry(header, footer));
    }
}
