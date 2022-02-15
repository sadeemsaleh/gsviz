package slicing;


import models.Edge;
import models.Point;
import models.ResultSetReturn;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import utils.DatabaseUtils;
import utils.PropertiesUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class FixedInterval implements Slicer{
    //query keyword
    private String query;
    //bound is the range of dates in the database
    private final Interval bound = new Interval(PropertiesUtil.getFirstDate(), PropertiesUtil.getLastDate());
    //initial duration of the first mini query
    private final FiniteDuration fixedInterval = Duration.create(PropertiesUtil.getFixedInterval(), "days");
    //interval of the mini query
    private Interval interval;

    public HashMap<Edge, Integer> init(String query) {
        this.query = query;
        interval = calculateFirst();
        HashMap<Edge, Integer> resultSet = issueQueryGroup(interval);
        calculateNext();
        return resultSet;
    }

    public ResultSetReturn askSlice() {
        HashMap<Edge, Integer> resultSet = issueQueryGroup(interval);
        calculateNext();
        ResultSetReturn returnResult = new ResultSetReturn(resultSet, false);
        if (interval.getStartMillis() >= bound.getEndMillis()) {
            returnResult.setDone(true);
        }
        return returnResult;
    }

    private void calculateNext() {
        long endTime = Math.min((interval.getEndMillis() + fixedInterval.toMillis()), bound.getEndMillis());
        long startTime = interval.getEndMillis();
        interval = new Interval(startTime, endTime);
    }

    private Interval calculateFirst() {
        long endTime = Math.min(bound.getEndMillis(), (bound.getStartMillis() + fixedInterval.toMillis()));
        return new Interval(bound.getStartMillis(), endTime);
    }

    private HashMap<Edge, Integer> issueQueryGroup(Interval interval) {

        Connection conn = DatabaseUtils.getConnection();
        PreparedStatement state = DatabaseUtils.prepareStatement(query, conn, interval.getStart().toDateTime().toString(), interval.getEnd().toDateTime().toString());
        HashMap<Edge, Integer> result = new LinkedHashMap<>();
        try {
            ResultSet resultSet = state.executeQuery();
            if (resultSet != null) {
                while (resultSet.next()) {
                    Point from = new Point(resultSet.getDouble("from_longitude"), resultSet.getDouble("from_latitude"));
                    Point to = new Point(resultSet.getDouble("to_longitude"), resultSet.getDouble("to_latitude"));
                    Edge currentEdge = new Edge(from, to);
                    // don't add short edges
                    if (Math.pow(currentEdge.length(), 2) > 0.001)
                        putEdgeIntoMap(result, currentEdge, 1);
                }
            }
            resultSet.close();
            state.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * put edge into map
     *
     * @param edges  map of edges and width
     * @param edge   edge
     * @param weight weight
     */
    private void putEdgeIntoMap(HashMap<Edge, Integer> edges, Edge edge, int weight) {
        if (edges.containsKey(edge)) {
            edges.put(edge, edges.get(edge) + weight);
        } else {
            edges.put(edge, weight);
        }
    }

}