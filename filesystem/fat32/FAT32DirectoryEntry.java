package filesystem.fat32;

import core.prediction.RecoveryPrediction;

public class FAT32DirectoryEntry {

    private final String fileName;
    private final long startCluster;
    private final long fileSize;
    private final boolean deleted;
    private final int attributes;
    private final RecoveryPrediction prediction;
    private final String path; // Directory path for subdirectory entries

    public FAT32DirectoryEntry(
            String fileName,
            long startCluster,
            long fileSize,
            boolean deleted,
            int attributes
    ) {
        this(fileName, startCluster, fileSize, deleted, attributes, null, null);
    }

    public FAT32DirectoryEntry(
            String fileName,
            long startCluster,
            long fileSize,
            boolean deleted,
            int attributes,
            RecoveryPrediction prediction
    ) {
        this(fileName, startCluster, fileSize, deleted, attributes, prediction, null);
    }

    public FAT32DirectoryEntry(
            String fileName,
            long startCluster,
            long fileSize,
            boolean deleted,
            int attributes,
            RecoveryPrediction prediction,
            String path
    ) {
        this.fileName = fileName;
        this.startCluster = startCluster;
        this.fileSize = fileSize;
        this.deleted = deleted;
        this.attributes = attributes;
        this.prediction = prediction;
        this.path = path;
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

    public RecoveryPrediction getPrediction() {
        return prediction;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "FAT32DirectoryEntry{" +
                "fileName='" + fileName + '\'' +
                ", path='" + path + '\'' +
                ", startCluster=" + startCluster +
                ", fileSize=" + fileSize +
                ", deleted=" + deleted +
                ", directory=" + isDirectory() +
                ", prediction=" + prediction +
                '}';
    }
}
