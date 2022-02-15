package controllers;

import actors.WebSocketActor;
import clustering.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import connection.Parser;
import connection.Response;
import edgeBundling.*;
import models.*;
import play.mvc.Controller;
import slicing.FixedInterval;
import slicing.MiniQueryGenerator;
import slicing.Slicer;
import treeCut.TreeCut;
import utils.DatabaseUtils;
import utils.PropertiesUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * This controller contains an action to handle HTTP requests to the
 * application's home page.
 */

public class GraphController extends Controller {

    // Indicates the sending process is completed
    private static final String finished = "Y";
    // Indicates the sending process is not completed
    private static final String unfinished = "N";
    // the tree that contains representative edges from previous batches
    private Tree centerEdges = null;
    // result to gain confidence in initial bundling
    private static final int minimum_edges_confidence = 1000;
    // hierarchical structure for HGC algorithm
    private Clustering clustering = new Clustering(0, 17);

    private Kmeans kmeans;
    // Incremental edge data
    private HashMap<Edge, Integer> batchEdges = new LinkedHashMap<>();
    //total accumulated edges
    private HashMap<Edge, Integer> totalEdges = new LinkedHashMap<>();
    //RGB for creating gradient effect for the edge direction
    private int max_RGB_color = 255;
    private ObjectMapper objectMapper = new ObjectMapper();

    private boolean incremental = false;
    private Parser parser = new Parser();
    private Response response = new Response();
    private final int K = 17;

    /**
     * Dispatcher for the request message.
     *
     * @param query received query message
     * @param actor WebSocket actor to return response.
     */
    public void dispatcher(String query, WebSocketActor actor) {


        // Heartbeat package handler
        // WebSocket will automatically close after several seconds
        // To keep the state, maintain WebSocket connection is a must
	parser.parse(query);
        if (query.isEmpty()) {
            return;
        }
        //set the radius of the range search for clustering
        // Parse the request message with JSON structure
        clustering.setRadius(parser.getRadius());
        if (parser.getNewQuery()) {
            clearPreviousResult();
            PropertiesUtil.loadProperties();
            if (parser.getClusteringAlgorithm() == 2) {
                if (kmeans == null)
                    kmeans = new Kmeans(K);
                doQuery();
                loadKmeans();
                processData(actor);
                response.setFlag(finished);
            } else {
                Slicer progressive = new MiniQueryGenerator();//either DRUM or Fixed interval
                HashMap<Edge, Integer> resultSet = progressive.init(parser.getQuery());
                if (resultSet != null) {

                    loadData(resultSet);
                    processData(actor);

                }
                ResultSetReturn result = progressive.askSlice();
                loadData(result.getResultSet());
                if (result.isDone()) {
                    response.setFlag(finished);
                    processData(actor);
                }
                response.setFlag(unfinished);
                processData(actor);
                while (!result.isDone()) {
                    result = progressive.askSlice();
                    loadData(result.getResultSet());
                    response.setFlag(unfinished);
                    if (result.isDone()) {
                        response.setFlag(finished);
                    }
                    processData(actor);
                }
            }
        } else {
            processOldData(actor);
        }

    }

    private void clearPreviousResult() {
        kmeans = null;
        totalEdges.clear();
        batchEdges.clear();
    }

