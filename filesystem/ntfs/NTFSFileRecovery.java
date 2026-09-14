package filesystem.ntfs;

import core.disk.SectorReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class NTFSFileRecovery {

    private static final long ATTRIBUTE_DATA =
            0x80L;

    private static final long ATTRIBUTE_END =
            0xFFFFFFFFL;

    private final SectorReader reader;
    private final NTFSBootSector boot;
    private final NTFSMFTReader mftReader;

    public NTFSFileRecovery(
            SectorReader reader,
            NTFSBootSector boot,
            NTFSMFTReader mftReader
    ) {

        if (reader == null) {
            throw new IllegalArgumentException(
                    "SectorReader cannot be null"
            );
        }

        if (boot == null) {
            throw new IllegalArgumentException(
                    "NTFSBootSector cannot be null"
            );
        }

        if (mftReader == null) {
            throw new IllegalArgumentException(
                    "NTFSMFTReader cannot be null"
            );
        }

        this.reader = reader;
        this.boot = boot;
        this.mftReader = mftReader;
    }


    public byte[] recover(
            NTFSFileEntry entry
    ) throws IOException {

        if (entry == null) {
            throw new IllegalArgumentException(
                    "NTFSFileEntry cannot be null"
            );
        }

        if (entry.isDirectory()) {

            throw new IOException(
                    "Cannot recover directory data"
            );
        }

        NTFSMFTRecord record =
                mftReader.readRecord(
                        entry.getRecordNumber()
                );

        if (!record.isValidFileRecord()) {

            throw new IOException(
                    "Invalid NTFS MFT record"
            );
        }

        byte[] recordData =
                record.getData().clone();

        if (!applyFixup(recordData)) {

            throw new IOException(
                    "Failed to apply NTFS fixup"
            );
        }

        int firstAttributeOffset =
                readUInt16LE(
                        recordData,
                        20
                );

        int offset =
                firstAttributeOffset;

        while (offset + 16 <=
               recordData.length) {

            long type =
                    readUInt32LE(
                            recordData,
                            offset
                    );

            if (type == ATTRIBUTE_END) {
                break;
            }

            int attributeLength =
                    (int) readUInt32LE(
                            recordData,
                            offset + 4
                    );

            if (attributeLength <= 0 ||
                offset + attributeLength >
                recordData.length) {

                break;
            }

            int nonResident =
                    recordData[offset + 8]
                            & 0xFF;

            if (type == ATTRIBUTE_DATA) {

                if (nonResident == 0) {

                    return recoverResident(
                            recordData,
                            offset
                    );

                } else {

                    return recoverNonResident(
                            recordData,
                            offset,
                            attributeLength
                    );
                }
            }

            offset += attributeLength;
        }

        throw new IOException(
                "No recoverable NTFS DATA attribute found"
        );
    }


    private byte[] recoverResident(
            byte[] recordData,
            int attributeOffset
    ) throws IOException {

        int contentLength =
                (int) readUInt32LE(
                        recordData,
                        attributeOffset + 16
                );

        int contentOffset =
                readUInt16LE(
                        recordData,
                        attributeOffset + 20
                );

        int contentStart =
                attributeOffset
                        + contentOffset;

        if (contentLength < 0 ||
            contentStart < 0 ||
            contentStart + contentLength >
            recordData.length) {

            throw new IOException(
                    "Invalid resident DATA attribute"
            );
        }

        return Arrays.copyOfRange(
                recordData,
                contentStart,
                contentStart
                        + contentLength
        );
    }


    private byte[] recoverNonResident(
            byte[] recordData,
            int attributeOffset,
            int attributeLength
    ) throws IOException {

        int runListOffset =
                readUInt16LE(
                        recordData,
                        attributeOffset + 32
                );

        int runListStart =
                attributeOffset
                        + runListOffset;

        int attributeEnd =
                attributeOffset
                        + attributeLength;


        if (runListStart <= attributeOffset ||
            runListStart >= attributeEnd) {

            throw new IOException(
                    "Invalid NTFS runlist offset"
            );
        }


        long realFileSize =
                readInt64LE(
                        recordData,
                        attributeOffset + 48
                );


        if (realFileSize < 0) {

            throw new IOException(
                    "Invalid NTFS real file size"
            );
        }


        NTFSRunListParser parser =
                new NTFSRunListParser();


        List<NTFSDataRun> runs =
                parser.parse(
                        recordData,
                        runListStart,
                        attributeEnd
                );


        if (runs.isEmpty()) {

            throw new IOException(
                    "NTFS DATA runlist is empty"
            );
        }


        ByteArrayOutputStream output =
                new ByteArrayOutputStream();


        long remaining =
                realFileSize;


        for (NTFSDataRun run : runs) {

            if (remaining <= 0) {
                break;
            }


            long runBytes =
                    run.getClusterCount()
                            * boot.getBytesPerCluster();


            long bytesToWrite =
                    Math.min(
                            runBytes,
                            remaining
                    );


            if (run.isSparse()) {

                writeZeros(
                        output,
                        bytesToWrite
                );

                remaining -=
                        bytesToWrite;

                continue;
            }


            long startSector =
                    run.getStartCluster()
                            * boot.getSectorsPerCluster();


            long sectorsToReadLong =
                    run.getClusterCount()
                            * boot.getSectorsPerCluster();


            if (sectorsToReadLong >
                Integer.MAX_VALUE) {

                throw new IOException(
                        "NTFS data run too large"
                );
            }


            byte[] runData =
                    reader.readSectors(
                            startSector,
                            (int) sectorsToReadLong
                    );


            int writeLength =
                    (int) Math.min(
                            bytesToWrite,
                            runData.length
                    );


            output.write(
                    runData,
                    0,
                    writeLength
            );


            remaining -=
                    writeLength;
        }


        if (remaining != 0) {

            throw new IOException(
                    "Unable to recover complete NTFS file. "
                            + remaining
                            + " bytes missing."
            );
        }


        return output.toByteArray();
    }


    private void writeZeros(
            ByteArrayOutputStream output,
            long count
    ) {

        byte[] zeros =
                new byte[4096];


        long remaining =
                count;


        while (remaining > 0) {

            int amount =
                    (int) Math.min(
                            zeros.length,
                            remaining
                    );

            output.write(
                    zeros,
                    0,
                    amount
            );

            remaining -=
                    amount;
        }
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
            usaOffset + usaCount * 2 >
            data.length) {

            return false;
        }


        int updateSequenceNumber =
                readUInt16LE(
                        data,
                        usaOffset
                );


        for (int i = 1;
             i < usaCount;
             i++) {

            int sectorEnd =
                    (i * 512) - 2;


            if (sectorEnd + 1 >=
                data.length) {

                return false;
            }


            int existing =
                    readUInt16LE(
                            data,
                            sectorEnd
                    );


            if (existing !=
                updateSequenceNumber) {

                return false;
            }


            data[sectorEnd] =
                    data[
                            usaOffset
                                    + i * 2
                    ];


            data[sectorEnd + 1] =
                    data[
                            usaOffset
                                    + i * 2
                                    + 1
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
                ((data[offset + 1] & 0xFF)
                        << 8);
    }


    private long readUInt32LE(
            byte[] data,
            int offset
    ) {

        return ((long) data[offset] & 0xFF)
                |
                (((long) data[offset + 1] & 0xFF)
                        << 8)
                |
                (((long) data[offset + 2] & 0xFF)
                        << 16)
                |
                (((long) data[offset + 3] & 0xFF)
                        << 24);
    }


    private long readInt64LE(
            byte[] data,
            int offset
    ) {

        long value = 0;

        for (int i = 0;
             i < 8;
             i++) {

            value |=
                    ((long) data[offset + i]
                            & 0xFF)
                            << (8 * i);
        }

        return value;
    }
}
