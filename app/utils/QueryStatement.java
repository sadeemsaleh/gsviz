package utils;

/**
 * Util class to store the query string.
 */
class QueryStatement {
    static String incrementalTweetsStatament = "select from_longitude, from_latitude, to_longitude, to_latitude "
            + "from replies where ( to_tsvector('english', from_text) @@ to_tsquery( ? ) or "
            + "to_tsvector('english', to_text) "
            + "@@ to_tsquery( ? )) AND from_create_at::timestamp > TO_TIMESTAMP( ? , 'yyyy-mm-dd\"T\"hh24:mi:ss') "
            + "AND from_create_at::timestamp <= TO_TIMESTAMP( ? , 'yyyy-mm-dd\"T\"hh24:mi:ss');";
       static String incrementalFoursquareStatament = "select from_longitude, from_latitude, to_longitude, to_latitude "
            + "from foursquare where checkin::timestamp > TO_TIMESTAMP( ? , 'yyyy-mm-dd\"T\"hh24:mi:ss') " 
+ "AND checkin::timestamp <= TO_TIMESTAMP( ? , 'yyyy-mm-dd\"T\"hh24:mi:ss');";
        static String incrementalFlightStatament = "select from_longitude, from_latitude, to_longitude, to_latitude "
            + "from flights where from_create_at::timestamp > TO_TIMESTAMP( ? , 'yyyy-mm-dd\"T\"hh24:mi:ss') "
           + "AND from_create_at::timestamp <= TO_TIMESTAMP( ? , 'yyyy-mm-dd\"T\"hh24:mi:ss');";
    static String statement = "select from_longitude, from_latitude, to_longitude, to_latitude " +
            "from replies where to_tsvector('english', from_text) @@ to_tsquery( ? ) or " +
            "to_tsvector('english', to_text) @@ to_tsquery( ? );";
    static String fixedIntervalStatement = "select from_longitude, from_latitude, to_longitude, to_latitude "
            + "from replies_100k where from_create_at::timestamp > TO_TIMESTAMP( ? , 'yyyy-mm-dd\"T\"hh24:mi:ss') "
            + "AND from_create_at::timestamp <= TO_TIMESTAMP( ? , 'yyyy-mm-dd\"T\"hh24:mi:ss');";
}

