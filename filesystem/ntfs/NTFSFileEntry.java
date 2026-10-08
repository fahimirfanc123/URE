package filesystem.ntfs;

import core.prediction.RecoveryPrediction;

public class NTFSFileEntry {

    private final long recordNumber;
    private final String fileName;
    private final boolean deleted;
    private final boolean directory;
    private final long fileSize;
    private final RecoveryPrediction prediction;

    public NTFSFileEntry(
            long recordNumber,
            String fileName,
            boolean deleted,
            boolean directory
    ) {
        this(recordNumber, fileName, deleted, directory, 0, null);
    }

    public NTFSFileEntry(
            long recordNumber,
            String fileName,
            boolean deleted,
            boolean directory,
            long fileSize,
            RecoveryPrediction prediction
    ) {
        this.recordNumber = recordNumber;
        this.fileName = fileName;
        this.deleted = deleted;
        this.directory = directory;
        this.fileSize = fileSize;
        this.prediction = prediction;
    }

    public long getRecordNumber() {
        return recordNumber;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long getFileSize() {
        return fileSize;
    }

    public RecoveryPrediction getPrediction() {
        return prediction;
    }

    @Override
    public String toString() {
        return "NTFSFileEntry{" +
                "recordNumber=" + recordNumber +
                ", fileName='" + fileName + '\'' +
                ", deleted=" + deleted +
                ", directory=" + directory +
                ", fileSize=" + fileSize +
                ", prediction=" + prediction +
                '}';
    }
}
