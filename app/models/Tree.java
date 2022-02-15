package models;

import clustering.Clustering;
import edgeBundling.ForceBundling;
import smile.clustering.SpectralClustering;

import java.util.*;
import java.util.stream.Collectors;

public class Tree {

    /**
     * node in tree
     */
    public class Node extends Edge implements Comparable<Node> {
        // the list of children nodes of this node
        List<Node> children;
        //weight of the node
        int weight;
        //parent node of this node
        Node parent;
        //score of the node with the incoming new edge
        double score;
        boolean isLeaf;

        public Node(Edge edge, List<Node> children, int weight, Node parent) {
            super(edge);
            this.children = children;
            this.weight = weight;
            this.parent = parent;
            this.isLeaf = true;
        }

        public List<Node> getChildren() {
            return children;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public double getScore() {
            return score;
        }

        public boolean isLeaf() {
            return isLeaf;
        }

        public void setLeafFlag(boolean leafFlag) {
            this.isLeaf = leafFlag;
        }

        public void propagateChange(double fromLong, double fromLat,
                                    double toLong, double toLat) {
            Node parent = getParent();
            while (parent != null) {
                parent.getFromPoint().setX((parent.getFromX() * parent.getWeight() + fromLong) / (parent.getWeight() + 1));
                parent.getFromPoint().setY((parent.getFromY() * parent.getWeight() + fromLat) / (parent.getWeight() + 1));
                parent.getToPoint().setX((parent.getToX() * parent.getWeight() + toLong) / (parent.getWeight() + 1));
                parent.getToPoint().setY((parent.getToY() * parent.getWeight() + toLat) / (parent.getWeight() + 1));
                parent = parent.getParent();
            }
        }

        public void setScore(double score) {
            this.score = score;
        }

        public boolean hasChildren() {
            return !isLeaf && children.size() > 0;
        }

        public void setChildren(List<Node> children) {
            this.children = children;
        }

        public Node getParent() {
            return parent;
        }

        public void setParent(Node parent) {
            this.parent = parent;
        }

        public void addToChildren(Node node) {
            //if this is a leaf, let its children point(parent) to another copy of this
            if (isLeaf) {
                Node copy = new Node(new Edge(new Point(getFromPoint()), new Point(getToPoint())), new ArrayList<>(), weight, this);
                children.forEach(child -> child.setParent(copy));
                copy.setChildren(new ArrayList<>(children));
                this.setLeafFlag(false);
                this.children.clear();
                this.children.add(copy);
                //let the other copy(leaf) point to this
                node.setParent(this);
                size++;
            }
            this.children.add(node);
            updateNode(node, node.getWeight());
            if (size >= treeSizeThreshold) {
                node.merge();
            } else {
                if (children.size() >= branchingFactor) {
                    split();
                }
            }
        }

        public void merge() {
            //check if node is in root, then siblings is root
            List<Node> siblings;
            if (parent == null) {
                siblings = root;
            } else {
                siblings = parent.getChildren();
            }
            //check all siblings for best compatibility
            double maxScore = -1;
            Node mostCompatibleSibling = null;
            double score;
            for (Node sibling : siblings) {
                if (this != sibling) {
                    score = fb.compatibilityScore(this, sibling);
                    if (score > maxScore) {
                        mostCompatibleSibling = sibling;
                        maxScore = score;
                    }
                }
            }
            //if there is no compatible sibling,
            // then it is a lonely child, then merge its parent
            if (mostCompatibleSibling == null) {
                parent.merge();
                return;
            }
            //merge with most compatible sibling
            mostCompatibleSibling.getFromPoint().setX((mostCompatibleSibling.getFromX() * mostCompatibleSibling.getWeight() + getFromX() * getWeight()) / (getWeight() + mostCompatibleSibling.getWeight()));
            mostCompatibleSibling.getFromPoint().setY((mostCompatibleSibling.getFromY() * mostCompatibleSibling.getWeight() + getFromY() * getWeight()) / (getWeight() + mostCompatibleSibling.getWeight()));
            mostCompatibleSibling.getToPoint().setX((mostCompatibleSibling.getToX() * mostCompatibleSibling.getWeight() + getToX() * getWeight()) / (getWeight() + mostCompatibleSibling.getWeight()));
            mostCompatibleSibling.getToPoint().setY((mostCompatibleSibling.getToY() * mostCompatibleSibling.getWeight() + getToY() * getWeight()) / (getWeight() + mostCompatibleSibling.getWeight()));

            mostCompatibleSibling.setWeight(mostCompatibleSibling.getWeight() + this.getWeight());
            //update the children's pointer
            for (Node child : this.getChildren()) {
                child.setParent(mostCompatibleSibling);
            }
            //update the parent to drop the merged sibling
            siblings.remove(this);
            //reduce the number of nodes
            size--;
        }

        private void split() {
            //first split the children to two clusters using spectral clustering
            double[][] adjacency = new double[children.size()][children.size()];
            for (int i = 0; i < children.size(); i++) {
                for (int j = i + 1; j < children.size(); j++) {
                    double score = fb.compatibilityScore(children.get(i), children.get(j));
                    adjacency[i][j] = score;
                    adjacency[j][i] = score;
                }
            }
            SpectralClustering sp = SpectralClustering.fit(adjacency, 2, 0.2);
            int[] clusters = sp.y;
            //construct the left node
            Node leftNode = new Node(new Edge(new Point(0, 0), new Point(0, 0)), new ArrayList<>(), 0, this);
            leftNode.setLeafFlag(isLeaf);
            //construct the right node
            Node rightNode = new Node(new Edge(new Point(0, 0), new Point(0, 0)), new ArrayList<>(), 0, this);
            rightNode.setLeafFlag(isLeaf);
            //insert all of the edges to the corresponding new parents
            for (int i = 0; i < clusters.length; i++) {
                //insert in left child
                Node child = children.get(i);
                if (clusters[i] == 0) {
                    leftNode.children.add(child);
                    leftNode.getFromPoint().setX((leftNode.getFromX() * leftNode.getWeight() + child.getFromX() * child.getWeight()) / (leftNode.getWeight() + child.getWeight()));
                    leftNode.getFromPoint().setY((leftNode.getFromY() * leftNode.getWeight() + child.getFromY() * child.getWeight()) / (leftNode.getWeight() + child.getWeight()));
                    leftNode.getToPoint().setX((leftNode.getToX() * leftNode.getWeight() + child.getToX() * child.getWeight()) / (leftNode.getWeight() + child.getWeight()));
                    leftNode.getToPoint().setY((leftNode.getToY() * leftNode.getWeight() + child.getToY() * child.getWeight()) / (leftNode.getWeight() + child.getWeight()));
                    leftNode.setWeight(leftNode.getWeight() + child.getWeight());
                    child.setParent(leftNode);
                }
                //insert in right child
                if (clusters[i] == 1) {
                    rightNode.children.add(child);
                    rightNode.getFromPoint().setX((rightNode.getFromX() * rightNode.getWeight() + child.getFromX() * child.getWeight()) / (rightNode.getWeight() + child.getWeight()));
                    rightNode.getFromPoint().setY((rightNode.getFromY() * rightNode.getWeight() + child.getFromY() * child.getWeight()) / (rightNode.getWeight() + child.getWeight()));
                    rightNode.getToPoint().setX((rightNode.getToX() * rightNode.getWeight() + child.getToX() * child.getWeight()) / (rightNode.getWeight() + child.getWeight()));
                    rightNode.getToPoint().setY((rightNode.getToY() * rightNode.getWeight() + child.getToY() * child.getWeight()) / (rightNode.getWeight() + child.getWeight()));
                    rightNode.setWeight(rightNode.getWeight() + child.getWeight());
                    child.setParent(rightNode);
                }
            }
            setLeafFlag(false);
            //children now point to these two nodes
            children = new ArrayList<>();
            children.add(leftNode);
            children.add(rightNode);
        }

        public void updateNode(Edge edge, int edgeWeight) {
            getFromPoint().setX((getFromX() * weight + edge.getFromX() * edgeWeight) / (weight + edgeWeight));
            getFromPoint().setY((getFromY() * weight + edge.getFromY() * edgeWeight) / (weight + edgeWeight));
            getToPoint().setX((getToX() * weight + edge.getToX() * edgeWeight) / (weight + edgeWeight));
            getToPoint().setY((getToY() * weight + edge.getToY() * edgeWeight) / (weight + edgeWeight));
            weight += edgeWeight;
            Node parent = this.parent;
            if (parent != null)
                parent.updateNode(edge, edgeWeight);
        }

        @Override
        public int compareTo(Node o) {
            if (score < o.score) return 1;
            if (score == o.score) return 0;
            return -1;
        }
    }

