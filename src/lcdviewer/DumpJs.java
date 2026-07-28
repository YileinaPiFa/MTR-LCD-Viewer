package lcdviewer;

import lcdviewer.ante.JsCompat;
import lcdviewer.pack.ResourcePack;

import java.io.File;
import java.nio.charset.StandardCharsets;

/** 导出转换后的脚本，定位仍未处理的 ES6 语法 */
public final class DumpJs {
    public static void main(String[] args) throws Exception {
        ResourcePack.Stack st = new ResourcePack.Stack();
        st.add(ResourcePack.open(new File(args[0])));
        String path = args[1];
        int line = args.length > 2 ? Integer.parseInt(args[2]) : -1;

        byte[] d = st.read(path);
        if (d == null) {
            System.out.println("NOT FOUND: " + path);
            return;
        }
        String src = new String(d, StandardCharsets.UTF_8);
        String out = JsCompat.transform(src);
        String[] lines = out.split("\n", -1);
        System.out.println("transformed lines=" + lines.length);
        if (line > 0) {
            for (int i = Math.max(0, line - 4); i < Math.min(lines.length, line + 3); i++) {
                System.out.println((i + 1) + ": " + lines[i]);
            }
        }
    }
}
