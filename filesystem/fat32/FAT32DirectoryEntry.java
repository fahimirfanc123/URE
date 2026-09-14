package filesystem.fat32;

public class FAT32DirectoryEntry {

    private final String fileName;
    private final long startCluster;
    private final long fileSize;
    private final boolean deleted;
    private final int attributes;

    public FAT32DirectoryEntry(
            String fileName,
            long startCluster,
            long fileSize,
            boolean deleted,
            int attributes
    ) {
        this.fileName = fileName;
        this.startCluster = startCluster;
        this.fileSize = fileSize;
        this.deleted = deleted;
        this.attributes = attributes;
    }

    public String getFileName() {
        return fileName;
    }

    public long getStartCluster() {
        return startCluster;
    }

    public long getFileSize() {
        return fileSize;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public int getAttributes() {
        return attributes;
    }

    public boolean isDirectory() {
        return (attributes & 0x10) != 0;
    }

    @Override
    public String toString() {
        return "FAT32DirectoryEntry{" +
                "fileName='" + fileName + '\'' +
                ", startCluster=" + startCluster +
                ", fileSize=" + fileSize +
                ", deleted=" + deleted +
                ", directory=" + isDirectory() +
                '}';
    }
}
