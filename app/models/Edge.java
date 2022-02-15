package models;

import java.util.List;
import java.util.Objects;

public class Edge{

    /** from Point of edge**/
    protected Point from;

    /** Turning points of the generated paths**/
    private List<Point> subdivisionPoints;

    /** list of indexes of other edges compatible with this**/
    private List<Integer> compatibilityList;

    /** list of nodes on the tree this node is compatible with**/
    private List<Edge> compatibleNodes;

    /** to Point of edge**/
    protected Point to;

    public Edge(Point from, Point to) {
        this.from = from;
        this.to = to;
    }

    public Edge(Edge e) {
        this.from = e.from;
        this.to = e.to;
        this.subdivisionPoints = e.getSubdivisionPoints();
    }

    public List<Edge> getCompatibleNodes() {
        return compatibleNodes;
    }

    public void setCompatibleNodes(List<Edge> compatibleNodes) {
        this.compatibleNodes = compatibleNodes;
    }

    public List<Integer> getCompatibilityList() {
        return compatibilityList;
    }

    public void setCompatibilityList(List<Integer> compatibilityList) {
        this.compatibilityList = compatibilityList;
    }

    public List<Point> getSubdivisionPoints() {
        return subdivisionPoints;
    }

    public void setSubdivisionPoints(List<Point> subdivisionPoints) {
        this.subdivisionPoints = subdivisionPoints;
    }
    public Edge getEdge(){ return this; }

    public Point getFromPoint() {
        return from;
    }

    public Point getToPoint() {
        return to;
    }

    public double getFromX() {
        return this.from.getX();
    }

    public double getFromY() {
        return this.from.getY();
    }

    public double getToX() {
        return this.to.getX();
    }

    public double getToY() {
        return this.to.getY();
    }

    public void setFrom(Point from) {
        this.from = from;
    }

    public void setTo(Point to) {
        this.to = to;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Edge )){
            return false;
        }
        Edge edge = (Edge) o;
        return (from.equals(edge.from) && to.equals(edge.to)) || (from.equals(edge.to) && to.equals(edge.from));
    }

    @Override
    public int hashCode() {
        if (getFromX() > getToX()) {
            return Objects.hash(getToX(), getToY(), getFromX(), getFromY());
        } else {
            return Objects.hash(getFromX(), getFromY(), getToX(), getToY());
        }
    }

    @Override
    public String toString() {
        return "{" +
                "\"source\":" + getFromX() + "," + getFromY() +
                ",\"target\":" + getToX() + "," + getToY() +
                "}";
    }

    public double length() {
        return from.distanceTo(to);
    }

    public double getDegree() {
        return Math.toDegrees(Math.atan((getToY() - getFromY()) / (getToX() - getFromX())));
    }

    /**
     * Calculates the vector format of edge.
     * @return corresponding vector.
     */
    public Point edgeAsVector() {
        return new Point(getToX() - getFromX(), getToY() - getFromY());
    }

    /**
     * Calculates the middle point of one edge.
     * @return corresponding middle point.
     */
    public Point edgeMidPoint() {
        double midX = (from.getX() + to.getX()) / 2.0;
        double midY = (from.getY() + to.getY()) / 2.0;
        return new Point(midX, midY);
    }
}