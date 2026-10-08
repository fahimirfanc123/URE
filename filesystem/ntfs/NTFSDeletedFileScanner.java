package filesystem.ntfs;

import core.disk.SectorReader;
import core.prediction.RecoveryFeatureExtractor;
import core.prediction.RecoveryFeatures;
import core.prediction.RecoveryPrediction;
import core.prediction.RecoveryPredictor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NTFSDeletedFileScanner {

    private final NTFSMFTReader mftReader;
    private final NTFSMFTParser parser;
    private final SectorReader reader;
    private final NTFSBootSector bootSector;
    private final RecoveryFeatureExtractor featureExtractor;
    private final RecoveryPredictor predictor;

    public NTFSDeletedFileScanner(
            NTFSMFTReader mftReader
    ) {
        this(mftReader, null, null);
    }

    public NTFSDeletedFileScanner(
            NTFSMFTReader mftReader,
            SectorReader reader,
            NTFSBootSector bootSector
    ) {
        if (mftReader == null) {
            throw new IllegalArgumentException(
                    "NTFSMFTReader cannot be null"
            );
        }

        this.mftReader = mftReader;
        this.reader = reader;
        this.bootSector = bootSector;
        this.parser = new NTFSMFTParser();
        this.featureExtractor = new RecoveryFeatureExtractor();
        this.predictor = new RecoveryPredictor();
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
                record = mftReader.readRecord(i);
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

                if (reader != null && bootSector != null) {
                    try {
                        RecoveryFeatures features =
                                featureExtractor.extractNTFS(
                                        record,
                                        reader,
                                        bootSector
                                );
                        RecoveryPrediction prediction =
                                predictor.predict(features);

                        entry = new NTFSFileEntry(
                                entry.getRecordNumber(),
                                entry.getFileName(),
                                entry.isDeleted(),
                                entry.isDirectory(),
                                entry.getFileSize(),
                                prediction
                        );
                    } catch (Exception e) {
                        // Non-fatal, use metadata entry
                    }
                }

                deletedFiles.add(
                        entry
                );
            }
        }

        return deletedFiles;
    }
}
