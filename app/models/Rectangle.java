package models;

public class Rectangle {

    // the left bottom point of the rectangle
    private Point minPoint;
    // the right top point of the rectangle
    private Point maxPoint;

    // construct the axis-aligned rectangle [getMinX, getMaxX] getX [getMinY, getMaxY]
    public Rectangle(double xmin, double ymin, double xmax, double ymax) {
        this.minPoint = new Point(xmin, ymin);
        this.maxPoint = new Point(xmax, ymax);
    }

    // accessor methods for 4 coordinates
    double getMinX() {
        return minPoint.getX();
    }

    double getMinY() {
        return minPoint.getY();
    }

    double getMaxX() {
        return maxPoint.getX();
    }

    double getMaxY() {
        return maxPoint.getY();
    }

    // does this axis-aligned rectangle contain p?
    public boolean contains(Cluster p) {
        return (p.getX() >= getMinX()) && (p.getX() <= getMaxX())
                && (p.getY() >= getMinY()) && (p.getY() <= getMaxY());
    }

    // are the two axis-aligned rectangles equal?
    public boolean equals(Object y) {
        if (y == this) return true;
        if (y == null) return false;
        if (y.getClass() != this.getClass()) return false;
        Rectangle that = (Rectangle) y;
        return this.minPoint == that.minPoint && this.maxPoint == that.maxPoint;
    }

    // return a string representation of this axis-aligned rectangle
    public String toString() {
        return "[" + getMinX() + ", " + getMaxX() + "] getX [" + getMinY() + ", " + getMaxY() + "]";
    }

}
