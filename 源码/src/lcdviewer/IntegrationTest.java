package lcdviewer;

import lcdviewer.mock.Scenario;
import lcdviewer.pack.LcdDiscovery;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.List;

/** RenderService 集成冒烟测试。 */
public final class IntegrationTest {
    public static void main(String[] args) throws Exception {
        RenderService s = new RenderService();
        s.setLogSink(System.out::println);
        List<LcdDiscovery.Entry> all = s.loadPacks(List.of(new File(args[0])));
        LcdDiscovery.Entry e = all.stream()
                .filter(x -> x.id.contains(args.length > 1 ? args[1] : "a9lcd"))
                .findFirst().orElse(all.get(0));
        Scenario sc = new Scenario();
        sc.phase = Scenario.Phase.ARRIVED;
        sc.doorValue = 1;
        s.select(e, sc);
        for (int i = 0; i < 3; i++) s.step(1.0 + i * 5.0);
        List<RenderService.Surface> surfaces = s.surfaces();
        System.out.println("SURFACES=" + surfaces.size());
        if (!surfaces.isEmpty()) {
            File out = new File("K:/LCD/lcdviewer/out/integration.png");
            ImageIO.write(surfaces.get(0).image, "PNG", out);
            System.out.println("OUTPUT=" + out + " SIZE=" + out.length());
        }
        s.close();
    }
}
