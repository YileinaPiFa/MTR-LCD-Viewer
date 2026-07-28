package lcdviewer.ante;

/**
 * 对应 ANTE 里的 ResourceLocation。
 * "mtr:a9lcd/logo.png" 解析为 namespace=mtr, path=a9lcd/logo.png，
 * 实际资源包路径为 assets/mtr/a9lcd/logo.png。
 */
public final class ResourceId {

    public final String namespace;
    public final String path;

    public ResourceId(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    /** 解析 "ns:path" 或裸 path（默认 mtr 命名空间）。 */
    public static ResourceId parse(String raw) {
        String s = raw.replace('\\', '/').trim();
        while (s.startsWith("/")) s = s.substring(1);
        int i = s.indexOf(':');
        if (i > 0) {
            return new ResourceId(s.substring(0, i), s.substring(i + 1));
        }
        return new ResourceId("mtr", s);
    }

    /** 在资源包中的完整路径。 */
    public String assetPath() {
        return "assets/" + namespace + "/" + path;
    }

    /** 所在目录（不含结尾斜杠），用于 idr 相对解析。 */
    public String dir() {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ResourceId)) return false;
        ResourceId r = (ResourceId) o;
        return namespace.equals(r.namespace) && path.equals(r.path);
    }

    @Override
    public int hashCode() {
        return namespace.hashCode() * 31 + path.hashCode();
    }
}
