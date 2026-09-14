package filesystem.ntfs;

public class NTFSFileEntry {

    private final long recordNumber;
    private final String fileName;
    private final boolean deleted;
    private final boolean directory;

    public NTFSFileEntry(
            long recordNumber,
            String fileName,
            boolean deleted,
            boolean directory
    ) {
        this.recordNumber = recordNumber;
        this.fileName = fileName;
        this.deleted = deleted;
        this.directory = directory;
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

    @Override
    public String toString() {
        return "NTFSFileEntry{" +
                "recordNumber=" + recordNumber +
                ", fileName='" + fileName + '\'' +
                ", deleted=" + deleted +
                ", directory=" + directory +
                '}';
    }
}
