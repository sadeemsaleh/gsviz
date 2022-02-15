package edgeBundling;

import clustering.Clustering;
import models.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Force Directed Edge Bundling Algorithm.
 */
public class ForceBundling {

    //batch edges in the spherical mercator range direct referencing to clusters
    private ArrayList<Edge> batchEdges;
    // All the data edges
    private ArrayList<Edge> dataEdges = new ArrayList<>();
    //tree representing previous edges from previous batches
    private Tree representatives;
    //flag to represent if the bundling is incremental
    private boolean incremental;
    // Algorithm parameters
    // K: global bundling constant controlling edge stiffness
    private final double K = 0.1;
    // S: init. distance to move points
    private double S_initial = 0.02;
    // P_initial: init. subdivision number
    private final int P_initial = 1;
    // P_rate: subdivision rate increase
    private final int P_rate = 2;
    // C: number of cycles to perform
    private final int C = 6;
    // I_initial: init. number of iterations for cycle
    private final double I_initial = 50;
    // I_rate: rate at which iteration number decreases i.e. 2/3
    private final double I_rate = 2.0 / 3.0;
    // compatibility_threshold: the threshold score of deciding compatibility
    public double compatibility_threshold = 0.6;
    // epsilon: decide the precision
    private final double eps = 1e-6;
    // isolatedEdgesCnt: unbundled edge count
    private int isolatedEdgesCnt = 0;

    /**
     * Constructor of fdeb algorithm.
     */
    public ForceBundling() {
    }

    /**
     * Constructor of fdeb algorithm.
     *
     * @param dataEdges incoming data edges.
     */
    public ForceBundling(Set<Edge> dataEdges) {
        this.dataEdges.addAll(dataEdges);
        this.representatives = null;
        this.incremental = false;
    }

    /**
     * Constructor of fdeb algorithm for incremenral
     *
     * @param dataEdges       incoming data edges.
     * @param representatives tree of edges from previous batches
     */
    public ForceBundling(Set<Edge> dataEdges, Tree representatives) {
        List<Edge> temp = new ArrayList<>(dataEdges);
        if (representatives != null) {
            representatives.filter(temp);
            temp.addAll(representatives.getChangedEdges());
        }
        this.dataEdges = temp.stream().map(edge ->
                new Edge(new Point(Clustering.xLng(edge.getFromPoint().getX()),
                        Clustering.yLat(edge.getFromPoint().getY())),
                        new Point(Clustering.xLng(edge.getToPoint().getX()),
                                Clustering.yLat(edge.getToPoint().getY())))
        ).collect(Collectors.toCollection(ArrayList::new));
        this.representatives = representatives;
        this.incremental = true;
        this.batchEdges = new ArrayList<>(temp);
    }

    public int getIsolatedEdgesCnt() {
        return isolatedEdgesCnt;
    }

    /**
     * Sets different moving distance to each zoom level.
     *
     * @param zoom current zoom level.
     */
    public void setS(int zoom) {
        S_initial = Math.max(0.025 - zoom * 0.0025, 0.001);
    }

    private double vectorDotProduct(Point p, Point q) {
        return p.getX() * q.getX() + p.getY() * q.getY();
    }


    private double edgeLength(Edge e) {
        if (Math.abs(e.getFromX() - e.getToX()) < eps &&
                Math.abs(e.getFromY() - e.getToY()) < eps) {
            return eps;
        }
        return e.length();
    }

    /**
     * Calculates distance between two points,
     * the distance of two edges is given by the distance
     * between the middle points of these two edges.
     *
     * @param p source node.
     * @param q target node.
     * @return distance of two points.
     */
    private double euclideanDistance(Point p, Point q) {
        return p.distanceTo(q);
    }

    /**
     * Calculates edge length after applying division.
     *
     * @param e_ind the index of the edge.
     * @return edge length.
     */
    private double computeDividedEdgeLength(int e_ind) {
        double length = 0;
        for (int i = 1; i < dataEdges.get(e_ind).getSubdivisionPoints().size(); i++) {
            double segmentLength = euclideanDistance(dataEdges.get(e_ind).getSubdivisionPoints().get(i), dataEdges.get(e_ind).getSubdivisionPoints().get(i - 1));
            length += segmentLength;
        }
        return length;
    }

