package lcdviewer.pack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在资源包中发现所有可渲染的 LCD 入口。
 *
 * 主路径：解析 assets/&lt;ns&gt;/mtr_custom_resources.json 的 custom_trains[*].script_files
 *        （这是 ANTE 官方约定，见 docs/js-train.md）。
 * 回退路径：对于只含脚本、没有 json 的“移植文件”包，按脚本文件名/内容特征启发式识别
 *        入口脚本（含 function render( 且含 DisplayHelper/GraphicsTexture 等）。
 */
public final class LcdDiscovery {

    /** 一个可渲染条目。 */
    public static final class Entry {
        public final String id;             // custom_trains 的 key，或脚本路径
        public final String displayName;    // 展示名
        public final List<String> scriptFiles; // 资源 ID 形式，如 mtr:a9lcd/a9lcd/lcd_main_a9.js
        public final String source;         // 来源说明
        public final boolean heuristic;     // 是否为启发式发现

        Entry(String id, String displayName, List<String> scriptFiles, String source, boolean heuristic) {
            this.id = id;
            this.displayName = displayName;
            this.scriptFiles = scriptFiles;
            this.source = source;
            this.heuristic = heuristic;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final Gson GSON = new Gson();

    /** 判断脚本是否"疑似 LCD 主入口"用的特征。 */
    private static final Pattern RENDER_FN = Pattern.compile("(function\\s+(render|create)|(render|create)\\s*:|DisplayHelper)");
    private static final Pattern DISPLAY_API = Pattern.compile(
            "DisplayHelper|GraphicsTexture|graphicsFor\\s*\\(|drawCarModel");

    private LcdDiscovery() {
    }

    public static List<Entry> discover(ResourcePack.Stack stack) {
        List<Entry> out = new ArrayList<>();
        Set<String> seenScriptSets = new LinkedHashSet<>();

        // ---- 1) 解析所有 mtr_custom_resources.json / custom_resources.json / config.json
        for (String path : stack.listAll()) {
            String p = ResourcePack.normalize(path);
            String lower = p.toLowerCase();
            if (!lower.endsWith("custom_resources.json") && !lower.endsWith("config.json")) continue;
            byte[] raw = stack.read(p);
            if (raw == null) continue;
            try {
                parseCustomResources(new String(raw, StandardCharsets.UTF_8), p, out, seenScriptSets);
            } catch (Exception e) {
                // 单个 json 解析失败不影响其他
            }
        }

        // ---- 2) 回退：启发式扫描脚本
        for (String path : stack.listAll()) {
            String p = ResourcePack.normalize(path);
            if (!p.endsWith(".js")) continue;
            String resId = pathToResourceId(p);
            if (resId == null) continue;

            String lower = p.toLowerCase();
            String fnName = lower.substring(lower.lastIndexOf('/') + 1);
            if (fnName.startsWith("num") || fnName.startsWith("conn") || fnName.contains("con_main") || fnName.contains("bogie")) continue;
            boolean nameLooksMain = lower.contains("lcd") || lower.contains("display") || lower.contains("screen") || lower.contains("draw") || lower.contains("main") || lower.contains("led");
            if (!nameLooksMain) continue;

            byte[] raw = stack.read(p);
            if (raw == null) continue;
            String src = new String(raw, StandardCharsets.UTF_8);
            if (!RENDER_FN.matcher(src).find()) continue;
            if (!DISPLAY_API.matcher(src).find()) continue;

            String key = String.join("|", List.of(resId));
            if (seenScriptSets.contains(key)) continue;

            // 尝试补上同目录的 gz_const.js（含常量/字体/配置加载），提高可运行率
            List<String> scripts = new ArrayList<>();
            String dir = p.contains("/") ? p.substring(0, p.lastIndexOf('/')) : "";
            for (String pre : new String[]{"gz_const.js", "wh_const.js", "const.js"}) {
                String cand = dir.isEmpty() ? pre : dir + "/" + pre;
                if (stack.has(cand)) {
                    String cid = pathToResourceId(cand);
                    if (cid != null) scripts.add(cid);
                }
            }
            scripts.add(resId);

            String name = p.substring(p.lastIndexOf('/') + 1) + "  (启发式)";
            out.add(new Entry(resId, name, scripts, "启发式扫描: " + p, true));
            seenScriptSets.add(key);
        }

        return out;
    }

    private static void parseCustomResources(String json, String jsonPath,
                                             List<Entry> out, Set<String> seen) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root == null || !root.has("custom_trains")) return;
        JsonElement ctEl = root.get("custom_trains");
        if (!ctEl.isJsonObject()) return;
        JsonObject ct = ctEl.getAsJsonObject();

        for (Map.Entry<String, JsonElement> en : ct.entrySet()) {
            if (!en.getValue().isJsonObject()) continue;
            JsonObject train = en.getValue().getAsJsonObject();
            if (!train.has("script_files")) continue;
            JsonElement sfEl = train.get("script_files");
            if (!sfEl.isJsonArray()) continue;
            JsonArray arr = sfEl.getAsJsonArray();
            List<String> scripts = new ArrayList<>();
            for (JsonElement e : arr) {
                if (e.isJsonPrimitive()) {
                    String s = e.getAsString().trim();
                    if (!s.isEmpty() && !scripts.contains(s)) scripts.add(s);
                }
            }
            if (scripts.isEmpty()) continue;

            String key = String.join("|", scripts);
            if (seen.contains(key)) continue; // 多个车型共享同一套脚本时只保留一个
            seen.add(key);

            String name = train.has("name") && train.get("name").isJsonPrimitive()
                    ? train.get("name").getAsString()
                    : en.getKey();

            // script_texts 也可能定义变量，一并保留（在 script_files 之前执行）
            List<String> texts = new ArrayList<>();
            if (train.has("script_texts") && train.get("script_texts").isJsonArray()) {
                for (JsonElement e : train.get("script_texts").getAsJsonArray()) {
                    if (e.isJsonPrimitive()) texts.add(e.getAsString());
                }
            }

            Entry entry = new Entry(en.getKey(), name, scripts, jsonPath, false);
            if (!texts.isEmpty()) SCRIPT_TEXTS.put(entry.id, texts);
            out.add(entry);
        }
    }

    /** custom_trains 的 script_texts（少量车型用它注入配置）。 */
    public static final Map<String, List<String>> SCRIPT_TEXTS = new LinkedHashMap<>();

    /**
     * assets/mtr/a9lcd/x.js -> mtr:a9lcd/x.js
     * 非 assets/ 结构返回 null。
     */
    public static String pathToResourceId(String path) {
        String p = ResourcePack.normalize(path);
        if (!p.startsWith("assets/")) return null;
        String rest = p.substring("assets/".length());
        int slash = rest.indexOf('/');
        if (slash <= 0) return null;
        String ns = rest.substring(0, slash);
        String sub = rest.substring(slash + 1);
        if (sub.isEmpty()) return null;
        return ns + ":" + sub;
    }

    /** mtr:a9lcd/x.js -> assets/mtr/a9lcd/x.js ；无命名空间时默认 mtr。 */
    public static String resourceIdToPath(String resId) {
        String s = resId.trim().replace('\\', '/');
        Matcher m = Pattern.compile("^([a-z0-9_.-]+):(.+)$").matcher(s);
        if (m.matches()) {
            return ResourcePack.normalize("assets/" + m.group(1) + "/" + m.group(2));
        }
        return ResourcePack.normalize("assets/mtr/" + s);
    }
}
