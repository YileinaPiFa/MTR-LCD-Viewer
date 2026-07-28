package lcdviewer;

import lcdviewer.ante.AnteUtils;
import lcdviewer.ante.ScriptEngineHost;
import lcdviewer.mock.MockTrain;
import lcdviewer.mock.Scenario;
import lcdviewer.pack.LcdDiscovery;
import lcdviewer.pack.ResourcePack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/** 蜻ｽ莉､陦悟・辜滓ｵ玖ｯ包ｼ壼刈霓ｽ霓ｦ蛹・-> 蜿醍鴫 LCD -> 貂ｲ譟・-> 蟇ｼ蜃ｺ PNG */
public final class SmokeTest {

    public static void main(String[] args) throws Exception {
        File packFile = new File(args.length > 0 ? args[0] : "K:\\LCD\\[CTS]gzmtr4.0.01.zip");
        String want = args.length > 1 ? args[1] : null;
        File outDir = new File(args.length > 2 ? args[2] : "K:\\LCD\\lcdviewer\\out");
        outDir.mkdirs();

        ResourcePack.Stack stack = new ResourcePack.Stack();
        stack.add(ResourcePack.open(packFile));

        List<LcdDiscovery.Entry> entries = LcdDiscovery.discover(stack);
        System.out.println("蜿醍鴫 LCD 蜈･蜿｣謨ｰ驥・ " + entries.size());
        for (int i = 0; i < entries.size(); i++) {
            System.out.println("  [" + i + "] " + entries.get(i));
        }
        if (entries.isEmpty()) return;

        LcdDiscovery.Entry target = entries.get(0);
        if (want != null) {
            for (LcdDiscovery.Entry e : entries) {
                if (e.id.contains(want) || e.displayName.contains(want)) {
                    target = e;
                    break;
                }
            }
        }
        System.out.println("\n>>> 貂ｲ譟鍋岼譬・ " + target);
        for (String s : target.scriptFiles) System.out.println("    script: " + s);

        ScriptEngineHost host = new ScriptEngineHost(stack, m -> System.out.println("  [js] " + m));
        host.init();

        long t0 = System.currentTimeMillis();
        host.load(target.scriptFiles);
        System.out.println("閼壽悽蜉霓ｽ閠玲慮 " + (System.currentTimeMillis() - t0) + "ms");

        Scenario sc = new Scenario();
        sc.phase = Scenario.Phase.ARRIVED;
        sc.doorValue = 1f;
        sc.nextStationIndex = 2;
        MockTrain train = sc.build();

        host.callCreate(train);
        System.out.println("create 螳梧・・瑚ｴｴ蝗ｾ謨ｰ=" + host.textures.size());

        for (int frame = 0; frame < 3; frame++) {
            AnteUtils.Timing.setForcedTime(frame * 8.0 + 1.0);
            host.callRender(train);
        }
        System.out.println("render 螳梧・・瑚ｴｴ蝗ｾ謨ｰ=" + host.textures.size());

        int i = 0;
        for (AnteUtils.GraphicsTexture t : host.textures) {
            BufferedImage img = t.image;
            boolean blank = isBlank(img);
            File f = new File(outDir, "tex_" + i + "_" + img.getWidth() + "x" + img.getHeight()
                    + (blank ? "_BLANK" : "") + ".png");
            ImageIO.write(img, "PNG", f);
            System.out.println("  蟇ｼ蜃ｺ " + f.getName() + " uploads=" + t.uploadCount);
            i++;
        }
        host.close();
    }

    static boolean isBlank(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        for (int y = 0; y < h; y += Math.max(1, h / 60)) {
            for (int x = 0; x < w; x += Math.max(1, w / 60)) {
                if ((img.getRGB(x, y) >>> 24) != 0) return false;
            }
        }
        return true;
    }
}

