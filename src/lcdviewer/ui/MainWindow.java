package lcdviewer.ui;

import lcdviewer.RenderService;
import lcdviewer.mock.Scenario;
import lcdviewer.pack.LcdDiscovery;

import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 主窗口。 */
public final class MainWindow extends JFrame {

    final RenderService service = new RenderService();
    final Scenario scenario = new Scenario();

    final DefaultListModel<LcdDiscovery.Entry> unitModel = new DefaultListModel<>();
    final JList<LcdDiscovery.Entry> unitList = new JList<>(unitModel);

    final DefaultListModel<RenderService.Surface> surfaceModel = new DefaultListModel<>();
    final JList<RenderService.Surface> surfaceList = new JList<>(surfaceModel);

    final PreviewPanel preview = new PreviewPanel();
    final ControlPanel controls = new ControlPanel();
    final StatusBar status = new StatusBar();
    final LogPanel logPanel = new LogPanel();

    final List<File> loadedPacks = new ArrayList<>();

    public MainWindow() {
        super("LCD Viewer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1280, 780));
        setSize(1480, 900);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG_APP);

        service.setLogSink(msg -> SwingUtilities.invokeLater(() -> logPanel.append(msg)));

        Layout.build(this);
        Actions.install(this);

        status.setState(StatusBar.State.IDLE, "就绪  ·  拖入 .zip 资源包，或点「打开」");

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                controls.saveSettings();
            }
        });
    }

    public void loadPackFiles(List<File> files) {
        Actions.loadPacks(this, files);
    }
}
