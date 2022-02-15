package models;

public class Pair implements Comparable<Pair>{
    static Point originalPoint;
    Cluster neighbor;

    public Pair(Point point){
        originalPoint = point;
    }

    public Pair(Cluster neighbor)
    {
        this.neighbor = neighbor;
    }

    public Cluster getCluster(){
        return neighbor;
    }

    @Override
    public int compareTo(Pair o) {
        if (neighbor.distanceTo(originalPoint) > o.neighbor.distanceTo(originalPoint)) return 1;
        if (neighbor.distanceTo(originalPoint) == o.neighbor.distanceTo(originalPoint)) return 0;
        return -1;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (other == null) return false;
        if (other.getClass() != this.getClass()) return false;
        Pair that = (Pair) other;
        return this.neighbor.getX() == that.neighbor.getX() && this.neighbor.getY() == that.neighbor.getY();
    }

    @Override
    public int hashCode() {
        int hashX = ((Double) neighbor.getX()).hashCode();
        int hashY = ((Double) neighbor.getY()).hashCode();
        return 31 * hashX + hashY;
    }

    }
