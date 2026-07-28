package lcdviewer.ui;

import lcdviewer.mock.Scenario;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** 右侧运行参数面板：调整模拟的列车/线路状态。 */
public final class ControlPanel extends JPanel {

    public final JComboBox<Scenario.Phase> phase = new JComboBox<>(Scenario.Phase.values());
    public final JSpinner cars = new JSpinner(new SpinnerNumberModel(6, 1, 32, 1));
    public final JSpinner nextIdx = new JSpinner(new SpinnerNumberModel(0, 0, 99, 1));
    public final JCheckBox reversed = new JCheckBox("反向运行");
    public final JCheckBox doorLeft = new JCheckBox("左侧开门", true);
    public final JSlider doorValue = new JSlider(0, 100, 100);
    public final JComboBox<String> circular = new JComboBox<>(
            new String[]{"非环线", "内环", "外环"});
    public final JTextField routeColorHex = new JTextField("00A0E9", 7);
    public final JButton colorBtn = new JButton();
    public final JTextArea routeName = new JTextArea();
    public final JTextArea stations = new JTextArea();

    public ControlPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.BG_PANEL);
        setBorder(Theme.pad(8, 10, 8, 10));

        routeName.setRows(1);
        routeName.setLineWrap(true);
        routeName.setWrapStyleWord(true);
        stations.setRows(10);

        // 线路颜色控件组装
        JPanel colorBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        colorBox.setBackground(Theme.BG_PANEL);
        colorBtn.setPreferredSize(new Dimension(24, 20));
        colorBtn.setBorder(Theme.line());
        colorBtn.setFocusPainted(false);
        updateColorBtn(routeColorHex.getText());

        colorBtn.addActionListener(e -> {
            Color initial = parseColor(routeColorHex.getText());
            Color chosen = JColorChooser.showDialog(this, "选择线路颜色", initial);
            if (chosen != null) {
                String hex = String.format("%06X", chosen.getRGB() & 0xFFFFFF);
                routeColorHex.setText(hex);
                colorBtn.setBackground(chosen);
            }
        });

        routeColorHex.addActionListener(e -> updateColorBtn(routeColorHex.getText()));
        colorBox.add(colorBtn);
        colorBox.add(routeColorHex);

        add(Theme.sectionLabel("运行状态"));
        add(row("阶段", phase));
        add(row("下一站序号", nextIdx));
        add(gap());

        add(Theme.sectionLabel("车辆"));
        add(row("编组数", cars));
        JPanel flags = new JPanel(new GridLayout(1, 2, 4, 0));
        flags.setBackground(Theme.BG_PANEL);
        styleCheck(reversed);
        styleCheck(doorLeft);
        flags.add(reversed);
        flags.add(doorLeft);
        flags.setAlignmentX(Component.LEFT_ALIGNMENT);
        flags.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        add(flags);
        add(row("车门开度", doorValue));
        add(gap());

        add(Theme.sectionLabel("线路"));
        add(row("环线状态", circular));
        add(row("线路颜色", colorBox));
        add(field("线路名称", routeName, 36));
        add(field("站点列表（每行一站）", stations, 160));

        styleField(routeName);
        styleField(stations);
        routeColorHex.setFont(Theme.MONO_SMALL);
        doorValue.setBackground(Theme.BG_PANEL);
        doorValue.setForeground(Theme.FG);

        loadSettings();
    }

    private void updateColorBtn(String hex) {
        colorBtn.setBackground(parseColor(hex));
    }

    private Color parseColor(String hex) {
        try {
            int rgb = (int) Long.parseLong(hex.trim().replaceAll("^#", ""), 16);
            return new Color(rgb);
        } catch (Exception e) {
            return new Color(0x00A0E9);
        }
    }

    private void loadSettings() {
        routeName.setText(Settings.getRouteName());
        stations.setText(Settings.getStations());
        cars.setValue(Settings.getCars());
        nextIdx.setValue(Settings.getNextIdx());
        reversed.setSelected(Settings.getReversed());
        doorLeft.setSelected(Settings.getDoorLeft());
        circular.setSelectedIndex(Math.max(0, Math.min(2, Settings.getCircular())));
        
        String savedPhase = Settings.getPhase();
        for (int i = 0; i < phase.getItemCount(); i++) {
            if (phase.getItemAt(i).name().equals(savedPhase)) {
                phase.setSelectedIndex(i);
                break;
            }
        }
        String colorHex = Settings.getRouteColor();
        routeColorHex.setText(colorHex);
        updateColorBtn(colorHex);
    }

    public void saveSettings() {
        Settings.setRouteName(routeName.getText());
        Settings.setStations(stations.getText());
        Settings.setCars((Integer) cars.getValue());
        Settings.setNextIdx((Integer) nextIdx.getValue());
        Settings.setReversed(reversed.isSelected());
        Settings.setDoorLeft(doorLeft.isSelected());
        Settings.setCircular(circular.getSelectedIndex());
        if (phase.getSelectedItem() != null) {
            Settings.setPhase(((Scenario.Phase) phase.getSelectedItem()).name());
        }
        Settings.setRouteColor(routeColorHex.getText().trim());
        Settings.flush();
    }

    private static void styleCheck(JCheckBox c) {
        c.setBackground(Theme.BG_PANEL);
        c.setForeground(Theme.FG);
        c.setFont(Theme.UI);
        c.setFocusPainted(false);
    }

    private static void styleField(JTextArea a) {
        a.setBackground(Theme.BG_FIELD);
        a.setForeground(Theme.FG);
        a.setCaretColor(Theme.FG);
        a.setFont(Theme.MONO_SMALL);
        a.setBorder(Theme.pad(4, 6, 4, 6));
    }

    private JPanel row(String label, Component c) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setBackground(Theme.BG_PANEL);
        JLabel l = new JLabel(label);
        l.setForeground(Theme.FG_DIM);
        l.setFont(Theme.UI);
        l.setPreferredSize(new Dimension(82, 22));
        p.add(l, BorderLayout.WEST);
        c.setFont(Theme.UI);
        p.add(c, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(Theme.pad(2, 0, 2, 0));
        return p;
    }

    private JPanel field(String label, JTextArea area, int h) {
        JPanel p = new JPanel(new BorderLayout(0, 3));
        p.setBackground(Theme.BG_PANEL);
        JLabel l = new JLabel(label);
        l.setForeground(Theme.FG_DIM);
        l.setFont(Theme.UI);
        p.add(l, BorderLayout.NORTH);
        javax.swing.JScrollPane sp = new javax.swing.JScrollPane(area);
        sp.setBorder(Theme.line());
        sp.getViewport().setBackground(Theme.BG_FIELD);
        sp.setPreferredSize(new Dimension(180, h));
        p.add(sp, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, h + 22));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(Theme.pad(4, 0, 4, 0));
        return p;
    }

    private Component gap() {
        return Box.createVerticalStrut(8);
    }

    /** 把面板上的设置写入场景对象。 */
    public void applyTo(Scenario sc) {
        sc.phase = (Scenario.Phase) phase.getSelectedItem();
        sc.trainCars = (Integer) cars.getValue();
        sc.nextStationIndex = (Integer) nextIdx.getValue();
        sc.reversed = reversed.isSelected();
        sc.doorLeft = doorLeft.isSelected();
        sc.doorValue = doorValue.getValue() / 100f;

        int ci = circular.getSelectedIndex();
        sc.circularState = ci == 1 ? lcdviewer.mock.MtrWorld.CircularState.CLOCKWISE
                : ci == 2 ? lcdviewer.mock.MtrWorld.CircularState.ANTICLOCKWISE
                : lcdviewer.mock.MtrWorld.CircularState.NONE;

        try {
            sc.routeColor = (int) Long.parseLong(routeColorHex.getText().trim().replaceAll("^#", ""), 16);
        } catch (Exception e) {
            sc.routeColor = 0x00A0E9;
        }

        String rn = routeName.getText().trim();
        sc.routeName = rn;

        List<String> list = Arrays.stream(stations.getText().split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        sc.stationNames = list;
        sc.stationExits = list.stream()
                .map(s -> Arrays.asList("A", "B"))
                .collect(Collectors.toList());

        saveSettings();
    }
}
