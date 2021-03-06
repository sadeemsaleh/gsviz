package clustering;

import models.Edge;
import models.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Incremental K-Means Algorithm
 */
public class IKmeans extends Kmeans {
    // the list of clusters for all accumulated data
    private List<List<Point>> allClusters;
    // the count of points in all accumulated data
    private int pointsCnt;

    /**
     * Constructor for k
     *
     * @param k Number of clusters
     */
    public IKmeans(int k) {
        super(k);
    }

    private List<List<Point>> getAllClusters() {
        return allClusters;
    }

    @Override
    public int getDataSetLength() {
        return pointsCnt;
    }

    @Override
    public void setDataSet(List<Point> dataSet) {
        this.dataSet = dataSet;
        pointsCnt += dataSet.size();
    }

    /**
     * Initialization of the whole I-KMeans process
     */
    @Override
    public void init() {
        allClusters = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            allClusters.add(new ArrayList<>());
        }
        initCenters();
        initCluster();
    }

    /**
     * Add each point to its closest cluster
     */
    @Override
    void clusterSet() {
        int minLocation;
        for (int i = 0; i < dataSet.size(); i++) {
            minLocation = assignPoint(i);
            Point point = new Point(dataSet.get(i).getX(), dataSet.get(i).getY());
            parents.put(point, minLocation); // Map each point to the cluster it belongs to
        }
    }

    /**
     * Set the new center for each cluster
     */
    @Override
    void setNewCenter() {
        for (int i = 0; i < k; i++) {
            int n = clusters.get(i).size();
            if (n != 0) {
                Point newCenter = initNewCenter(i, n);
                // Calculate the average coordinate of all points in the cluster
                newCenter.setX(newCenter.getX() + centers.get(i).getX() * allClusters.get(i).size());
                newCenter.setX(newCenter.getX() / (n + allClusters.get(i).size()));
                newCenter.setY(newCenter.getY() + centers.get(i).getY() * allClusters.get(i).size());
                newCenter.setY(newCenter.getY() / (n + allClusters.get(i).size()));
                centers.set(i, newCenter);
            }
        }
    }

    /**
     * load the new batch of data and do incremental K-Means
     *
     * @param edges the new batch of data
     */
    @Override
    public void execute(HashMap<Edge, Integer> edges) {
        List<Point> points = new ArrayList<>();
        points.addAll(edges.entrySet().stream().map(entry -> entry.getKey().getFromPoint()).collect(Collectors.toList()));
        points.addAll(edges.entrySet().stream().map(entry -> entry.getKey().getToPoint()).collect(Collectors.toList()));
        boolean isFirst = dataSet == null;
        setDataSet(points);
        if (isFirst) {
            if (k > dataSet.size()) {
                k = dataSet.size();
            }
            if (k != 0) {
                init();
            }
        } else {
            if (k == 0 && dataSet.size() > 0) {
                k = dataSet.size();
                init();
            }
        }
        if (k == 0) return;
        clusterSet();
        setNewCenter();
        for (int j = 0; j < getK(); j++) {
            allClusters.get(j).addAll(clusters.get(j));
        }
        initCluster();
    }

    /**
     * Get the map containing the accumulated clusters and their sizes
     * @return the map containing the accumulated clusters and their sizes
     */
    @Override
    public HashMap<Point, Integer> getClustersMap() {
        HashMap<Point, Integer> clustersSizes = new HashMap<>();
        for (int i = 0; i < getK(); i++) {
            clustersSizes.put(getCenters().get(i), getAllClusters().get(i).size());
        }
        return clustersSizes;
    }
}