    // the root of tree
    private List<Node> root;
    private ForceBundling fb = new ForceBundling();
    private int size;
    private final double minimum_compatibility = 0.3;
    //branching factor(size of list or children or root)
    final int branchingFactor = 50;
    private final int treeSizeThreshold = 16400;
    private List<Node> leaves;

    /**
     * return an instance of tree
     */
    public Tree() {
        root = null;
        size = 0;
        leaves = new ArrayList<>();
    }

    /**
     * check if the tree is empty
     *
     * @return if the tree is empty
     */
    public boolean isEmpty() {
        return root == null;
    }

    /**
     * return the size of the tree
     *
     * @return the size of the tree
     */
    public int size() {
        return size;
    }

    public List<Node> getLeaves() {
        return leaves;
    }

    public void filter(List<Edge> edges) {
        edges.removeIf(leaf -> {
            if (this.leaves.contains(leaf)) {
                int indx = this.leaves.indexOf(leaf);
                Node n = this.leaves.get(indx);
                n.setWeight(n.getWeight() + 1);
                return true;
            }
            return false;
        });
    }

    public List<Edge> getChangedEdges() {
        List<Edge> changed = new ArrayList<>();
        for (Edge e : leaves) {
            if (e.getFromPoint() instanceof Cluster && e.getToPoint() instanceof Cluster) {
                Cluster from = (Cluster) e.getFromPoint();
                Cluster to = (Cluster) e.getToPoint();
                if (from.isShifted() || to.isShifted()) {
                    changed.add(e);
                    double fromLong = Clustering.xLng(from.getX());
                    double fromLat = Clustering.yLat(from.getY());
                    double toLong = Clustering.xLng(to.getX());
                    double toLat = Clustering.yLat(to.getY());
                    ((Node) e).propagateChange(fromLong, fromLat, toLong, toLat);
                    from.setShifted(false);
                    to.setShifted(false);
                }
            }
        }
        return changed;
    }

