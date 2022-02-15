package models;

import java.util.HashMap;

public class ResultSetReturn {
    private HashMap<Edge, Integer> resultSet;
    private boolean done;

    public ResultSetReturn(HashMap<Edge, Integer> resultSet, boolean done) {
        this.resultSet = resultSet;
        this.done = done;
    }

    public HashMap<Edge, Integer> getResultSet() {
        return resultSet;
    }

    public void setResultSet(HashMap<Edge, Integer> resultSet) {
        this.resultSet = resultSet;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }
}
