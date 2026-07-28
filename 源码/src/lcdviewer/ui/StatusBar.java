package lcdviewer.ui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** 底部状态栏：状态指示灯 + 文本 + 右侧数据字段。 */
public final class StatusBar extends JPanel {

    public enum State {
        IDLE(Theme.FG_DIM), BUSY(Theme.WARN), OK(Theme.OK), ERROR(Theme.ERR);

        final Color color;

        State(Color c) {
            this.color = c;
        }
    }

    private State state = State.IDLE;
    private final Lamp lamp = new Lamp();
    private final JLabel text = new JLabel(" ");
    private final JLabel right = new JLabel(" ");

    public StatusBar() {
        setLayout(new BorderLayout());
        setBackground(Theme.BG_HEADER);
        setBorder(javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE));
        setPreferredSize(new Dimension(10, 26));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        left.setOpaque(false);
        left.add(lamp);
        text.setFont(Theme.UI);
        text.setForeground(Theme.FG);
        left.add(text);
        add(left, BorderLayout.WEST);

        right.setFont(Theme.MONO_SMALL);
        right.setForeground(Theme.FG_DIM);
        right.setBorder(Theme.pad(0, 0, 0, 10));
        add(right, BorderLayout.EAST);
    }

    public void setState(State s, String msg) {
        this.state = s;
        lamp.repaint();
        text.setText(msg);
    }

    public void setRight(String s) {
        right.setText(s);
    }

    private final class Lamp extends JPanel {
        Lamp() {
            setOpaque(false);
            setPreferredSize(new Dimension(10, 10));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(state.color);
            g.fillOval(0, 1, 9, 9);
            g.setColor(state.color.darker().darker());
            g.drawOval(0, 1, 9, 9);
            g.dispose();
        }
    }
}
