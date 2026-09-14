package tests.filesystem.ntfs;

import core.disk.SectorReader;
import filesystem.ntfs.NTFSBootSector;

public class NTFSBootSectorTest {

    public static void main(String[] args) {

        if (args.length != 1) {

            System.out.println(
                    "Usage: NTFSBootSectorTest <ntfs-image>"
            );

            return;
        }


        System.out.println(
                "=== NTFS Boot Sector Test ==="
        );


        try (SectorReader reader =
                     new SectorReader(args[0])) {

            NTFSBootSector boot =
                    NTFSBootSector.parse(
                            reader
                    );


            System.out.println(
                    "[PASS] NTFS boot sector parsed"
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
                    "Bytes/cluster: "
                            + boot.getBytesPerCluster()
            );


            System.out.println(
                    "Total sectors: "
                            + boot.getTotalSectors()
            );


            System.out.println(
                    "MFT cluster: "
                            + boot.getMftCluster()
            );


            System.out.println(
                    "MFT mirror cluster: "
                            + boot.getMftMirrorCluster()
            );


            System.out.println(
                    "MFT start sector: "
                            + boot.getMftStartSector()
            );


            System.out.println();

            System.out.println(
                    boot
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
