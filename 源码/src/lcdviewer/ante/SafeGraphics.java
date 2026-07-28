package lcdviewer.ante;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.ImageObserver;

/**
 * Graphics2D 的转发包装，用于消除 Nashorn 调用重载方法时的歧义。
 *
 * 问题：LCD 脚本大量调用 g.drawString(text, x, y)，其中 x/y 是 JS number。
 * Nashorn 无法判断该选 (String,float,float) 还是 (String,int,int)，会抛
 * NoSuchMethodException。这里显式收敛为 double 版本，彻底避免歧义。
 *
 * 继承 Graphics2D 并把所有调用转发给底层实例，脚本无感。
 */
public final class SafeGraphics extends java.awt.Graphics2D {

    private final Graphics2D d;

    public SafeGraphics(Graphics2D delegate) {
        this.d = delegate;
    }

    public Graphics2D delegate() {
        return d;
    }

    // ------------------------------------------------- 消歧的核心方法
    //
    // 一律用 Object 接参再自行转 double。若声明成 double，Nashorn 在
    // 实参混有 Integer 与 Double 时仍无法在 int 版与 double 版之间选择。

    // drawString 的两个签名都是 Graphics2D 的抽象方法，必须原样实现。
    // 消歧改由 JsCompat 在源码层把 g.drawString(...) 重写为 drawStr(...)。
    @Override
    public void drawString(String s, int x, int y) {
        d.drawString(s, x, y);
    }

    /**
     * 供脚本调用的无歧义入口，并带中文字体回退。
     *
     * LCD 脚本有时会用拉丁字体（如 sans_lao.ttf，仅 1022 字形）
     * 去画含中文的串，结果全是豆腐块。这里逐段检查当前字体
     * 能否显示，不能显示的字符改用已注册的中文字体绘制。
     */
    public void drawStr(Object s, Object x, Object y) {
        String text = String.valueOf(s);
        float fx = (float) num(x), fy = (float) num(y);
        Font base = d.getFont();
        if (base == null || base.canDisplayUpTo(text) < 0) {
            d.drawString(text, fx, fy);
            return;
        }
        Font cjk = FontFallback.pick(base);
        if (cjk == null) {
            d.drawString(text, fx, fy);
            return;
        }
        // 按“能否用当前字体显示”分段，分别绘制
        int i = 0, n = text.length();
        float cursor = fx;
        while (i < n) {
            boolean ok = base.canDisplay(text.charAt(i));
            int j = i + 1;
            while (j < n && base.canDisplay(text.charAt(j)) == ok) j++;
            String part = text.substring(i, j);
            Font use = ok ? base : cjk;
            d.setFont(use);
            d.drawString(part, cursor, fy);
            cursor += d.getFontMetrics(use).stringWidth(part);
            i = j;
        }
        d.setFont(base);
    }

    // Graphics2D 的抽象方法，必须实现
    @Override
    public void drawString(String s, float x, float y) {
        d.drawString(s, x, y);
    }

    public void drawLine(Object x1, Object y1, Object x2, Object y2) {
        d.drawLine(r(x1), r(y1), r(x2), r(y2));
    }

    public void drawRect(Object x, Object y, Object w, Object h) {
        d.drawRect(r(x), r(y), r(w), r(h));
    }

    public void fillRect(Object x, Object y, Object w, Object h) {
        d.fillRect(r(x), r(y), r(w), r(h));
    }

    public void clearRect(Object x, Object y, Object w, Object h) {
        d.clearRect(r(x), r(y), r(w), r(h));
    }

    public void drawOval(Object x, Object y, Object w, Object h) {
        d.drawOval(r(x), r(y), r(w), r(h));
    }

    public void fillOval(Object x, Object y, Object w, Object h) {
        d.fillOval(r(x), r(y), r(w), r(h));
    }

    public void drawRoundRect(Object x, Object y, Object w, Object h, Object aw, Object ah) {
        d.drawRoundRect(r(x), r(y), r(w), r(h), r(aw), r(ah));
    }

    public void fillRoundRect(Object x, Object y, Object w, Object h, Object aw, Object ah) {
        d.fillRoundRect(r(x), r(y), r(w), r(h), r(aw), r(ah));
    }

    public void drawArc(Object x, Object y, Object w, Object h, Object s, Object e) {
        d.drawArc(r(x), r(y), r(w), r(h), r(s), r(e));
    }

    public void fillArc(Object x, Object y, Object w, Object h, Object s, Object e) {
        d.fillArc(r(x), r(y), r(w), r(h), r(s), r(e));
    }