    /**
     * Insert the set of edges to the leaf hashset,
     * and their centroid to the tree, and update the parents
     *
     * @param edge
     */
    public int insert(Edge edge, List<Edge> leaves) {
        double maxScore = -1;
        //poll the first and add its children,
        //do the same until the deepest child most compatible
        //either add a node to the child and merge if branching factor is reached or split
        Node node = new Node(edge, new ArrayList<>(), leaves.size(), null);
        List<Node> nodesLeaves = leaves.stream().map(leaf -> new Node(leaf, null, 1, node)).collect(Collectors.toList());
        //first construct the node
        node.setChildren(nodesLeaves);
        nodesLeaves.forEach(leaf -> {
            if (this.leaves.contains(leaf)) {
                int indx = this.leaves.indexOf(leaf);
                Node n = this.leaves.get(indx);
                n.setWeight(n.getWeight() + 1);
            } else {
                this.leaves.add(leaf);
            }
        });
        // base case: empty tree
        if (isEmpty()) {
            root = new ArrayList<>();
        }
        // if didn't reach threshold of branching, then insert it in the node
        if (root.size() < branchingFactor) {
            root.add(node);
            size++;
            return 0;
        }
        //insert all the root to the priority queue
        PriorityQueue<Node> queue = new PriorityQueue<>();
        queue.addAll(root.stream().map(e -> {
            e.setScore(fb.compatibilityScore(e.getEdge(), edge));
            return e;
        }).collect(Collectors.toList()));
        root.forEach(n -> queue.addAll(n.getChildren().stream().map(child -> {
            child.setScore(fb.compatibilityScore(child.getEdge(), edge));
            return child;
        }).collect(Collectors.toList())));
        //keep polling as long as there are more children that are more compatible
        Node otherNode = null;
        while (queue.peek().score > maxScore) {
            otherNode = queue.poll();
            //either stop here or insert its children if it has any
            if (otherNode.hasChildren()) {
                //if its children are in the queue, insert the grand children
                if (queue.containsAll(otherNode.getChildren()))
                    otherNode.getChildren().forEach(n -> queue.addAll(n.getChildren().stream().map(child -> {
                        child.setScore(fb.compatibilityScore(child.getEdge(), edge));
                        return child;
                    }).collect(Collectors.toList())));
                    //otherwise, insert the children
                else
                    queue.addAll(otherNode.getChildren().stream().map(e -> {
                        e.setScore(fb.compatibilityScore(e.getEdge(), edge));
                        return e;
                    }).collect(Collectors.toList()));
            }
            maxScore = otherNode.score;
        }
        otherNode.addToChildren(node);
        size++;
        return queue.size();
    }

