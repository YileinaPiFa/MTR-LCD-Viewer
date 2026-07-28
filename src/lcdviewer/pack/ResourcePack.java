package lcdviewer.pack;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 一个资源包来源：可以是 .zip 资源包，也可以是解压后的文件夹。
 * 统一以 "assets/mtr/xxx" 这样的相对路径访问。
 */
public abstract class ResourcePack implements AutoCloseable {

    protected final String displayName;

    protected ResourcePack(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 返回资源字节，不存在返回 null。 */
    public abstract byte[] read(String path);

    /** 该包内所有条目路径（正斜杠分隔）。 */
    public abstract List<String> listAll();

    public boolean has(String path) {
        return read(path) != null;
    }

    @Override
    public void close() {
    }

    /** 修复日志（供 UI 展示“已解除加密”信息）。 */
    public final List<String> repairNotes = new ArrayList<>();

    public static ResourcePack open(File file) throws IOException {
        if (file.isDirectory()) {
            return new FolderPack(file);
        }
        return new ZipPack(file);
    }

    /** 规范化路径：统一正斜杠、剥离外层嵌套文件夹前缀、去掉前导 ./ 与 / */
    public static String normalize(String path) {
        String p = path.replace('\\', '/');
        int aIdx = p.lastIndexOf("assets/");
        if (aIdx > 0) p = p.substring(aIdx);
        while (p.startsWith("./")) p = p.substring(2);
        while (p.startsWith("/")) p = p.substring(1);
        // 折叠 a/b/../c
        if (p.contains("../")) {
            String[] parts = p.split("/");
            ArrayList<String> out = new ArrayList<>();
            for (String s : parts) {
                if (s.equals(".") || s.isEmpty()) continue;
                if (s.equals("..")) {
                    if (!out.isEmpty()) out.remove(out.size() - 1);
                } else {
                    out.add(s);
                }
            }
            p = String.join("/", out);
        }
        return p;
    }

    // ---------------------------------------------------------------- zip

    /**
     * ZIP 包。使用以中央目录为权威源的 ZipReader，
     * 因此可直接打开被“加密”（结构伪装）的 MTR 追加包。
     */
    static final class ZipPack extends ResourcePack {
        private final java.util.Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        private List<String> cache;

        ZipPack(File file) throws IOException {
            super(file.getName());
            byte[] raw = Files.readAllBytes(file.toPath());
            ZipReader zr = ZipReader.parse(raw);

            for (java.util.Map.Entry<String, byte[]> e : zr.files.entrySet()) {
                entries.put(normalize(e.getKey()), e.getValue());
            }

            if (zr.wasObfuscated) {
                repairNotes.add("检测到加密/伪装的压缩包，已自动解除");
            }
            repairNotes.addAll(zr.notes);
            if (entries.isEmpty()) {
                throw new IOException("无法解析压缩包：" + file.getName());
            }
        }

        @Override
        public byte[] read(String path) {
            return entries.get(normalize(path));
        }

        @Override
        public List<String> listAll() {
            if (cache != null) return cache;
            cache = new ArrayList<>(entries.keySet());
            return cache;
        }

        @Override
        public void close() {
            entries.clear();
        }
    }

    // ------------------------------------------------------------- folder

    static final class FolderPack extends ResourcePack {
        private final Path root;
        private List<String> cache;

        FolderPack(File dir) {
            super(dir.getName());
            this.root = dir.toPath();
        }

        @Override
        public byte[] read(String path) {
            Path p = root.resolve(normalize(path));
            if (!Files.isRegularFile(p)) return null;
            try {
                return Files.readAllBytes(p);
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public List<String> listAll() {
            if (cache != null) return cache;
            List<String> out = new ArrayList<>();
            try {
                Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
                    @Override
                    public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                        if (attrs.isRegularFile()) {
                            out.add(root.relativize(file).toString().replace('\\', '/'));
                        }
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }

                    @Override
                    public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    }

                    @Override
                    public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                        String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                        if (name.startsWith("$") || name.startsWith(".")) {
                            return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                        }
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
            } catch (Exception ignored) {
            }
            cache = out;
            return out;
        }
    }

    // -------------------------------------------------------------- utils

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(Math.max(1024, in.available()));
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    /**
     * 多个资源包的叠加视图。后加入的包优先级更高（模拟 Minecraft 资源包覆盖，
     * 这正是 LCD 自定义线路信息 DLC 覆盖 assets/mtr/lcd_config/route.js 的机制）。
     */
    public static final class Stack implements AutoCloseable {
        private final List<ResourcePack> packs = new ArrayList<>();

        public void add(ResourcePack pack) {
            packs.add(pack);
        }

        public List<ResourcePack> packs() {
            return packs;
        }

        /** 高优先级在前查找。 */
        public byte[] read(String path) {
            for (int i = packs.size() - 1; i >= 0; i--) {
                byte[] b = packs.get(i).read(path);
                if (b != null) return b;
            }
            return null;
        }

        public boolean has(String path) {
            return read(path) != null;
        }

        public Set<String> listAll() {
            Set<String> out = new LinkedHashSet<>();
            for (ResourcePack p : packs) out.addAll(p.listAll());
            return out;
        }

        @Override
        public void close() {
            for (ResourcePack p : packs) p.close();
            packs.clear();
        }
    }
}