    public void clipRect(Object x, Object y, Object w, Object h) {
        d.clipRect(r(x), r(y), r(w), r(h));
    }

    public void setClip(Object x, Object y, Object w, Object h) {
        d.setClip(r(x), r(y), r(w), r(h));
    }

    public void copyArea(Object x, Object y, Object w, Object h, Object dx, Object dy) {
        d.copyArea(r(x), r(y), r(w), r(h), r(dx), r(dy));
    }

    /**
     * 宽高可为负表示镜像绘制（LCD 脚本常用此技巧做左右屏镜像）。
     */
    public boolean drawImage(Image img, Object ox, Object oy, Object ow, Object oh, ImageObserver o) {
        int ix = r(ox), iy = r(oy), iw = r(ow), ih = r(oh);
        if (iw >= 0 && ih >= 0) return d.drawImage(img, ix, iy, iw, ih, o);
        int x2 = ix + iw, y2 = iy + ih;
        int dx1 = Math.min(ix, x2), dx2 = Math.max(ix, x2);
        int dy1 = Math.min(iy, y2), dy2 = Math.max(iy, y2);
        int sw = img.getWidth(null), sh = img.getHeight(null);
        if (sw <= 0 || sh <= 0) return false;
        int sx1 = iw < 0 ? sw : 0, sx2 = iw < 0 ? 0 : sw;
        int sy1 = ih < 0 ? sh : 0, sy2 = ih < 0 ? 0 : sh;
        return d.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, o);
    }

    public boolean drawImage(Image img, Object x, Object y, ImageObserver o) {
        return d.drawImage(img, r(x), r(y), o);
    }

    private static int r(Object o) {
        return (int) Math.round(num(o));
    }

    private static int r(double v) {
        return (int) Math.round(v);
    }

    private static double num(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o == null) return 0;
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }
    // ------------------------------------------------------ 纯转发部分

    @Override public void draw(java.awt.Shape s) { d.draw(s); }
    @Override public boolean drawImage(Image img, java.awt.geom.AffineTransform xform, ImageObserver obs) { return d.drawImage(img, xform, obs); }
    @Override public void drawImage(java.awt.image.BufferedImage img, java.awt.image.BufferedImageOp op, int x, int y) { d.drawImage(img, op, x, y); }
    @Override public void drawRenderedImage(java.awt.image.RenderedImage img, java.awt.geom.AffineTransform xform) { d.drawRenderedImage(img, xform); }
    @Override public void drawRenderableImage(java.awt.image.renderable.RenderableImage img, java.awt.geom.AffineTransform xform) { d.drawRenderableImage(img, xform); }
    @Override public void drawString(java.text.AttributedCharacterIterator it, int x, int y) { d.drawString(it, x, y); }
    @Override public void drawString(java.text.AttributedCharacterIterator it, float x, float y) { d.drawString(it, x, y); }
    @Override public void drawGlyphVector(java.awt.font.GlyphVector g, float x, float y) { d.drawGlyphVector(g, x, y); }
    @Override public void fill(java.awt.Shape s) { d.fill(s); }
    @Override public boolean hit(java.awt.Rectangle rect, java.awt.Shape s, boolean onStroke) { return d.hit(rect, s, onStroke); }
    @Override public java.awt.GraphicsConfiguration getDeviceConfiguration() { return d.getDeviceConfiguration(); }
    @Override public void setComposite(java.awt.Composite comp) { d.setComposite(comp); }
    @Override public void setPaint(java.awt.Paint paint) { d.setPaint(paint); }
    @Override public void setStroke(java.awt.Stroke s) { d.setStroke(s); }
    @Override public void setRenderingHint(java.awt.RenderingHints.Key key, Object value) { d.setRenderingHint(key, value); }
    @Override public Object getRenderingHint(java.awt.RenderingHints.Key key) { return d.getRenderingHint(key); }
    @Override public void setRenderingHints(java.util.Map<?, ?> hints) { d.setRenderingHints(hints); }
    @Override public void addRenderingHints(java.util.Map<?, ?> hints) { d.addRenderingHints(hints); }
    @Override public java.awt.RenderingHints getRenderingHints() { return d.getRenderingHints(); }
    @Override public void translate(int x, int y) { d.translate(x, y); }
    @Override public void translate(double tx, double ty) { d.translate(tx, ty); }
    @Override public void rotate(double theta) { d.rotate(theta); }
    @Override public void rotate(double theta, double x, double y) { d.rotate(theta, x, y); }
    @Override public void scale(double sx, double sy) { d.scale(sx, sy); }
    @Override public void shear(double shx, double shy) { d.shear(shx, shy); }
    @Override public void transform(java.awt.geom.AffineTransform tx) { d.transform(tx); }
    @Override public void setTransform(java.awt.geom.AffineTransform tx) { d.setTransform(tx); }
    @Override public java.awt.geom.AffineTransform getTransform() { return d.getTransform(); }
    @Override public java.awt.Paint getPaint() { return d.getPaint(); }
    @Override public java.awt.Composite getComposite() { return d.getComposite(); }
    @Override public void setBackground(java.awt.Color color) { d.setBackground(color); }
    @Override public java.awt.Color getBackground() { return d.getBackground(); }
    @Override public java.awt.Stroke getStroke() { return d.getStroke(); }
    @Override public void clip(java.awt.Shape s) { d.clip(s); }
    @Override public java.awt.font.FontRenderContext getFontRenderContext() { return d.getFontRenderContext(); }
    @Override public Graphics create() { return new SafeGraphics((Graphics2D) d.create()); }
    @Override public java.awt.Color getColor() { return d.getColor(); }
    @Override public void setColor(java.awt.Color c) { d.setColor(c); }
    @Override public void setPaintMode() { d.setPaintMode(); }
    @Override public void setXORMode(java.awt.Color c1) { d.setXORMode(c1); }
    @Override public java.awt.Font getFont() { return d.getFont(); }
    @Override public void setFont(java.awt.Font font) { d.setFont(font); }
    @Override public java.awt.FontMetrics getFontMetrics(java.awt.Font f) { return d.getFontMetrics(f); }
    @Override public java.awt.Rectangle getClipBounds() { return d.getClipBounds(); }
    @Override public void clipRect(int x, int y, int w, int h) { d.clipRect(x, y, w, h); }
    @Override public void setClip(int x, int y, int w, int h) { d.setClip(x, y, w, h); }
    @Override public java.awt.Shape getClip() { return d.getClip(); }
    @Override public void setClip(java.awt.Shape clip) { d.setClip(clip); }
    @Override public void copyArea(int x, int y, int w, int h, int dx, int dy) { d.copyArea(x, y, w, h, dx, dy); }
    @Override public void drawLine(int x1, int y1, int x2, int y2) { d.drawLine(x1, y1, x2, y2); }
    @Override public void fillRect(int x, int y, int w, int h) { d.fillRect(x, y, w, h); }
    @Override public void clearRect(int x, int y, int w, int h) { d.clearRect(x, y, w, h); }
    @Override public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) { d.drawRoundRect(x, y, w, h, aw, ah); }
    @Override public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) { d.fillRoundRect(x, y, w, h, aw, ah); }
    @Override public void drawOval(int x, int y, int w, int h) { d.drawOval(x, y, w, h); }
    @Override public void fillOval(int x, int y, int w, int h) { d.fillOval(x, y, w, h); }
    @Override public void drawArc(int x, int y, int w, int h, int s, int e) { d.drawArc(x, y, w, h, s, e); }
    @Override public void fillArc(int x, int y, int w, int h, int s, int e) { d.fillArc(x, y, w, h, s, e); }
    @Override public void drawPolyline(int[] xs, int[] ys, int n) { d.drawPolyline(xs, ys, n); }
    @Override public void drawPolygon(int[] xs, int[] ys, int n) { d.drawPolygon(xs, ys, n); }
    @Override public void fillPolygon(int[] xs, int[] ys, int n) { d.fillPolygon(xs, ys, n); }
    @Override public boolean drawImage(Image img, int x, int y, ImageObserver o) { return d.drawImage(img, x, y, o); }
    @Override public boolean drawImage(Image img, int x, int y, int w, int h, ImageObserver o) { return d.drawImage(img, x, y, w, h, o); }
    @Override public boolean drawImage(Image img, int x, int y, java.awt.Color bg, ImageObserver o) { return d.drawImage(img, x, y, bg, o); }
    @Override public boolean drawImage(Image img, int x, int y, int w, int h, java.awt.Color bg, ImageObserver o) { return d.drawImage(img, x, y, w, h, bg, o); }
    @Override public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver o) { return d.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, o); }
    @Override public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, java.awt.Color bg, ImageObserver o) { return d.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bg, o); }
    @Override public void dispose() { d.dispose(); }
}
