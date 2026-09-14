package filesystem.ntfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NTFSDeletedFileScanner {

    private final NTFSMFTReader mftReader;
    private final NTFSMFTParser parser;

    public NTFSDeletedFileScanner(
            NTFSMFTReader mftReader
    ) {

        if (mftReader == null) {
            throw new IllegalArgumentException(
                    "NTFSMFTReader cannot be null"
            );
        }

        this.mftReader =
                mftReader;

        this.parser =
                new NTFSMFTParser();
    }


    public List<NTFSFileEntry> scan(
            int maxRecords
    ) throws IOException {

        if (maxRecords <= 0) {
            throw new IllegalArgumentException(
                    "maxRecords must be greater than zero"
            );
        }


        List<NTFSFileEntry> deletedFiles =
                new ArrayList<>();


        for (int i = 0; i < maxRecords; i++) {

            NTFSMFTRecord record;

            try {

                record =
                        mftReader.readRecord(i);

            } catch (Exception e) {

                break;
            }


            if (!record.isValidFileRecord()) {
                continue;
            }


            NTFSFileEntry entry =
                    parser.parse(
                            record
                    );


            if (entry != null &&
                entry.isDeleted() &&
                !entry.isDirectory()) {

                deletedFiles.add(
                        entry
                );
            }
        }


        return deletedFiles;
    }
}
