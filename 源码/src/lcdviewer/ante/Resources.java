package lcdviewer.ante;

import lcdviewer.pack.ResourcePack;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 模拟 ANTE 注入 JS 的 Resources 全局对象。
 *
 * 关键语义（与 ANTE 一致）：
 *  - Resources.id("mtr:xxx")  绝对资源 ID
 *  - Resources.idr("a.png")   相对于「当前正在执行的脚本所在目录」
 *  - 读取失败必须抛异常（脚本用 try/catch 探测文件是否存在）
 */
public final class Resources {

    private static ResourcePack.Stack stack;

    /** 当前脚本目录栈，供 idr() 相对解析。 */
    private static final Deque<String> scriptDirs = new ArrayDeque<>();

    private static final Map<String, Font> fontCache = new HashMap<>();
    private static final Map<String, BufferedImage> imageCache = new HashMap<>();
    /** 程序生成的虚拟资源，仅在真实资源不存在时使用。 */
    private static final Map<String, byte[]> virtualResources = new HashMap<>();

    private Resources() {
    }

    public static void bind(ResourcePack.Stack s) {
        stack = s;
        fontCache.clear();
        imageCache.clear();
        virtualResources.clear();
        FontFallback.reset();
    }

    public static ResourcePack.Stack stack() {
        return stack;
    }

    // ------------------------------------------------- 脚本目录上下文

    public static void pushScriptDir(String namespace, String dir) {
        scriptDirs.push(namespace + "|" + dir);
    }

    public static void popScriptDir() {
        if (!scriptDirs.isEmpty()) scriptDirs.pop();
    }

    private static String currentNamespace() {
        String cur = scriptDirs.peek();
        if (cur == null) return "mtr";
        return cur.substring(0, cur.indexOf('|'));
    }

    private static String currentDir() {
        String cur = scriptDirs.peek();
        if (cur == null) return "";
        return cur.substring(cur.indexOf('|') + 1);
    }

    // ------------------------------------------------- JS 可见 API

    /** Resources.id("mtr:path") —— 绝对资源 ID。 */
    public static ResourceId id(Object raw) {
        if (raw instanceof ResourceId) return (ResourceId) raw;
        return ResourceId.parse(String.valueOf(raw));
    }

    /** Resources.idr("a.png") —— 相对当前脚本目录。 */
    public static ResourceId idr(Object raw) {
        if (raw instanceof ResourceId) return (ResourceId) raw;
        String s = String.valueOf(raw).replace('\\', '/').trim();
        if (s.contains(":")) return ResourceId.parse(s);
        String dir = currentDir();
        String path = dir.isEmpty() ? s : dir + "/" + s;
        // 折叠 ../
        path = ResourcePack.normalize(path);
        return new ResourceId(currentNamespace(), path);
    }

    /** Resources.idRelative("a.png") —— idr 的完整名字别名。 */
    public static ResourceId idRelative(Object raw) {
        return idr(raw);
    }

    /** 读取字节，失败抛异常。 */
    public static byte[] readBytes(Object idOrPath) {
        ResourceId rid = toId(idOrPath);
        byte[] b = stack == null ? null : stack.read(rid.assetPath());
        if (b == null) b = virtualResources.get(rid.toString());
        
        // 1) 针对 mtrsteamloco/mtr 内置 helper 存根
        if (b == null && (rid.path.endsWith("display_helper.js") || rid.path.endsWith("mtr_util.js"))) {
            String stub = "function DisplayHelper(slotCfg) {\n" +
                          "  this.slotCfg = slotCfg;\n" +
                          "}\n" +
                          "DisplayHelper.createGraphics = function(w, h) { return Resources.createTexture(w || 1024, h || 1024); };\n" +
                          "DisplayHelper.graphicsFor = function(t) { return t ? (t.createGraphics ? t.createGraphics() : (t.graphics ? t.graphics : t)) : null; };\n" +
                          "DisplayHelper.prototype.create = function() {\n" +
                          "  var tex = Resources.createTexture(1024, 1024);\n" +
                          "  var g = tex.createGraphics();\n" +
                          "  return {\n" +
                          "    texture: tex,\n" +
                          "    graphics: g,\n" +
                          "    model: { copyForMaterialChanges: function(){ return this; }, replaceAllTexture: function(){} },\n" +
                          "    close: function(){},\n" +
                          "    graphicsFor: function(s) { return g; }\n" +
                          "  };\n" +
                          "};\n";
            return stub.getBytes(StandardCharsets.UTF_8);
        }

        // 2) 模糊全包匹配：如果 assets/mtr/xxx 找不到，按文件名全局搜索
        if (b == null && stack != null) {
            String fileName = rid.path;
            if (fileName.contains("/")) fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
            if (!fileName.isEmpty()) {
                for (String p : stack.listAll()) {
                    if (p.equalsIgnoreCase(fileName) || p.toLowerCase().endsWith("/" + fileName.toLowerCase())) {
                        b = stack.read(p);
                        if (b != null) break;
                    }
                }
            }
        }

        if (b == null) {
            // 如果仍然找不到且是 js 脚本，给一个空脚本防崩存根
            if (rid.path.endsWith(".js")) {
                return "// Stub for missing script\n".getBytes(StandardCharsets.UTF_8);
            }
            throw new RuntimeException("Resource not found: " + rid);
        }
        return b;
    }

