package core.carving;

/**
 * File formats recognized and supported by MINION URE signature carving.
 */
public enum FileFormat {

    JPEG("JPEG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    PDF("PDF", "pdf", "application/pdf"),
    ZIP("ZIP", "zip", "application/zip"),
    UNKNOWN("Unknown", "bin", "application/octet-stream");

    private final String displayName;
    private final String extension;
    private final String mimeType;

    FileFormat(String displayName, String extension, String mimeType) {
        this.displayName = displayName;
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getExtension() {
        return extension;
    }

    public String getMimeType() {
        return mimeType;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
