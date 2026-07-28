package lcdviewer.ante;

import lcdviewer.mock.MockTrain;
import lcdviewer.mock.MtrWorld;
import lcdviewer.pack.ResourcePack;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 用 Nashorn 运行 LCD 脚本，并模拟 ANTE 注入的全局环境。
 *
 * 渲染结果的获取方式：脚本通过 DisplayHelper/DisplayHelperRE 创建 GraphicsTexture，
 * 我们记录所有被创建的 GraphicsTexture，其 BufferedImage 就是真实的 LCD 画面。
 */
public final class ScriptEngineHost implements AutoCloseable {

    private ScriptEngine engine;
    private final ResourcePack.Stack packs;
    private final Consumer<String> logger;

    /** 脚本创建的全部动态贴图（按创建顺序）。 */
    public final List<AnteUtils.GraphicsTexture> textures = new ArrayList<>();

    /** 已 include 的资源，避免重复执行。 */
    private final Set<String> included = new HashSet<>();

    private String currentScript = "?";
    private Object stateObj;
    private MockTrain train;

    public ScriptEngineHost(ResourcePack.Stack packs, Consumer<String> logger) {
        this.packs = packs;
        this.logger = logger == null ? s -> {
        } : logger;
    }

    // --------------------------------------------------------------- 初始化