    public static String readString(Object idOrPath) {
        return new String(readBytes(idOrPath), StandardCharsets.UTF_8);
    }

    /** 读取图片，若不存在或损坏则返回透明存根。 */
    public static BufferedImage readBufferedImage(Object idOrPath) {
        ResourceId rid = toId(idOrPath);
        String key = rid.toString();
        BufferedImage cached = imageCache.get(key);
        if (cached != null) return cached;
        try {
            byte[] data = readBytes(rid);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img != null) {
                imageCache.put(key, img);
                return img;
            }
        } catch (Exception e) {
            // 优雅防护：图片缺失时返回 64x64 透明存根，不阻断主流程
        }
        BufferedImage stub = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        imageCache.put(key, stub);
        return stub;
    }

    /** Resources.createTexture(w, h) 静态方法，供 display_helper.js 等脚本直接调用。 */
    public static AnteUtils.GraphicsTexture createTexture(Object w, Object h) {
        int iw = (int) Math.round(ScriptEngineHost.toDouble(w));
        int ih = (int) Math.round(ScriptEngineHost.toDouble(h));
        AnteUtils.GraphicsTexture t = new AnteUtils.GraphicsTexture(iw, ih);
        if (ScriptEngineHost.activeHost != null) {
            ScriptEngineHost.activeHost.registerTexture(t);
        }
        return t;
    }

    public static AnteUtils.GraphicsTexture createTexture(int w, int h) {
        return createTexture((Object) w, (Object) h);
    }

    /** Resources.readFont(id) —— 返回未设字号的 Font，脚本会 deriveFont。 */
    public static Font readFont(Object idOrPath) {
        ResourceId rid = toId(idOrPath);
        String key = rid.toString();
        Font cached = fontCache.get(key);
        if (cached != null) return cached;
        byte[] data = readBytes(rid);
        try {
            Font f = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(data));
            fontCache.put(key, f);
            FontFallback.register(f);
            return f;
        } catch (Exception e1) {
            try {
                Font f = Font.createFont(Font.TYPE1_FONT, new ByteArrayInputStream(data));
                fontCache.put(key, f);
                FontFallback.register(f);
                return f;
            } catch (Exception e2) {
                throw new RuntimeException("Failed to read font " + rid + ": " + e1.getMessage(), e1);
            }
        }
    }

    /** 注册虚拟文本资源。真实资源包同路径内容始终优先。 */
    public static void putVirtual(Object idOrPath, String content) {
        ResourceId rid = toId(idOrPath);
        virtualResources.put(rid.toString(), content.getBytes(StandardCharsets.UTF_8));
    }

    /** 供 include() 使用：资源是否存在。 */
    public static boolean exists(Object idOrPath) {
        try {
            ResourceId rid = toId(idOrPath);
            return (stack != null && stack.read(rid.assetPath()) != null)
                    || virtualResources.containsKey(rid.toString());
        } catch (Exception e) {
            return false;
        }
    }

    /** ANTE 里用于 ModelManager.loadRawModel(Resources.manager(), ...)，此处返回占位对象。 */
    public static Object manager() {
        return stack;
    }

    /** 部分脚本用 Resources.readString 读取 json 配置。 */
    public static String readJson(Object idOrPath) {
        return readString(idOrPath);
    }
    // ---------------------------------------------- ANTE 版本查询接口
    // 部分脚本（如深圳包）会按 NTE 版本走不同分支，这里报告一个较新的版本，
    // 使脚本走现代 API 路径。

    public static int getNTEVersionInt() {
        return 40000;
    }

    public static String getNTEVersion() {
        return "4.0.0";
    }

    public static int getMTRVersionInt() {
        return 30000;
    }

    public static String getMTRVersion() {
        return "3.0.0";
    }
    static ResourceId toId(Object idOrPath) {
        if (idOrPath instanceof ResourceId) return (ResourceId) idOrPath;
        return ResourceId.parse(String.valueOf(idOrPath));
    }
}
