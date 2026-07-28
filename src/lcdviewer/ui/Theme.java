package lcdviewer.ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 桌面工具风格：浅灰窗体、直角细边框、等宽数字。
 * 避免深色 dashboard / 渐变卡片一类观感。
 */
public final class Theme {

    // 背景层次：偏 Windows 桌面工具，而不是深色 dashboard 模板
    public static final Color BG_APP = new Color(0xF0F0F0);
    public static final Color BG_PANEL = new Color(0xE8E8E8);
    public static final Color BG_FIELD = new Color(0xFFFFFF);
    public static final Color BG_HEADER = new Color(0xDCDCDC);
    public static final Color BG_SELECTED = new Color(0xCCE4F7);
    public static final Color BG_HOVER = new Color(0xE5F1FB);

    // 线条
    public static final Color LINE = new Color(0xA0A0A0);
    public static final Color LINE_LIGHT = new Color(0xB8B8B8);

    // 文字
    public static final Color FG = new Color(0x1A1A1A);
    public static final Color FG_DIM = new Color(0x555555);
    public static final Color FG_TITLE = new Color(0x000000);

    // 状态色
    public static final Color OK = new Color(0x1B7A2C);
    public static final Color WARN = new Color(0x9A6B00);
    public static final Color ERR = new Color(0xB00020);
    public static final Color ACCENT = new Color(0x0078D4);

    public static final Font UI;
    public static final Font UI_BOLD;
    public static final Font MONO;
    public static final Font MONO_SMALL;

    static {
        String uiName = pickFont("Microsoft YaHei UI", "Microsoft YaHei", "微软雅黑",
                "Segoe UI", "Dialog");
        String monoName = pickFont("Microsoft YaHei UI", "Microsoft YaHei", "Consolas",
                "Cascadia Mono", "微软雅黑", "Dialog");
        UI = new Font(uiName, Font.PLAIN, 12);
        UI_BOLD = new Font(uiName, Font.BOLD, 12);
        MONO = new Font(monoName, Font.PLAIN, 12);
        MONO_SMALL = new Font(monoName, Font.PLAIN, 11);
    }

    private Theme() {
    }

    private static String pickFont(String... names) {
        Set<String> avail = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : names) {
            if (avail.contains(n)) return n;
        }
        return names[names.length - 1];
    }

    /** 应用全局 UIManager 设置。 */
    public static void install() {
        UIManager.put("Panel.background", BG_APP);
        UIManager.put("Label.foreground", FG);
        UIManager.put("Label.font", UI);
        UIManager.put("Button.font", UI);
        UIManager.put("ComboBox.font", UI);
        UIManager.put("TextField.font", UI);
        UIManager.put("List.font", UI);
        UIManager.put("Table.font", UI);
        UIManager.put("TabbedPane.font", UI);
        UIManager.put("CheckBox.font", UI);
        UIManager.put("ToolTip.font", UI);

        UIManager.put("List.background", BG_FIELD);
        UIManager.put("List.foreground", FG);
        UIManager.put("List.selectionBackground", BG_SELECTED);
        UIManager.put("List.selectionForeground", FG_TITLE);

        UIManager.put("TextField.background", BG_FIELD);
        UIManager.put("TextField.foreground", FG);
        UIManager.put("TextField.caretForeground", FG);
        UIManager.put("TextArea.background", BG_FIELD);
        UIManager.put("TextArea.foreground", FG);

        UIManager.put("ScrollPane.background", BG_APP);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("SplitPane.background", BG_APP);
        UIManager.put("SplitPaneDivider.draggingColor", ACCENT);

        UIManager.put("ToolTip.background", BG_HEADER);
        UIManager.put("ToolTip.foreground", FG);

        UIManager.put("ComboBox.background", BG_FIELD);
        UIManager.put("ComboBox.foreground", FG);
        UIManager.put("ComboBox.selectionBackground", BG_SELECTED);
        UIManager.put("ComboBox.selectionForeground", FG_TITLE);

        UIManager.put("Slider.background", BG_PANEL);
        UIManager.put("CheckBox.background", BG_PANEL);
        UIManager.put("CheckBox.foreground", FG);
    }

    /** 一像素细边框。 */
    public static Border line() {
        return BorderFactory.createLineBorder(LINE);
    }

    public static Border pad(int t, int l, int b, int r) {
        return BorderFactory.createEmptyBorder(t, l, b, r);
    }

    public static Border titled(String text) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, LINE),
                BorderFactory.createEmptyBorder(6, 8, 8, 8));
    }

    /** 分区小标题（工控软件常见的全大写细标签）。 */
    public static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UI_BOLD.deriveFont(11f));
        l.setForeground(FG_DIM);
        l.setBorder(pad(0, 0, 4, 0));
        return l;
    }

    public static void panel(JComponent c) {
        c.setBackground(BG_PANEL);
        c.setOpaque(true);
    }
}
