package tests.filesystem.fat32;

import core.disk.SectorReader;
import filesystem.fat32.FAT32BootSector;

public class FAT32BootSectorTest {

    public static void main(String[] args) {

        if (args.length != 1) {

            System.out.println(
                    "Usage: FAT32BootSectorTest <fat32-image>"
            );

            return;
        }

        System.out.println(
                "=== FAT32 Boot Sector Test ==="
        );

        try (SectorReader reader =
                     new SectorReader(args[0])) {

            FAT32BootSector boot =
                    FAT32BootSector.parse(reader);

            System.out.println(
                    "[PASS] FAT32 boot sector parsed"
            );

            System.out.println(
                    "Bytes/sector: "
                            + boot.getBytesPerSector()
            );

            System.out.println(
                    "Sectors/cluster: "
                            + boot.getSectorsPerCluster()
            );

            System.out.println(
                    "Reserved sectors: "
                            + boot.getReservedSectorCount()
            );

            System.out.println(
                    "Number of FATs: "
                            + boot.getNumberOfFATs()
            );

            System.out.println(
                    "FAT size: "
                            + boot.getFatSize()
            );

            System.out.println(
                    "Root cluster: "
                            + boot.getRootCluster()
            );

            System.out.println(
                    "First data sector: "
                            + boot.getFirstDataSector()
            );

            System.out.println(
                    "Root directory sector: "
                            + boot.clusterToSector(
                                    boot.getRootCluster()
                            )
            );

            System.out.println();

            System.out.println(boot);

        } catch (Exception e) {

            System.out.println(
                    "[FAIL] " + e.getMessage()
            );

            e.printStackTrace();
        }
    }
}
