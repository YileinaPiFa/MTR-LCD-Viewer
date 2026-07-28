package lcdviewer.mock;

import lcdviewer.mock.MtrWorld.CircularState;
import lcdviewer.mock.MtrWorld.MTRClientData;
import lcdviewer.mock.MtrWorld.PathData;
import lcdviewer.mock.MtrWorld.Platform;
import lcdviewer.mock.MtrWorld.PlatformInfo;
import lcdviewer.mock.MtrWorld.Route;
import lcdviewer.mock.MtrWorld.RoutePlatform;
import lcdviewer.mock.MtrWorld.Station;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 构建一条用于预览的模拟线路，并驱动列车状态。
 */
public final class Scenario {

    /** 列车运行阶段，对应脚本里的 getTrainStatus 结果。 */
    public enum Phase {
        WAITING_FOR_DEPARTURE("待发"),
        ON_ROUTE("运行"),
        ARRIVED("到站"),
        RETURNING_TO_DEPOT("回库"),
        NO_ROUTE("无线路");

        public final String label;

        Phase(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public String routeName = "";
    public int routeColor = 0x00A0E9;
    public CircularState circularState = CircularState.NONE;
    public List<String> stationNames = new ArrayList<>();
    public List<List<String>> stationExits = new ArrayList<>();

    public int trainCars = 6;
    public boolean reversed = false;
    public int nextStationIndex = 1;
    public Phase phase = Phase.ON_ROUTE;
    public float doorValue = 0f;
    public boolean doorLeft = true;

    private final List<Station> stations = new ArrayList<>();
    private final List<Platform> platforms = new ArrayList<>();
    private Route route;

    public Scenario() {
    }

    /** 构造世界数据并返回一个配置好的 MockTrain。 */
    public MockTrain build() {
        stations.clear();
        platforms.clear();
        MTRClientData.DATA_CACHE = new MtrWorld.DataCache();

        // 确保站名列表至少有 2 个站点，避免数组越界崩溃
        List<String> activeNames = new ArrayList<>(stationNames);
        if (activeNames.size() < 2) {
            activeNames.clear();
            activeNames.add("广州南站|Guangzhou South Railway Station");
            activeNames.add("石壁|Shibi");
            activeNames.add("会江|Huijiang");
            activeNames.add("南浦|Nanpu");
            activeNames.add("洛溪|Luoxi");
        }
        String activeRouteName = (routeName != null && !routeName.trim().isEmpty())
                ? routeName.trim()
                : "2号线|Line 2";

        route = new Route(1001L, activeRouteName, routeColor);
        route.circularState = circularState;

        int n = activeNames.size();
        for (int i = 0; i < n; i++) {
            long sid = 2000L + i;
            long pid = 3000L + i;
            Station st = new Station(sid, activeNames.get(i));
            st.color = routeColor;
            List<String> ex = i < stationExits.size() ? stationExits.get(i) : Arrays.asList("A", "B");
            for (String e : ex) {
                st.exits.put(e, new ArrayList<>(Arrays.asList(e + "1")));
            }
            Platform pf = new Platform(pid, String.valueOf(i + 1));
            pf.dwellTime = 200;

            stations.add(st);
            platforms.add(pf);

            route.platformIds.add(new RoutePlatform(pid));

            MTRClientData.DATA_CACHE.stationIdMap.put(sid, st);
            MTRClientData.DATA_CACHE.platformIdMap.put(pid, pf);
            MTRClientData.DATA_CACHE.platformIdToStation.put(pid, st);
        }
        MTRClientData.DATA_CACHE.routeIdMap.put(route.id, route);

        // 换乘线路表。脚本的 getAllInterchangeRoutes() 会遍历它并读取每条
        // 线路的 name/color/circularState；此表不完整就会报
        // Cannot read property "name" from undefined。
        buildInterchanges(n);

        // 车厂 / 停车线（脚本用 train.siding().id 反查车厂名）
        MtrWorld.Depot depot = new MtrWorld.Depot(8001L, "车辆段|Depot");
        MtrWorld.Siding siding = new MtrWorld.Siding(9001L, "01号停车线");
        MTRClientData.DATA_CACHE.sidingIdMap.put(siding.id, siding);
        MTRClientData.DATA_CACHE.sidingIdMap.put((double) siding.id, siding);
        MTRClientData.DATA_CACHE.sidingIdToDepot.put(siding.id, depot);
        MTRClientData.DATA_CACHE.sidingIdToDepot.put((double) siding.id, depot);

        MockTrain train = new MockTrain();
        train.depot = depot;
        train.siding = siding;
        train.trainCars = Math.max(1, trainCars);
        train.isReversed = reversed;
        train.syncDoorArrays();

        Station terminal = stations.get(n - 1);

        for (int i = 0; i < n; i++) {
            PlatformInfo pi = new PlatformInfo();
            pi.route = route;
            pi.station = stations.get(i);
            pi.platform = platforms.get(i);
            pi.destinationStation = terminal;
            pi.destinationName = terminal.name;
            pi.distance = 100.0 * (i + 1);
            train.allPlatforms.add(pi);
            train.thisRoutePlatforms.add(pi);
        }

        // path：给两条轨道，站台轨 dwellTime != 0
        int idx = clampIndex(nextStationIndex, n);
        PathData onPlatform = new PathData();
        onPlatform.dwellTime = 200;
        onPlatform.savedRailBaseId = platforms.get(idx).id;
        PathData onLine = new PathData();
        onLine.dwellTime = 0;
        onLine.savedRailBaseId = -1;

        train.path.clear();

        switch (phase) {
            case NO_ROUTE:
                train.allPlatforms.clear();
                train.thisRoutePlatforms.clear();
                train.isOnRoute = false;
                train.path.add(onLine);
                break;
            case WAITING_FOR_DEPARTURE:
                train.isOnRoute = false;
                train.allPlatformsNextIndex = 0;
                train.thisRoutePlatformsNextIndex = 0;
                train.path.add(onLine);
                break;
            case RETURNING_TO_DEPOT:
                train.isOnRoute = true;
                train.allPlatformsNextIndex = n;   // == size() 触发回库
                train.thisRoutePlatformsNextIndex = n;
                train.path.add(onLine);
                break;
            case ARRIVED:
                train.isOnRoute = true;
                train.allPlatformsNextIndex = idx;
                train.thisRoutePlatformsNextIndex = idx;
                train.path.add(onPlatform);
                train.speed = 0f;
                train.doorValue = doorValue;
                train.isDoorOpening = doorValue > 0 && doorValue < 1;
                train.setDoorSide(doorLeft, !doorLeft);
                break;
            case ON_ROUTE:
            default:
                train.isOnRoute = true;
                train.allPlatformsNextIndex = Math.max(1, idx);
                train.thisRoutePlatformsNextIndex = Math.max(1, idx);
                train.path.add(onLine);
                train.speed = 1.5f;
                break;
        }
        if (train.path.isEmpty()) train.path.add(onLine);
        train.railProgress = 100.0 * (idx + 1) - 5;
        return train;
    }

    private static int clampIndex(int i, int n) {
        if (n <= 0) return 0;
        return Math.max(0, Math.min(i, n - 1));
    }

    /** 为部分车站生成换乘线路，使 LCD 的换乘标记有真实内容可画。 */
    private void buildInterchanges(int n) {
        String[][] presets = {
                {"1号线|Line 1", "E60012"},
                {"2号线|Line 2", "0072BC"},
                {"3号线|Line 3", "F7941E"},
                {"5号线|Line 5", "92278F"},
                {"8号线|Line 8", "00A79D"},
        };
        long nextId = 1100L;
        for (int i = 0; i < n; i++) {
            Station st = stations.get(i);
            java.util.Map<Long, Route> rs = new java.util.LinkedHashMap<>();
            rs.put(route.id, route);             // 本线必须包含在内
            if (i > 0 && i < n - 1 && i % 2 == 1) {
                String[] pr = presets[(i / 2) % presets.length];
                Route ic = new Route(nextId++, pr[0], (int) Long.parseLong(pr[1], 16));
                ic.circularState = MtrWorld.CircularState.NONE;
                ic.platformIds.add(new RoutePlatform(3000L + i));
                MTRClientData.DATA_CACHE.routeIdMap.put(ic.id, ic);
                rs.put(ic.id, ic);
            }
            MTRClientData.DATA_CACHE.stationIdToRoutes.put(st.id, rs);
            MTRClientData.DATA_CACHE.stationIdToConnectingStations.put(st, new ArrayList<>());
        }
    }

    /**
     * 生成与 LCD DLC 完全相同格式的 route.js。
     * 资源包本身没有 assets/mtr/lcd_config/route.js 时，程序会注入此内容，
     * 因此 LCD 能读取当前面板里的线路、站名、编号、设施与出口，而不是显示“无线路信息”。
     */
    public String toRouteJs() {
        List<String> list = new ArrayList<>(stationNames);
        if (list.size() < 2) {
            list.clear();
            list.add("广州南站|Guangzhou South Railway Station");
            list.add("石壁|Shibi");
            list.add("会江|Huijiang");
            list.add("南浦|Nanpu");
            list.add("洛溪|Luoxi");
        }
        String rn = (routeName != null && !routeName.trim().isEmpty()) ? routeName.trim() : "2号线|Line 2";

        StringBuilder s = new StringBuilder();
        s.append("var RoutePlatformInfos = [{\n");
        s.append("  routeName: \"").append(js(rn)).append("\",\n");
        s.append("  routeNumber: \"").append(js(routeNumber(rn))).append("\",\n");
        s.append("  routeType: null,\n  stationList: [\n");
        for (int i = 0; i < list.size(); i++) {
            s.append("    { stationName: \"").append(js(list.get(i))).append("\",");
            s.append(" stationNumber: \"").append(String.format("%02d", i + 1)).append("\",");
            s.append(" stationFacilities: [[\"Escalator\",0.20,false],[\"Stair\",0.42,false],[\"Elevator\",0.68,false]],");
            List<String> ex = i < stationExits.size() ? stationExits.get(i) : Arrays.asList("A", "B");
            s.append(" stationExit: [");
            for (int j = 0; j < ex.size(); j++) {
                if (j > 0) s.append(',');
                double x = ex.size() <= 1 ? 0.5 : 0.12 + 0.76 * j / (ex.size() - 1.0);
                s.append("[\"").append(js(ex.get(j))).append("\",").append(String.format(java.util.Locale.ROOT, "%.3f", x)).append(']');
            }
            s.append("] }");
            if (i + 1 < list.size()) s.append(',');
            s.append('\n');
        }
        s.append("  ]\n}];\n");
        return s.toString();
    }

    private String routeNumber(String rn) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]+)").matcher(rn);
        return m.find() ? m.group(1) : "2";
    }

    private static String js(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    public Route route() {
        return route;
    }
}
