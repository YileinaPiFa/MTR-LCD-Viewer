package lcdviewer.ui;

import lcdviewer.RenderService;
import lcdviewer.pack.LcdDiscovery;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

/** 装配主窗口的各个区域。 */
final class Layout {

    static FlatButton btnLoad, btnAddDlc, btnReload, btnExport, btnFit, btnZoomIn, btnZoomOut;
    static FlatButton btnGrid, btnBacking, btnPlay, btnStep;
    static JLabel unitCount, packLabel;

    private Layout() {
    }

    static void build(MainWindow w) {
        w.setLayout(new BorderLayout());
        w.add(toolbar(w), BorderLayout.NORTH);
        w.add(w.status, BorderLayout.SOUTH);

        // 左：LCD 单元列表
        JPanel left = section("LCD 单元", listPane(w.unitList, unitRenderer()));
        unitCount = new JLabel("0");
        unitCount.setFont(Theme.MONO_SMALL);
        unitCount.setForeground(Theme.FG_DIM);
        ((JPanel) left.getComponent(0)).add(unitCount, BorderLayout.EAST);
        left.setMinimumSize(new Dimension(240, 120));
        w.unitList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // 中：预览 + 显示面清单
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Theme.BG_APP);
        center.add(w.preview, BorderLayout.CENTER);
        center.setMinimumSize(new Dimension(420, 200));

        JPanel surfaceBar = section("显示面", listPane(w.surfaceList, surfaceRenderer()));
        surfaceBar.setMinimumSize(new Dimension(150, 120));
        w.surfaceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // 右：参数
        JScrollPane ctrlScroll = new JScrollPane(w.controls);
        ctrlScroll.setBorder(null);
        ctrlScroll.getViewport().setBackground(Theme.BG_PANEL);
        ctrlScroll.getVerticalScrollBar().setUnitIncrement(16);
        ctrlScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JPanel right = section("运行参数", ctrlScroll);
        right.setMinimumSize(new Dimension(300, 120));

        // 用固定像素的分割位置，避免默认权重把两侧挤没
        JSplitPane centerSplit = split(JSplitPane.HORIZONTAL_SPLIT, center, surfaceBar, 1.0);
        JSplitPane mid = split(JSplitPane.HORIZONTAL_SPLIT, centerSplit, right, 1.0);
        JSplitPane leftMid = split(JSplitPane.HORIZONTAL_SPLIT, left, mid, 0.0);

        JPanel logSection = section("运行日志", w.logPanel);
        logSection.setMinimumSize(new Dimension(200, 90));

        JSplitPane vertical = split(JSplitPane.VERTICAL_SPLIT, leftMid, logSection, 1.0);
        w.add(vertical, BorderLayout.CENTER);

