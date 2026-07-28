package lcdviewer.ante;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * ANTE 注入 JS 的工具类集合。语义严格对照 mtr-ante 源码：
 * common/src/main/java/cn/zbx1425/mtrsteamloco/scripting/util/*.java
 */
public final class AnteUtils {

    private AnteUtils() {
    }

    // ------------------------------------------------------------ Timing

    /** 对应 ANTE TimingUtil.elapsed()：以秒为单位的连续时间。 */
    public static final class Timing {
        private static long originNanos = System.nanoTime();
        /** 允许 UI 强制指定时间，用于"定格/回放"某一时刻的画面。 */
        private static Double forced = null;

        public static double elapsed() {
            if (forced != null) return forced;
            return (System.nanoTime() - originNanos) / 1_000_000_000.0;
        }

        public static void setForcedTime(Double t) {
            forced = t;
        }

        public static void reset() {
            originNanos = System.nanoTime();
            forced = null;
        }
    }

    // --------------------------------------------------------- RateLimit

    public static final class RateLimit {
        private double lastTime = 0;
        private final double interval;

        public RateLimit(double interval) {
            this.interval = interval;
        }

        public boolean shouldUpdate() {
            double now = Timing.elapsed();
            if (now - lastTime > interval) {
                lastTime = now;
                return true;
            }
            return false;
        }

        public void resetCoolDown() {
            lastTime = 0;
        }
    }

    // ------------------------------------------------------ StateTracker

    public static final class StateTracker {
        private String lastState;
        private String currentState;
        private double currentStateTime;
        private boolean firstTimeCurrentState;

        public void setState(String value) {
            if (value != null && !value.equals(currentState)) {
                lastState = currentState;
                currentState = value;
                currentStateTime = Timing.elapsed();
                firstTimeCurrentState = true;
            } else if (value != null) {
                firstTimeCurrentState = false;
            }
        }

        public String stateNow() {
            return currentState;
        }

        public String stateLast() {
            return lastState;
        }

        public double stateNowDuration() {
            return Timing.elapsed() - currentStateTime;
        }

        public boolean stateNowFirst() {
            return firstTimeCurrentState;
        }
    }

    // ------------------------------------------------------ CycleTracker

    public static final class CycleTracker {
        private final String[] states;
        private final float[] offsets;
        private final float cycleDuration;

        private String lastState;
        private String currentState;
        private double currentStateTime;
        private int lastStateNum;
        private boolean firstTimeCurrentState;

        public CycleTracker(Object[] params) {
            if (params.length % 2 != 0) throw new IllegalArgumentException("CycleTracker params must be pairs");
            float offset = 0;
            states = new String[params.length / 2];
            offsets = new float[params.length / 2];
            for (int i = 0; i < params.length; i += 2) {
                states[i / 2] = String.valueOf(params[i]);
                float elemDuration = Float.parseFloat(String.valueOf(params[i + 1]));
                offsets[i / 2] = offset;
                offset += elemDuration;
            }
            cycleDuration = offset;
        }

        public void tick() {
            if (cycleDuration <= 0) return;
            double time = Timing.elapsed() % cycleDuration;
            int cycleNum = (int) (Timing.elapsed() / cycleDuration);
            for (int i = offsets.length - 1; i >= 0; i--) {
                if (time >= offsets[i]) {
                    int stateNum = cycleNum * offsets.length + i;
                    currentState = states[i];
                    currentStateTime = cycleNum * cycleDuration + offsets[i];
                    lastState = states[i == 0 ? offsets.length - 1 : i - 1];
                    if (lastStateNum != stateNum) {
                        firstTimeCurrentState = true;
                        lastStateNum = stateNum;
                    } else {
                        firstTimeCurrentState = false;
                    }
                    break;
                }
            }
        }

        public String stateNow() {
            return currentState;
        }

        public String stateLast() {
            return lastState;
        }

        public double stateNowDuration() {
            return Timing.elapsed() - currentStateTime;
        }

        public boolean stateNowFirst() {
            return firstTimeCurrentState;
        }
    }

    // ---------------------------------------------------------- TextUtil

    /**
     * 对照 ANTE TextUtil。CJK 判定对应 MTR 的 IGui.isCjk。
     * 名称格式： "中文|English||extra"
     */
    public static final class TextUtil {

        public static String getCjkParts(String src) {
            return getCjkMatching(src, true);
        }

        public static String getNonCjkParts(String src) {
            return getCjkMatching(src, false);
        }

        public static String getExtraParts(String src) {
            return getExtraMatching(src, true);
        }

        public static String getNonExtraParts(String src) {
            return getExtraMatching(src, false);
        }

        public static String getNonCjkAndExtraParts(String src) {
            String extraParts = getExtraMatching(src, false).trim();
            return getCjkMatching(src, false).trim() + (extraParts.isEmpty() ? "" : "|" + extraParts);
        }

        public static boolean isCjk(String src) {
            return isCjkString(src);
        }

        private static String getExtraMatching(String src, boolean extra) {
            if (src == null) return "";
            if (src.contains("||")) {
                return src.split("\\|\\|", 2)[extra ? 1 : 0].trim();
            }
            return "";
        }

        private static String getCjkMatching(String src, boolean isCJK) {
            if (src == null) return "";
            String s = src;
            if (s.contains("||")) s = s.split("\\|\\|", 2)[0];
            String[] stringSplit = s.split("\\|");
            StringBuilder result = new StringBuilder();
            for (final String part : stringSplit) {
                if (isCjkString(part) == isCJK) {
                    if (result.length() > 0) result.append(' ');
                    result.append(part);
                }
            }
            return result.toString().trim();
        }
    }

    /** 对应 MTR IGui.isCjk：含有 CJK 字符即视为 CJK 串。 */
    public static boolean isCjkString(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if ((cp >= 0x2E80 && cp <= 0x9FFF)
                    || (cp >= 0xF900 && cp <= 0xFAFF)
                    || (cp >= 0xFF00 && cp <= 0xFF60)
                    || (cp >= 0x20000 && cp <= 0x3FFFF)) {
                return true;
            }
        }
        return false;
    }

    // --------------------------------------------------- GraphicsTexture

    /**
     * 对应 ANTE 的 GraphicsTexture：JS 侧在其 graphics 上绘制，然后 upload()。
     * 在本模拟器中它就是一张 BufferedImage —— 这正是我们要展示的 LCD 画面。
     */
    public static final class GraphicsTexture {
        public final BufferedImage image;
        public final SafeGraphics graphics;
        public final ResourceId identifier;

        private static int counter = 0;

        public GraphicsTexture(int width, int height) {
            int w = Math.max(1, width);
            int h = Math.max(1, height);
            this.image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            this.graphics = new SafeGraphics(image.createGraphics());
            this.graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            this.graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            this.graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            this.graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            this.graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            this.identifier = new ResourceId("lcdviewer", "dynamic/tex_" + (counter++));
        }

        /** JS 访问 texture.graphics */
        public SafeGraphics getGraphics() {
            return graphics;
        }

        public ResourceId getIdentifier() {
            return identifier;
        }

        public int getWidth() {
            return image.getWidth();
        }

        public int getHeight() {
            return image.getHeight();
        }

        /** 上传：模拟器中标记一次画面更新。 */
        public void upload() {
            uploadCount++;
        }

        public volatile int uploadCount = 0;

        public void close() {
            graphics.dispose();
        }
    }
}
