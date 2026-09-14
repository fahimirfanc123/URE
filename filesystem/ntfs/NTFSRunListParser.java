package filesystem.ntfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NTFSRunListParser {

    public List<NTFSDataRun> parse(
            byte[] data,
            int startOffset,
            int endOffset
    ) throws IOException {

        if (data == null) {
            throw new IllegalArgumentException(
                    "Data cannot be null"
            );
        }

        if (startOffset < 0 ||
            endOffset > data.length ||
            startOffset >= endOffset) {

            throw new IOException(
                    "Invalid NTFS runlist bounds"
            );
        }

        List<NTFSDataRun> runs =
                new ArrayList<>();

        long currentLCN = 0;

        int offset =
                startOffset;

        while (offset < endOffset) {

            int header =
                    data[offset] & 0xFF;

            offset++;

            /*
             * 0x00 marks the end of a runlist.
             */
            if (header == 0) {
                break;
            }

            int lengthSize =
                    header & 0x0F;

            int offsetSize =
                    (header >> 4) & 0x0F;

            if (lengthSize == 0 ||
                lengthSize > 8 ||
                offsetSize > 8) {

                throw new IOException(
                        "Invalid NTFS runlist entry"
                );
            }

            if (offset + lengthSize + offsetSize >
                endOffset) {

                throw new IOException(
                        "Truncated NTFS runlist"
                );
            }

            long clusterCount =
                    readUnsignedLE(
                            data,
                            offset,
                            lengthSize
                    );

            offset += lengthSize;

            if (clusterCount <= 0) {
                throw new IOException(
                        "Invalid NTFS run length"
                );
            }

            /*
             * offsetSize == 0 means sparse run.
             */
            if (offsetSize == 0) {

                runs.add(
                        new NTFSDataRun(
                                -1,
                                clusterCount,
                                true
                        )
                );

                continue;
            }

            long relativeLCN =
                    readSignedLE(
                            data,
                            offset,
                            offsetSize
                    );

            offset += offsetSize;

            currentLCN +=
                    relativeLCN;

            if (currentLCN < 0) {

                throw new IOException(
                        "Invalid NTFS cluster location"
                );
            }

            runs.add(
                    new NTFSDataRun(
                            currentLCN,
                            clusterCount,
                            false
                    )
            );
        }

        return runs;
    }


    private long readUnsignedLE(
            byte[] data,
            int offset,
            int size
    ) {

        long value = 0;

        for (int i = 0; i < size; i++) {

            value |=
                    ((long) data[offset + i] & 0xFF)
                            << (8 * i);
        }

        return value;
    }


    private long readSignedLE(
            byte[] data,
            int offset,
            int size
    ) {

        long value =
                readUnsignedLE(
                        data,
                        offset,
                        size
                );

        /*
         * Sign extend the value if the most-significant
         * stored byte has bit 7 set.
         */
        if (size < 8) {

            long signBit =
                    1L << ((size * 8) - 1);

            if ((value & signBit) != 0) {

                value |=
                        (-1L) << (size * 8);
            }
        }

        return value;
    }
}
