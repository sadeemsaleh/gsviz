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

public class MiniQueryGenerator implements Slicer{
    //query keyword
    private String query;
    //bound is the range of dates in the database
    private final Interval bound = new Interval(PropertiesUtil.getFirstDate(), PropertiesUtil.getLastDate());
    private Interval boundary = new Interval(bound.getStartMillis(), bound.getEndMillis());
    //initial duration of the first mini query
    private final FiniteDuration initialDuration = Duration.create(PropertiesUtil.getInitialDuration(), "days");
    //minimum duration is the minimum time range of a sliced query
    private final FiniteDuration minimumDuration = Duration.create(PropertiesUtil.getMinimumDuration(), "day");
    //interval of the mini query
    private Interval interval;
    //pace of returning the result of the mini queries in milliseconds
    private final long intervalMS = PropertiesUtil.getIntervalMS();
    private NextEstimates nextEstimates;
    private Drum estimator;
    private long nextLimit;

    public HashMap<Edge, Integer> init(String query) {
        this.query = query;
        interval = calculateFirst(boundary, initialDuration);
        nextEstimates = new NextEstimates(interval, Integer.MAX_VALUE);
        estimator = new Drum((int) boundary.toDuration().getStandardHours(), 0.00001, (int) minimumDuration.toHours());
        long t0 = System.currentTimeMillis();
        DateTime issuedTimestamp = DateTime.now();
        HashMap<Edge, Integer> resultSet = issueQueryGroup(interval);
        long timeSpend = DateTime.now().getMillis() - issuedTimestamp.getMillis();
        long diff = Math.max(0, intervalMS - timeSpend);
        nextLimit = intervalMS + diff;
        calculateNext(timeSpend);
        return resultSet;
    }

    public ResultSetReturn askSlice() {
        long t0 = System.currentTimeMillis();
        DateTime issuedTimestamp = DateTime.now();
        HashMap<Edge, Integer> resultSet = issueQueryGroup(nextEstimates.getNextInterval());
        long timeSpend = DateTime.now().getMillis() - issuedTimestamp.getMillis();
        long diff = Math.max(0, nextLimit - timeSpend);
        nextLimit = intervalMS + diff;
        calculateNext(timeSpend);
        ResultSetReturn returnResult = new ResultSetReturn(resultSet, false);
        if (nextEstimates.getNextInterval().toDurationMillis() == 0) {
            returnResult.setDone(true);
        }
        return returnResult;
    }

    private void calculateNext(long lastActualMS) {
        estimator.learn((int) nextEstimates.getNextInterval().toDuration().getStandardHours(), (int) nextEstimates.getNextEstimateMS(), (int) lastActualMS);
        Drum.RangeTime estimate = estimator.estimate((int) nextLimit);

        long startTime = Math.max(boundary.getStartMillis(), nextEstimates.getNextInterval().getStart().minusHours(estimate.range()).getMillis());
        Interval interval = new Interval(startTime, nextEstimates.getNextInterval().getStartMillis());
        nextEstimates.setNextInterval(interval);
        nextEstimates.setNextEstimateMS((long) estimate.estimateMS());
    }

    private Interval calculateFirst(Interval entireInterval, FiniteDuration duration) {
        long startTime = Math.max(entireInterval.getEndMillis() - duration.toMillis(), entireInterval.getStartMillis());
        return new Interval(startTime, entireInterval.getEndMillis());
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

    class NextEstimates {
        private Interval nextInterval;
        private long nextEstimateMS;

        public NextEstimates(Interval interval, long nextEstimateMS) {
            this.nextInterval = interval;
            this.nextEstimateMS = nextEstimateMS;
        }

        public Interval getNextInterval() {
            return nextInterval;
        }

        public void setNextInterval(Interval nextInterval) {
            this.nextInterval = nextInterval;
        }

        public long getNextEstimateMS() {
            return nextEstimateMS;
        }

        public void setNextEstimateMS(long nextEstimateMS) {
            this.nextEstimateMS = nextEstimateMS;
        }
    }


}
