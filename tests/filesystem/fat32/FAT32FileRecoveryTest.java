package tests.filesystem.fat32;

import core.disk.SectorReader;
import core.recovery.RecoveryWriter;

import filesystem.fat32.FAT32BootSector;
import filesystem.fat32.FAT32DeletedFileScanner;
import filesystem.fat32.FAT32DirectoryEntry;
import filesystem.fat32.FAT32FileRecovery;

import java.nio.file.Path;
import java.util.List;

public class FAT32FileRecoveryTest {

    public static void main(String[] args) {

        if (args.length != 2) {

            System.out.println(
                    "Usage: FAT32FileRecoveryTest "
                            + "<image> <output-file>"
            );

            return;
        }

        System.out.println(
                "=== FAT32 Deleted File Recovery Test ==="
        );

        try (SectorReader reader =
                     new SectorReader(args[0])) {

            /*
             * Parse FAT32 filesystem.
             */
            FAT32BootSector boot =
                    FAT32BootSector.parse(
                            reader
                    );


            /*
             * Find deleted files.
             */
            FAT32DeletedFileScanner scanner =
                    new FAT32DeletedFileScanner(
                            reader,
                            boot
                    );


            List<FAT32DirectoryEntry> deletedFiles =
                    scanner.scanRootDirectory();


            if (deletedFiles.isEmpty()) {

                System.out.println(
                        "[FAIL] No deleted files found"
                );

                return;
            }


            FAT32DirectoryEntry entry =
                    deletedFiles.get(0);


            System.out.println(
                    "Recovering: "
                            + entry.getFileName()
            );

            System.out.println(
                    "Original size: "
                            + entry.getFileSize()
                            + " bytes"
            );

            System.out.println(
                    "Start cluster: "
                            + entry.getStartCluster()
            );


            /*
             * Recover file bytes.
             */
            FAT32FileRecovery recovery =
                    new FAT32FileRecovery(
                            reader,
                            boot
                    );


            byte[] recovered =
                    recovery.recover(
                            entry
                    );


            /*
             * Write recovered file.
             */
            RecoveryWriter writer =
                    new RecoveryWriter();


            Path output =
                    writer.write(
                            recovered,
                            Path.of(args[1])
                    );


            System.out.println(
                    "[PASS] Deleted file recovered"
            );

            System.out.println(
                    "Recovered bytes: "
                            + recovered.length
            );

            System.out.println(
                    "Output: "
                            + output.toAbsolutePath()
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
