import lcdviewer.RenderService;
import lcdviewer.mock.Scenario;
import lcdviewer.pack.LcdDiscovery;
import lcdviewer.pack.ResourcePack;

import java.io.File;
import java.util.List;

public class Full79Test {
    public static void main(String[] args) {
        File packFile = new File(args.length > 0 ? args[0] : "K:\\LCD\\[CTS]gzmtr4.0.01.zip");
        System.out.println("=== 开始 79 项 LCD 单元全量自动化测试: " + packFile.getName() + " ===");

        try (RenderService service = new RenderService()) {
            List<LcdDiscovery.Entry> entries = service.loadPacks(List.of(packFile));
            System.out.println("成功载入，共发现 " + entries.size() + " 个 LCD 单元。");

            Scenario sc = new Scenario();
            sc.phase = Scenario.Phase.ARRIVED;
            sc.doorValue = 1f;

            int passed = 0;
            int failed = 0;

            for (int i = 0; i < entries.size(); i++) {
                LcdDiscovery.Entry entry = entries.get(i);
                System.out.print(String.format("[%2d/%2d] 测试 LCD: %-40s ... ", (i + 1), entries.size(), entry.displayName));
                try {
                    service.select(entry, sc);
                    // 推进 5 帧测试 render 循环
                    for (int f = 0; f < 5; f++) {
                        service.step((double) (f * 5 + 1));
                    }
                    System.out.println("OK (贴图数: " + service.surfaces().size() + ")");
                    passed++;
                } catch (Throwable ex) {
                    System.out.println("FAIL ❌");
                    Throwable t = ex;
                    while (t.getCause() != null && t.getCause() != t) t = t.getCause();
                    System.out.println("      原因: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                    failed++;
                }
            }

            System.out.println("\n============================================");
            System.out.println(String.format("测试完成! 总数: %d, 成功: %d, 失败: %d", entries.size(), passed, failed));
            System.out.println("============================================");
            if (failed > 0) {
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
