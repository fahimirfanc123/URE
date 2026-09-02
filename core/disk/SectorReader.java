package core.disk;

import java.io.IOException;
import java.io.RandomAccessFile;

public class SectorReader {

    private static final int SECTOR_SIZE = 512;

    private final RandomAccessFile file;

    public SectorReader(String path) throws IOException {
        file = new RandomAccessFile(path, "r");
    }

    public byte[] readSector(long sectorNumber) throws IOException {

        long position = sectorNumber * SECTOR_SIZE;

        file.seek(position);

        byte[] buffer = new byte[SECTOR_SIZE];

        file.readFully(buffer);

        return buffer;
    }

    public void close() throws IOException {
        file.close();
    }
}