        // 首次显示按固定像素分配，避免默认权重把两侧挤扁
        w.addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean done = false;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (done || w.getWidth() < 400) return;
                done = true;
                javax.swing.SwingUtilities.invokeLater(() -> {
                    int W = Math.max(1200, w.getContentPane().getWidth());
                    int H = Math.max(750, w.getContentPane().getHeight());
                    final int leftW = 320;
                    final int rightW = 320;
                    final int surfaceW = 180;
                    final int logH = 150;
                    leftMid.setDividerLocation(leftW);
                    mid.setDividerLocation(Math.max(400, W - rightW));
                    centerSplit.setDividerLocation(Math.max(350, W - leftW - rightW - surfaceW));
                    vertical.setDividerLocation(Math.max(400, H - logH));
                });
            }
        });
    }
    // ------------------------------------------------------------ 工具条

    private static JComponent toolbar(MainWindow w) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.BG_HEADER);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));

        JPanel leftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        leftGroup.setOpaque(false);

        btnLoad = new FlatButton("打开");
        btnAddDlc = new FlatButton("追加");
        btnReload = new FlatButton("刷新");
        leftGroup.add(btnLoad);
        leftGroup.add(btnAddDlc);
        leftGroup.add(btnReload);
        leftGroup.add(sep());

        btnPlay = new FlatButton("播放").asToggle();
        btnStep = new FlatButton("步进");
        leftGroup.add(btnPlay);
        leftGroup.add(btnStep);
        leftGroup.add(sep());

        btnFit = new FlatButton("自适应");
        btnZoomOut = new FlatButton("缩小");
        btnZoomIn = new FlatButton("放大");
        btnGrid = new FlatButton("网格").asToggle();
        btnBacking = new FlatButton("深色底").asToggle();
        btnBacking.setToggled(true);
        leftGroup.add(btnFit);
        leftGroup.add(btnZoomOut);
        leftGroup.add(btnZoomIn);
        leftGroup.add(btnGrid);
        leftGroup.add(btnBacking);
        leftGroup.add(sep());

        btnExport = new FlatButton("导出");
        leftGroup.add(btnExport);

        bar.add(leftGroup, BorderLayout.WEST);

        packLabel = new JLabel("未加载资源包");
        packLabel.setFont(Theme.MONO_SMALL);
        packLabel.setForeground(Theme.FG_DIM);
        packLabel.setBorder(Theme.pad(0, 0, 0, 12));
        bar.add(packLabel, BorderLayout.EAST);
        return bar;
    }

    private static Component sep() {
        JPanel p = new JPanel();
        p.setPreferredSize(new Dimension(1, 20));
        p.setBackground(Theme.LINE);
        return p;
    }

    // ------------------------------------------------------------ 区块

    static JPanel section(String title, JComponent content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.BG_PANEL);
        p.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.LINE));

        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(Theme.BG_HEADER);
        head.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                Theme.pad(4, 8, 4, 8)));
        JLabel t = new JLabel(title);
        t.setFont(Theme.UI_BOLD.deriveFont(11f));
        t.setForeground(Theme.FG_DIM);
        head.add(t, BorderLayout.WEST);

        p.add(head, BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    private static JScrollPane listPane(JList<?> list, ListCellRenderer r) {
        list.setBackground(Theme.BG_FIELD);
        list.setForeground(Theme.FG);
        list.setFixedCellHeight(-1);
        @SuppressWarnings("unchecked")
        ListCellRenderer<Object> rr = (ListCellRenderer<Object>) r;
        ((JList<Object>) list).setCellRenderer(rr);
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(null);
        sp.getViewport().setBackground(Theme.BG_FIELD);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private static JSplitPane split(int orient, JComponent a, JComponent b, double weight) {
        JSplitPane sp = new JSplitPane(orient, a, b);
        sp.setBorder(null);
        sp.setDividerSize(4);
        sp.setResizeWeight(weight);
        sp.setBackground(Theme.LINE);
        sp.setContinuousLayout(true);
        return sp;
    }

    // ------------------------------------------------------------ 渲染器

    private static ListCellRenderer<LcdDiscovery.Entry> unitRenderer() {
        return (list, value, index, sel, focus) -> {
            JPanel p = new JPanel(new BorderLayout());
            p.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE_LIGHT),
                    Theme.pad(5, 8, 5, 6)));
            p.setBackground(sel ? Theme.BG_SELECTED : Theme.BG_FIELD);

            JLabel name = new JLabel(value.displayName);
            name.setFont(Theme.UI);
            name.setForeground(sel ? Theme.FG_TITLE : Theme.FG);
            p.add(name, BorderLayout.CENTER);

            JLabel sub = new JLabel(value.scriptFiles.size() + " 脚本"
                    + (value.heuristic ? " · 推断" : ""));
            sub.setFont(Theme.UI);
            sub.setForeground(Theme.FG_DIM);
            p.add(sub, BorderLayout.SOUTH);
            return p;
        };
    }

    private static ListCellRenderer<RenderService.Surface> surfaceRenderer() {
        return (list, value, index, sel, focus) -> {
            JLabel l = new JLabel(value.name);
            l.setOpaque(true);
            l.setFont(Theme.MONO_SMALL);
            l.setBackground(sel ? Theme.BG_SELECTED : Theme.BG_FIELD);
            l.setForeground(sel ? Theme.FG_TITLE : Theme.FG);
            l.setBorder(Theme.pad(4, 8, 4, 6));
            return l;
        };
    }
}
