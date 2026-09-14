package filesystem.ntfs;

public class NTFSDataRun {

    private final long startCluster;
    private final long clusterCount;
    private final boolean sparse;

    public NTFSDataRun(
            long startCluster,
            long clusterCount,
            boolean sparse
    ) {
        this.startCluster = startCluster;
        this.clusterCount = clusterCount;
        this.sparse = sparse;
    }

    public long getStartCluster() {
        return startCluster;
    }

    public long getClusterCount() {
        return clusterCount;
    }

    public boolean isSparse() {
        return sparse;
    }

    @Override
    public String toString() {
        return "NTFSDataRun{" +
                "startCluster=" + startCluster +
                ", clusterCount=" + clusterCount +
                ", sparse=" + sparse +
                '}';
    }
}