    /**
     * Project a point onto an edge.
     *
     * @param p  point to be projected.
     * @param q1 edge source.
     * @param q2 edge target.
     * @return Projected vector origins from edge source node.
     */
    private Point projectPointOnLine(Point p, Point q1, Point q2) {
        double L = Math.sqrt(Math.pow(q2.getX() - q1.getX(), 2) + Math.pow(q2.getY() - q1.getY(), 2));
        double r = ((q1.getY() - p.getY()) * (q1.getY() - q2.getY()) - (q1.getX() - p.getX()) * (q2.getX() - q1.getX())) / (Math.pow(L, 2));
        double x = q1.getX() + r * (q2.getX() - q1.getX());
        double y = q1.getY() + r * (q2.getY() - q1.getY());
        return new Point(x, y);
    }

    /**
     * Initialize the generated paths.
     */
    private void initializeEdgeSubdivisions() {
        for (int i = 0; i < dataEdges.size(); i++) {
            dataEdges.get(i).setSubdivisionPoints(new ArrayList<>());
            dataEdges.get(i).setCompatibilityList(new ArrayList<>());
        }
    }


    /**
     * Apply the spring force within nodes in the same edge.
     *
     * @param e_ind  the index of the edge.
     * @param cp_ind the index of the control points within the edge.
     * @param kP     parameter for calculation.
     * @return force vector.
     */
    private Point applySpringForce(int e_ind, int cp_ind, double kP) {
        if (dataEdges.get(e_ind).getSubdivisionPoints().size() <= 2) {
            return new Point(0, 0);
        }
        Point prev = dataEdges.get(e_ind).getSubdivisionPoints().get(cp_ind - 1);
        Point next = dataEdges.get(e_ind).getSubdivisionPoints().get(cp_ind + 1);
        Point crnt = dataEdges.get(e_ind).getSubdivisionPoints().get(cp_ind);
        double x = prev.getX() - crnt.getX() + next.getX() - crnt.getX();
        double y = prev.getY() - crnt.getY() + next.getY() - crnt.getY();
        x *= kP;
        y *= kP;
        return new Point(x, y);
    }

    /**
     * Apply the electrostatic force between edges.
     *
     * @param e_ind the index of the edge
     * @param i     the index of the node within the edge.
     * @return force vector
     */
    private Point applyElectrostaticForce(int e_ind, int i) {
        Point sumOfForces = new Point(0, 0);
        if (dataEdges.get(e_ind).getCompatibilityList() == null
                && dataEdges.get(e_ind).getCompatibleNodes() == null) {
            return sumOfForces;
        }
        double x, y, x1, y1, x2, y2;
        x2 = dataEdges.get(e_ind).getSubdivisionPoints().get(i).getX();
        y2 = dataEdges.get(e_ind).getSubdivisionPoints().get(i).getY();
        double weight = 1;
        if (dataEdges.get(e_ind).getCompatibilityList() != null)
            for (Integer o_ind : dataEdges.get(e_ind).getCompatibilityList()) {
                x1 = dataEdges.get(o_ind).getSubdivisionPoints().get(i).getX();
                y1 = dataEdges.get(o_ind).getSubdivisionPoints().get(i).getY();
                x = x1 - x2;
                y = y1 - y2;
                if ((Math.abs(x) > eps || Math.abs(y) > eps)) {
                    Point source = dataEdges.get(o_ind).getSubdivisionPoints().get(i);
                    Point target = dataEdges.get(e_ind).getSubdivisionPoints().get(i);
                    double diff = euclideanDistance(source, target);
                    sumOfForces.setX(sumOfForces.getX() + x / diff);
                    sumOfForces.setY(sumOfForces.getY() + y / diff);
                }
            }
        if (dataEdges.get(e_ind).getCompatibleNodes() != null) {
            for (Edge oe : dataEdges.get(e_ind).getCompatibleNodes()) {
                x1 = oe.getSubdivisionPoints().get(i).getX();
                y1 = oe.getSubdivisionPoints().get(i).getY();
                x = x1 - x2;
                y = y1 - y2;
                if ((Math.abs(x) > eps || Math.abs(y) > eps)) {
                    Point source = oe.getSubdivisionPoints().get(i);
                    Point target = dataEdges.get(e_ind).getSubdivisionPoints().get(i);
                    double diff = euclideanDistance(source, target);
                    sumOfForces.setX(sumOfForces.getX() + x / diff * weight);
                    sumOfForces.setY(sumOfForces.getY() + y / diff * weight);
                }
            }
        }
        return sumOfForces;
    }


