package connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Edge;
import models.EdgeFeature;
import models.Point;

import java.util.*;

/**
 * this class is for GraphController to send responses to frontend
 */
public class Response {
    // 0: don't draw points, 1: draw points
    private int pointStatus = 0;
    // 0: don't draw edges, 1: draw edges
    private int edgeStatus = 0;
    // flag indicates whether the incremental query is finished
    private String flag;
    // the number of edges on the screen
    private int edgesCnt;
    // the number of reply tweets corresponding to a keyword
    private int repliesCnt;
    // the data of points for drawing graph in frontend
    private String pointData;
    // the data of edges for drawing graph in frontend
    private String edgeData;
    // the number of edges on the screen that are not bundled
    private int isolatedEdgesCnt;
    // the number of points (or points in clusters) on the screen
    private int pointsCnt;
    // the number of clusters on the screen
    private int clustersCnt;

    public void setPointStatus(int pointStatus) {
        this.pointStatus = pointStatus;
    }

    public void setEdgeStatus(int edgeStatus) {
        this.edgeStatus = edgeStatus;
    }

    public String getFlag() {
        return flag;
    }

    public String getPointData(){return pointData;}

    public String getEdgeData(){return edgeData;}

    public int getPointStatus(){return pointStatus;}

    public int getEdgeStatus(){return edgeStatus;}

    public int getPointsCnt() {
        return pointsCnt;
    }
    public int getClustersCnt() {
        return clustersCnt;
    }

    public int getEdgesCnt() {
        return edgesCnt;
    }

    public int getRepliesCnt() {
        return repliesCnt;
    }

    public int getIsolatedEdgesCnt() {
        return isolatedEdgesCnt;
    }

    public void setClustersCnt(int clustersCnt) {
        this.clustersCnt = clustersCnt;
    }

    public void setPointsCnt(int pointsCnt) {
        this.pointsCnt = pointsCnt;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public void setEdgesCnt(int edgesCnt) {
        this.edgesCnt = edgesCnt;
    }

    public void setRepliesCnt(int repliesCnt) {
        this.repliesCnt = repliesCnt;
    }

    public void setPointData(String data) {
        this.pointData = data;
    }

    public void setEdgeData(String data) {
        this.edgeData = data;
    }

    public void setIsolatedEdgesCnt(int isolatedEdgesCnt) {
        this.isolatedEdgesCnt = isolatedEdgesCnt;
    }

    /**
     * set data as edges in JSON format
     * @param edges edges stored in HashMap
     */
    public void setEdges(HashMap<Edge, Integer> edges) {
        int max_RGB_color = 255;
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (Edge edge : edges.keySet()) {
            ObjectNode lineNode = objectMapper.createObjectNode();
            lineNode.putArray("from").add(edge.getFromX()).add(edge.getFromY());
            lineNode.putArray("to").add(edge.edgeMidPoint().getX()).add(edge.edgeMidPoint().getY());
            lineNode.put("width", edges.get(edge));
            lineNode.putArray("color").add(0).add(0).add(max_RGB_color);
            arrayNode.add(lineNode);
            lineNode = objectMapper.createObjectNode();
            lineNode.putArray("from").add(edge.edgeMidPoint().getX()).add(edge.edgeMidPoint().getY());
            lineNode.putArray("to").add(edge.getToX()).add(edge.getToY());
            lineNode.put("width", edges.get(edge));
            lineNode.putArray("color").add(max_RGB_color).add(0).add(0);
            arrayNode.add(lineNode);
        }
        setEdgeData(arrayNode.toString());
    }

    /**
     * set data as edges in JSON format
     * @param edges edges stored in HashMap
     */
    public void setBundledEdges(HashMap<Edge, EdgeFeature> edges) {
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (Edge edge : edges.keySet()) {
            ObjectNode lineNode = objectMapper.createObjectNode();
            lineNode.putArray("from").add(edge.getFromX()).add(edge.getFromY());
            lineNode.putArray("to").add(edge.getToX()).add(edge.getToY());
            lineNode.put("width", edges.get(edge).getWidth());
            lineNode.putArray("color").add(edges.get(edge).getColor()[0]).add(edges.get(edge).getColor()[1]).add(edges.get(edge).getColor()[2]);
            arrayNode.add(lineNode);
        }
        setEdgeData(arrayNode.toString());
    }

    /**
     * set data as points in JSON format
     * @param points points stored in HashMap
     */
    public void setPoints(HashMap<Point, Integer> points) {
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (Point point : points.keySet()) {
            ObjectNode objectNode = objectMapper.createObjectNode();
            objectNode.putArray("coordinates").add(point.getX()).add(point.getY());
            objectNode.put("size", points.get(point));
            arrayNode.add(objectNode);
        }
        setPointData(arrayNode.toString());
        clustersCnt = points.size();
    }

    /**
     * set data as points and edges in JSON format
     * @param edges edges stored in HashMap
     */
    public void setUnbundled(HashMap<Edge, Integer> edges) {
        HashMap<Point, Integer> points = new HashMap<>();
        int id = 0;
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (Edge edge : edges.keySet()) {
            //visit every edge and put the nodes in a map
            if(!points.containsKey(edge.getFromPoint()))
                points.put(edge.getFromPoint(), id++);
            if(!points.containsKey(edge.getToPoint()))
                points.put(edge.getToPoint(), id++);
            ObjectNode lineNode = objectMapper.createObjectNode();
            lineNode.put("source", points.get(edge.getFromPoint())+"");
            lineNode.put("target", points.get(edge.getToPoint())+"");
            arrayNode.add(lineNode);
        }
        setEdgeData(arrayNode.toString());
        //after looking at every edge, now we have the points
        ObjectNode nodes = objectMapper.createObjectNode();
        for (Map.Entry<Point, Integer> entry : points.entrySet()) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("x", entry.getKey().getX());
            node.put("y", entry.getKey().getY());
            nodes.set(entry.getValue()+"", node);
        }
        setPointData(nodes.toString());
    }
}
