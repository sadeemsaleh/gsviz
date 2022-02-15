package models;

import java.util.*;
import java.util.stream.Collectors;

public class KdTree {

    /**
     * node in kd-tree
     */
    class Node {
        // drawPoints of this node
        private Cluster point;
        // whether this node is in vertical version or not
        boolean align;
        // left son of this node
        Node left;
        private List<Cluster> duplicates;
        // right son of this node
        Node right;
        int depth;
        boolean deleted = false;

        /**
         * constructor of node
         *
         * @param p        drawPoints of this node
         * @param vertical whether this node is in vertical version or not
         */
        private Node(Cluster p, boolean vertical, int depth) {
            this.point = p;
            this.align = vertical;
            this.depth = depth;
            this.duplicates = new LinkedList<>();
        }

        public void addDuplicate(Cluster point) {
            this.duplicates.add(point);
        }

        public List<Cluster> getDuplicates() {
            return this.duplicates;
        }

        public Cluster getPoint() {
            return this.point;
        }

    }

    // the root of kd-tree
    private Node root;
    private int height = 0;
    // the number of nodes
    private int size;

    /**
     * return an instance of kd tree
     */
    public KdTree() {
        root = null;
        size = 0;
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
     * get the number of points in the tree
     *
     * @return the number of points
     */
    public int size() {
        return size;
    }

    /**
     * Insert a drawPoints into kd tree
     *
     * @param point drawPoints
     */
    public void insert(Cluster point) {
        size++;

        // empty tree
        if (root == null) {
            root = new Node(point, true, 0);
            height = 1;
            return;
        }

        // root always align with x
        boolean align = true;
        Node currentNode = root;
        Node parentNode = currentNode;
        boolean left = true;
        // find the position to insert
        while (currentNode != null) {
            Point currentPoint = currentNode.getPoint();
            // duplicate
            if (currentPoint.equals(point)) {
                currentNode.addDuplicate(point);
                return;
            } else {
                // check x
                if (align) {
                    if (point.getX() < currentPoint.getX()) {
                        parentNode = currentNode;
                        currentNode = currentNode.left;
                        left = true;
                    } else {
                        parentNode = currentNode;
                        currentNode = currentNode.right;
                        left = false;
                    }
                }
                // check y
                else {
                    if (point.getY() < currentPoint.getY()) {
                        parentNode = currentNode;
                        currentNode = currentNode.left;
                        left = true;
                    } else {
                        parentNode = currentNode;
                        currentNode = currentNode.right;
                        left = false;
                    }
                }
            }
            align = !align;
        }
        // parentNode clusters to the parent of new node
        currentNode = new Node(point, align, parentNode.depth + 1);
        if (currentNode.depth + 1 > height) {
            height = currentNode.depth + 1;
        }
        if (left) {
            parentNode.left = currentNode;
        } else {
            parentNode.right = currentNode;
        }
    }


    public Map<Integer, List<Cluster>> within(Point center, double radius) {
        HashMap<Integer, List<Cluster>> result = new HashMap<>();
        Pair newPair = new Pair(center);
        PriorityQueue<Pair> temp = new PriorityQueue<>();
        if (root == null) {
            return result;//temp.stream().map(pair -> pair.getCluster()).collect(Collectors.toList());
        }
        Queue<Node> queue = new LinkedList<>();
        queue.add(root);
        while (queue.size() > 0) {
            Node currentNode = queue.poll();
            boolean align = currentNode.align;
            Cluster currentPoint = currentNode.getPoint();
            // if current node within range, put it into result, and put both children to queue
            if (currentPoint.distanceTo(center) <= radius) {
                if (!currentNode.deleted)
                    temp.add(new Pair(currentPoint));
                // also add duplicates inside current node
                for (Cluster duplicate : currentNode.getDuplicates()) {
                    temp.add(new Pair(duplicate));
                }
                if (currentNode.left != null) {
                    queue.add(currentNode.left);
                }
                if (currentNode.right != null) {
                    queue.add(currentNode.right);
                }
            }
            // else current node outside range
            else {
                // check x
                if (align) {
                    // but if NOT (currentNode.x + r) < center.x, left child still needs to be checked
                    if (currentPoint.getX() + radius >= center.getX()) {
                        if (currentNode.left != null) {
                            queue.add(currentNode.left);
                        }
                    }
                    // but if NOT center.x < (currentNode.x - r), right child still needs to be checked
                    if (center.getX() >= currentPoint.getX() - radius) {
                        if (currentNode.right != null) {
                            queue.add(currentNode.right);
                        }
                    }
                }
                // check y
                else {
                    // but if NOT (currentNode.y + r) < center.y, left child still needs to be checked
                    if (currentPoint.getY() + radius >= center.getY()) {
                        if (currentNode.left != null) {
                            queue.add(currentNode.left);
                        }
                    }
                    // but if NOT center.y < (currentNode.y - r), right child still needs to be checked
                    if (center.getY() >= currentPoint.getY() - radius) {
                        if (currentNode.right != null) {
                            queue.add(currentNode.right);
                        }
                    }
                }
            }
        }
        temp.forEach(pair -> {
            Cluster c = pair.getCluster();
            int code = c.getGridLocation();
            if (!result.containsKey(code)) {
                result.put(code, new ArrayList<>());
               // if (!result.get(code).contains(c))
                    result.get(code).add(c);
            }
        });
        return result;
    }

    public List<Cluster> range(Point leftBottom, Point rightTop) {
        List<Cluster> result = new ArrayList<>();
        if (root == null) {
            return result;
        }
        Queue<Node> queue = new LinkedList<>();
        queue.add(root);
        while (queue.size() > 0) {
            Node currentNode = queue.poll();
            boolean align = currentNode.align;
            Cluster currentPoint = currentNode.getPoint();
            // if current node within range, put it into result, and put both children to queue
            if (currentPoint.rightAbove(leftBottom) && currentPoint.leftBelow(rightTop)) {
                if (!currentNode.deleted)
                    result.add(currentPoint);
                // also add duplicates inside current node
                for (Cluster duplicate : currentNode.getDuplicates()) {
                    result.add(duplicate);
                }
                if (currentNode.left != null) {
                    queue.add(currentNode.left);
                }
                if (currentNode.right != null) {
                    queue.add(currentNode.right);
                }
            }
            // else current node outside range
            else {
                // check x
                if (align) {
                    // currentNode is to the right of right edge of rectangle, only check left child
                    if (rightTop.getX() < currentPoint.getX()) {
                        if (currentNode.left != null) {
                            queue.add(currentNode.left);
                        }
                    }
                    // currentNode is to the left of left edge of rectangle, only check right child
                    else if (leftBottom.getX() > currentPoint.getX()) {
                        if (currentNode.right != null) {
                            queue.add(currentNode.right);
                        }
                    }
                    // currentNode.x is between leftBottom and rightTop, both children need to be explored
                    else {
                        if (currentNode.left != null) {
                            queue.add(currentNode.left);
                        }
                        if (currentNode.right != null) {
                            queue.add(currentNode.right);
                        }
                    }
                }
                // check y
                else {
                    // currentNode is above the top edge of rectangle, only check left child
                    if (rightTop.getY() < currentPoint.getY()) {
                        if (currentNode.left != null) {
                            queue.add(currentNode.left);
                        }
                    }
                    // currentNode is below the bottom edge of rectangle, only check right child
                    else if (leftBottom.getY() > currentPoint.getY()) {
                        if (currentNode.right != null) {
                            queue.add(currentNode.right);
                        }
                    }
                    // currentNode.y is between leftBottom and rightTop, both children need to be explored
                    else {
                        if (currentNode.left != null) {
                            queue.add(currentNode.left);
                        }
                        if (currentNode.right != null) {
                            queue.add(currentNode.right);
                        }
                    }
                }
            }
        }
        return result;
    }

    public void delete(Cluster point) {
        // root always align with x
        boolean align = true;
        Node currentNode = root;

        // find the point
        while (currentNode != null) {
            Cluster currentPoint = currentNode.getPoint();
            // hit
            if (currentPoint.equals(point)) {
                // if hit the node's point
                if (!currentNode.deleted) {
                    currentNode.deleted = true;
                }
                // else hit the node's duplicate point
                else {
                    currentNode.duplicates.remove(0);
                }
                size--;
                return;
            } else {
                // check x
                if (align) {
                    if (point.getX() < currentPoint.getX()) {
                        currentNode = currentNode.left;
                    } else {
                        currentNode = currentNode.right;
                    }
                }
                // check y
                else {
                    if (point.getY() < currentPoint.getY()) {
                        currentNode = currentNode.left;
                    } else {
                        currentNode = currentNode.right;
                    }
                }
            }
            align = !align;
        }
        // didn't find the point
    }

    public Cluster findPoint(Cluster point) {
        Cluster result = null;
        if (root == null) {
            return result;
        }
        Queue<Node> queue = new LinkedList<>();
        queue.add(root);
        while (queue.size() > 0) {
            Node currentNode = queue.poll();
            Cluster currentPoint = currentNode.getPoint();
            if (currentPoint.equals(point)) {
                return currentPoint;
            }
            if (isSmaller(point, currentNode)) {
                if (currentNode.left != null)
                    queue.add(currentNode.left);
            } else {
                if (currentNode.right != null)
                    queue.add(currentNode.right);
            }
        }
        return result;
    }

    private boolean isSmaller(Cluster p, Node n) {
        if (n.align) return p.getX() < n.getPoint().getX();
        else return p.getY() < n.getPoint().getY();
    }
}
