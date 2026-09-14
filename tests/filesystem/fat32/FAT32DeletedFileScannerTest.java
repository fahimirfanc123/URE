package tests.filesystem.fat32;

import core.disk.SectorReader;
import filesystem.fat32.FAT32BootSector;
import filesystem.fat32.FAT32DeletedFileScanner;
import filesystem.fat32.FAT32DirectoryEntry;

import java.util.List;

public class FAT32DeletedFileScannerTest {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println(
                    "Usage: FAT32DeletedFileScannerTest <image>"
            );
            return;
        }

        System.out.println(
                "=== FAT32 Deleted File Scanner Test ==="
        );

        try (SectorReader reader =
                     new SectorReader(args[0])) {

            FAT32BootSector boot =
                    FAT32BootSector.parse(reader);

            FAT32DeletedFileScanner scanner =
                    new FAT32DeletedFileScanner(
                            reader,
                            boot
                    );

            List<FAT32DirectoryEntry> deleted =
                    scanner.scanRootDirectory();

            System.out.println(
                    "[PASS] Root directory scanned"
            );

            System.out.println(
                    "Deleted entries found: "
                            + deleted.size()
            );

            for (FAT32DirectoryEntry entry : deleted) {

                System.out.println(
                        "--------------------------------"
                );

                System.out.println(
                        "File: "
                                + entry.getFileName()
                );

                System.out.println(
                        "Size: "
                                + entry.getFileSize()
                                + " bytes"
                );

                System.out.println(
                        "Start cluster: "
                                + entry.getStartCluster()
                );

                System.out.println(
                        "Directory: "
                                + entry.isDirectory()
                );
            }

        } catch (Exception e) {

            System.out.println(
                    "[FAIL] " + e.getMessage()
            );

            e.printStackTrace();
        }
    }
}
