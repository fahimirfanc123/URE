package filesystem.fat32;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads a FAT32 cluster chain and determines
 * whether a file is fragmented.
 *
 * Example:
 *
 * Contiguous:
 * 5 -> 6 -> 7 -> 8
 *
 * Fragmented:
 * 5 -> 20 -> 21 -> 35
 */
public class Fat32ClusterChain {

    private static final long FAT32_MASK = 0x0FFFFFFFL;

    private static final long END_OF_CHAIN_MIN = 0x0FFFFFF8L;
    private static final long END_OF_CHAIN_MAX = 0x0FFFFFFFL;

    private static final long BAD_CLUSTER = 0x0FFFFFF7L;

    /**
     * Reads the cluster chain starting from startCluster.
     *
     * @param fatData      raw bytes of the FAT table
     * @param startCluster first cluster of the file
     * @return ordered list of cluster numbers
     */
    public List<Long> readChain(
            byte[] fatData,
            long startCluster) {

        if (fatData == null || fatData.length == 0) {
            throw new IllegalArgumentException(
                    "FAT data cannot be null or empty"
            );
        }

        if (startCluster < 2) {
            throw new IllegalArgumentException(
                    "Start cluster must be 2 or greater"
            );
        }

        List<Long> chain = new ArrayList<>();
        Set<Long> visited = new HashSet<>();

        long currentCluster = startCluster;

        while (true) {

            if (visited.contains(currentCluster)) {
                throw new IllegalStateException(
                        "Loop detected in FAT32 cluster chain"
                );
            }

            visited.add(currentCluster);
            chain.add(currentCluster);

            long nextCluster =
                    readFatEntry(fatData, currentCluster);

            if (isEndOfChain(nextCluster)) {
                break;
            }

            if (nextCluster == BAD_CLUSTER) {
                throw new IllegalStateException(
                        "Bad cluster detected in FAT32 chain"
                );
            }

            if (nextCluster < 2) {
                break;
            }

            currentCluster = nextCluster;
        }

        return chain;
    }

    /**
     * Determines whether a cluster chain is fragmented.
     *
     * A chain is fragmented when consecutive cluster numbers
     * are not physically sequential.
     */
    public boolean isFragmented(List<Long> chain) {

        if (chain == null || chain.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cluster chain cannot be null or empty"
            );
        }

        if (chain.size() == 1) {
            return false;
        }

        for (int i = 1; i < chain.size(); i++) {

            long previous = chain.get(i - 1);
            long current = chain.get(i);

            if (current != previous + 1) {
                return true;
            }
        }

        return false;
    }

    /**
     * Convenience method that reads the FAT chain
     * and immediately determines fragmentation.
     */
    public boolean analyzeFragmentation(
            byte[] fatData,
            long startCluster) {

        List<Long> chain =
                readChain(fatData, startCluster);

        return isFragmented(chain);
    }

    /**
     * Reads one 32-bit FAT32 entry.
     *
     * Each FAT32 entry occupies 4 bytes.
     */
    private long readFatEntry(
            byte[] fatData,
            long cluster) {

        long offsetLong = cluster * 4L;

        if (offsetLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "FAT entry offset is too large"
            );
        }

        int offset = (int) offsetLong;

        if (offset < 0 ||
                offset + 4 > fatData.length) {

            throw new IllegalArgumentException(
                    "Cluster lies outside FAT data: " + cluster
            );
        }

        int rawValue = ByteBuffer
                .wrap(fatData, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();

        return rawValue & FAT32_MASK;
    }

    private boolean isEndOfChain(long value) {
        return value >= END_OF_CHAIN_MIN &&
               value <= END_OF_CHAIN_MAX;
    }
}
