package tests.filesystem.ntfs;

import core.disk.SectorReader;
import core.recovery.RecoveryWriter;

import filesystem.ntfs.NTFSBootSector;
import filesystem.ntfs.NTFSDeletedFileScanner;
import filesystem.ntfs.NTFSFileEntry;
import filesystem.ntfs.NTFSFileRecovery;
import filesystem.ntfs.NTFSMFTReader;

import java.nio.file.Path;
import java.util.List;

public class NTFSFileRecoveryTest {

    public static void main(String[] args) {

        if (args.length != 2) {

            System.out.println(
                    "Usage: NTFSFileRecoveryTest <ntfs-image> <output-file>"
            );

            return;
        }

        String imagePath =
                args[0];

        Path outputPath =
                Path.of(
                        args[1]
                );

        System.out.println(
                "=== NTFS Deleted File Recovery Test ==="
        );

        try (SectorReader reader =
                     new SectorReader(imagePath)) {

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

            if (deleted.isEmpty()) {

                System.out.println(
                        "[FAIL] No deleted NTFS files found"
                );

                return;
            }

            NTFSFileEntry target =
                    deleted.get(0);

            System.out.println(
                    "Recovering: "
                            + target.getFileName()
            );

            System.out.println(
                    "MFT record: "
                            + target.getRecordNumber()
            );

            NTFSFileRecovery recovery =
                    new NTFSFileRecovery(
                            reader,
                            boot,
                            mftReader
                    );

            byte[] recovered =
                    recovery.recover(
                            target
                    );

            RecoveryWriter writer =
                    new RecoveryWriter();

            writer.write(
                    recovered,
                    outputPath
            );

            System.out.println(
                    "[PASS] Deleted NTFS file recovered"
            );

            System.out.println(
                    "Recovered bytes: "
                            + recovered.length
            );

            System.out.println(
                    "Output: "
                            + outputPath.toAbsolutePath()
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
