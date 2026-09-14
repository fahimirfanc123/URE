package filesystem.ntfs;

import core.disk.SectorReader;

import java.io.IOException;

public class NTFSMFTReader {

    private final SectorReader reader;
    private final NTFSBootSector boot;

    public NTFSMFTReader(
            SectorReader reader,
            NTFSBootSector boot
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

        this.reader = reader;
        this.boot = boot;
    }

    public NTFSMFTRecord readRecord(
            long recordNumber
    ) throws IOException {

        if (recordNumber < 0) {
            throw new IllegalArgumentException(
                    "Record number cannot be negative"
            );
        }

        int recordSize =
                calculateRecordSize();

        long mftByteOffset =
                boot.getMftStartSector()
                        * boot.getBytesPerSector();

        long recordByteOffset =
                mftByteOffset
                        + (recordNumber * recordSize);

        long sectorNumber =
                recordByteOffset
                        / boot.getBytesPerSector();

        int offsetInsideSector =
                (int) (
                        recordByteOffset
                                % boot.getBytesPerSector()
                );

        int sectorsNeeded =
                (int) Math.ceil(
                        (offsetInsideSector + recordSize)
                                /
                                (double) boot.getBytesPerSector()
                );

        byte[] sectors =
                reader.readSectors(
                        sectorNumber,
                        sectorsNeeded
                );

        byte[] record =
                new byte[recordSize];

        System.arraycopy(
                sectors,
                offsetInsideSector,
                record,
                0,
                recordSize
        );

        return new NTFSMFTRecord(
                recordNumber,
                record
        );
    }

    public int calculateRecordSize() {

        int value =
                boot.getClustersPerFileRecord();

        if (value > 0) {

            return (int) (
                    value
                            * boot.getBytesPerCluster()
            );
        }

        int exponent =
                -value;

        return 1 << exponent;
    }
}
