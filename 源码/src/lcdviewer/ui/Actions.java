package lcdviewer.ui;

import lcdviewer.RenderService;
import lcdviewer.pack.LcdDiscovery;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 绑定交互逻辑。 */
final class Actions {

    private static Timer playTimer;
    private static long frameCount;

    private Actions() {
    }

    static void install(MainWindow w) {
        installDnD(w);

        Layout.btnLoad.addActionListener(e -> chooseAndLoad(w, false));
        Layout.btnAddDlc.addActionListener(e -> chooseAndLoad(w, true));
        Layout.btnReload.addActionListener(e -> {
            if (!w.loadedPacks.isEmpty()) loadPacks(w, new ArrayList<>(w.loadedPacks));
        });

        Layout.btnFit.addActionListener(e -> w.preview.fitToWindow());
        Layout.btnZoomIn.addActionListener(e -> w.preview.zoomBy(1.25));
        Layout.btnZoomOut.addActionListener(e -> w.preview.zoomBy(1 / 1.25));
        Layout.btnGrid.addActionListener(e -> w.preview.setShowGrid(Layout.btnGrid.isToggled()));
        Layout.btnBacking.addActionListener(e -> w.preview.setDarkBacking(Layout.btnBacking.isToggled()));
        Layout.btnExport.addActionListener(e -> exportPng(w));

        Layout.btnStep.addActionListener(e -> renderOnce(w, true));
        Layout.btnPlay.addActionListener(e -> togglePlay(w));

        w.unitList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            LcdDiscovery.Entry sel = w.unitList.getSelectedValue();
            if (sel != null) selectUnit(w, sel);
        });

        w.surfaceList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            RenderService.Surface s = w.surfaceList.getSelectedValue();
            if (s != null) {
                w.preview.setImage(s.image);
                updateRight(w);
            }
        });

        w.preview.setOnViewChanged(() -> updateRight(w));

        // 参数变更后重建场景并刷新
        java.awt.event.ActionListener reapply = e -> reapplyScenario(w);
        w.controls.phase.addActionListener(reapply);
        w.controls.circular.addActionListener(reapply);
        w.controls.reversed.addActionListener(reapply);
        w.controls.doorLeft.addActionListener(reapply);
        w.controls.cars.addChangeListener(e -> reapplyScenario(w));
        w.controls.nextIdx.addChangeListener(e -> reapplyScenario(w));
        w.controls.doorValue.addChangeListener(e -> {
            if (!w.controls.doorValue.getValueIsAdjusting()) reapplyScenario(w);
        });
    }

    // ------------------------------------------------------------ 拖放

    private static void installDnD(MainWindow w) {
        w.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                if (!canImport(s)) return false;
                try {
                    List<File> files = (List<File>) s.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    List<File> expanded = expandFiles(files);
                    if (expanded.isEmpty()) return false;
                    if (!files.isEmpty()) Settings.setLastDir(files.get(0));
                    loadPacks(w, expanded);
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        });
    }

    private static List<File> expandFiles(List<File> input) {
        List<File> result = new ArrayList<>();
        for (File f : input) {
            if (f.isDirectory()) {
                // 如果目录下包含 zip 文件，自动展平添加所有 zip（分包场景）
                File[] zips = f.listFiles((d, name) -> name.toLowerCase().endsWith(".zip"));
                if (zips != null && zips.length > 0) {
                    java.util.Arrays.sort(zips, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    for (File z : zips) result.add(z);
                } else {
                    result.add(f); // 普通解压解开的资源包文件夹
                }
            } else if (f.getName().toLowerCase().endsWith(".zip")) {
                result.add(f);
            }
        }
        return result;
    }

    private static void chooseAndLoad(MainWindow w, boolean append) {
        JFileChooser fc = new JFileChooser();
        File last = Settings.getLastDir();
        if (last != null) fc.setCurrentDirectory(last);
        fc.setMultiSelectionEnabled(true);
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setFileFilter(new FileNameExtensionFilter("MTR 资源包 / 追加包 / 分包目录 (*.zip)", "zip"));
        fc.setDialogTitle(append ? "选择追加包" : "选择资源包");
        if (fc.showOpenDialog(w) != JFileChooser.APPROVE_OPTION) return;
        
        File[] selected = fc.getSelectedFiles();
        if (selected != null && selected.length > 0) {
            Settings.setLastDir(selected[0]);
        }
        
        List<File> files = new ArrayList<>();
        if (append) files.addAll(w.loadedPacks);
        for (File f : selected) files.add(f);
        loadPacks(w, expandFiles(files));
    }

    // ------------------------------------------------------------ 载入

    static void loadPacks(MainWindow w, List<File> files) {
        stopPlay(w);
        w.status.setState(StatusBar.State.BUSY, "正在解析资源包…");
        setBusy(w, true);

        new SwingWorker<List<LcdDiscovery.Entry>, Void>() {
            @Override
            protected List<LcdDiscovery.Entry> doInBackground() throws Exception {
                return w.service.loadPacks(files);
            }

            @Override
            protected void done() {
                setBusy(w, false);
                try {
                    List<LcdDiscovery.Entry> entries = get();
                    w.loadedPacks.clear();
                    w.loadedPacks.addAll(files);

                    w.unitModel.clear();
                    for (LcdDiscovery.Entry e : entries) w.unitModel.addElement(e);
                    Layout.unitCount.setText(String.valueOf(entries.size()));

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < files.size(); i++) {
                        if (i > 0) sb.append("  +  ");
                        sb.append(files.get(i).getName());
                    }
                    Layout.packLabel.setText(sb.toString());

                    if (entries.isEmpty()) {
                        w.status.setState(StatusBar.State.ERROR, "未在资源包中找到 LCD 单元");
                    } else {
                        w.status.setState(StatusBar.State.OK,
                                "已载入 " + files.size() + " 个包，发现 " + entries.size() + " 个 LCD 单元");
                        w.unitList.setSelectedIndex(0);
                    }
                } catch (Exception ex) {
                    w.status.setState(StatusBar.State.ERROR, "载入失败：" + rootMsg(ex));
                    w.logPanel.append("载入失败：" + rootMsg(ex));
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------ 选中

    private static void selectUnit(MainWindow w, LcdDiscovery.Entry entry) {
        stopPlay(w);
        w.status.setState(StatusBar.State.BUSY, "正在初始化 " + entry.displayName + " …");
        setBusy(w, true);
        w.controls.applyTo(w.scenario);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                w.service.select(entry, w.scenario);
                // 预热若干帧，让脚本走完初始化分支
                for (int i = 0; i < 3; i++) {
                    w.service.step(1.0 + i * 7.0);
                }
                return null;
            }

            @Override
            protected void done() {
                setBusy(w, false);
                try {
                    get();
                    refreshSurfaces(w, true);
                    w.status.setState(StatusBar.State.OK, entry.displayName + " — 渲染就绪");
                } catch (Exception ex) {
                    w.status.setState(StatusBar.State.ERROR, "初始化失败：" + rootMsg(ex));
                    w.logPanel.append("初始化失败：" + rootMsg(ex));
                }
            }
        }.execute();
    }

    private static void reapplyScenario(MainWindow w) {
        if (w.service.current() == null) return;
        w.controls.applyTo(w.scenario);
        try {
            w.service.applyScenario(w.scenario);
            renderOnce(w, false);
        } catch (Exception ex) {
            w.logPanel.append("参数应用失败：" + rootMsg(ex));
        }
    }

    // ------------------------------------------------------------ 渲染

    private static void renderOnce(MainWindow w, boolean advance) {
        if (w.service.current() == null) return;
        try {
            frameCount++;
            w.service.step(advance ? (1.0 + frameCount * 3.0) : null);
            refreshSurfaces(w, false);
        } catch (Exception ex) {
            w.logPanel.append("渲染失败：" + rootMsg(ex));
        }
    }

    private static void refreshSurfaces(MainWindow w, boolean reset) {
        List<RenderService.Surface> list = w.service.surfaces();
        int keep = w.surfaceList.getSelectedIndex();
        w.surfaceModel.clear();
        for (RenderService.Surface s : list) w.surfaceModel.addElement(s);
        if (!list.isEmpty()) {
            int idx = (reset || keep < 0 || keep >= list.size()) ? 0 : keep;
            w.surfaceList.setSelectedIndex(idx);
            w.preview.setImage(list.get(idx).image);
        } else {
            w.preview.setImage(null);
        }
        updateRight(w);
    }

    private static void togglePlay(MainWindow w) {
        if (Layout.btnPlay.isToggled()) {
            if (playTimer == null) {
                playTimer = new Timer(200, e -> renderOnce(w, true));
            }
            playTimer.start();
            w.status.setState(StatusBar.State.BUSY, "连续刷新中（5 fps）");
        } else {
            stopPlay(w);
        }
    }

    private static void stopPlay(MainWindow w) {
        if (playTimer != null) playTimer.stop();
        Layout.btnPlay.setToggled(false);
    }

    // ------------------------------------------------------------ 导出

    private static void exportPng(MainWindow w) {
        RenderService.Surface s = w.surfaceList.getSelectedValue();
        if (s == null) {
            JOptionPane.showMessageDialog(w, "当前没有可导出的画面。", "导出",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        File last = Settings.getLastDir();
        if (last != null) fc.setCurrentDirectory(last);
        String base = w.service.current() != null ? w.service.current().id : "lcd";
        fc.setSelectedFile(new File(base.replaceAll("[^\\w.-]", "_")
                + "_" + s.width + "x" + s.height + ".png"));
        if (fc.showSaveDialog(w) != JFileChooser.APPROVE_OPTION) return;
        try {
            File f = fc.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith(".png")) f = new File(f.getPath() + ".png");
            Settings.setLastDir(f);
            ImageIO.write(s.image, "PNG", f);
            w.status.setState(StatusBar.State.OK, "已导出 " + f.getName());
            w.logPanel.append("导出 " + f.getAbsolutePath());
        } catch (Exception ex) {
            w.status.setState(StatusBar.State.ERROR, "导出失败：" + rootMsg(ex));
        }
    }

    // ------------------------------------------------------------ 辅助

    private static void updateRight(MainWindow w) {
        RenderService.Surface s = w.surfaceList.getSelectedValue();
        StringBuilder sb = new StringBuilder();
        if (s != null) {
            sb.append(s.width).append("×").append(s.height);
            sb.append("   缩放 ").append(Math.round(w.preview.effectiveZoom() * 100)).append("%");
        }
        sb.append("   帧 ").append(frameCount);
        w.status.setRight(sb.toString());
    }

    private static void setBusy(MainWindow w, boolean busy) {
        Layout.btnLoad.setEnabled(!busy);
        Layout.btnAddDlc.setEnabled(!busy);
        Layout.btnReload.setEnabled(!busy);
        w.unitList.setEnabled(!busy);
        w.setCursor(busy
                ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR)
                : java.awt.Cursor.getDefaultCursor());
    }

    static String rootMsg(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String m = t.getMessage();
        return m == null ? t.toString() : m;
    }
}
