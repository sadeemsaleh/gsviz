package connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * this class is for GraphController to parse requests sent from frontend
 */
public class Parser {
    // 0: don't draw points, 1: draw points
    private int pointStatus = 0;
    // 0: don't draw edges, 1: draw edges
    private int edgeStatus = 0;
    // the four coordinates corresponding to the current window
    private double lowerLongitude = 0;
    private double upperLongitude = 0;
    private double lowerLatitude = 0;
    private double upperLatitude = 0;
    // 0: display points, 1: display clusters
    private int clustering = 0;
    // 0: HGC, 1: IKmeans, 2: Kmeans
    private int clusteringAlgorithm = 0;
    // 0: no bundling, 1: do FDEB
    private int bundling = 0;
    // 0: FDEB, 1: IFDEB
    private int bundlingAlgorithm = 0;
    // the current zoom level
    private int zoom = 0;
    // 0: no treeCutting, 1: do treeCutting
    private int treeCutting = 0;
    // the query keyword
    private String query = "";
    // the flag whether a new query is submitted to query the database or just frontend change
    private boolean newQuery;
    //the range search radius for clustering
    private double radius;

    public boolean getNewQuery(){
        return newQuery;
    }
    public int getPointStatus() {
        return pointStatus;
    }

    public int getEdgeStatus() {
        return edgeStatus;
    }

    public double getLowerLongitude() {
        return lowerLongitude;
    }

    public double getUpperLongitude() {
        return upperLongitude;
    }

    public double getLowerLatitude() {
        return lowerLatitude;
    }

    public double getUpperLatitude() {
        return upperLatitude;
    }

    public int getClustering() {
        return clustering;
    }

    public int getClusteringAlgorithm() {
        return clusteringAlgorithm;
    }

    public int getBundling() {
        return bundling;
    }

    public int getBundlingAlgorithm() {
        return bundlingAlgorithm;
    }

    public int getZoom() {
        return zoom;
    }

    public int getTreeCutting() {
        return treeCutting;
    }

    public String getQuery() {
        return query;
    }

    public double getRadius() {
        return radius;
    }

    /**
     * parse the JSON sent from frontend into variables in backend
     * @param query the JSON sent from frontend
     */
    public void parse(String query) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = null;
        try {
            jsonNode = objectMapper.readTree(query);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (jsonNode != null) {
            if (jsonNode.has("query")) {
                this.query = jsonNode.get("query").asText();
            }
            if (jsonNode.has("pointStatus")) {
                pointStatus = Integer.parseInt(jsonNode.get("pointStatus").asText());
            }
            if (jsonNode.has("edgeStatus")) {
                edgeStatus = Integer.parseInt(jsonNode.get("edgeStatus").asText());
            }
            if (jsonNode.has("clusteringAlgorithm")) {
                clusteringAlgorithm = Integer.parseInt(jsonNode.get("clusteringAlgorithm").asText());
            }
            if (jsonNode.has("bundlingAlgorithm")) {
                bundlingAlgorithm = Integer.parseInt(jsonNode.get("bundlingAlgorithm").asText());
            }
            if (jsonNode.has("lowerLongitude"))
                lowerLongitude = Double.parseDouble(jsonNode.get("lowerLongitude").asText());
            if (jsonNode.has("upperLongitude")) {
                upperLongitude = Double.parseDouble(jsonNode.get("upperLongitude").asText());
            }
            if (jsonNode.has("lowerLatitude")) {
                lowerLatitude = Double.parseDouble(jsonNode.get("lowerLatitude").asText());
            }
            if (jsonNode.has("upperLatitude")) {
                upperLatitude = Double.parseDouble(jsonNode.get("upperLatitude").asText());
            }
            if (jsonNode.has("zoom"))
                zoom = Integer.parseInt(jsonNode.get("zoom").asText());
            if (jsonNode.has("bundling"))
                bundling = Integer.parseInt(jsonNode.get("bundling").asText());
            if (jsonNode.has("clustering"))
                clustering = Integer.parseInt(jsonNode.get("clustering").asText());
            if (jsonNode.has("treeCut"))
                treeCutting = Integer.parseInt(jsonNode.get("treeCut").asText());
            if (jsonNode.has("newQuery"))
                newQuery = Boolean.parseBoolean(jsonNode.get("newQuery").asText());
            if (jsonNode.has("radius"))
                radius = Double.parseDouble(jsonNode.get("radius").asText());

        }
    }
}
