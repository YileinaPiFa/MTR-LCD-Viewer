package lcdviewer.ui;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.text.SimpleDateFormat;
import java.util.Date;

/** 运行日志（脚本 print 输出与加载信息）。 */
public final class LogPanel extends JPanel {

    private final JTextArea area = new JTextArea();
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");
    private boolean autoScroll = true;

    public LogPanel() {
        setLayout(new BorderLayout());
        setBackground(Theme.BG_FIELD);

        area.setEditable(false);
        area.setBackground(Theme.BG_FIELD);
        area.setForeground(Theme.FG_DIM);
        area.setCaretColor(Theme.FG);
        area.setFont(Theme.MONO_SMALL);
        area.setBorder(Theme.pad(4, 6, 4, 6));
        area.setLineWrap(false);

        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(null);
        sp.getViewport().setBackground(Theme.BG_FIELD);
        add(sp, BorderLayout.CENTER);
    }

    public void append(String msg) {
        if (msg == null) return;
        String cleaned = sanitize(msg);
        if (cleaned == null) return;
        String line = "[" + fmt.format(new Date()) + "] " + cleaned;
        area.append(line + "\n");
        if (area.getLineCount() > 1500) {
            try {
                int end = area.getLineEndOffset(300);
                area.replaceRange("", 0, end);
            } catch (Exception ignored) {
            }
        }
        if (autoScroll) area.setCaretPosition(area.getDocument().getLength());
    }

    /**
     * 去掉脚本调试噪音：超长 JSON、重复状态切换、无关 3D 脚本错误。
     * 保留加载结果、线路挂载、初始化、真正失败信息。
     */
    private static String sanitize(String msg) {
        String m = msg.trim();
        if (m.isEmpty()) return null;

        // 大段 JSON / 调试 dump
        if (m.length() > 280 && (m.contains("{") || m.contains("["))) {
            if (m.startsWith("Train ") && m.contains("LCD_routeInfo")) {
                return "线路信息已更新";
            }
            if (m.startsWith("routePlatformInfo:")) {
                return "站台配置已匹配";
            }
            return "调试输出已省略 (" + m.length() + " 字符)";
        }

        // 无关脚本噪声
        if (m.contains("conn11.js") || m.contains("toVector3f")
                || m.contains("bogieDistance") || m.contains("Circumference")
                || m.contains("rail.getPosition") || m.contains("num_a9.js")) {
            return null;
        }

        // 重复/冗余状态
        if (m.startsWith("trainStatus") || m.startsWith("sidingNum:")
                || m.contains("train_type_info_map=[object Object]")) {
            return null;
        }
        if (m.contains("RouteInfo config is not found")
                || m.contains("Route PlatformInfo was not found")) {
            return null;
        }
        if (m.contains("RouteType config has been loaded from the default")) {
            return "线路类型配置：使用包内默认";
        }
        return m;
    }

    public void clear() {
        area.setText("");
    }

    public void setAutoScroll(boolean b) {
        autoScroll = b;
    }
}