    public void init() throws Exception {
        activeHost = this;
        Resources.bind(packs);
        AnteUtils.Timing.reset();
        textures.clear();
        included.clear();

        engine = newNashornEs6();
        if (engine == null) {
            throw new IllegalStateException("找不到 Nashorn 脚本引擎，请确认 nashorn-core 依赖已在 classpath 中。");
        }

        Bindings b = engine.getBindings(ScriptContext.ENGINE_SCOPE);

        // 让 Java 类可在 JS 里直接 new
        engine.eval("var Resources = Java.type('lcdviewer.ante.Resources');");
        engine.eval("var RateLimit = Java.type('lcdviewer.ante.AnteUtils$RateLimit');");
        engine.eval("var StateTracker = Java.type('lcdviewer.ante.AnteUtils$StateTracker');");
        engine.eval("var CycleTracker = Java.type('lcdviewer.ante.AnteUtils$CycleTracker');");
        engine.eval("var TextUtil = Java.type('lcdviewer.ante.AnteUtils$TextUtil');");
        engine.eval("var Timing = Java.type('lcdviewer.ante.AnteUtils$Timing');");

        engine.eval("var RawMeshBuilder = Java.type('lcdviewer.ante.AnteModel$RawMeshBuilder');");
        // RawModel / ModelCluster 涉及大量 3D 模型变换方法，且与 LCD 画面无关。
        // Nashorn 无 ES6 Proxy，改用其原生 JSAdapter：未知方法一律返回自身。
        engine.eval(
                "function __chainable() {\n" +
                "  var self = new JSAdapter({\n" +
                "    __get__: function(name) { return function() { return self; }; },\n" +
                "    __has__: function(name) { return true; },\n" +
                "    __call__: function(name) { return self; }\n" +
                "  });\n" +
                "  return self;\n" +
                "}\n" +
                "var RawModel = function() { return __chainable(); };\n" +
                "var RawMesh = function() { return __chainable(); };");
        engine.eval(
                "var ModelManager = {\n" +
                "  uploadVertArrays: function(){ return __chainable(); },\n" +
                "  loadRawModel: function(){ return __chainable(); },\n" +
                "  uploadModel: function(){ return __chainable(); }\n" +
                "};");
        engine.eval("var Matrices = Java.type('lcdviewer.ante.AnteModel$Matrices');");
        engine.eval("var Vector3f = Java.type('lcdviewer.ante.AnteModel$Vector3f');");
        engine.eval("var Matrix4f = Java.type('lcdviewer.ante.AnteModel$Matrices');");

        engine.eval("var MTRClientData = Java.type('lcdviewer.mock.MtrWorld$MTRClientData');");
        engine.eval("var Optional = Java.type('java.util.Optional');");

        // GraphicsTexture 需要记录实例，用包装构造函数
        b.put("__host", this);
        engine.eval(
                "var GraphicsTexture = function(w, h) { return __host.createTexture(w, h); };");

        // include / print / asJavaArray
        engine.eval(
                "function include(res) { __host.include(res); }\n" +
                "function print(msg) { __host.print(String(msg)); }\n" +
                "function asJavaArray(arr) { return Java.to(arr); }\n");

        // ANTE 的做法：load("nashorn:mozilla_compat.js") 提供 importPackage/importClass
        try {
            engine.eval("load('nashorn:mozilla_compat.js');");
        } catch (Exception ignored) {
        }
        // 兜底：若 mozilla_compat 不可用，则自己实现 importPackage/importClass
        engine.eval(
                "if (typeof importPackage === 'undefined') {\n" +
                "  var __pkgs = [];\n" +
                "  var importPackage = function(pkg) { __pkgs.push(String(pkg)); };\n" +
                "  var importClass = function(cls) {};\n" +
                "}\n");

        // 显式注入 LCD 脚本用到的 java.awt 类（importPackage 在 Nashorn 里对
        // 未被 Java.type 解析的裸类名支持不稳定，直接绑定最可靠）
        engine.eval(
                "var Color = Java.type('java.awt.Color');\n" +
                "var Font = Java.type('java.awt.Font');\n" +
                "var BasicStroke = Java.type('java.awt.BasicStroke');\n" +
                "var Polygon = Java.type('java.awt.Polygon');\n" +
                "var Rectangle = Java.type('java.awt.Rectangle');\n" +
                "var RenderingHints = Java.type('java.awt.RenderingHints');\n" +
                "var AlphaComposite = Java.type('java.awt.AlphaComposite');\n" +
                "var LinearGradientPaint = Java.type('java.awt.LinearGradientPaint');\n" +
                "var RadialGradientPaint = Java.type('java.awt.RadialGradientPaint');\n" +
                "var GradientPaint = Java.type('java.awt.GradientPaint');\n" +
                "var MultipleGradientPaint = Java.type('java.awt.MultipleGradientPaint');\n" +
                "var TexturePaint = Java.type('java.awt.TexturePaint');\n" +
                "var BufferedImage = Java.type('java.awt.image.BufferedImage');\n" +
                "var AffineTransform = Java.type('java.awt.geom.AffineTransform');\n" +
                "var Area = Java.type('java.awt.geom.Area');\n" +
                "var Ellipse2D = Java.type('java.awt.geom.Ellipse2D');\n" +
                "var Line2D = Java.type('java.awt.geom.Line2D');\n" +
                "var Path2D = Java.type('java.awt.geom.Path2D');\n" +
                "var Rectangle2D = Java.type('java.awt.geom.Rectangle2D');\n" +
                "var RoundRectangle2D = Java.type('java.awt.geom.RoundRectangle2D');\n" +
                "var Arc2D = Java.type('java.awt.geom.Arc2D');\n" +
                "var GeneralPath = Java.type('java.awt.geom.GeneralPath');\n" +
                "var FontRenderContext = Java.type('java.awt.font.FontRenderContext');\n" +
                "var TextLayout = Java.type('java.awt.font.TextLayout');\n" +
                "var GlyphVector = Java.type('java.awt.font.GlyphVector');\n");

        // 注入 ES6 降级所需的运行时辅助
        engine.eval(JsCompat.RUNTIME_HELPERS);

        // ANTE 的一些常量
        b.put("SIDE", "client");
        b.put("MOD_ENV", "lcdviewer");
        engine.eval("var CONFIG_INFO = {};");

        // 生命周期函数注册表：每个脚本的 create/render/dispose 都收集起来依次调用
        engine.eval("var __fns = { create: [], render: [], dispose: [] };" +
                    "var __fnSrc = { create: [], render: [], dispose: [] };");

        // Graphics2D 重载消歧：脚本会直接 new BufferedImage(...).createGraphics()，
        // 这里把 createGraphics 的结果统一包成 SafeGraphics。
        engine.eval(
                "var __SafeG = Java.type('lcdviewer.ante.SafeGraphics');\n" +
                "function __wrapG(g) { return (g instanceof __SafeG) ? g : new __SafeG(g); }\n");;

        // ctx：只需 drawCarModel / drawConnModel / setDebugInfo 等不报错
        engine.eval(
                "var __ctx = {\n" +
                "  drawCarModel: function(){}, drawConnModel: function(){},\n" +
                "  drawConnStretchTexture: function(){},\n" +
                "  playCarSound: function(){}, playAnnSound: function(){},\n" +
                "  setDebugInfo: function(){}, drawCalls: null, train: null\n" +
                "};");

        // DisplayHelper 稳健全局挂载：使用 __wrapDisplayHelper 包装，防止任何脚本覆写为 Plain Object
        engine.eval(
                "try {\n" +
                "  Object.defineProperty(this, 'DisplayHelper', {\n" +
                "    get: function() { return __dhCurrent; },\n" +
                "    set: function(v) { __dhCurrent = __wrapDisplayHelper(v); },\n" +
                "    configurable: true\n" +
                "  });\n" +
                "} catch(e) {\n" +
                "  var DisplayHelper = __dhCurrent;\n" +
                "}\n");

        // ClientConfig 桩
        engine.eval(
                "var ClientConfig = { register: function(){}, get: function(){ return null; }, save: function(){} };\n" +
                "var ComponentUtil = { literal: function(s){ return s; }, translatable: function(s){ return s; } };\n");
    }

