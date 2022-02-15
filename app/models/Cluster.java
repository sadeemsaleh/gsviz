package models;

import clustering.Clustering;

import java.util.ArrayList;
import java.util.List;

public class Cluster extends Point {
    private int numPoints;
    private int zoom;
    private Cluster parent;
    private List<Cluster> children;
    private List<Cluster> targetClusters;
    private boolean shifted;
    private Point original;
    private int gridLocation;
    double minX = -180;
    double minY = -90;
    double maxX = 180;
    double maxY = 90;
    double step;
    // calculate number of grids
    int m;
    int n;

    public Cluster(Point point) {
        this.setX(point.getX());
        this.setY(point.getY());
        this.numPoints = 1;
        this.children = new ArrayList<>();
        if (point instanceof Cluster)
            this.numPoints = ((Cluster) point).getNumPoints();
        this.parent = null;
        this.zoom = Integer.MAX_VALUE;
        this.targetClusters = new ArrayList<>();
        this.shifted = false;
        this.original = new Point(point.getX(), point.getY());
        this.gridLocation = -1;
    }

    public boolean isShifted(){
        return shifted;
    }

    public Point getOriginal(){
        return original;
    }

    public void setShifted(boolean shifted){
        this.shifted = shifted;
    }

    public int getGridLocation() {
        return gridLocation;
    }

    public int getZoom() {
        return zoom;
    }

    public List<Cluster> getChildren(){
        return children;
    }

    public void setChildren(List<Cluster> children){
        this.children = children;
    }

    public Cluster getParent() {
        return parent;
    }

    public void setParent(Cluster parent) {
        this.parent = parent;
    }

    public void setZoom(int zoom) {
        this.zoom = zoom;
        this.step = Clustering.getZoomRadius(40, zoom);
        this.m = (int) Math.ceil((maxX - minX) / step);
        this.n = (int) Math.ceil((maxY - minY) / step);
        // Make sure m / n is never larger than MAX_RESOLUTION,
        // so that JVM will not be OutOfMemory because of this List[][] array
        if (m > GridIndex.MAX_RESOLUTION || n > GridIndex.MAX_RESOLUTION) {
            this.step = Math.max((maxX - minX) / GridIndex.MAX_RESOLUTION, (maxY - minY) / GridIndex.MAX_RESOLUTION);
            m = (int) Math.ceil((maxX - minX) / this.step);
            n = (int) Math.ceil((maxY - minY) / this.step);
        }
        int i = locateX(getX());
        int j = locateY(getY());
        this.gridLocation = j*m + i;
    }
    /**
     * find grid position i on X axis
     *
     * @param x
     * @return
     */
    private int locateX(double x) {
        int i = (int) Math.floor((x - minX) / step);
        i = i < 0? 0: i;
        i = i > m-1? m-1: i;
        return i;
    }

    /**
     * find grid position j on Y axis
     *
     * @param y
     * @return
     */
    private int locateY(double y) {
        int j = (int) Math.floor((y - minY) / step);
        j = j < 0? 0: j;
        j = j > n-1? n-1: j;
        return j;
    }
    public int getNumPoints() {
        return numPoints;
    }

    public void setNumPoints(int numPoints) {
        this.numPoints = numPoints;
    }

    public List<Cluster> getTargetClusters() {
        return targetClusters;
    }

    public void setTargetClusters(List<Cluster> targetClusters) {
        this.targetClusters = targetClusters;
    }

    @Override
    public String toString() {
        return String.format("(%.2f, %.2f), zoom: %d, numPoints: %d", Clustering.xLng(getX()), Clustering.yLat(getY()), zoom, numPoints);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