    /**
     * Calculates the net force.
     *
     * @param e_ind the index of the edge.
     * @param P     the subdivision number.
     * @param S     the moving distance.
     * @return net forces
     */
    private ArrayList<Point> applyResultingForcesOnSubdivisionPoints(int e_ind, int P, double S) {
        double kP = K / (edgeLength(dataEdges.get(e_ind)) * (P + 1));
        ArrayList<Point> resultingForcesForSubdivisionPoints = new ArrayList<>();
        for (int i = 1; i < P + 1; i++) {
            Point resultingForce = new Point(0, 0);
            Point springForce = applySpringForce(e_ind, i, kP);
            Point electrostaticForce = applyElectrostaticForce(e_ind, i);
            resultingForce.setX(S * (springForce.getX() + electrostaticForce.getX()));
            resultingForce.setY(S * (springForce.getY() + electrostaticForce.getY()));
            resultingForcesForSubdivisionPoints.add(resultingForce);
        }
        return resultingForcesForSubdivisionPoints;
    }

    /**
     * Update the path points by applying net forces
     *
     * @param P the subdivision number
     */
    private void updateEdgeDivisions(int P) {
        double compatibilityThreshold;
        Edge edge;
        for (int e_ind = 0; e_ind < dataEdges.size(); e_ind++) {
            if (P == 1) {
                edge = dataEdges.get(e_ind);
                compatibilityThreshold = compatibility_threshold;
                edge.getSubdivisionPoints().add(edge.getFromPoint());
                edge.getSubdivisionPoints().add(edge.edgeMidPoint());
                edge.getSubdivisionPoints().add(edge.getToPoint());
                for (int oe = e_ind + 1; oe < dataEdges.size(); oe++) {
                    double score = compatibilityScore(edge, dataEdges.get(oe));
                    if (score > compatibilityThreshold) {
                        edge.getCompatibilityList().add(oe);
                        dataEdges.get(oe).getCompatibilityList().add(e_ind);
                    }
                }
                if (edge.getCompatibilityList() == null && edge.getCompatibleNodes() == null) {
                    isolatedEdgesCnt++;
                }
            } else {
                double dividedEdgeLength = computeDividedEdgeLength(e_ind);
                double segmentLength = dividedEdgeLength / (P + 1);
                double currentSegmentLength = segmentLength;
                ArrayList<Point> newDivisionPoints = new ArrayList<>();
                newDivisionPoints.add(dataEdges.get(e_ind).getFromPoint());
                for (int i = 1; i < dataEdges.get(e_ind).getSubdivisionPoints().size(); i++) {
                    double oldSegmentLength = euclideanDistance(dataEdges.get(e_ind).getSubdivisionPoints().get(i), dataEdges.get(e_ind).getSubdivisionPoints().get(i - 1));
                    while (oldSegmentLength > currentSegmentLength) {
                        double percentPosition = currentSegmentLength / oldSegmentLength;
                        double newDivisionPointX = dataEdges.get(e_ind).getSubdivisionPoints().get(i - 1).getX();
                        double newDivisionPointY = dataEdges.get(e_ind).getSubdivisionPoints().get(i - 1).getY();
                        newDivisionPointX += percentPosition * (dataEdges.get(e_ind).getSubdivisionPoints().get(i).getX() - dataEdges.get(e_ind).getSubdivisionPoints().get(i - 1).getX());
                        newDivisionPointY += percentPosition * (dataEdges.get(e_ind).getSubdivisionPoints().get(i).getY() - dataEdges.get(e_ind).getSubdivisionPoints().get(i - 1).getY());
                        newDivisionPoints.add(new Point(newDivisionPointX, newDivisionPointY));
                        oldSegmentLength -= currentSegmentLength;
                        currentSegmentLength = segmentLength;
                    }
                    currentSegmentLength -= oldSegmentLength;
                }
                newDivisionPoints.add(dataEdges.get(e_ind).getToPoint());
                dataEdges.get(e_ind).setSubdivisionPoints(newDivisionPoints);
            }
        }
    }

    /**
     * Metric to measure the angle compatibility.
     *
     * @param P the first edge
     * @param Q the second edge
     * @return score of compatibility
     */
    private double angleCompatibility(Edge P, Edge Q) {
        return Math.abs(vectorDotProduct(P.edgeAsVector(), Q.edgeAsVector()) / (edgeLength(P) * edgeLength(Q)));
    }

