package tests.filesystem.ntfs;

import core.disk.SectorReader;
import filesystem.ntfs.NTFSBootSector;
import filesystem.ntfs.NTFSMFTReader;
import filesystem.ntfs.NTFSMFTRecord;

public class NTFSMFTReaderTest {

    public static void main(String[] args) {

        if (args.length != 1) {

            System.out.println(
                    "Usage: NTFSMFTReaderTest <ntfs-image>"
            );

            return;
        }

        System.out.println(
                "=== NTFS MFT Reader Test ==="
        );

        try (SectorReader reader =
                     new SectorReader(args[0])) {

            NTFSBootSector boot =
                    NTFSBootSector.parse(
                            reader
                    );

            NTFSMFTReader mftReader =
                    new NTFSMFTReader(
                            reader,
                            boot
                    );

            System.out.println(
                    "Calculated MFT record size: "
                            + mftReader.calculateRecordSize()
                            + " bytes"
            );

            int validRecords = 0;

            for (int i = 0; i < 16; i++) {

                NTFSMFTRecord record =
                        mftReader.readRecord(i);

                System.out.println(
                        "Record "
                                + i
                                + ": "
                                + record
                );

                if (record.isValidFileRecord()) {
                    validRecords++;
                }
            }

            if (validRecords > 0) {

                System.out.println();
                System.out.println(
                        "[PASS] Valid NTFS MFT FILE records detected"
                );

                System.out.println(
                        "Valid records: "
                                + validRecords
                );

            } else {

                System.out.println();
                System.out.println(
                        "[FAIL] No valid FILE records detected"
                );
            }

        } catch (Exception e) {

            System.out.println(
                    "[FAIL] "
                            + e.getMessage()
            );

            e.printStackTrace();
        }
    }
}