    /**
     * find all the nodes this edge is compatible with in the tree
     *
     * @param edge
     */
    public int traverse(Edge edge) {
        // while queue has elements,
        // if node is compatible, add it to the result,
        // check if parent is not incompatible, explore the children
        int explored = 0;
        if (root == null) {
            return explored;
        }
        Queue<Node> queue = new ArrayDeque<>();
        queue.addAll(root);
        while (!queue.isEmpty()) {
            Edge node = queue.poll();
            explored++;
            if (fb.areCompatible(node.getEdge(), edge, fb.compatibility_threshold)) {
                if (edge.getCompatibleNodes() == null) {
                    edge.setCompatibleNodes(new ArrayList<>());
                }
                edge.getCompatibleNodes().add(node);
            } else {
                if (fb.compatibilityScore(node.getEdge(), edge) > minimum_compatibility)
                    queue.addAll(((Node) node).getChildren());
            }
        }
        return explored;
    }

    /**
     * function to initialize all the control points
     */
    public void initializeSubdivisions() {
        Queue<Node> queue = new ArrayDeque<>();
        queue.addAll(root);
        while (!queue.isEmpty()) {
            //pull the first element and update the node
            Node node = queue.poll();
            if (node.getSubdivisionPoints() == null) {
                node.setSubdivisionPoints(new ArrayList<>());
            }
            node.getSubdivisionPoints().add(node.getFromPoint());
            node.getSubdivisionPoints().add(node.edgeMidPoint());
            node.getSubdivisionPoints().add(node.getToPoint());
            if (!node.isLeaf())
                queue.addAll(node.getChildren());

        }
    }

    /**
     * function to update all the control points
     */
    public void updateControlPoints(int P) {
        Queue<Node> queue = new ArrayDeque<>();
        queue.addAll(root);
        while (!queue.isEmpty()) {
            //pull the first element and update the node
            Node node = queue.poll();
            double dividedEdgeLength = computeDividedEdgeLength(node);
            double segmentLength = dividedEdgeLength / (P + 1);
            double currentSegmentLength = segmentLength;
            ArrayList<Point> newDivisionPoints = new ArrayList<>();
            newDivisionPoints.add(node.getFromPoint());
            for (int i = 1; i < node.getSubdivisionPoints().size(); i++) {
                double oldSegmentLength = node.getSubdivisionPoints().get(i).distanceTo(node.getSubdivisionPoints().get(i - 1));
                while (oldSegmentLength > currentSegmentLength) {
                    double percentPosition = currentSegmentLength / oldSegmentLength;
                    double newDivisionPointX = node.getSubdivisionPoints().get(i - 1).getX();
                    double newDivisionPointY = node.getSubdivisionPoints().get(i - 1).getY();
                    newDivisionPointX += percentPosition * (node.getSubdivisionPoints().get(i).getX() - node.getSubdivisionPoints().get(i - 1).getX());
                    newDivisionPointY += percentPosition * (node.getSubdivisionPoints().get(i).getY() - node.getSubdivisionPoints().get(i - 1).getY());
                    newDivisionPoints.add(new Point(newDivisionPointX, newDivisionPointY));
                    oldSegmentLength -= currentSegmentLength;
                    currentSegmentLength = segmentLength;
                }
                currentSegmentLength -= oldSegmentLength;
            }
            newDivisionPoints.add(node.getToPoint());
            node.setSubdivisionPoints(newDivisionPoints);
            if (!node.isLeaf())
                queue.addAll(node.getChildren());
        }
    }

    private double computeDividedEdgeLength(Edge e) {
        double length = 0;
        for (int i = 1; i < e.getSubdivisionPoints().size(); i++) {
            double segmentLength = e.getSubdivisionPoints().get(i).distanceTo(e.getSubdivisionPoints().get(i - 1));
            length += segmentLength;
        }
        return length;
    }
}