    private void processOldData(WebSocketActor actor) {
        if (parser.getBundlingAlgorithm() == 2) {
            response.setEdgesCnt(totalEdges.size());
            response.setRepliesCnt(getTotalEdgesSize());
            response.setUnbundled(totalEdges);
        } else {
            if (parser.getPointStatus() == 1) {
                drawPoints();
            }
            if (parser.getEdgeStatus() == 1) {
                drawEdges();
            }
        }
        response.setPointStatus(parser.getPointStatus());
        response.setEdgeStatus(parser.getEdgeStatus());
        try {
            actor.returnData(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private void processData(WebSocketActor actor) {
        if (parser.getClusteringAlgorithm() == 0) {
            loadHGC();
        } else if (parser.getClusteringAlgorithm() == 1) {
            if (kmeans == null)
                kmeans = new IKmeans(K);
            loadKmeans();
        }
        if (parser.getBundlingAlgorithm() == 2) {
            response.setEdgesCnt(totalEdges.size());
            response.setRepliesCnt(getTotalEdgesSize());
            response.setUnbundled(totalEdges);
        } else {
            if (parser.getPointStatus() == 1) {
                drawPoints();
            }
            if (parser.getEdgeStatus() == 1) {
                drawEdges();
            }
        }
        response.setPointStatus(parser.getPointStatus());
        response.setEdgeStatus(parser.getEdgeStatus());
        try {
            actor.returnData(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private void doQuery() {
        Connection conn = DatabaseUtils.getConnection();
        PreparedStatement state = DatabaseUtils.prepareStatement(parser.getQuery(), conn);
        ResultSet resultSet;
        try {
            resultSet = state.executeQuery();
            if (resultSet != null) {
                while (resultSet.next()) {
                    Point from = new Point(resultSet.getDouble("from_longitude"), resultSet.getDouble("from_latitude"));
                    Point to = new Point(resultSet.getDouble("to_longitude"), resultSet.getDouble("to_latitude"));
                    Edge currentEdge = new Edge(from, to);
                    putEdgeIntoMap(totalEdges, currentEdge, 1);
                }
            }
            resultSet.close();
            state.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    private void loadData(HashMap<Edge, Integer> resultSet) {
        batchEdges.clear();
        for (Map.Entry<Edge, Integer> entry : resultSet.entrySet()) {
            putEdgeIntoMap(batchEdges, entry.getKey(), entry.getValue());
            putEdgeIntoMap(totalEdges, entry.getKey(), entry.getValue());
        }
    }

    private void loadKmeans() {
        if (kmeans instanceof IKmeans) {
            kmeans.execute(batchEdges);
        } else {
            kmeans.execute(totalEdges);
        }
    }

    private void loadHGC() {
        clustering.load(batchEdges);
    }


    /**
     * draw points
     */
    private void drawPoints() {
        HashMap<Point, Integer> pointsMap;
        if (parser.getClustering() == 0) {
            pointsMap = totalEdges.keySet().stream().collect(HashMap::new, (point, edge) -> {
                point.put(edge.getToPoint(), 1);
                point.put(edge.getFromPoint(), 1);
            }, HashMap::putAll);
        } else {
            if (parser.getClusteringAlgorithm() == 0) {
                //so it always redraws all batches from scratch though they were added incrementally
                List<Cluster> clusters = this.clustering.getClusters(new double[]{parser.getLowerLongitude(), parser.getLowerLatitude(), parser.getUpperLongitude(), parser.getUpperLatitude()}, parser.getZoom());
//                List<model.Cluster> clusters = Arrays.asList(iSuperCluster.getClusters(parser.getLowerLongitude(), parser.getLowerLatitude(), parser.getUpperLongitude(), parser.getUpperLatitude(), parser.getZoom()));
//                pointsMap = clusters.stream().collect(HashMap::new, (point, cluster) -> {
//                    point.put(new Point(cluster.getX(), cluster.getY()), cluster.getNumPoints());
//                }, HashMap::putAll);
                pointsMap = clusters.stream().collect(HashMap::new, (point, cluster) -> {
                    point.put(new Point(Clustering.xLng(cluster.getX()), Clustering.yLat(cluster.getY())), cluster.getNumPoints());
                }, HashMap::putAll);
//                if(response.getFlag() !=null) {
//                    if (response.getFlag().equalsIgnoreCase(unfinished))
//                        this.clustering.silhouetteIndex();
//                }
            } else {
                pointsMap = kmeans.getClustersMap();
            }
        }
        response.setPoints(pointsMap);
        response.setPointsCnt(getTotalPointsSize());
        response.setClustersCnt(pointsMap.size());
        response.setRepliesCnt(getTotalEdgesSize());
    }

    /**
     * draw edges
     */
    private void drawEdges() {
        HashMap<Edge, Integer> edges = new HashMap<>();
        HashSet<Edge> externalEdgeSet = new HashSet<>();
        HashSet<Cluster> externalCluster = new HashSet<>();
        HashSet<Cluster> internalCluster = new HashSet<>();
        int zoomLevel = 18;
        if (parser.getClustering() == 1 && parser.getClusteringAlgorithm() != 0) {
            for (Edge edge : totalEdges.keySet()) {
                putEdgeIntoMap(edges, new Edge(kmeans.getParent(edge.getFromPoint()), kmeans.getParent(edge.getToPoint())), totalEdges.get(edge));
            }
        } else {
            if (parser.getClustering() != 0)
                zoomLevel = parser.getZoom();
            generateEdgeSet(edges, externalEdgeSet, externalCluster, internalCluster, zoomLevel);
            if (parser.getTreeCutting() == 1) {
                TreeCut treeCutInstance = new TreeCut();
                treeCutInstance.execute(this.clustering, parser.getLowerLongitude(), parser.getUpperLongitude(), parser.getLowerLatitude(), parser.getUpperLatitude(), parser.getZoom(), edges, externalEdgeSet, externalCluster, internalCluster);
            }
        }
        int edgeCnt = edges.size();
        response.setEdgesCnt(edgeCnt);
        if (parser.getBundling() == 0) {
            noBundling(edges);
        } else {
            if (parser.getBundlingAlgorithm() == 0) {
                runFDEB(edges);
            } else {
                if (!incremental) {
                    runFDEB(edges);
                } else {
                    runIFDEB(edges);
                }
            }
        }
        response.setRepliesCnt(getTotalEdgesSize());
    }

    /**
     * get the number of total edges
     *
     * @return the number of total edges
     */
    private int getTotalEdgesSize() {
        int tot = 0;
        for (Integer weight : totalEdges.values()) {
            tot += weight;
        }
        return tot;
    }

    /**
     * get the number of total points
     *
     * @return the number of total points
     */
    private int getTotalPointsSize() {
        return totalEdges.keySet().stream().collect(HashSet::new, (point, edge) -> {
            point.add(edge.getToPoint());
            point.add(edge.getFromPoint());
        }, Set::addAll).size();
    }

    /**
     * prepares external edge set for tree cut.
     *
     * @param edges           the returning edge set, if tree cut is enabled, it contains only the internal edges.
     *                        Otherwise, it contains all the edges.
     * @param externalEdgeSet the returning external edge set
     * @param externalCluster outside cluster corresponding to edge set with only one node inside screen
     * @param internalCluster inside screen clusters
     */
    private void generateEdgeSet(HashMap<Edge, Integer> edges, HashSet<Edge> externalEdgeSet,
                                 HashSet<Cluster> externalCluster, HashSet<Cluster> internalCluster, int zoom) {
        HashMap<Edge, Integer> edgesMap = totalEdges;
        if (incremental)
            edgesMap = batchEdges;
        if (parser.getBundlingAlgorithm() == 1 && totalEdges.size() > minimum_edges_confidence)
            incremental = true;
        for (Edge edge : edgesMap.keySet()) {
            Cluster fromCluster = clustering.parentCluster(new Cluster(new Point(Clustering.lngX(edge.getFromX()), Clustering.latY(edge.getFromY()))), zoom);
            Cluster toCluster = clustering.parentCluster(new Cluster(new Point(Clustering.lngX(edge.getToX()), Clustering.latY(edge.getToY()))), zoom);
            double fromLongitude = Clustering.xLng(fromCluster.getX());
            double fromLatitude = Clustering.yLat(fromCluster.getY());
            double toLongitude = Clustering.xLng(toCluster.getX());
            double toLatitude = Clustering.yLat(toCluster.getY());
            boolean fromWithinRange = parser.getLowerLongitude() <= fromLongitude && fromLongitude <= parser.getUpperLongitude()
                    && parser.getLowerLatitude() <= fromLatitude && fromLatitude <= parser.getUpperLatitude();
            boolean toWithinRange = parser.getLowerLongitude() <= toLongitude && toLongitude <= parser.getUpperLongitude()
                    && parser.getLowerLatitude() <= toLatitude && toLatitude <= parser.getUpperLatitude();
            Edge e = new Edge(fromCluster, toCluster);
            if (Math.pow(e.length(), 2) <= 0.001)
                continue;
            if (fromWithinRange && toWithinRange) {
                putEdgeIntoMap(edges, e, edgesMap.get(edge));
                internalCluster.add(fromCluster);
                internalCluster.add(toCluster);
            } else if (fromWithinRange || toWithinRange) {
                if (parser.getTreeCutting() == 0) {
                    putEdgeIntoMap(edges, e, edgesMap.get(edge));
                } else {
                    if (fromWithinRange) {
                        externalCluster.add(toCluster);
                    } else {
                        externalCluster.add(fromCluster);
                    }
                    externalEdgeSet.add(edge);
                }

            }
        }
    }

    /**
     * put edge into map
     *
     * @param edges  map of edges and width
     * @param edge   edge
     * @param weight weight
     */
    private void putEdgeIntoMap(HashMap<Edge, Integer> edges, Edge edge, int weight) {
        if (Math.pow(edge.length(), 2) <= 0.001)
            return;
        if (edges.containsKey(edge)) {
            edges.put(edge, edges.get(edge) + weight);
        } else {
            edges.put(edge, weight);
        }
    }

    /**
     * put edge into map
     *
     * @param edges       map of edges and width
     * @param edge        edge
     * @param edgeFeature includes colors
     */
    private void putBundledEdgesIntoMap(HashMap<Edge, EdgeFeature> edges, Edge edge, EdgeFeature edgeFeature) {
        if (Math.pow(edge.length(), 2) <= 0.001)
            return;
        if (edges.containsKey(edge)) {
            edges.put(edge, new EdgeFeature(edgeFeature.getColor(), edges.get(edge).getWidth() + edgeFeature.getWidth()));
        } else {
            edges.put(edge, edgeFeature);
        }
    }

    /**
     * run FDEB
     *
     * @param edges input edges
     */
    private void runFDEB(HashMap<Edge, Integer> edges) {
        HashMap<Edge, Integer> edgesLongLat = edges.entrySet().stream().collect(HashMap::new, (edge, cluster) -> {
            edge.put(new Edge(new Point(Clustering.xLng(cluster.getKey().getFromX()),
                            Clustering.yLat(cluster.getKey().getFromY())),
                            new Point(Clustering.xLng(cluster.getKey().getToX()),
                                    Clustering.yLat(cluster.getKey().getToY())))
                    , cluster.getValue());
        }, HashMap::putAll);
        ForceBundling forceBundling = new ForceBundling(edgesLongLat.keySet());
        forceBundling.setS(parser.getZoom());
        forceBundling.forceBundle();
        int isolatedEdgesCnt = forceBundling.getIsolatedEdgesCnt();
        HashMap<Edge, EdgeFeature> edgesData = new HashMap<>();
        double percentage;
        for (Edge edge : edgesLongLat.keySet()) {
            for (int j = 0; j < edge.getSubdivisionPoints().size() - 1; j++) {
                percentage = (float) j / edge.getSubdivisionPoints().size();
                int[] color = {(int) Math.round(percentage * max_RGB_color), 0, (int) Math.round((1 - percentage) * max_RGB_color)};
                putBundledEdgesIntoMap(edgesData, new Edge(edge.getSubdivisionPoints().get(j), edge.getSubdivisionPoints().get(j + 1)), new EdgeFeature(color, edgesLongLat.get(edge)));
            }
        }
        response.setBundledEdges(edgesData);
        response.setIsolatedEdgesCnt(isolatedEdgesCnt);
    }

    /**
     * run Incremental FDEB
     *
     * @param edges input edges
     */
    private void runIFDEB(HashMap<Edge, Integer> edges) {
        ForceBundling forceBundling = new ForceBundling(edges.keySet(), centerEdges);
        forceBundling.setS(parser.getZoom());
        centerEdges = forceBundling.incrementalBundle();
        int isolatedEdgesCnt = forceBundling.getIsolatedEdgesCnt();
        HashMap<Edge, EdgeFeature> edgesData = new HashMap<>();
        double percentage;
        for (Edge edge : centerEdges.getLeaves()) {
            int weight = ((Tree.Node) edge).getWeight();
            for (int j = 0; j < edge.getSubdivisionPoints().size() - 1; j++) {
                percentage = (float) j / edge.getSubdivisionPoints().size();
                int[] color = {(int) Math.round(percentage * max_RGB_color), 0, (int) Math.round((1 - percentage) * max_RGB_color)};
                putBundledEdgesIntoMap(edgesData, new Edge(edge.getSubdivisionPoints().get(j), edge.getSubdivisionPoints().get(j + 1)), new EdgeFeature(color, weight));
            }
        }
        response.setBundledEdges(edgesData);
        response.setIsolatedEdgesCnt(isolatedEdgesCnt);
    }

    /**
     * show edges without bundling
     *
     * @param edges input edges
     */
    private void noBundling(HashMap<Edge, Integer> edges) {
        edges = edges.entrySet().stream().collect(HashMap::new, (edge, cluster) -> {
            edge.put(new Edge(new Point(Clustering.xLng(cluster.getKey().getFromX()),
                            Clustering.yLat(cluster.getKey().getFromY())),
                            new Point(Clustering.xLng(cluster.getKey().getToX()),
                                    Clustering.yLat(cluster.getKey().getToY())))
                    , cluster.getValue());
        }, HashMap::putAll);
        response.setEdges(edges);
        response.setIsolatedEdgesCnt(edges.size());
    }
}
