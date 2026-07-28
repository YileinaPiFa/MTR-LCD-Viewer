package lcdviewer;

import lcdviewer.pack.ZipReader;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/** 诊断 zip 解析结果 */
public final class ZipDiag {
    public static void main(String[] args) throws Exception {
        File f = new File(args[0]);
        byte[] raw = Files.readAllBytes(f.toPath());
        ZipReader zr = ZipReader.parse(raw);
        System.out.println("obfuscated=" + zr.wasObfuscated + " ok=" + zr.okCount + " fail=" + zr.failCount);
        for (String n : zr.notes) System.out.println("  note: " + n);

        String want = args.length > 1 ? args[1] : "mtr_custom_resources.json";
        for (Map.Entry<String, byte[]> e : zr.files.entrySet()) {
            if (e.getKey().endsWith(want)) {
                byte[] d = e.getValue();
                System.out.println("\n=== " + e.getKey() + " len=" + d.length + " ===");
                String s = new String(d, StandardCharsets.UTF_8);
                System.out.println("head: " + s.substring(0, Math.min(200, s.length())).replace("\n", " "));
                int tail = Math.max(0, s.length() - 200);
                System.out.println("tail: " + s.substring(tail).replace("\n", " "));
            }
        }
    }
}
