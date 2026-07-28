package lcdviewer.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟 MTR 的世界数据（线路 / 车站 / 站台），供 LCD 脚本读取。
 * 字段命名与 MTR 源码保持一致，脚本才能直接访问。
 */
public final class MtrWorld {

    // ------------------------------------------------------------ Station

    public static final class Station {
        public long id;
        public String name;
        public int color;
        /** MTR 中 exits 是 Map<String, List<String>> */
        public final Map<String, List<String>> exits = new LinkedHashMap<>();

        public Station(long id, String name) {
            this.id = id;
            this.name = name;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Map<String, List<String>> getExits() {
            return exits;
        }
    }

    // ----------------------------------------------------------- Platform

    public static final class Platform {
        public long id;
        public String name;
        /** 单位 tick */
        public int dwellTime = 200;

        public Platform(long id, String name) {
            this.id = id;
            this.name = name;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getDwellTime() {
            return dwellTime;
        }
    }

    /** Route.platformIds 的元素 */
    public static final class RoutePlatform {
        public long platformId;
        public String customDestination;

        public RoutePlatform(long platformId) {
            this.platformId = platformId;
        }

        public long getPlatformId() {
            return platformId;
        }
    }

    // -------------------------------------------------------------- Route

    /** 对应 MTR Route.CircularState 枚举。 */
    public enum CircularState {
        NONE, CLOCKWISE, ANTICLOCKWISE;

        @Override
        public String toString() {
            return name();
        }
    }

    public static final class Route {
        public long id;
        public String name;
        public int color;
        public boolean isHidden = false;
        public CircularState circularState = CircularState.NONE;
        public final List<RoutePlatform> platformIds = new ArrayList<>();

        public Route(long id, String name, int color) {
            this.id = id;
            this.name = name;
            this.color = color;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getColor() {
            return color;
        }

        public CircularState getCircularState() {
            return circularState;
        }

        public List<RoutePlatform> getPlatformIds() {
            return platformIds;
        }
    }

    // --------------------------------------------------------------- Depot

    public static final class Depot {
        public long id;
        public String name;

        public Depot(long id, String name) {
            this.id = id;
            this.name = name;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    /** 停车线。train.siding() 返回它，脚本再用 siding.id 反查车厂。 */
    public static final class Siding {
        public long id;
        public String name;

        public Siding(long id, String name) {
            this.id = id;
            this.name = name;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    // -------------------------------------------------------- PlatformInfo

    /** 对应 ANTE 文档中的 PlatformInfo。 */
    public static final class PlatformInfo {
        public Route route;
        public Station station;
        public Platform platform;
        public Station destinationStation;
        public String destinationName;
        public double distance;
        public boolean reverseAtPlatform = false;

        public Route getRoute() {
            return route;
        }

        public Station getStation() {
            return station;
        }

        public Platform getPlatform() {
            return platform;
        }

        public Station getDestinationStation() {
            return destinationStation;
        }

        public String getDestinationName() {
            return destinationName;
        }

        public double getDistance() {
            return distance;
        }
    }

    // ----------------------------------------------------------- PathData

    public static final class PathData {
        public int dwellTime = 0;
        public long savedRailBaseId = 0;
        public Rail rail = new Rail();

        public int getDwellTime() {
            return dwellTime;
        }

        public long getSavedRailBaseId() {
            return savedRailBaseId;
        }
    }

    public static final class Rail {
        public String railType = "IRON";

        public String getModelKey() {
            return "";
        }

        public double getLength() {
            return 100;
        }

        /** 轨道上某处的坐标。静态预览用直线近似即可。 */
        public Vec3 getPosition(double distance) {
            return new Vec3(0, 0, distance);
        }

        public float getRollAngle(double distance) {
            return 0f;
        }
    }

    /** 简单三维坐标，对应 MTR 的 Vec3。 */
    public static final class Vec3 {
        public final double x, y, z;

        public Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }

        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
    }

    // -------------------------------------------------------- DATA_CACHE

    /** 对应脚本使用的 MTRClientData.DATA_CACHE */
    public static final class DataCache {
        public final Map<Long, Route> routeIdMap = new LinkedHashMap<>();
        public final Map<Long, Platform> platformIdMap = new LinkedHashMap<>();
        public final Map<Long, Station> platformIdToStation = new LinkedHashMap<>();
        public final Map<Long, Station> stationIdMap = new LinkedHashMap<>();
        /** value 脚本用 .values() 遍历，必须是 Map。 */
        public final Map<Long, Map<Long, Route>> stationIdToRoutes = new LinkedHashMap<>();
        public final Map<Object, Siding> sidingIdMap = new LinkedHashMap<>();
        /** Object key：同时放 Long 与 Double，兼容 Nashorn 的 JS number 比较。 */
        public final Map<Object, Depot> sidingIdToDepot = new LinkedHashMap<>();
        public final Map<Long, List<Route>> requestPlatformIdToRoutes = new LinkedHashMap<>();
        public final Map<Long, List<Platform>> requestStationIdToPlatforms = new LinkedHashMap<>();
        /** key 是 Station 对象本身（脚本按对象取），value 需支持 forEach。 */
        public final Map<Object, List<Station>> stationIdToConnectingStations = new LinkedHashMap<>();

        public Map<Long, Route> getRouteIdMap() {
            return routeIdMap;
        }

        public Map<Long, Platform> getPlatformIdMap() {
            return platformIdMap;
        }

        public Map<Long, Station> getPlatformIdToStation() {
            return platformIdToStation;
        }
    }

    /** 脚本里写 MTRClientData.DATA_CACHE.xxx */
    public static final class MTRClientData {
        public static DataCache DATA_CACHE = new DataCache();

        public static DataCache getDATA_CACHE() {
            return DATA_CACHE;
        }
    }
}
