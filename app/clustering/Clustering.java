package clustering;

import akka.util.HashCode;
import models.*;
import smile.plot.swing.Grid;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Clustering {

    // max longitude
    private static final int MAX_LONGITUDE = 180;
    // max latitude
    private static final int MAX_LATITUDE = 90;
    // max degree
    private static final int MAX_DEGREE = 360;
    private static final int MAX_CLUSTER_SIZE = 500;
    private static final int MIN_CLUSTER_SIZE = 30;
    //multiplier for setting the boundary for measuring the shifting
    private static final double MULTIPLIER = 0.000001;
    private static double minX = -180;
    private static double minY = -90;
    private static double maxX = 180;
    private static double maxY = 90;
    //percentage to measure the similarity of two
    // clusters having most edges going the same direction
    private static final double PERCENTAGE_OF_SAME_DIRECTION_EDGES = 0.6;
    // min zoom level in clustering tree
    private int minZoom;
    // max zoom level in clustering tree
    private int maxZoom;
    // radius in max zoom level
    private double radius;
    //maximum possible radius for the range search
    private double max_radius;
    // kd-trees in different zoom level
    private KdTree[] trees;
    private HashMap<BucketCode, List<HyperEdge>>[] hyperEdges;

    /**
     * Create an instance of hierarchical greedy clustering
     *
     * @param minZoom the minimum zoom level
     * @param maxZoom the maximum zoom level
     */
    public Clustering(int minZoom, int maxZoom) {
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
        this.radius = 40;
        this.max_radius = radius * 2;
        trees = new KdTree[maxZoom + 2];
        hyperEdges = new HashMap[maxZoom + 2];
        for (int z = minZoom; z <= maxZoom + 1; z++) {
            trees[z] = new KdTree();// GridIndex(getZoomRadius(radius, z));
            hyperEdges[z] = new HashMap<>();
        }
    }

    /**
     * set the range search radius
     *
     * @param radius range search radius
     */
    public void setRadius(double radius) {
        this.radius = radius;
        this.max_radius = radius * 2;
    }

    /**
     * translate longitude to spherical mercator in [0..1] range
     *
     * @param lng longitude
     * @return a number in [0..1] range
     */
    public static double lngX(double lng) {
        return lng / (MAX_LONGITUDE * 2) + 0.5;
    }

    /**
     * translate latitude to spherical mercator in [0..1] range
     *
     * @param lat latitude
     * @return a number in [0..1] range
     */
    public static double latY(double lat) {
        double sin = Math.sin(lat * Math.PI / (MAX_LATITUDE * 2));
        double y = (0.5 - 0.25 * Math.log((1 + sin) / (1 - sin)) / Math.PI);
        return y < 0 ? 0 : y > 1 ? 1 : y;
    }

    /**
     * translate spherical mercator in [0..1] range to longitude
     *
     * @param x a number in [0..1] range
     * @return longitude
     */
    public static double xLng(double x) {
        return (x - 0.5) * (MAX_LONGITUDE * 2);
    }

    /**
     * translate spherical mercator in [0..1] range to latitude
     *
     * @param y a number in [0..1] range
     * @return latitude
     */
    public static double yLat(double y) {
        double y2 = (MAX_LATITUDE * 2 - y * 360) * Math.PI / 180;
        return MAX_DEGREE * Math.atan(Math.exp(y2)) / Math.PI - MAX_LATITUDE;
    }

    /**
     * Load all the points and run clustering algorithm
     *
     * @param edges input of edges
     */
    public void load(HashMap<Edge, Integer> edges) {
        for (Edge edge : edges.keySet()) {
            Point from = edge.getFromPoint();
            Point to = edge.getToPoint();
            Cluster fromCluster = new Cluster(new Point(lngX(from.getX()), latY(from.getY())));
            Cluster toCluster = new Cluster(new Point(lngX(to.getX()), latY(to.getY())));
            fromCluster.getTargetClusters().add(toCluster);
            toCluster.getTargetClusters().add(fromCluster);
            fromCluster.setZoom(maxZoom + 1);
            toCluster.setZoom(maxZoom + 1);
//            insert(fromCluster);
//            insert(toCluster);
           insert(fromCluster, toCluster);
        }
    }

    /**
     * insert one point into the tree
     *
     * @param point the input of point
     */
    public void insert(Cluster point) {
        //point.setZoom(maxZoom + 1);
        trees[maxZoom + 1].insert(point);
        for (int z = maxZoom; z >= minZoom; z--) {
            // search if there are any neighbor near this point
            List<Cluster> neighbors = new ArrayList<>();
            trees[z].within(point, getZoomRadius(radius, z)).values().forEach(list -> neighbors.addAll(list));
            // if no, insert it into kd-tree
            if (neighbors.isEmpty()) {
                Cluster c = new Cluster(point);
                c.setZoom(z);
                point.setParent(c);
                trees[z].insert(c);
                point = c;
                // if have, choose which drawPoints this point belongs to
            } else {
//                Cluster neighbor = null;
                point.setZoom(z + 1);
                // choose the closest cluster
                Cluster neighbor = neighbors.get(0);

                // let this cluster be its parent
                point.setParent(neighbor);
                // update its parents
                while (neighbor != null) {
                    double wx = neighbor.getX() * neighbor.getNumPoints() + point.getX();
                    double wy = neighbor.getY() * neighbor.getNumPoints() + point.getY();
                    neighbor.setNumPoints(neighbor.getNumPoints() + 1);
                    neighbor.setX(wx / neighbor.getNumPoints());
                    neighbor.setY(wy / neighbor.getNumPoints());
                    neighbor.setZoom(neighbor.getZoom());
                    neighbor = neighbor.getParent();
                }
                break;
            }
        }
    }


    /**
     * insert both point into the tree
     *
     * @param fromCluster the input of source point
     * @param toCluster   the input of target point
     */
    public void insert(Cluster fromCluster, Cluster toCluster) {
        //insert the edges in both points
        //insert in the lowest level
        Cluster[] clusters = {fromCluster, toCluster};
        double zoomRadius;
        trees[maxZoom + 1].insert(clusters[0]);
        trees[maxZoom + 1].insert(clusters[1]);
        Map<Integer, List<Cluster>> fromNeighborsMap = null;
        Map<Integer, List<Cluster>> toNeighborsMap = null;
        //loop over every level bottom up
        for (int z = maxZoom; z >= minZoom; z--) {
            Cluster[] neighbors = {null, null};
            BucketCode[] code = {null};
            //loop over every range search
            zoomRadius = getZoomRadius(max_radius, z);
            //if they are within the radius, then merge them
            if (clusters[0].distanceTo(clusters[1]) <= getZoomRadius(radius, z)) {
                Cluster merged = new Cluster(clusters[1]);
                merged.setZoom(z);
                merged.getTargetClusters().remove(clusters[0]);
                mergeTwoPoints(clusters[0], merged);
                //continue with other levels using the merged cluster
                clusters[0].setParent(merged);
                clusters[1].setParent(merged);
                merged.getChildren().add(clusters[0]);
                merged.getChildren().add(clusters[1]);
                trees[z].insert(merged);
                insert(merged, z - 1);
                return;
            }
            //check the map
            //if not found, then find the neighbors and merge
            if (findClusterBasedOnDegree(code, clusters, neighbors, z)) {
                //then insert and continue to upper levels
                clusters[0].setZoom(z + 1);
                clusters[1].setZoom(z + 1);
                clusters[0].setParent(neighbors[0]);
                neighbors[0].getChildren().add(clusters[0]);
                clusters[1].setParent(neighbors[1]);
                neighbors[1].getChildren().add(clusters[1]);
                //then insert and update both
                updateTwoClusters(code, neighbors[0], neighbors[1], fromCluster, toCluster);
                return;
            }
            //find all neighbors
            fromNeighborsMap = trees[z].within(clusters[0], zoomRadius);
            toNeighborsMap = trees[z].within(clusters[1], zoomRadius);
            //if both are empty
            if (fromNeighborsMap.isEmpty() && toNeighborsMap.isEmpty()) {
                insertTwoClusters(clusters, z);
            } else {
                if (!fromNeighborsMap.isEmpty() && !toNeighborsMap.isEmpty()) {
                    //if the clusters won't increase the number of edges
                    //use lowest range search
                    //if both ends don't have clusters to merge into

                    //if there are clusters to merge into
                    //just choose any neighbors
                    Map.Entry from = fromNeighborsMap.entrySet().iterator().next();
                    neighbors[0] = ((List<Cluster>) from.getValue()).get(0);
                    Map.Entry to = toNeighborsMap.entrySet().iterator().next();
                    neighbors[1] = ((List<Cluster>) to.getValue()).get(0);
                    clusters[0].setZoom(z + 1);
                    clusters[1].setZoom(z + 1);
                    clusters[0].setParent(neighbors[0]);
                    neighbors[0].getChildren().add(clusters[0]);
                    clusters[1].setParent(neighbors[1]);
                    neighbors[1].getChildren().add(clusters[1]);
                    code[0] = new BucketCode((int) from.getKey(), (int) to.getKey());
                } else {
                    //if one of the lists is empty, then choose one cluster
                    if (!toNeighborsMap.isEmpty()) {
                        fromNeighborsMap = toNeighborsMap;
                        Cluster temp = clusters[1];
                        clusters[1] = clusters[0];
                        clusters[0] = temp;
                        temp = toCluster;
                        toCluster = fromCluster;
                        fromCluster = temp;
                    }
                    z = findCluster(code, fromNeighborsMap, neighbors, clusters, z);
                    if (z <= -1)
                        break;

                }
                updateTwoClusters(code, neighbors[0], neighbors[1], fromCluster, toCluster);
                return;
            }
        }
    }

    /**
     * insert one point into the tree
     *
     * @param point     the input of point
     * @param zoomLevel the zoomlevel to start inserting the cluster
     */
    public void insert(Cluster point, int zoomLevel) {
        for (int z = zoomLevel; z >= minZoom; z--) {
            // search if there are any neighbor near this point
            List<Cluster> neighbors = new ArrayList<>();
            trees[z].within(point, getZoomRadius(radius, z)).values().forEach(list -> neighbors.addAll(list));
            // if no, insert it into kd-tree
            if (neighbors.isEmpty()) {
                point = insertClusterAlone(point, z);
                // if have, choose which drawPoints this point belongs to
            } else {
                Cluster neighbor = neighbors.get(0);
                point.setZoom(z + 1);
                updateParents(point, neighbor);
                break;
            }
        }
    }

    /**
     * insert two points in the kdtree
     *
     * @param clusters array containing both end of the edge
     * @param z        the zoom level
     */
    private void insertTwoClusters(Cluster[] clusters, int z) {
        Cluster fromC = new Cluster(clusters[0]);
        Cluster toC = new Cluster(clusters[1]);
        fromC.getTargetClusters().add(toC);
        toC.getTargetClusters().add(fromC);
        fromC.setZoom(z);
        toC.setZoom(z);
        clusters[0].setParent(fromC);
        clusters[1].setParent(toC);
        fromC.getChildren().add(clusters[0]);
        toC.getChildren().add(clusters[1]);
        trees[z].insert(fromC);
        trees[z].insert(toC);
        int leftBucket = fromC.getGridLocation();
        int rightBucket = toC.getGridLocation();
        HyperEdge hyperEdge = new HyperEdge(fromC, toC, getZoomRadius(max_radius, z));
        BucketCode key = new BucketCode(leftBucket, rightBucket);
        if (!hyperEdges[z].containsKey(key))
            hyperEdges[z].put(key, new ArrayList<>());
        if (!hyperEdges[z].get(key).contains(hyperEdge))
            hyperEdges[z].get(key).add(hyperEdge);
        clusters[0] = fromC;
        clusters[1] = toC;
    }

    /**
     * insert one point in the kdtree
     *
     * @param point the point that needs to be inserted
     * @param z     the zoom level
     * @return the point that was inserted to be recursively inserted in upper levels
     */
    private Cluster insertClusterAlone(Cluster point, int z) {
        Cluster c = new Cluster(point);
        c.setZoom(z);
        point.setParent(c);
        c.getChildren().add(point);
        trees[z].insert(c);
        return c;
    }

    /**
     * update the clusters coordinates and their parents pointers
     *
     * @param point  the point that needs to be inserted
     * @param parent the cluster that contains the point
     */
    private void updateParents(Cluster point, Cluster parent) {
        // let this cluster be its parent
        point.setParent(parent);
        parent.getChildren().add(point);
        // update its parents
        while (parent != null) {
            updateCluster(point, parent, true);
            parent = parent.getParent();
        }
    }

    private boolean findClusterBasedOnDegree(BucketCode[] code, Cluster[] clusters, Cluster[] neighbors, int z) {
        int[] mXn = {0, 0};
        double zoomRadius = getZoomRadius(radius, z);
        zoomRadius = calculateMXN(mXn, zoomRadius);
        //calculate grid index
        int iL = locateX(mXn, clusters[0].getX(), zoomRadius);
        int jL = locateY(mXn, clusters[0].getY(), zoomRadius);
        int iR = locateX(mXn, clusters[1].getX(), zoomRadius);
        int jR = locateY(mXn, clusters[1].getY(), zoomRadius);
        //get the list from grid
        code[0] = new BucketCode((jL * mXn[0] + iL), (jR * mXn[0] + iR));
        List<HyperEdge> list = hyperEdges[z].get(code[0]);
        if (list == null)
            return false;
        //get the hyperedge
        double newZoomRadius = getZoomRadius(max_radius, z);
        int index = list.indexOf(new HyperEdge(clusters[0], clusters[1], newZoomRadius));
        if (index != -1) {
            HyperEdge existingEdge = list.get(index);
            //do double verification which one is closer to which and assign the variable
            neighbors[0] = clusters[0].distanceTo(existingEdge.getFromPoint()) <= newZoomRadius ? (Cluster) existingEdge.getFromPoint() : (Cluster) existingEdge.getToPoint();
            neighbors[1] = clusters[1].distanceTo(existingEdge.getFromPoint()) <= newZoomRadius ? (Cluster) existingEdge.getFromPoint() : (Cluster) existingEdge.getToPoint();
            return true;
        }
        return false;
    }

    /**
     * find grid position i on X axis
     *
     * @param x
     * @return
     */
    private int locateX(int[] mXn, double x, double step) {
        int i = (int) Math.floor((x - minX) / step);
        i = i < 0 ? 0 : i;
        i = i > mXn[0] - 1 ? mXn[0] - 1 : i;
        return i;
    }

    /**
     * find grid position j on Y axis
     *
     * @param y
     * @return
     */
    private int locateY(int[] mXn, double y, double step) {
        int j = (int) Math.floor((y - minY) / step);
        j = j < 0 ? 0 : j;
        j = j > mXn[1] - 1 ? mXn[1] - 1 : j;
        return j;
    }

    private double calculateMXN(int[] values, double step) {

        // calculate number of grids
        int m = (int) Math.ceil((maxX - minX) / step);
        int n = (int) Math.ceil((maxY - minY) / step);

        // Make sure m / n is never larger than MAX_RESOLUTION,
        // so that JVM will not be OutOfMemory because of this List[][] array
        if (m > GridIndex.MAX_RESOLUTION || n > GridIndex.MAX_RESOLUTION) {
            step = Math.max((maxX - minX) / GridIndex.MAX_RESOLUTION, (maxY - minY) / GridIndex.MAX_RESOLUTION);
            m = (int) Math.ceil((maxX - minX) / step);
            n = (int) Math.ceil((maxY - minY) / step);
        }
        values[0] = m;
        values[1] = n;
        return step;
    }

    /**
     * @param point     the point that needs to be inserted
     * @param neighbors the list of neighbors in the range search
     * @return the nearest cluster
     */
    private Cluster findNearestCluster(Cluster point, List<Cluster> neighbors) {
        Cluster neighbor = null;
        // choose the closest cluster
        double minDis = 1e9;
        for (Cluster c : neighbors) {
            double dis = c.distanceTo(point);
            if (dis < minDis) {
                minDis = dis;
                neighbor = c;
            }
        }
        return neighbor;
    }


    /**
     * try finding the appropriate clusters to merge into
     *
     * @param neighbors [0] for neighbors of tobemerged, [1] for neighbors of solo
     * @param clusters  [0] for neighbors of tobemerged, [1] for neighbors of solo
     * @param zoomLevel
     * @return the zoom level to continue merging the clusters
     */
    private int findCluster(BucketCode[] code, Map<Integer, List<Cluster>> toBeMergedNeighbors,
                            Cluster[] neighbors, Cluster[] clusters, int zoomLevel) {
        Cluster mergedPoint = clusters[0];
        Map.Entry toBeMerged = toBeMergedNeighbors.entrySet().iterator().next();
        neighbors[0] = ((List<Cluster>) toBeMerged.getValue()).get(0);
        int leftCode = (int) toBeMerged.getKey();
        for (int z = zoomLevel; z >= minZoom; z--) {
            double zoomRadius = getZoomRadius(max_radius, z);
            Map<Integer, List<Cluster>> toNeighbors = trees[z].within(clusters[1], zoomRadius);
            if (toNeighbors.isEmpty()) {
                //insert alone, and merge the other one, also insert the hyper edge
                clusters[0].setParent(neighbors[0]);
                neighbors[0].getChildren().add(clusters[0]);
                updateCluster(mergedPoint, neighbors[0], true);
                Cluster c = new Cluster(clusters[1]);
                c.setZoom(z);
                clusters[1].setParent(c);
                c.getChildren().add(clusters[1]);
                trees[z].insert(c);
                int rightCode = c.getGridLocation();
                clusters[1] = c;
                if (!clusters[1].getTargetClusters().contains(neighbors[0]))
                    clusters[1].getTargetClusters().add(neighbors[0]);
                if (!neighbors[0].getTargetClusters().contains(clusters[1]))
                    neighbors[0].getTargetClusters().add(clusters[1]);
                HyperEdge hyperEdge = new HyperEdge(neighbors[0], c, zoomRadius);
                BucketCode key = new BucketCode(leftCode, rightCode);
                if (!hyperEdges[z].containsKey(key))
                    hyperEdges[z].put(key, new ArrayList<>());
                if (!hyperEdges[z].get(key).contains(hyperEdge))
                    hyperEdges[z].get(key).add(hyperEdge);
                clusters[0] = neighbors[0];
                neighbors[0] = neighbors[0].getParent();
            } else {
                Map.Entry to = toNeighbors.entrySet().iterator().next();
                neighbors[1] = ((List<Cluster>) to.getValue()).get(0);
                clusters[0].setZoom(z + 1);
                clusters[1].setZoom(z + 1);
                clusters[0].setParent(neighbors[0]);
                neighbors[0].getChildren().add(clusters[0]);
                clusters[1].setParent(neighbors[1]);
                neighbors[1].getChildren().add(clusters[1]);
                code[0] = new BucketCode((int) toBeMerged.getKey(), (int) to.getKey());
                return z;
            }
        }
        return -1;
    }

    /**
     * update the clusters coordinates
     *
     * @param point   the point that needs to be inserted
     * @param cluster the cluster that contains the point
     */
    private void mergeTwoPoints(Cluster point, Cluster cluster) {
        double wx = cluster.getX() * cluster.getNumPoints() + point.getX() * point.getNumPoints();
        double wy = cluster.getY() * cluster.getNumPoints() + point.getY() * point.getNumPoints();
        cluster.setNumPoints(cluster.getNumPoints() + point.getNumPoints());
        cluster.setX(wx / cluster.getNumPoints());
        cluster.setY(wy / cluster.getNumPoints());
        cluster.setZoom(cluster.getZoom());
    }

    /**
     * update the clusters coordinates
     *
     * @param point   the point that needs to be inserted
     * @param cluster the cluster that contains the point
     */
    private void updateCluster(Cluster point, Cluster cluster, boolean update) {
        mergeTwoPoints(point, cluster);
        int zoomLevel = cluster.getZoom();
        //check if the cluster is overlapping with another cluster
        List<Cluster> neighbors = new ArrayList<>();
        trees[zoomLevel].within(cluster, getZoomRadius(radius, zoomLevel)).values().forEach(list -> neighbors.addAll(list));
        //if yes
        if (!neighbors.isEmpty()) {
            Cluster[] clusterTobeMergedWith = {null};
            // check if their edges go similar direction
            if (haveCommonNeighborsOtherEnd(clusterTobeMergedWith, neighbors, cluster)) {
                //if yes, merge them together create a new object and let the cluster refer to it
                if(update)
                mergeTwoClusters(cluster, clusterTobeMergedWith[0]);
            }
        }
        if (cluster.getNumPoints() < MIN_CLUSTER_SIZE) {
            if (!cluster.equals(cluster.getOriginal())) {//compare original with now
                cluster.setShifted(true);
                cluster.getOriginal().setX(cluster.getX());
                cluster.getOriginal().setX(cluster.getX());
            }
        } else {
            int clusterSize = Math.min(cluster.getNumPoints(), MAX_CLUSTER_SIZE);
            double scale = (1 + clusterSize / 100) * MULTIPLIER;
            double r = clusterSize * scale;
            if (cluster.distanceTo(cluster.getOriginal()) > r) {//compare original with now
                cluster.setShifted(true);
                cluster.getOriginal().setX(cluster.getX());
                cluster.getOriginal().setX(cluster.getX());
            }
        }
    }

    /**
     * choose the first neighbor that most of
     * its outgoing/incoming edges are within the range
     * of the cluster's outgoing/incoming edges
     *
     * @param chosenNeighbor the neighbor that satisfies the condition
     * @param neighbors      the list of neighbors for this cluster
     * @param cluster        the cluster
     * @return true if found a cluster to merge into or false otherwise
     */
    private boolean haveCommonNeighborsOtherEnd(Cluster[] chosenNeighbor,
                                                List<Cluster> neighbors,
                                                Cluster cluster) {
        double count;
        List<Cluster> thisTargetClusters = cluster.getTargetClusters();
        Cluster neighbor;
        for (int i = 0; i < neighbors.size(); i++) {
            neighbor = neighbors.get(i);
            if (neighbor == cluster)
                continue;
            count = 0;
            List<Cluster> otherTargetClusters = neighbor.getTargetClusters();
            for (Cluster occurrence : otherTargetClusters) {
                if (thisTargetClusters.contains(occurrence)) {
                    count++;
                    if ((count / Math.min(thisTargetClusters.size(), otherTargetClusters.size())) > PERCENTAGE_OF_SAME_DIRECTION_EDGES) {
                        chosenNeighbor[0] = neighbor;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * merge two clusters together
     *
     * @param cluster
     * @param neighbor
     */
    private void mergeTwoClusters(Cluster cluster, Cluster neighbor) {
        //update the coordinate of cluster
        mergeTwoPoints(neighbor, cluster);
        //add cluster to all neighbors's out/in edges
        for (Cluster neighborTarget : neighbor.getTargetClusters()) {
            if (neighborTarget == neighbor)
                continue;
            if (!neighborTarget.getTargetClusters().contains(cluster))
                neighborTarget.getTargetClusters().add(cluster);
            if (!neighborTarget.equals(neighbor))
                neighborTarget.getTargetClusters().remove(neighbor);
            //add all neighbor's out/in edges to cluster
            if (!cluster.getTargetClusters().contains(neighborTarget))
                cluster.getTargetClusters().add(neighborTarget);
        }
        //let neighbor's children point to cluster
        for (Cluster child : neighbor.getChildren()) {
            child.setParent(cluster);
            cluster.getChildren().add(child);
        }
        if(neighbor.getParent() != null)
        neighbor.getParent().getChildren().remove(neighbor);
        trees[neighbor.getZoom()].delete(neighbor);
    }

    /**
     * update the clusters coordinates and their parents pointers
     *
     * @param fromCluster  the point that needs to be inserted
     * @param fromNeighbor the cluster that contains the point
     * @param toCluster    the other end of the edge that needs to be inserted
     * @param toNeighbor   the other cluster that contains the other point
     */
    private void updateTwoClusters(BucketCode[] code, Cluster fromNeighbor, Cluster toNeighbor, Cluster fromCluster, Cluster toCluster) {
        int z = fromNeighbor.getZoom();
        while (fromNeighbor != null && toNeighbor != null) {
            updateCluster(fromCluster, fromNeighbor, false);
            updateCluster(toCluster, toNeighbor, false);
            if (fromNeighbor != toNeighbor) {
                z = fromNeighbor.getZoom();
                HyperEdge newEdge = new HyperEdge(fromNeighbor, toNeighbor, z);
                if (!hyperEdges[z].containsKey(code[0]))
                    hyperEdges[z].put(code[0], new ArrayList<>());
                if (!hyperEdges[z].get(code[0]).contains(newEdge))
                    hyperEdges[z].get(code[0]).add(newEdge);
                if (!fromNeighbor.getTargetClusters().contains(toNeighbor))
                    fromNeighbor.getTargetClusters().add(toNeighbor);
                if (!toNeighbor.getTargetClusters().contains(fromNeighbor))
                    toNeighbor.getTargetClusters().add(fromNeighbor);
                toNeighbor = toNeighbor.getParent();
                fromNeighbor = fromNeighbor.getParent();
            } else {
                fromNeighbor = fromNeighbor.getParent();
                toNeighbor = fromNeighbor;
            }
        }

    }

    /**
     * @param r    is the radius for the range search
     * @param zoom the zoom level
     * @return the radius in this zoom level
     */
    public static double getZoomRadius(double r, int zoom) {
        // extent used to calculate the radius in different zoom level
        double extent = 512;
        return r / (extent * Math.pow(2, zoom));
    }

    /**
     * get clusters within certain window and zoom level
     *
     * @param bbox the bounding box of the window
     * @param zoom the zoom level
     * @return all the clusters within this bounding box and this zoom level
     */
    public List<Cluster> getClusters(double[] bbox, int zoom) {
        double minLongitude = ((bbox[0] + MAX_LONGITUDE) % (MAX_LONGITUDE * 2) + MAX_LONGITUDE * 2) % (MAX_LONGITUDE * 2) - MAX_LONGITUDE;
        double minLatitude = Math.max(-MAX_LATITUDE, Math.min(MAX_LATITUDE, bbox[1]));
        double maxLongitude = bbox[2] == MAX_LONGITUDE ? MAX_LONGITUDE : ((bbox[2] + MAX_LONGITUDE) % (MAX_LONGITUDE * 2) + (MAX_LONGITUDE * 2)) % (MAX_LONGITUDE * 2) - MAX_LONGITUDE;
        double maxLatitude = Math.max(-MAX_LATITUDE, Math.min(MAX_LATITUDE, bbox[3]));
        // if the range of longitude is larger than 360, set the range to be [-180, 180]
        if (bbox[2] - bbox[0] >= MAX_LONGITUDE * 2) {
            minLongitude = -MAX_LONGITUDE;
            maxLongitude = MAX_LONGITUDE;
            // if the range of longitude is negative, set the range to be [-180, max longitude] and [min longitude, 180]
        } else if (minLongitude > maxLongitude) {
            List<Cluster> results = getClusters(new double[]{minLongitude, minLatitude, MAX_LONGITUDE, maxLatitude}, zoom);
            results.addAll(getClusters(new double[]{-MAX_LONGITUDE, minLatitude, maxLongitude, maxLatitude}, zoom));
            return results;
        }
//List<Cluster> result = new ArrayList<>();
return trees[limitZoom(zoom)].range(new Point(lngX(minLongitude), latY(maxLatitude)), new Point(lngX(maxLongitude), latY(minLatitude)));//.values().forEach(list -> result.addAll(list));
//return result;    
}

    private int limitZoom(int z) {
        return Math.max(minZoom, Math.min(z, maxZoom + 1));
    }

    /**
     * get the parent drawPoints in certain zoom level
     *
     * @param cluster the input drawPoints
     * @param zoom    the zoom level of its parent
     * @return the parent drawPoints of this drawPoints
     */
    public Cluster parentCluster(Cluster cluster, int zoom) {
        Cluster c = trees[maxZoom + 1].findPoint(cluster);//, getZoomRadius(radius, zoom));
        while (c != null) {
            if (c.getZoom() == zoom) {
                break;
            }
            c = c.getParent();
        }
        return c;
    }
}