    // ------------------------------------------------------- JS 调用的方法

    public static volatile ScriptEngineHost activeHost;

    public void registerTexture(AnteUtils.GraphicsTexture t) {
        if (t != null && !textures.contains(t)) {
            textures.add(t);
        }
    }

    /** 供 JS 的 GraphicsTexture 构造函数调用。 */
    public AnteUtils.GraphicsTexture createTexture(Object w, Object h) {
        int iw = (int) Math.round(toDouble(w));
        int ih = (int) Math.round(toDouble(h));
        AnteUtils.GraphicsTexture t = new AnteUtils.GraphicsTexture(iw, ih);
        textures.add(t);
        return t;
    }

    public void print(String msg) {
        logger.accept(msg);
    }

    /** 对应 ANTE 的 include()：支持相对文件名与 Resources.id 对象。 */
    public void include(Object res) {
        ResourceId rid;
        if (res instanceof ResourceId) {
            rid = (ResourceId) res;
        } else {
            // 相对当前脚本目录
            rid = Resources.idr(String.valueOf(res));
        }
        String key = rid.toString();
        if (included.contains(key)) return;
        included.add(key);
        runScript(rid);
    }

    /** 执行一个脚本资源，并维护脚本目录上下文（供 idr 使用）。 */
    public void runScript(ResourceId rid) {
        byte[] data;
        try {
            // 通过 Resources 读取，才能同时看到真实资源包与程序生成的虚拟 route.js。
            data = Resources.readBytes(rid);
        } catch (Exception ex) {
            throw new RuntimeException("Script not found: " + rid + " (" + rid.assetPath() + ")");
        }
        String src = JsCompat.transform(new String(data, java.nio.charset.StandardCharsets.UTF_8));
        currentScript = rid.path.substring(rid.path.lastIndexOf('/') + 1);
        Resources.pushScriptDir(rid.namespace, rid.dir());
        try {
            engine.eval(src);
            collectLifecycleFunctions();
        } catch (Exception e) {
            throw new RuntimeException("脚本执行失败 " + rid + ": " + rootMessage(e), e);
        } finally {
            Resources.popScriptDir();
        }
    }