    /**
     * Metric to measure the scale compatibility.
     *
     * @param P the first edge
     * @param Q the second edge
     * @return score of compatibility
     */
    private double scaleCompatibility(Edge P, Edge Q) {
        double lavg = (edgeLength(P) + edgeLength(Q)) / 2.0;
        return 2.0 / (lavg / Math.min(edgeLength(P), edgeLength(Q)) + Math.max(edgeLength(P), edgeLength(Q)) / lavg);
    }

    /**
     * Metric to measure the position compatibility.
     *
     * @param P the first edge
     * @param Q the second edge
     * @return score of compatibility
     */
    private double positionCompatibility(Edge P, Edge Q) {
        double lavg = (edgeLength(P) + edgeLength(Q)) / 2.0;
        Point midP = P.edgeMidPoint();
        Point midQ = Q.edgeMidPoint();
        return lavg / (lavg + euclideanDistance(midP, midQ));
    }

    /**
     * Metric to measure the visibility compatibility. (intersection part)
     *
     * @param P the first edge
     * @param Q the second edge
     * @return score of compatibility
     */
    private double edgeVisibility(Edge P, Edge Q) {
        Point I0 = projectPointOnLine(Q.getFromPoint(), P.getFromPoint(), P.getToPoint());
        Point I1 = projectPointOnLine(Q.getToPoint(), P.getFromPoint(), P.getToPoint());
        Point midI = new Point(
                (I0.getX() + I1.getX()) / 2.0,
                (I0.getY() + I1.getY()) / 2.0
        );
        Point midP = P.edgeMidPoint();
        return Math.max(0, 1 - 2 * euclideanDistance(midP, midI) / euclideanDistance(I0, I1));
    }

    /**
     * Metric to measure the visibility compatibility.
     *
     * @param P the first edge
     * @param Q the second edge
     * @return score of compatibility
     */
    private double visibilityCompatibility(Edge P, Edge Q) {
        return Math.min(edgeVisibility(P, Q), edgeVisibility(Q, P));
    }

    /**
     * Calculates the compatibility score.
     *
     * @param P the first edge
     * @param Q the second edge
     * @return score of compatibility
     */
    public double compatibilityScore(Edge P, Edge Q) {
        return (angleCompatibility(P, Q) * scaleCompatibility(P, Q) * positionCompatibility(P, Q) * visibilityCompatibility(P, Q));
    }

    /**
     * Returns the compatible judgement.
     *
     * @param P the first edge
     * @param Q the second edge
     * @return compatible result
     */
    public boolean areCompatible(Edge P, Edge Q, double compatibilityThreshold) {
        return (compatibilityScore(P, Q) > compatibilityThreshold);
    }

    /**
     * Runs the edge bundle
     *
     * @return bundling results (path)
     */
    public void forceBundle() {
        double S = S_initial;
        double I = I_initial;
        int P = P_initial;
        initializeEdgeSubdivisions();
        updateEdgeDivisions(P);
        ArrayList<ArrayList<Point>> forces = new ArrayList<>();
        for (int cycle = 0; cycle < C; cycle++) {
            for (int iteration = 0; iteration < I; iteration++) {
                forces.clear();
                for (int edge = 0; edge < dataEdges.size(); edge++) {
                    forces.add(applyResultingForcesOnSubdivisionPoints(edge, P, S));
                }
                for (int e = 0; e < dataEdges.size(); e++) {
                    for (int i = 1; i < P + 1; i++) {
                        dataEdges.get(e).getSubdivisionPoints().get(i).setX(dataEdges.get(e).getSubdivisionPoints().get(i).getX() + forces.get(e).get(i - 1).getX());
                        dataEdges.get(e).getSubdivisionPoints().get(i).setY(dataEdges.get(e).getSubdivisionPoints().get(i).getY() + forces.get(e).get(i - 1).getY());
                    }
                }
            }
            S = S / 2;
            P = P * P_rate;
            I = I_rate * I;
            updateEdgeDivisions(P);
        }
    }

