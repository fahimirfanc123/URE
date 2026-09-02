package tests.core.disk;

import core.disk.SectorReader;

import java.nio.charset.StandardCharsets;

public class SectorReaderTest {

    public static void main(String[] args) throws Exception {

        SectorReader reader =
                new SectorReader("test-images/test.img");

        byte[] sector = reader.readSector(0);

        System.out.println("Sector size: " + sector.length + " bytes");

        String data = new String(sector, StandardCharsets.US_ASCII);

        System.out.println("Sector data: " + data.trim());

        reader.close();
    }
}