    /**
     * 每个脚本执行完后立即收走 create/render/dispose，然后删除全局同名函数。
     *
     * 一辆车的 script_files 常包含多个脚本（LCD、受电弓、车轮……），
     * 它们各自定义同名生命周期函数。ANTE 会全部收集并依次调用，
     * 若不这样做，后加载的脚本会覆盖前面的 LCD 渲染逻辑。
     */
    private void collectLifecycleFunctions() {
        for (String fn : LIFECYCLE) {
            try {
                Object isFn = engine.eval("(typeof " + fn + " === 'function')");
                if (Boolean.TRUE.equals(isFn)) {
                    engine.eval("__fns['" + fn + "'].push(" + fn + "); "
                            + "__fnSrc['" + fn + "'].push('" + currentScript + "'); "
                            + fn + " = undefined;");
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static final String[] LIFECYCLE = {"create", "render", "dispose"};

    // --------------------------------------------------------- 生命周期调用

    /** 先执行 script_texts（车型级变量定义，如 bogieDistance）。 */
    public void loadScriptTexts(List<String> texts) {
        if (texts == null) return;
        for (String t : texts) {
            try {
                engine.eval(JsCompat.transform(t));
            } catch (Exception e) {
                logger.accept("script_texts 执行失败: " + rootMessage(e));
            }
        }
    }

    /** 加载入口脚本列表（对应 custom_trains.script_files）。 */
    public void load(List<String> scriptFiles) {
        for (String s : scriptFiles) {
            ResourceId rid = ResourceId.parse(s);
            String key = rid.toString();
            if (included.contains(key)) continue;
            included.add(key);
            runScript(rid);
        }
    }

    public void callCreate(MockTrain train) throws Exception {
        this.train = train;
        Bindings b = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        b.put("__train", train);
        engine.eval(
                "function __createSafeDhInstance() {\n" +
                "  var tex = Resources.createTexture(1024, 1024);\n" +
                "  var g = tex.createGraphics();\n" +
                "  return {\n" +
                "    texture: tex,\n" +
                "    graphics: g,\n" +
                "    model: { copyForMaterialChanges: function(){ return this; }, replaceAllTexture: function(){} },\n" +
                "    close: function(){},\n" +
                "    graphicsFor: function(s) { return g; }\n" +
                "  };\n" +
                "}\n" +
                "var __rawState = {};\n" +
                "try {\n" +
                "  var __state = new Proxy(__rawState, {\n" +
                "    get: function(target, prop) {\n" +
                "      if (prop in target) return target[prop];\n" +
                "      if (typeof prop === 'string' && (prop === 'dh' || prop.indexOf('dh') >= 0 || prop.indexOf('Dh') >= 0 || prop.indexOf('display') >= 0)) {\n" +
                "        target[prop] = __createSafeDhInstance();\n" +
                "        return target[prop];\n" +
                "      }\n" +
                "      return target[prop];\n" +
                "    }\n" +
                "  });\n" +
                "} catch(e) {\n" +
                "  var __state = __rawState;\n" +
                "}\n");
        engine.eval("__ctx.train = __train;");
        callAll("create");
    }

    public void callRender(MockTrain train) throws Exception {
        this.train = train;
        Bindings b = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        b.put("__train", train);
        callAll("render");
    }

    /**
     * 依次调用所有脚本注册的同名函数；单个失败不中断其他。
     * 错误日志带上脚本名与堆栈首行，便于定位。
     */
    private void callAll(String fn) throws Exception {
        engine.eval(
                "(function(){\n" +
                "  var a = __fns['" + fn + "'] || [];\n" +
                "  var src = __fnSrc['" + fn + "'] || [];\n" +
                "  for (var i = 0; i < a.length; i++) {\n" +
                "    try { a[i](__ctx, __state, __train); }\n" +
                "    catch (e) {\n" +
                "      var where = '';\n" +
                "      try {\n" +
                "        if (e && e.stack) {\n" +
                "          var ln = String(e.stack).split('\\n');\n" +
                "          where = ln.length > 1 ? '  @ ' + ln[1].trim() : '';\n" +
                "        }\n" +
                "      } catch (e2) {}\n" +
                "      __host.print('[" + fn + "] ' + (src[i] || i) + ': ' + e + where);\n" +
                "    }\n" +
                "  }\n" +
                "})();");
    }

    /** 运行中已注册的生命周期函数数量，用于诊断。 */
    public int functionCount(String fn) {
        try {
            Object n = engine.eval("(__fns['" + fn + "'] || []).length");
            return n instanceof Number ? ((Number) n).intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public void callDispose(MockTrain train) {
        try {
            if (hasFunction("dispose")) {
                engine.getBindings(ScriptContext.ENGINE_SCOPE).put("__train", train);
                engine.eval("dispose(__ctx, __state, __train);");
            }
        } catch (Exception ignored) {
        }
    }

    public boolean hasFunction(String name) {
        try {
            Object r = engine.eval("(typeof " + name + " === 'function')");
            return Boolean.TRUE.equals(r);
        } catch (Exception e) {
            return false;
        }
    }

    public Object eval(String js) throws Exception {
        return engine.eval(js);
    }

    /** 返回目前所有非空白的动态贴图画面。 */
    public Map<String, BufferedImage> capture() {
        Map<String, BufferedImage> out = new LinkedHashMap<>();
        int i = 0;
        for (AnteUtils.GraphicsTexture t : textures) {
            out.put("Texture#" + (i++) + " (" + t.getWidth() + "x" + t.getHeight() + ")", t.image);
        }
        return out;
    }

    /** 创建启用 ES6 的 Nashorn 引擎（LCD 脚本使用 let/const/for-of/模板字符串）。 */
    private static ScriptEngine newNashornEs6() {
        String[] opts = {"--language=es6", "--no-java", "-strict"};
        try {
            Class<?> f = Class.forName("org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory");
            Object factory = f.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method m = f.getMethod("getScriptEngine", String[].class);
            return (ScriptEngine) m.invoke(factory, (Object) new String[]{"--language=es6"});
        } catch (Throwable ignored) {
        }
        try {
            Class<?> f = Class.forName("jdk.nashorn.api.scripting.NashornScriptEngineFactory");
            Object factory = f.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method m = f.getMethod("getScriptEngine", String[].class);
            return (ScriptEngine) m.invoke(factory, (Object) new String[]{"--language=es6"});
        } catch (Throwable ignored) {
        }
        ScriptEngineManager mgr = new ScriptEngineManager(ScriptEngineHost.class.getClassLoader());
        ScriptEngine e = mgr.getEngineByName("nashorn");
        if (e == null) e = mgr.getEngineByName("javascript");
        return e;
    }
    static double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    public static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String m = t.getMessage();
        return m == null ? t.toString() : m;
    }

    @Override
    public void close() {
        for (AnteUtils.GraphicsTexture t : textures) {
            try {
                t.close();
            } catch (Exception ignored) {
            }
        }
        textures.clear();
        engine = null;
    }
}