    /**
     * Runs the edge bundle incrementally
     * 1. the batchEdges contains the edges pointing to clusters - DONE
     * 2. convert them into a longitude, latitude version - DONE
     * 3. bundle them
     * 4. save the bundled result in the spherical mercator version - DONE
     * 5. insert the batchEdges as leaves in the tree - DONE
     * 6. insert their centroids as representatives(clusters) in the tree - DONE
     * 7. when a new batch arrives,
     * a. check if the edge is already in the set, then delete it - DONE
     * b. then check all the edges that need to be rebundled - DONE
     * c. insert the new batch to the tree - DONE
     *
     * @return a tree representing the edges seen so far to be used in next batch
     */
    public Tree incrementalBundle() {
        double S = S_initial;
        double I = I_initial;
        int P = P_initial;
        initializeEdgeSubdivisions();
        updateEdgeDivisions(P);
        if (representatives != null) {
            representatives.initializeSubdivisions();
        }
        ArrayList<ArrayList<Point>> forces = new ArrayList<>();
        for (int cycle = 0; cycle < C; cycle++) {
            for (int iteration = 0; iteration < I; iteration++) {
                forces.clear();
                for (int edge = 0; edge < dataEdges.size(); edge++) {
                    forces.add(applyResultingForcesOnSubdivisionPoints(edge, P, S));

                }
                for (int e = 0; e < dataEdges.size(); e++) {
                    for (int i = 1; i < P + 1; i++) {
                        dataEdges.get(e).getSubdivisionPoints().get(i).setX(dataEdges.get(e).getSubdivisionPoints().get(i).getX() + forces.get(e).get(i - 1).getX());
                        dataEdges.get(e).getSubdivisionPoints().get(i).setY(dataEdges.get(e).getSubdivisionPoints().get(i).getY() + forces.get(e).get(i - 1).getY());
                    }
                }
            }
            S = S / 2;
            P = P * P_rate;
            I = I_rate * I;
            updateEdgeDivisions(P);
            if (representatives != null) {
                representatives.updateControlPoints(P);
            }
        }
        getCenterEdges();
        return representatives;
    }

    private void getCenterEdges() {
        HashMap<Integer, LinkedHashSet<Integer>> map = new HashMap<>();
        //to keep track of the inserted edges to avoid duplicates
        List<Integer> inserted = new ArrayList<>();
        //get the centroids
        boolean flag;
        for (int i = 0; i < dataEdges.size(); i++) {
            //update the bundling result in the leaves
            batchEdges.get(i).setSubdivisionPoints(dataEdges.get(i).getSubdivisionPoints());
            Edge e = dataEdges.get(i);
            flag = false;
            for (Map.Entry<Integer, LinkedHashSet<Integer>> entry : map.entrySet()) {
                if (entry.getValue().contains(i)) {
                    if (e.getCompatibilityList().size() > dataEdges.get(entry.getKey()).getCompatibilityList().size()) {
                        e.getCompatibilityList().removeIf(element -> element == entry.getKey());
                        entry.getValue().addAll(new LinkedHashSet<>(e.getCompatibilityList()));
                    }
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                if (e.getCompatibilityList() != null) {
                    map.put(i, new LinkedHashSet<>(e.getCompatibilityList()));
                }
            }
        }
        //loop over the entries in the map
        Edge e;
        int explored = 0;
        if (representatives == null)
            representatives = new Tree();
        for (Map.Entry<Integer, LinkedHashSet<Integer>> entry : map.entrySet()) {
            List<Edge> leaves = new ArrayList<>();
            int length = 1;
            Set<Integer> edges = entry.getValue();
            edges.removeAll(inserted);
            int e_ind = entry.getKey();
            if (inserted.contains(e_ind)) {
                if (edges.iterator().hasNext()) {
                    e_ind = edges.iterator().next();
                    edges.remove(e_ind);
                }
            }
            leaves.add(batchEdges.get(e_ind));
            inserted.add(e_ind);
            inserted.addAll(edges);
            Edge cE = dataEdges.get(e_ind);
            Point centerS = cE.getFromPoint();
            Point centerT = cE.getToPoint();
            Point centerL = centerS.getX() < centerT.getX() ? new Point(centerS) : new Point(centerT);
            Point centerR = centerS.getX() < centerT.getX() ? new Point(centerT) : new Point(centerS);
            for (int indx : edges) {
                leaves.add(batchEdges.get(indx));
                e = dataEdges.get(indx);
                Point s = e.getFromPoint();
                Point t = e.getToPoint();
                Point l = s.getX() < t.getX() ? s : t;
                Point r = s.getX() < t.getX() ? t : s;
                centerL.setX((centerL.getX() * length + l.getX()) / (length + 1));
                centerL.setY((centerL.getY() * length + l.getY()) / (length + 1));
                centerR.setX((centerR.getX() * length + r.getX()) / (length + 1));
                centerR.setY((centerR.getY() * length + r.getY()) / (length + 1));
                length++;
            }
            explored += representatives.insert(new Edge(new Point(centerL.getX(), centerL.getY()), new Point(centerR.getX(), centerR.getY())), leaves);
        }
    }
}
