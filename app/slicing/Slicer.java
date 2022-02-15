package slicing;

import models.Edge;
import models.ResultSetReturn;

import java.util.HashMap;

public interface Slicer {

    HashMap<Edge, Integer> init(String query);
    ResultSetReturn askSlice();
}
