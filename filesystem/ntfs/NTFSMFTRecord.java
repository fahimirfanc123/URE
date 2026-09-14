package filesystem.ntfs;

public class NTFSMFTRecord {

    private final long recordNumber;
    private final byte[] data;
    private final boolean validFileRecord;

    public NTFSMFTRecord(
            long recordNumber,
            byte[] data
    ) {

        this.recordNumber = recordNumber;
        this.data = data;

        this.validFileRecord =
                data != null &&
                data.length >= 4 &&
                data[0] == 'F' &&
                data[1] == 'I' &&
                data[2] == 'L' &&
                data[3] == 'E';
    }

    public long getRecordNumber() {
        return recordNumber;
    }

    public byte[] getData() {
        return data;
    }

    public boolean isValidFileRecord() {
        return validFileRecord;
    }

    @Override
    public String toString() {
        return "NTFSMFTRecord{" +
                "recordNumber=" + recordNumber +
                ", validFileRecord=" + validFileRecord +
                ", size=" + (data == null ? 0 : data.length) +
                '}';
    }
}
