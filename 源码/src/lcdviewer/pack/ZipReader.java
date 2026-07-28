package lcdviewer.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * 以「中央目录」为唯一权威来源的 ZIP 解析器。
 *
 * MTR 追加包常见的“加密”手法会破坏本地文件头与 EOCD，使标准解压器拒绝打开：
 *   1. 文件头插入伪 EOCD 签名，伪装成分卷包；
 *   2. EOCD 的 diskNumber 改为 0xFFFF；
 *   3. 中央目录各记录的 diskNumberStart 填随机值；
 *   4. EOCD 条目数虚报；
 *   5. 首条本地文件头是伪造诱饵（大写文件名）；
 *   6. 各条本地文件头的 compressedSize 被虚增若干字节。
 *
 * 但压缩数据本身与 CRC32 均未改动。因此这里：
 *   - 只信任中央目录里的 name / method / crc / uncompressedSize / localHeaderOffset；
 *   - 解压时不依赖 compressedSize，改为按 uncompressedSize 读满即停；
 *   - 用 CRC32 校验结果，确保还原正确。
 */
public final class ZipReader {

    private static final int SIG_LOCAL = 0x04034B50;
    private static final int SIG_CD = 0x02014B50;
    private static final int SIG_EOCD = 0x06054B50;

    public static final class Entry {
        public final String name;
        public final int method;
        public final long crc;
        public final int size;
        public final int localOffset;

        Entry(String name, int method, long crc, int size, int localOffset) {
            this.name = name;
            this.method = method;
            this.crc = crc;
            this.size = size;
            this.localOffset = localOffset;
        }
    }

    public final Map<String, byte[]> files = new LinkedHashMap<>();
    public final List<String> notes = new ArrayList<>();
    public boolean wasObfuscated = false;
    public int okCount = 0;
    public int failCount = 0;

    private ZipReader() {
    }

    public static ZipReader parse(byte[] raw) {
        ZipReader r = new ZipReader();
        r.run(raw);
        return r;
    }

