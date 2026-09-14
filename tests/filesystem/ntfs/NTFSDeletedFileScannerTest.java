package tests.filesystem.ntfs;

import core.disk.SectorReader;
import filesystem.ntfs.*;

import java.util.List;

public class NTFSDeletedFileScannerTest {

    public static void main(String[] args) {

        if (args.length != 1) {

            System.out.println(
                    "Usage: NTFSDeletedFileScannerTest <ntfs-image>"
            );

            return;
        }


        System.out.println(
                "=== NTFS Deleted File Scanner Test ==="
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


            NTFSDeletedFileScanner scanner =
                    new NTFSDeletedFileScanner(
                            mftReader
                    );


            List<NTFSFileEntry> deleted =
                    scanner.scan(
                            1024
                    );


            System.out.println(
                    "Deleted files found: "
                            + deleted.size()
            );


            for (NTFSFileEntry entry : deleted) {

                System.out.println(
                        "--------------------------------"
                );

                System.out.println(
                        "Record: "
                                + entry.getRecordNumber()
                );

                System.out.println(
                        "File: "
                                + entry.getFileName()
                );

                System.out.println(
                        "Deleted: "
                                + entry.isDeleted()
                );
            }


            System.out.println();
            System.out.println(
                    "[PASS] MFT scan completed"
            );


        } catch (Exception e) {

            System.out.println(
                    "[FAIL] "
                            + e.getMessage()
            );

            e.printStackTrace();
        }
    }
}
