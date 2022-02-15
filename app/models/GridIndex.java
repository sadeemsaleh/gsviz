package models;

import java.util.*;

/**
 * Grid Index for I2DPoint
 *
 *     Parameters:
 *         left-bottom [minX, minY],
 *         right-top [maxX, maxY],
 *         step length
 *
 *     Algorithms:
 *         (1) number of grids at X axis:
 *                m = ceil((maxX - minX) / step);
 *         (2) number of grids at y axis:
 *                n = ceil((maxY - minY) / step);
 *         (3) a point (x, y) is mapped to a grid [i, j] in 2D space:
 *                i = floor((x - minX) / step);
 *                j = floor((y - minY) / step);
 *         (4) a grid [i, j] in 2D space is mapped to an element in array grids:
 *                index = j * m + i;
 *
 */
public class GridIndex{

    // m or n can not be over this MAX_RESOLUTION
    public static final int MAX_RESOLUTION = 1800;

    private double minX;
    private double minY;
    private double maxX;
    private double maxY;
    private double step;
    private int m;
    private int n;
    private int size = 0;

    private List<Cluster>[] grids;

    public GridIndex(double step) {
        this(-180, -90, 180, 90, step);
    }

    public GridIndex(double left, double bottom, double right, double top, double step) {

        this.minX = left;
        this.minY = bottom;
        this.maxX = right;
        this.maxY = top;
        this.step = step;

        // calculate number of grids
        m = (int) Math.ceil((maxX - minX) / step);
        n = (int) Math.ceil((maxY - minY) / step);

        // Make sure m / n is never larger than MAX_RESOLUTION,
        // so that JVM will not be OutOfMemory because of this List[][] array
        if (m > MAX_RESOLUTION || n > MAX_RESOLUTION) {
            this.step = Math.max((maxX - minX) / MAX_RESOLUTION, (maxY - minY) / MAX_RESOLUTION);
            m = (int) Math.ceil((maxX - minX) / this.step);
            n = (int) Math.ceil((maxY - minY) / this.step);
        }

        // initialize an array of grids
        grids = new List[m * n];
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

    public int insert(Cluster[] point) {
        // find the grid position [i, j]
        int i = locateX(point[0].getX());
        int j = locateY(point[0].getY());
        if (i < 0 || i >= m || j < 0 || j >= n) return -1;
        if (grids[j*m + i] == null) {
            grids[j*m + i] = new ArrayList<>();
        }
        // insert point into this grid's list
        size ++;
        int k = grids[j*m +i].indexOf(point[0]);
        if(k != -1){
            point[0] = grids[j*m+i].get(k);
            return -1;
        }
        grids[j*m + i].add(point[0]);
        return j*m+i;
    }
    public int insert(Cluster point) {
        // find the grid position [i, j]
        int i = locateX(point.getX());
        int j = locateY(point.getY());
        if (i < 0 || i >= m || j < 0 || j >= n) return -1;
        if (grids[j*m + i] == null) {
            grids[j*m + i] = new ArrayList<>();
        }
        // insert point into this grid's list
        size ++;
        grids[j*m + i].add(point);
        return j*m+i;
    }
    public void delete(Cluster point) {
        // find the grid position [i, j]
        int i = locateX(point.getX());
        int j = locateY(point.getY());
        if (i < 0 || i >= m || j < 0 || j >= n) return;
        // remove point from this grid's list
        if (grids[j*m + i] != null && grids[j*m + i].remove(point)) size --;
    }

    /**
     * find the four vertices of the inscribed square of given circle,
     *     all of grids inside inscribed square are in result directly;
     * find the four vertices of the circumscribed square of given circle,
     *     traverse the grids between circumscribed square edges and inscribed edges;
     *
     * @param center
     * @param radius
     * @return
     */
    public Map<Integer, List<Cluster>> within(Cluster center, double radius) {
        Map<Integer, List<Cluster>> result = new HashMap<>();

        // find the left bottom right top of the inscribed square of the circle
        double inLeftX = center.getX() - (radius / Math.sqrt(2.0));
        double inBottomY = center.getY() - (radius / Math.sqrt(2.0));
        double inRightX = center.getX() + (radius / Math.sqrt(2.0));
        double inTopY = center.getY() + (radius / Math.sqrt(2.0));

        // find the offset of left bottom right top 4 vertices of the inscribed square
        double inLeft = (inLeftX - minX) / step;
        double inBottom = (inBottomY - minY) / step;
        double inRight = (inRightX - minX) / step;
        double inTop = (inTopY - minY) / step;

        // find those inside grids and add their clusters directly into result list
        for (int i = (int) Math.ceil(inLeft); i < (int) Math.floor(inRight); i ++) {
            for (int j = (int) Math.ceil(inBottom); j < (int) Math.floor(inTop); j ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                if (grids[j*m + i] == null) continue;
                if(!result.containsKey(j*m+i))
                    result.put(j*m+i, new ArrayList<>());
                result.get(j*m+i).addAll(grids[j*m + i]);
            }
        }

        // find the left bottom right top of the circumscribed square of the circle
        double outLeftX = center.getX() - radius;
        double outBottomY = center.getY() - radius;
        double outRightX = center.getX() + radius;
        double outTopY = center.getY() + radius;

        // find the offset of left bottom right top 4 vertices of the circumscribed square
        double outLeft = (outLeftX - minX) / step;
        double outBottom = (outBottomY - minY) / step;
        double outRight = (outRightX - minX) / step;
        double outTop = (outTopY - minY) / step;

        // traverse the rectangle between outLeft and inLeft
        // note: if inLeft % 1 == 0.0,
        //       it means inLeft aligns with grid edge,
        //       and the right grids have been traversed above, so skip that column here.
        for (int i = (int) Math.floor(outLeft); i <= (int) Math.floor(inLeft) - (inLeft % 1 == 0.0? 1: 0); i ++) {
            // note: if outTop % 1 == 0.0, outTop aligns with grid edge, then do not check grids above outTop
            for (int j = (int) Math.floor(outBottom); j <= (int) Math.floor(outTop) - (outTop % 1 == 0.0? 1: 0); j ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                List<Cluster> grid = grids[j*m + i];
                if (grid == null) continue;
                for (Cluster p: grid) {
                    if (p.distanceTo(center) <= radius) {
                        if(!result.containsKey(j*m+i))
                            result.put(j*m+i, new ArrayList<>());
                        if(!result.get(j*m+i).contains(p))
                            result.get(j*m+i).add(p);
                    }
                }
            }
        }

        // traverse the rectangle between inRight and outRight
        // note: if outRight % 1 == 0.0,
        //       it means outRight aligns with grid edge, then do not check the grids right after outRight.
        for (int i = (int) Math.floor(inRight); i <= (int) Math.floor(outRight) - (outRight % 1 == 0.0? 1: 0); i ++) {
            for (int j = (int) Math.floor(outBottom); j <= (int) Math.floor(outTop) - (outTop % 1 == 0.0? 1: 0); j ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                List<Cluster> grid = grids[j*m + i];
                if (grid == null) continue;
                for (Cluster p: grid) {
                    if (p.distanceTo(center) <= radius) {
                        if(!result.containsKey(j*m+i))
                            result.put(j*m+i, new ArrayList<>());
                        if(!result.get(j*m+i).contains(p))
                            result.get(j*m+i).add(p);
                    }
                }
            }
        }

        // traverse the rectangle between outBottom and inBottom
        // note: if inBottom % 1 == 0.0,
        //       it means inBottom aligns with grid edge,
        //       and the grids above inBottom have been traversed above, so skip that row here.
        for (int j = (int) Math.floor(outBottom); j <= (int) Math.floor(inBottom) - (inBottom % 1 == 0.0? 1: 0); j ++) {
            // note: skip duplicated traversal (left-bottom, right-bottom)
            for (int i = (int) Math.floor(inLeft) + 1; i <= (int) Math.floor(inRight) - 1; i ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                List<Cluster> grid = grids[j*m + i];
                if (grid == null) continue;
                for (Cluster p: grid) {
                    if (p.distanceTo(center) <= radius) {
                        if(!result.containsKey(j*m+i))
                            result.put(j*m+i, new ArrayList<>());
                        if(!result.get(j*m+i).contains(p))
                            result.get(j*m+i).add(p);
                    }
                }
            }
        }

        // traverse the rectangle between inTop and outTop
        // note: if outTop % 1 == 0.0,
        //       it means outTop aligns with grid edge, then do not check the grids above outTop.
        for (int j = (int) Math.floor(inTop); j <= (int) Math.floor(outTop) - (outTop % 1 == 0.0? 1: 0); j ++) {
            // note: skip duplicated traversal (left-top, right-top)
            for (int i = (int) Math.floor(inLeft) + 1; i <= (int) Math.floor(inRight) - 1; i ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                List<Cluster> grid = grids[j*m + i];
                if (grid == null) continue;
                for (Cluster p: grid) {
                    if (p.distanceTo(center) <= radius) {
                        if(!result.containsKey(j*m+i))
                            result.put(j*m+i, new ArrayList<>());
                        if(!result.get(j*m+i).contains(p))
                            result.get(j*m+i).add(p);
                    }
                }
            }
        }

        return result;
    }

    public Map<Integer, List<Cluster>> range(Point leftBottom, Point rightTop) {
        Map<Integer, List<Cluster>> result = new HashMap<>();

        // find the offset of left bottom right top 4 vertices of the rectangle
        double left = (leftBottom.getX() - minX) / step;
        double bottom = (leftBottom.getY() - minY) / step;
        double right = (rightTop.getX() - minX) / step;
        double top = (rightTop.getY() - minY) / step;

        // find those inside grids and add their clusters directly into result list
        for (int i = (int) Math.ceil(left); i < (int) Math.floor(right); i ++) {
            for (int j = (int) Math.ceil(bottom); j < (int) Math.floor(top); j ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                if (grids[j*m + i] == null) continue;
                if(!result.containsKey(j*m+i))
                    result.put(j*m+i, new ArrayList<>());
                result.get(j*m+i).addAll(grids[j*m + i]);
            }
        }

        /** traverse clusters in those edge grids and add clusters that within the rectangle range */
        // left edge does not align with edge of grids
        if (left % 1 != 0.0) {
            int i = (int) Math.floor(left);
            // traverse left edge grids,
            // note: if top % 1 == 0.0, top aligns with grid edge, then do not check grids above top
            for (int j = (int) Math.floor(bottom); j <= (int) Math.floor(top) - (top % 1 == 0.0? 1: 0); j ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                List<Cluster> grid = grids[j*m + i];
                if (grid == null) continue;
                for (Cluster p: grid) {
                    if (p.rightAbove(leftBottom) && p.leftBelow(rightTop)) {
                        if(!result.containsKey(j*m+i))
                            result.put(j*m+i, new ArrayList<>());
                        if(!result.get(j*m+i).contains(p))
                        result.get(j*m+i).add(p);
                    }
                }
            }
        }

        // right edge does not align with edge of grids, and not the same with left
        if (right % 1 != 0.0 && Math.floor(left) < Math.floor(right)) {
            int i = (int) Math.floor(right);
            // traverse right edge grids,
            // note: if top % 1 == 0.0, top aligns with grid edge, then do not check grids above top
            for (int j = (int) Math.floor(bottom); j <= (int) Math.floor(top) - (top % 1 == 0.0? 1: 0); j ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                List<Cluster> grid = grids[j*m + i];
                if (grid == null) continue;
                for (Cluster p: grid) {
                    if (p.rightAbove(leftBottom) && p.leftBelow(rightTop)) {
                        if(!result.containsKey(j*m+i))
                            result.put(j*m+i, new ArrayList<>());
                        if(!result.get(j*m+i).contains(p))
                            result.get(j*m+i).add(p);
                    }
                }
            }
        }

        // bottom edge does not align with edge of grids
        if (bottom % 1 != 0.0) {
            int j = (int) Math.floor(bottom);
            // traverse bottom edge grids,
            // note: skip duplicated traversal (left-bottom, right-bottom)
            for (int i = (int) Math.floor(left) + 1; i <= (int) Math.floor(right) - 1; i ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                List<Cluster> grid = grids[j*m + i];
                if (grid == null) continue;
                for (Cluster p: grid) {
                    if (p.rightAbove(leftBottom) && p.leftBelow(rightTop)) {
                        if(!result.containsKey(j*m+i))
                            result.put(j*m+i, new ArrayList<>());
                        if(!result.get(j*m+i).contains(p))
                            result.get(j*m+i).add(p);
                    }
                }
            }
        }

        // top edge does not align with edge of grids, and not the same with bottom
        if (top % 1 != 0.0 && Math.floor(bottom) < Math.floor(top)) {
            int j = (int) Math.floor(top);
            // traverse top edge grids,
            // note: skip duplicated traversal (left-top, right-top)
            for (int i = (int) Math.floor(left) + 1; i <= (int) Math.floor(right) - 1; i ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                List<Cluster> grid = grids[j*m + i];
                if (grid == null) continue;
                for (Cluster p: grid) {
                    if (p.rightAbove(leftBottom) && p.leftBelow(rightTop)) {
                        if(!result.containsKey(j*m+i))
                            result.put(j*m+i, new ArrayList<>());
                        if(!result.get(j*m+i).contains(p))
                            result.get(j*m+i).add(p);
                    }
                }
            }
        }

        return result;
    }

    public Cluster findPoint(Cluster center, double radius) {
        Cluster result = null;

        // find the left bottom right top of the inscribed square of the circle
        double inLeftX = center.getX() - (radius / Math.sqrt(2.0));
        double inBottomY = center.getY() - (radius / Math.sqrt(2.0));
        double inRightX = center.getX() + (radius / Math.sqrt(2.0));
        double inTopY = center.getY() + (radius / Math.sqrt(2.0));

        // find the offset of left bottom right top 4 vertices of the inscribed square
        double inLeft = (inLeftX - minX) / step;
        double inBottom = (inBottomY - minY) / step;
        double inRight = (inRightX - minX) / step;
        double inTop = (inTopY - minY) / step;

        // find those inside grids and add their clusters directly into result list
        for (int i = (int) Math.ceil(inLeft); i < (int) Math.floor(inRight); i ++) {
            for (int j = (int) Math.ceil(inBottom); j < (int) Math.floor(inTop); j ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                if (grids[j*m + i] == null) continue;
                   int k = grids[j*m+i].indexOf(center);
                   if(k != -1)
                       return grids[j*m+i].get(k);
            }
        }

        // find the left bottom right top of the circumscribed square of the circle
        double outLeftX = center.getX() - radius;
        double outBottomY = center.getY() - radius;
        double outRightX = center.getX() + radius;
        double outTopY = center.getY() + radius;

        // find the offset of left bottom right top 4 vertices of the circumscribed square
        double outLeft = (outLeftX - minX) / step;
        double outBottom = (outBottomY - minY) / step;
        double outRight = (outRightX - minX) / step;
        double outTop = (outTopY - minY) / step;

        // traverse the rectangle between outLeft and inLeft
        // note: if inLeft % 1 == 0.0,
        //       it means inLeft aligns with grid edge,
        //       and the right grids have been traversed above, so skip that column here.
        for (int i = (int) Math.floor(outLeft); i <= (int) Math.floor(inLeft) - (inLeft % 1 == 0.0? 1: 0); i ++) {
            // note: if outTop % 1 == 0.0, outTop aligns with grid edge, then do not check grids above outTop
            for (int j = (int) Math.floor(outBottom); j <= (int) Math.floor(outTop) - (outTop % 1 == 0.0? 1: 0); j ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                List<Cluster> grid = grids[j*m + i];
                if (grid == null) continue;
                for (Cluster p: grid) {
                    if (p.equals(center)) {
                        return p;
                    }
                }
            }
        }

        // traverse the rectangle between inRight and outRight
        // note: if outRight % 1 == 0.0,
        //       it means outRight aligns with grid edge, then do not check the grids right after outRight.
        for (int i = (int) Math.floor(inRight); i <= (int) Math.floor(outRight) - (outRight % 1 == 0.0? 1: 0); i ++) {
            for (int j = (int) Math.floor(outBottom); j <= (int) Math.floor(outTop) - (outTop % 1 == 0.0? 1: 0); j ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                List<Cluster> grid = grids[j*m + i];
                if (grid == null) continue;
                for (Cluster p: grid) {
                    if (p.equals(center)) {
                        return p;
                    }
                }
            }
        }

        // traverse the rectangle between outBottom and inBottom
        // note: if inBottom % 1 == 0.0,
        //       it means inBottom aligns with grid edge,
        //       and the grids above inBottom have been traversed above, so skip that row here.
        for (int j = (int) Math.floor(outBottom); j <= (int) Math.floor(inBottom) - (inBottom % 1 == 0.0? 1: 0); j ++) {
            // note: skip duplicated traversal (left-bottom, right-bottom)
            for (int i = (int) Math.floor(inLeft) + 1; i <= (int) Math.floor(inRight) - 1; i ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                List<Cluster> grid = grids[j*m + i];
                if (grid == null) continue;
                for (Cluster p: grid) {
                    if (p.equals(center)) {
                        return p;
                    }
                }
            }
        }

        // traverse the rectangle between inTop and outTop
        // note: if outTop % 1 == 0.0,
        //       it means outTop aligns with grid edge, then do not check the grids above outTop.
        for (int j = (int) Math.floor(inTop); j <= (int) Math.floor(outTop) - (outTop % 1 == 0.0? 1: 0); j ++) {
            // note: skip duplicated traversal (left-top, right-top)
            for (int i = (int) Math.floor(inLeft) + 1; i <= (int) Math.floor(inRight) - 1; i ++) {
                if (i < 0 || i >= m || j < 0 || j >= n) continue;
                List<Cluster> grid = grids[j*m + i];
                if (grid == null) continue;
                for (Cluster p: grid) {
                        if (p.equals(center)) {
                            return p;
                    }
                }
            }
        }

        return result;
    }
    

    public int size() {
        return this.size;
    }
}
