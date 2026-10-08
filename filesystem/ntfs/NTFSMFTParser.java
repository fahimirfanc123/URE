package filesystem.ntfs;

import java.nio.charset.StandardCharsets;

public class NTFSMFTParser {

    private static final int ATTRIBUTE_FILE_NAME = 0x30;
    private static final int ATTRIBUTE_DATA = 0x80;
    private static final int ATTRIBUTE_END = 0xFFFFFFFF;

    public NTFSFileEntry parse(
            NTFSMFTRecord record
    ) {

        if (record == null ||
            !record.isValidFileRecord()) {

            return null;
        }

        byte[] data =
                record.getData().clone();

        /*
         * Apply NTFS Update Sequence Array fixups.
         */
        if (!applyFixup(data)) {
            return null;
        }

        /*
         * MFT record flags:
         *
         * bit 0 = record is in use
         * bit 1 = directory
         */
        int flags =
                readUInt16LE(
                        data,
                        22
                );

        boolean inUse =
                (flags & 0x0001) != 0;

        boolean directory =
                (flags & 0x0002) != 0;

        boolean deleted =
                !inUse;

        int firstAttributeOffset =
                readUInt16LE(
                        data,
                        20
                );

        if (firstAttributeOffset <= 0 ||
            firstAttributeOffset >= data.length) {

            return null;
        }

        int offset =
                firstAttributeOffset;

        String fileName = null;
        long fileSize = 0;

        while (offset + 16 <= data.length) {

            long attributeType =
                    readUInt32LE(
                            data,
                            offset
                    );

            if (attributeType == ATTRIBUTE_END) {
                break;
            }

            int attributeLength =
                    (int) readUInt32LE(
                            data,
                            offset + 4
                    );

            if (attributeLength <= 0 ||
                offset + attributeLength > data.length) {

                break;
            }

            int nonResident =
                    data[offset + 8] & 0xFF;

            if (attributeType == ATTRIBUTE_FILE_NAME &&
                nonResident == 0) {

                int contentLength =
                        (int) readUInt32LE(
                                data,
                                offset + 16
                        );

                int contentOffset =
                        readUInt16LE(
                                data,
                                offset + 20
                        );

                int contentStart =
                        offset + contentOffset;

                if (contentStart >= 0 &&
                    contentLength >= 66 &&
                    contentStart + contentLength <= data.length) {

                    int nameLength =
                            data[
                                    contentStart + 64
                            ] & 0xFF;

                    int nameByteLength =
                            nameLength * 2;

                    int nameStart =
                            contentStart + 66;

                    if (nameStart + nameByteLength <= data.length) {

                        fileName =
                                new String(
                                        data,
                                        nameStart,
                                        nameByteLength,
                                        StandardCharsets.UTF_16LE
                                );

                        if (fileSize == 0 && contentStart + 56 <= data.length) {
                            fileSize = readInt64LE(data, contentStart + 48);
                        }
                    }
                }
            } else if (attributeType == ATTRIBUTE_DATA) {
                if (nonResident == 0) {
                    fileSize = readUInt32LE(data, offset + 16);
                } else {
                    fileSize = readInt64LE(data, offset + 48);
                }
            }

            offset += attributeLength;
        }

        if (fileName != null && !fileName.isBlank()) {
            return new NTFSFileEntry(
                    record.getRecordNumber(),
                    fileName,
                    deleted,
                    directory,
                    fileSize,
                    null
            );
        }

        return null;
    }

    private boolean applyFixup(
            byte[] data
    ) {

        if (data.length < 8) {
            return false;
        }

        int usaOffset =
                readUInt16LE(
                        data,
                        4
                );

        int usaCount =
                readUInt16LE(
                        data,
                        6
                );

        if (usaOffset <= 0 ||
            usaCount <= 1 ||
            usaOffset + usaCount * 2 > data.length) {

            return false;
        }

        int updateSequenceNumber =
                readUInt16LE(
                        data,
                        usaOffset
                );

        for (int i = 1; i < usaCount; i++) {

            int sectorEnd =
                    (i * 512) - 2;

            if (sectorEnd + 1 >= data.length) {
                return false;
            }

            int existing =
                    readUInt16LE(
                            data,
                            sectorEnd
                    );

            if (existing != updateSequenceNumber) {
                return false;
            }

            data[sectorEnd] =
                    data[
                            usaOffset + i * 2
                    ];

            data[sectorEnd + 1] =
                    data[
                            usaOffset + i * 2 + 1
                    ];
        }

        return true;
    }

    private int readUInt16LE(
            byte[] data,
            int offset
    ) {

        return (data[offset] & 0xFF)
                |
                ((data[offset + 1] & 0xFF) << 8);
    }

    private long readUInt32LE(
            byte[] data,
            int offset
    ) {

        return ((long) data[offset] & 0xFF)
                |
                (((long) data[offset + 1] & 0xFF) << 8)
                |
                (((long) data[offset + 2] & 0xFF) << 16)
                |
                (((long) data[offset + 3] & 0xFF) << 24);
    }

    private long readInt64LE(
            byte[] data,
            int offset
    ) {

        long value = 0;

        for (int i = 0; i < 8; i++) {
            value |= ((long) data[offset + i] & 0xFF) << (8 * i);
        }

        return value;
    }
}
