package lcdviewer.ui;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** 直角、无渐变的工具条按钮。 */
public final class FlatButton extends JButton {

    private boolean hover;
    private boolean down;
    private boolean toggled;
    private boolean toggleMode;

    public FlatButton(String text) {
        super(text);
        setFont(Theme.UI);
        setForeground(Theme.FG);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setBorder(Theme.pad(4, 12, 4, 12));
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; down = false; repaint(); }
            @Override public void mousePressed(MouseEvent e) { down = true; repaint(); }
            @Override public void mouseReleased(MouseEvent e) { down = false; repaint(); }
        });
    }

    public FlatButton asToggle() {
        toggleMode = true;
        return this;
    }

    public boolean isToggled() {
        return toggled;
    }

    public void setToggled(boolean b) {
        toggled = b;
        repaint();
    }

    @Override
    protected void fireActionPerformed(java.awt.event.ActionEvent e) {
        if (toggleMode) toggled = !toggled;
        super.fireActionPerformed(e);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        Color bg;
        if (!isEnabled()) bg = Theme.BG_PANEL;
        else if (down) bg = Theme.BG_SELECTED.darker();
        else if (toggled) bg = Theme.BG_SELECTED;
        else if (hover) bg = Theme.BG_HOVER;
        else bg = Theme.BG_PANEL;

        g.setColor(bg);
        g.fillRect(0, 0, w, h);
        g.setColor(toggled ? Theme.ACCENT : Theme.LINE);
        g.drawRect(0, 0, w - 1, h - 1);

        g.setFont(getFont());
        g.setColor(isEnabled() ? (toggled ? Theme.FG_TITLE : Theme.FG) : Theme.FG_DIM);
        java.awt.FontMetrics fm = g.getFontMetrics();
        String t = getText();
        int tx = (w - fm.stringWidth(t)) / 2;
        int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(t, tx, ty);
        g.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, Math.max(26, d.height));
    }
}
