package lcdviewer.ui;

import java.io.File;
import java.util.prefs.Preferences;

/**
 * 参数与路径配置持久化工具（使用 Java Preferences API）。
 */
public final class Settings {

    private static final Preferences prefs = Preferences.userNodeForPackage(Settings.class);

    private static final String KEY_ROUTE_NAME = "routeName";
    private static final String KEY_STATIONS = "stations";
    private static final String KEY_CARS = "cars";
    private static final String KEY_NEXT_IDX = "nextIdx";
    private static final String KEY_PHASE = "phase";
    private static final String KEY_CIRCULAR = "circular";
    private static final String KEY_REVERSED = "reversed";
    private static final String KEY_DOOR_LEFT = "doorLeft";
    private static final String KEY_ROUTE_COLOR = "routeColor";
    private static final String KEY_LAST_DIR = "lastDir";

    private Settings() {}

    public static String getRouteName() {
        return prefs.get(KEY_ROUTE_NAME, "");
    }

    public static void setRouteName(String val) {
        prefs.put(KEY_ROUTE_NAME, val == null ? "" : val);
    }

    public static String getStations() {
        return prefs.get(KEY_STATIONS, "");
    }

    public static void setStations(String val) {
        prefs.put(KEY_STATIONS, val == null ? "" : val);
    }

    public static int getCars() {
        return prefs.getInt(KEY_CARS, 6);
    }

    public static void setCars(int val) {
        prefs.putInt(KEY_CARS, val);
    }

    public static int getNextIdx() {
        return prefs.getInt(KEY_NEXT_IDX, 0);
    }

    public static void setNextIdx(int val) {
        prefs.putInt(KEY_NEXT_IDX, val);
    }

    public static String getPhase() {
        return prefs.get(KEY_PHASE, "");
    }

    public static void setPhase(String val) {
        prefs.put(KEY_PHASE, val == null ? "" : val);
    }

    public static int getCircular() {
        return prefs.getInt(KEY_CIRCULAR, 0);
    }

    public static void setCircular(int val) {
        prefs.putInt(KEY_CIRCULAR, val);
    }

    public static boolean getReversed() {
        return prefs.getBoolean(KEY_REVERSED, false);
    }

    public static void setReversed(boolean val) {
        prefs.putBoolean(KEY_REVERSED, val);
    }

    public static boolean getDoorLeft() {
        return prefs.getBoolean(KEY_DOOR_LEFT, true);
    }

    public static void setDoorLeft(boolean val) {
        prefs.putBoolean(KEY_DOOR_LEFT, val);
    }

    public static String getRouteColor() {
        return prefs.get(KEY_ROUTE_COLOR, "00A0E9");
    }

    public static void setRouteColor(String val) {
        prefs.put(KEY_ROUTE_COLOR, val == null ? "00A0E9" : val);
    }

    public static File getLastDir() {
        String path = prefs.get(KEY_LAST_DIR, null);
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) return f.isDirectory() ? f : f.getParentFile();
        }
        return null;
    }

    public static void setLastDir(File dirOrFile) {
        if (dirOrFile != null) {
            File d = dirOrFile.isDirectory() ? dirOrFile : dirOrFile.getParentFile();
            if (d != null && d.exists()) {
                prefs.put(KEY_LAST_DIR, d.getAbsolutePath());
            }
        }
    }

    /** 刷新偏好设置到磁盘 */
    public static void flush() {
        try {
            prefs.flush();
        } catch (Exception ignored) {}
    }
}
