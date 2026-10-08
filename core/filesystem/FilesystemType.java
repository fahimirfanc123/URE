package core.filesystem;

/**
 * Supported filesystem types recognized by MINION URE auto-detection.
 */
public enum FilesystemType {

    FAT32("FAT32"),
    NTFS("NTFS"),
    UNKNOWN("Unknown / RAW");

    private final String displayName;

    FilesystemType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
