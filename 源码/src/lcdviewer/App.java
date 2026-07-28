package lcdviewer;

import lcdviewer.ui.MainWindow;
import lcdviewer.ui.Theme;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 程序入口。 */
public final class App {

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("file.encoding", "UTF-8");

        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
            System.setErr(new java.io.PrintStream(System.err, true, "UTF-8"));
        } catch (Exception ignored) {}

        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        Theme.install();

        List<File> initial = new ArrayList<>();
        for (String a : args) {
            File f = new File(a);
            if (f.exists()) {
                if (f.isDirectory()) {
                    File[] zips = f.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
                    if (zips != null && zips.length > 0) {
                        java.util.Arrays.sort(zips, (p1, p2) -> p1.getName().compareToIgnoreCase(p2.getName()));
                        for (File z : zips) initial.add(z);
                    } else {
                        initial.add(f);
                    }
                } else if (f.getName().toLowerCase().endsWith(".zip")) {
                    initial.add(f);
                }
            }
        }

        SwingUtilities.invokeLater(() -> {
            MainWindow w = new MainWindow();
            w.setVisible(true);
            if (!initial.isEmpty()) w.loadPackFiles(initial);
        });
    }
}
