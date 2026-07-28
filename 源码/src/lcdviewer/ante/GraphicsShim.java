package lcdviewer.ante;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

/**
 * Nashorn 在调用 Java 重载方法时，若参数是 JS number（Double），
 * 无法在 drawString(String,float,float) 与 drawString(String,int,int) 之间抉择，
 * 会抛 NoSuchMethodException。
 *
 * 这些是 LCD 文字渲染的核心调用，因此在 JS 侧为 Graphics2D 补一层
 * 明确签名的辅助函数（见 ScriptEngineHost 里注入的 __g 系列）。
 * 本类提供 Java 端的明确入口，消除歧义。
 */
public final class GraphicsShim {

    private GraphicsShim() {
    }

    public static void drawString(Graphics2D g, String str, double x, double y) {
        g.drawString(str, (float) x, (float) y);
    }

    public static void drawChars(Graphics2D g, String str, double x, double y) {
        g.drawString(str, (float) x, (float) y);
    }

    public static void drawLine(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.drawLine(round(x1), round(y1), round(x2), round(y2));
    }

    public static void drawRect(Graphics2D g, double x, double y, double w, double h) {
        g.drawRect(round(x), round(y), round(w), round(h));
    }

    public static void fillRect(Graphics2D g, double x, double y, double w, double h) {
        g.fillRect(round(x), round(y), round(w), round(h));
    }

    public static void clearRect(Graphics2D g, double x, double y, double w, double h) {
        g.clearRect(round(x), round(y), round(w), round(h));
    }

    public static void drawOval(Graphics2D g, double x, double y, double w, double h) {
        g.drawOval(round(x), round(y), round(w), round(h));
    }

    public static void fillOval(Graphics2D g, double x, double y, double w, double h) {
        g.fillOval(round(x), round(y), round(w), round(h));
    }

    public static void drawRoundRect(Graphics2D g, double x, double y, double w, double h,
                                     double aw, double ah) {
        g.drawRoundRect(round(x), round(y), round(w), round(h), round(aw), round(ah));
    }

    public static void fillRoundRect(Graphics2D g, double x, double y, double w, double h,
                                     double aw, double ah) {
        g.fillRoundRect(round(x), round(y), round(w), round(h), round(aw), round(ah));
    }

    public static void drawArc(Graphics2D g, double x, double y, double w, double h,
                               double start, double extent) {
        g.drawArc(round(x), round(y), round(w), round(h), round(start), round(extent));
    }

    public static void fillArc(Graphics2D g, double x, double y, double w, double h,
                               double start, double extent) {
        g.fillArc(round(x), round(y), round(w), round(h), round(start), round(extent));
    }

    public static void translate(Graphics2D g, double x, double y) {
        g.translate(x, y);
    }

    public static void scale(Graphics2D g, double x, double y) {
        g.scale(x, y);
    }

    public static void rotate(Graphics2D g, double theta) {
        g.rotate(theta);
    }

    public static void rotate(Graphics2D g, double theta, double x, double y) {
        g.rotate(theta, x, y);
    }

    public static void setClip(Graphics2D g, double x, double y, double w, double h) {
        g.setClip(round(x), round(y), round(w), round(h));
    }

    public static void clipRect(Graphics2D g, double x, double y, double w, double h) {
        g.clipRect(round(x), round(y), round(w), round(h));
    }

    /** drawImage(img, x, y, w, h, observer) —— 宽高可能为负（镜像），需保留符号。 */
    public static void drawImage(Graphics2D g, java.awt.Image img,
                                 double x, double y, double w, double h) {
        int ix = round(x), iy = round(y), iw = round(w), ih = round(h);
        if (iw >= 0 && ih >= 0) {
            g.drawImage(img, ix, iy, iw, ih, null);
            return;
        }
        // 负宽高表示镜像：转成 dst 坐标形式
        int x1 = ix, x2 = ix + iw;
        int y1 = iy, y2 = iy + ih;
        int dx1 = Math.min(x1, x2), dx2 = Math.max(x1, x2);
        int dy1 = Math.min(y1, y2), dy2 = Math.max(y1, y2);
        int sw = img.getWidth(null), sh = img.getHeight(null);
        int sx1 = iw < 0 ? sw : 0, sx2 = iw < 0 ? 0 : sw;
        int sy1 = ih < 0 ? sh : 0, sy2 = ih < 0 ? 0 : sh;
        g.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
    }

    public static void drawImage(Graphics2D g, java.awt.Image img, double x, double y) {
        g.drawImage(img, round(x), round(y), null);
    }

    public static void drawImage(Graphics2D g, java.awt.Image img, AffineTransform tx) {
        g.drawImage(img, tx, null);
    }

    private static int round(double v) {
        return (int) Math.round(v);
    }
}
