package filesystem.fat32;

/**
 * Represents the outcome of a FAT32 file recovery attempt.
 *
 * EXACT: All file bytes recovered with consistent evidence
 * PARTIAL: Some data recovered, but evidence indicates missing/corrupted data
 * FAILED: No trustworthy recovery could be produced
 */
public enum FAT32RecoveryStatus {
    EXACT("Exact Recovery"),
    PARTIAL("Partial Recovery"),
    FAILED("Recovery Failed");

    private final String displayName;

    FAT32RecoveryStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
