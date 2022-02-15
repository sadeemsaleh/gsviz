package models;

import java.util.Objects;

public class HyperEdge extends Edge{
    double radius;
    public HyperEdge(Point from, Point to, double radius) {
        super(from, to);
        this.radius = radius;
    }
    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Edge )){
            return false;
        }
        Edge edge = (Edge) o;

        Point thisLeft = from.getX() < to.getX() ? from : to;
        Point thisRight = from.getX() < to.getX() ? to : from;
        Point edgeLeft = edge.from.getX() < edge.to.getX() ? edge.from : edge.to;
        Point edgeRight = edge.from.getX() < edge.to.getX() ? edge.to : edge.from;
//        return thisLeft.equals(edgeLeft) && thisRight.equals(edgeRight);
        return thisLeft.distanceTo(edgeLeft) <= radius && thisRight.distanceTo(edgeRight) <= radius;
    }

    @Override
    public int hashCode() {
        if (getFromX() > getToX()) {
            return Objects.hash(getToX(), getToY(), getFromX(), getFromY());
        } else {
            return Objects.hash(getFromX(), getFromY(), getToX(), getToY());
        }
    }
}
