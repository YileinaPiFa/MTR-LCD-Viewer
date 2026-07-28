package lcdviewer.ante;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 中文字体回退表。
 *
 * LCD 脚本有时会用只含拉丁字形的字体（如 sans_lao.ttf）去绘制中文，
 * 在游戏内 ANTE 依赖 Minecraft 的字体链兜底，而在纯 AWT 环境下会画成豆腐块。
 * 这里维护一份「包内中文字体 + 系统中文字体」的候选表供 SafeGraphics 回退。
 */
public final class FontFallback {

    private static final List<Font> candidates = new ArrayList<>();
    private static final Map<String, Font> derivedCache = new HashMap<>();
    private static boolean systemScanned = false;

    private FontFallback() {
    }

    /** 资源包里成功加载的字体都登记进来，能显示中文的作为回退候选。 */
    public static void register(Font f) {
        if (f == null) return;
        // 用常见汉字探测
        if (!f.canDisplay('中') || !f.canDisplay('站')) return;
        synchronized (candidates) {
            for (Font c : candidates) {
                if (c.getFontName().equals(f.getFontName())) return;
            }
            candidates.add(f);
        }
    }

    public static void reset() {
        synchronized (candidates) {
            candidates.clear();
            derivedCache.clear();
            systemScanned = false;
        }
    }

    /**
     * 取一个能显示中文、且字号/字重与 base 一致的字体。
     */
    public static Font pick(Font base) {
        Font src = primary();
        if (src == null) return null;
        String key = src.getFontName() + "|" + base.getSize2D() + "|" + base.getStyle();
        synchronized (candidates) {
            Font cached = derivedCache.get(key);
            if (cached != null) return cached;
            Font d = src.deriveFont(base.getStyle(), base.getSize2D());
            derivedCache.put(key, d);
            return d;
        }
    }

    private static Font primary() {
        synchronized (candidates) {
            if (!candidates.isEmpty()) return candidates.get(0);
            if (!systemScanned) {
                systemScanned = true;
                scanSystem();
            }
            return candidates.isEmpty() ? null : candidates.get(0);
        }
    }

    /** 资源包里没有中文字体时，退回系统字体。 */
    private static void scanSystem() {
        String[] prefer = {
                "Microsoft YaHei", "微软雅黑", "Microsoft YaHei UI",
                "SimHei", "黑体", "SimSun", "宋体",
                "Noto Sans CJK SC", "Source Han Sans CN", "Dialog"
        };
        String[] avail = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        for (String want : prefer) {
            for (String have : avail) {
                if (have.equalsIgnoreCase(want)) {
                    Font f = new Font(have, Font.PLAIN, 32);
                    if (f.canDisplay('中')) {
                        candidates.add(f);
                        return;
                    }
                }
            }
        }
    }
}
