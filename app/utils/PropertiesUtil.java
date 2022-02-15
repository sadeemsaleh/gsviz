package utils;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;

/**
 * Util class to read properties file.
 */
public class PropertiesUtil {

    private static DateTime firstDate;
    private static DateTime lastDate;
    private static DateTimeFormatter dateFormat;
    private static int minimumDuration;
    private static int initialDuration;
    private static long intervalMS;
    private static int fixedInterval;

    private static File configFile = new File("./conf/config.properties");

    public static void loadProperties() {

        Properties configProps = new Properties();

        // loads properties from file
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(configFile);

            configProps.load(inputStream);
            dateFormat = DateTimeFormat.forPattern(configProps.getProperty("dateFormat"));
            firstDate = dateFormat.parseDateTime(configProps.getProperty("firstDate"));
            lastDate = dateFormat.parseDateTime(configProps.getProperty("lastDate"));
            minimumDuration = Integer.parseInt(configProps.getProperty("minimumDuration"));
            initialDuration = Integer.parseInt(configProps.getProperty("initialDuration"));
            intervalMS = Long.parseLong(configProps.getProperty("intervalMS"));
            fixedInterval = Integer.parseInt(configProps.getProperty("fixedInterval"));
            inputStream.close();
        }
        catch (FileNotFoundException e) {
            System.out.println("The config.properties file does not exist, default properties loaded.");
        } catch (IOException e) {
            System.out.println("The config.properties file does not exist, default properties loaded.");
        }
    }

    public static DateTime getFirstDate() {
        return firstDate;
    }

    public static DateTime getLastDate() {
        return lastDate;
    }

    public static int getMinimumDuration() {
        return minimumDuration;
    }
    public static int getFixedInterval(){
        return fixedInterval;
    }
    public static int getInitialDuration() {
        return initialDuration;
    }

    public static long getIntervalMS() {
        return intervalMS;
    }
}
