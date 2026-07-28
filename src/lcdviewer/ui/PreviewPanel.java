package lcdviewer.ui;

import javax.swing.JPanel;
import javax.swing.event.MouseInputAdapter;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

/**
 * LCD 画面显示区。支持缩放、拖动、棋盘格透明底、像素网格。
 */
public final class PreviewPanel extends JPanel {

    private BufferedImage image;
    private double zoom = 1.0;
    private boolean fitMode = true;
    private int offsetX = 0, offsetY = 0;
    private Point dragStart;
    private boolean showGrid = false;
    private boolean darkBacking = true;

    private Runnable onViewChanged;

    public PreviewPanel() {
        setBackground(Theme.BG_FIELD);
        setOpaque(true);

        MouseInputAdapter ma = new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
                setCursor(java.awt.Cursor.getDefaultCursor());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart == null) return;
                fitMode = false;
                offsetX += e.getX() - dragStart.x;
                offsetY += e.getY() - dragStart.y;
                dragStart = e.getPoint();
                repaint();
                fireViewChanged();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        addMouseWheelListener((MouseWheelEvent e) -> {
            if (image == null) return;
            double old = effectiveZoom();
            double factor = e.getWheelRotation() < 0 ? 1.15 : 1 / 1.15;
            double nz = clamp(old * factor, 0.02, 16.0);

            // 以鼠标位置为中心缩放
            Rectangle r = drawRect(old);
            double relX = (e.getX() - r.x) / (double) r.width;
            double relY = (e.getY() - r.y) / (double) r.height;

            fitMode = false;
            zoom = nz;
            Rectangle nr = drawRect(nz);
            offsetX += (int) Math.round(e.getX() - (nr.x + relX * nr.width));
            offsetY += (int) Math.round(e.getY() - (nr.y + relY * nr.height));
            repaint();
            fireViewChanged();
        });
    }

    public void setOnViewChanged(Runnable r) {
        this.onViewChanged = r;
    }

    private void fireViewChanged() {
        if (onViewChanged != null) onViewChanged.run();
    }

    public void setImage(BufferedImage img) {
        boolean isNew = (this.image != img)
                || img == null
                || (this.image != null && (img.getWidth() != this.image.getWidth()
                || img.getHeight() != this.image.getHeight()));
        this.image = img;
        if (isNew) {
            fitMode = true;
            offsetX = offsetY = 0;
        }
        repaint();
        fireViewChanged();
    }

    public BufferedImage getImage() {
        return image;
    }

    public void fitToWindow() {
        fitMode = true;
        offsetX = offsetY = 0;
        repaint();
        fireViewChanged();
    }

    public void setZoom(double z) {
        fitMode = false;
        zoom = clamp(z, 0.02, 16.0);
        repaint();
        fireViewChanged();
    }

    public void zoomBy(double factor) {
        setZoom(effectiveZoom() * factor);
    }

    public double effectiveZoom() {
        if (!fitMode || image == null) return zoom;
        double sx = (getWidth() - 24.0) / image.getWidth();
        double sy = (getHeight() - 24.0) / image.getHeight();
        return Math.max(0.02, Math.min(sx, sy));
    }

    public void setShowGrid(boolean b) {
        showGrid = b;
        repaint();
    }

    public void setDarkBacking(boolean b) {
        darkBacking = b;
        repaint();
    }

    private Rectangle drawRect(double z) {
        if (image == null) return new Rectangle();
        int w = (int) Math.round(image.getWidth() * z);
        int h = (int) Math.round(image.getHeight() * z);
        int x = (getWidth() - w) / 2 + (fitMode ? 0 : offsetX);
        int y = (getHeight() - h) / 2 + (fitMode ? 0 : offsetY);
        return new Rectangle(x, y, w, h);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        int W = getWidth(), H = getHeight();

        if (image == null) {
            g.setColor(Theme.FG_DIM);
            g.setFont(Theme.UI);
            String s = "未选择 LCD 单元";
            int sw = g.getFontMetrics().stringWidth(s);
            g.drawString(s, (W - sw) / 2, H / 2);
            g.dispose();
            return;
        }

        double z = effectiveZoom();
        Rectangle r = drawRect(z);

        // 画面背衬：LCD 贴图多为半透明，需要衬底才能看清
        if (darkBacking) {
            g.setColor(new Color(0x1E1E1E));
            g.fillRect(r.x, r.y, r.width, r.height);
        } else {
            drawCheckerboard(g, r);
        }

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                z >= 3 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                       : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, r.x, r.y, r.width, r.height, null);

        // 边界线
        g.setColor(Theme.LINE_LIGHT);
        g.setStroke(new BasicStroke(1f));
        g.drawRect(r.x, r.y, r.width, r.height);

        if (showGrid && z >= 4) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            g.setColor(Theme.LINE_LIGHT);
            for (int px = 0; px <= image.getWidth(); px++) {
                int x = r.x + (int) Math.round(px * z);
                g.drawLine(x, r.y, x, r.y + r.height);
            }
            for (int py = 0; py <= image.getHeight(); py++) {
                int y = r.y + (int) Math.round(py * z);
                g.drawLine(r.x, y, r.x + r.width, y);
            }
        }
        g.dispose();
    }

    private void drawCheckerboard(Graphics2D g, Rectangle r) {
        final int cell = 8;
        Color a = new Color(0xE8E8E8), b = new Color(0xD0D0D0);
        java.awt.Shape old = g.getClip();
        g.clipRect(r.x, r.y, r.width, r.height);
        for (int y = r.y; y < r.y + r.height; y += cell) {
            for (int x = r.x; x < r.x + r.width; x += cell) {
                boolean alt = (((x - r.x) / cell) + ((y - r.y) / cell)) % 2 == 0;
                g.setColor(alt ? a : b);
                g.fillRect(x, y, cell, cell);
            }
        }
        g.setClip(old);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(900, 460);
    }
}