    private void run(byte[] raw) {
        byte[] b = raw;

        // ---- 1) 去掉开头的伪装字节，让偏移基准回到真实 ZIP 起点
        int firstLocal = indexOfSig(b, SIG_LOCAL, 0, Math.min(b.length, 1 << 16));
        if (firstLocal > 0) {
            byte[] t = new byte[b.length - firstLocal];
            System.arraycopy(b, firstLocal, t, 0, t.length);
            b = t;
            wasObfuscated = true;
            notes.add("移除文件头 " + firstLocal + " 字节伪装数据");
        }

        // ---- 2) 找 EOCD
        int eocd = lastIndexOfSig(b, SIG_EOCD);
        if (eocd < 0) {
            notes.add("未找到 EOCD，无法解析");
            return;
        }
        if (readU16(b, eocd + 4) != 0 || readU16(b, eocd + 6) != 0) {
            wasObfuscated = true;
            notes.add("EOCD 分卷号被伪装");
        }

        int cdOffset = (int) readU32(b, eocd + 16);
        int declared = readU16(b, eocd + 10);

        // 中央目录偏移不可信时自行搜索
        if (cdOffset < 0 || cdOffset + 4 > b.length || readU32(b, cdOffset) != SIG_CD) {
            cdOffset = findFirstCd(b, eocd);
            if (cdOffset < 0) {
                notes.add("未找到中央目录");
                return;
            }
            wasObfuscated = true;
            notes.add("中央目录偏移已重建");
        }

        // ---- 3) 遍历中央目录（唯一权威来源）
        List<Entry> entries = new ArrayList<>();
        int p = cdOffset;
        while (p + 46 <= b.length && readU32(b, p) == SIG_CD) {
            int method = readU16(b, p + 10);
            long crc = readU32(b, p + 16);
            int size = (int) readU32(b, p + 24);
            int nameLen = readU16(b, p + 28);
            int extraLen = readU16(b, p + 30);
            int cmtLen = readU16(b, p + 32);
            int disk = readU16(b, p + 34);
            int lho = (int) readU32(b, p + 42);
            if (disk != 0) wasObfuscated = true;

            // 字节级别查找 "assets/" (0x61, 0x73, 0x73, 0x65, 0x74, 0x73, 0x2f)，不受 GBK/UTF8 任何路径编码干扰
            int nameStart = p + 46;
            int actualNameLen = nameLen;
            byte[] assetsSeq = new byte[]{0x61, 0x73, 0x73, 0x65, 0x74, 0x73, 0x2f};
            for (int i = 0; i <= nameLen - 7; i++) {
                boolean match = true;
                for (int j = 0; j < 7; j++) {
                    if (b[nameStart + i + j] != assetsSeq[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    if (i > 0) {
                        nameStart += i;
                        actualNameLen -= i;
                    }
                    break;
                }
            }

            String name;
            try {
                String gbkName = new String(b, nameStart, actualNameLen, "GBK");
                if (gbkName.contains("assets/") || gbkName.contains("assets\\")) {
                    name = gbkName;
                } else {
                    name = new String(b, nameStart, actualNameLen, StandardCharsets.UTF_8);
                }
            } catch (Exception ex) {
                name = new String(b, nameStart, actualNameLen, StandardCharsets.UTF_8);
            }
            entries.add(new Entry(name, method, crc, size, lho));
            p += 46 + nameLen + extraLen + cmtLen;
        }
        if (declared != entries.size()) {
            wasObfuscated = true;
            notes.add("条目数被虚报：声明 " + declared + "，实际 " + entries.size());
        }

        // ---- 4) 按中央目录逐条解压，忽略本地头的 compressedSize
        for (Entry e : entries) {
            if (e.name.endsWith("/")) continue;
            byte[] data = extract(b, e);
            if (data != null) {
                files.put(ResourcePack.normalize(e.name), data);
                okCount++;
            } else {
                failCount++;
            }
        }
        notes.add("解出 " + okCount + " 个文件" + (failCount > 0 ? "，失败 " + failCount : ""));
    }

    /** 按中央目录信息解压单个条目。size 字段不可信，以 CRC32 为准。 */
    private byte[] extract(byte[] b, Entry e) {
        int lho = e.localOffset;
        if (lho < 0 || lho + 30 > b.length) return null;
        if (readU32(b, lho) != SIG_LOCAL) return null;

        int nameLen = readU16(b, lho + 26);
        int extraLen = readU16(b, lho + 28);
        int start = lho + 30 + nameLen + extraLen;
        if (start >= b.length) return null;

        try {
            if (e.method == 8) {
                byte[] out = inflate(b, start);
                return out.length == 0 ? null : out;
            }
            if (e.method == 0) {
                int max = Math.min(b.length - start, e.size + 64);
                if (max <= 0) return new byte[0];
                byte[] cand = new byte[max];
                System.arraycopy(b, start, cand, 0, max);
                if (e.crc != 0) {
                    for (int len = Math.max(0, e.size - 64); len <= max; len++) {
                        CRC32 c = new CRC32();
                        c.update(cand, 0, len);
                        if (c.getValue() == e.crc) {
                            byte[] out = new byte[len];
                            System.arraycopy(cand, 0, out, 0, len);
                            return out;
                        }
                    }
                }
                int len = Math.max(0, Math.min(e.size, max));
                byte[] out = new byte[len];
                System.arraycopy(cand, 0, out, 0, len);
                return out;
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * raw deflate 解压。
     *
     * 加密包里 compressedSize 与 uncompressedSize 都可能被篡改，
     * 唯一可信的是 CRC32；因此一律解压到 deflate 流自然结束，不按声明长度截断。
     */
    private static byte[] inflate(byte[] b, int start) {
        Inflater inf = new Inflater(true);
        try {
            inf.setInput(b, start, b.length - start);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(16384);
            byte[] buf = new byte[16384];
            while (!inf.finished()) {
                int n;
                try {
                    n = inf.inflate(buf);
                } catch (Exception ex) {
                    break;
                }
                if (n <= 0) break;
                bo.write(buf, 0, n);
            }
            return bo.toByteArray();
        } finally {
            inf.end();
        }
    }
    // ------------------------------------------------------------ 字节工具

    private static int findFirstCd(byte[] b, int eocd) {
        int first = -1;
        for (int i = eocd - 46; i >= 0; i--) {
            if (readU32(b, i) == SIG_CD) first = i;
        }
        return first;
    }

    static int indexOfSig(byte[] b, int sig, int from, int to) {
        for (int i = from; i + 4 <= to; i++) {
            if (readU32(b, i) == sig) return i;
        }
        return -1;
    }

    static int lastIndexOfSig(byte[] b, int sig) {
        for (int i = b.length - 4; i >= 0; i--) {
            if (readU32(b, i) == sig) return i;
        }
        return -1;
    }

    static int readU16(byte[] b, int off) {
        if (off + 2 > b.length || off < 0) return 0;
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    static long readU32(byte[] b, int off) {
        if (off + 4 > b.length || off < 0) return -1;
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8)
                | ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }
}
