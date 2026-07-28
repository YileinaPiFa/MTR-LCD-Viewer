package lcdviewer;

import lcdviewer.ante.AnteUtils;
import lcdviewer.ante.ScriptEngineHost;
import lcdviewer.mock.MockTrain;
import lcdviewer.mock.Scenario;
import lcdviewer.pack.LcdDiscovery;
import lcdviewer.pack.ResourcePack;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 把「加载资源包 -> 发现 LCD -> 运行脚本 -> 取画面」串起来的服务层。
 * UI 只与本类交互。
 */
public final class RenderService implements AutoCloseable {

    /** 一块可显示的画面。 */
    public static final class Surface {
        public final String name;
        public final BufferedImage image;
        public final int width;
        public final int height;

        Surface(String name, BufferedImage image) {
            this.name = name;
            this.image = image;
            this.width = image.getWidth();
            this.height = image.getHeight();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private ResourcePack.Stack stack;
    private ScriptEngineHost host;
    private LcdDiscovery.Entry current;
    private MockTrain train;

    private final List<String> log = new ArrayList<>();
    private Consumer<String> logSink = s -> {
    };

    public void setLogSink(Consumer<String> sink) {
        this.logSink = sink == null ? s -> {
        } : sink;
    }

    private void log(String s) {
        log.add(s);
        if (log.size() > 4000) log.remove(0);
        logSink.accept(s);
    }

    public List<String> logLines() {
        return log;
    }

    // ------------------------------------------------------------ 资源包

    /** 载入一组资源包（后者优先级更高，用于 DLC 覆盖）。 */
    public List<LcdDiscovery.Entry> loadPacks(List<File> files) throws Exception {
        closeStack();
        stack = new ResourcePack.Stack();
        List<File> expanded = expandFiles(files);
        for (File f : expanded) {
            ResourcePack p = ResourcePack.open(f);
            stack.add(p);
            log("载入 " + f.getName());
            for (String n : p.repairNotes) log("  · " + n);
        }
        List<LcdDiscovery.Entry> entries = LcdDiscovery.discover(stack);
        log("发现 LCD 单元 " + entries.size() + " 项");
        return entries;
    }

    private static List<File> expandFiles(List<File> input) {
        List<File> result = new ArrayList<>();
        for (File f : input) {
            if (f.isDirectory()) {
                File[] zips = f.listFiles((d, name) -> name.toLowerCase().endsWith(".zip"));
                if (zips != null && zips.length > 0) {
                    java.util.Arrays.sort(zips, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    for (File z : zips) result.add(z);
                } else {
                    result.add(f);
                }
            } else if (f.getName().toLowerCase().endsWith(".zip")) {
                result.add(f);
            }
        }
        return result;
    }

    public ResourcePack.Stack stack() {
        return stack;
    }

    // ------------------------------------------------------------ 渲染

    /** 选中某个 LCD 单元并初始化脚本环境。 */
    public void select(LcdDiscovery.Entry entry, Scenario scenario) throws Exception {
        closeHost();
        current = entry;
        host = new ScriptEngineHost(stack, this::log);
        host.init();

        // 先构建场景，再把当前线路注入为虚拟 route.js。
        // 如果用户/DLC 已提供同路径文件，Resources 会优先读取真实资源。
        train = scenario.build();
        lcdviewer.ante.Resources.putVirtual("mtr:lcd_config/route.js", scenario.toRouteJs());
        log("线路数据已挂载：" + scenario.routeName + "，" + scenario.stationNames.size() + " 站");

        List<String> texts = LcdDiscovery.SCRIPT_TEXTS.get(entry.id);
        if (texts != null && !texts.isEmpty()) host.loadScriptTexts(texts);

        long t0 = System.currentTimeMillis();
        List<String> focused = lcdScripts(entry.scriptFiles);
        host.load(focused);
        log("LCD 脚本加载完成 " + (System.currentTimeMillis() - t0) + " ms，"
                + focused.size() + " / " + entry.scriptFiles.size() + " 个文件");

        host.callCreate(train);
        log("初始化完成，动态贴图 " + host.textures.size() + " 张");
    }

    /** 用新场景重建列车状态（不重载脚本）。 */
    public void applyScenario(Scenario scenario) throws Exception {
        if (host == null) return;
        train = scenario.build();
        // 参数面板改变线路/站点后，立即刷新 JS 全局配置，不需要重载整个资源包。
        host.eval(scenario.toRouteJs());
    }

    /** 推进一帧。time 为 null 表示使用真实时间。 */
    public void step(Double time) throws Exception {
        if (host == null || train == null) return;
        AnteUtils.Timing.setForcedTime(time);
        host.callRender(train);
    }

    /**
     * 从车型 script_files 中筛出 LCD 本体。
     * 车轮、受电弓、车号、普通贯通道脚本与离线 LCD 预览无关，
     * 而且会访问真实 3D 轨道/模型数据，加载它们只会产生误导性错误。
     */
    private static List<String> lcdScripts(List<String> all) {
        List<String> out = new ArrayList<>();
        for (String raw : all) {
            String p = raw.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
            String base = p.substring(p.lastIndexOf('/') + 1);
            boolean exclude = base.contains("wheel")
                    || base.equals("pan.js")
                    || base.contains("pantograph")
                    || base.contains("doorlight")
                    || base.startsWith("num_")
                    || base.contains("bogie")
                    || base.contains("headlight")
                    || (base.startsWith("conn") && !p.contains("conn_lcd") && !p.contains("lcd_con"));
            if (!exclude && !out.contains(raw)) {
                out.add(raw);
            }
        }
        if (out.isEmpty()) out.addAll(all);
        return out;
    }

    /** 取当前所有画面，按面积从大到小（主屏在前）。 */
    public List<Surface> surfaces() {
        List<Surface> out = new ArrayList<>();
        if (host == null) return out;
        int i = 0;
        for (AnteUtils.GraphicsTexture t : host.textures) {
            out.add(new Surface(String.format("#%02d  %d × %d", i, t.getWidth(), t.getHeight()),
                    t.image));
            i++;
        }
        out.sort((a, b) -> Long.compare((long) b.width * b.height, (long) a.width * a.height));
        return out;
    }

    public LcdDiscovery.Entry current() {
        return current;
    }

    public MockTrain train() {
        return train;
    }

    // ------------------------------------------------------------ 清理

    private void closeHost() {
        if (host != null) {
            try {
                if (train != null) host.callDispose(train);
            } catch (Exception ignored) {
            }
            host.close();
            host = null;
        }
    }

    private void closeStack() {
        closeHost();
        if (stack != null) {
            stack.close();
            stack = null;
        }
    }

    @Override
    public void close() {
        closeStack();
    }
}
