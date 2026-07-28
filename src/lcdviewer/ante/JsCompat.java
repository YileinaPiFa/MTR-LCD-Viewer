package lcdviewer.ante;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nashorn 兼容层：把 LCD 脚本里 Nashorn 尚未实现的 ES6 语法降级为等价的 ES5。
 *
 * Nashorn（含 nashorn-core 15.x）支持 let/const/for-of/模板字符串/箭头函数，
 * 但 **不支持解构赋值**。广州 LCD 脚本用到了三种形态，这里逐一转换：
 *
 *   1) function f([a, b, c]) {...}
 *        -> function f(__d0) { var a = __d0[0], b = __d0[1], c = __d0[2]; ... }
 *   2) let [a, b] = expr;
 *        -> var __dN = expr; var a = __dN[0], b = __dN[1];
 *   3) let {a, b} = expr;
 *        -> var __dN = expr; var a = __dN.a, b = __dN.b;
 *   4) for (let [k, v] of map)
 *        -> for (let __eN of map) { let k = __eN[0], v = __eN[1]; ...
 *           （Map 迭代改为 entrySet 形式处理，见下）
 */
public final class JsCompat {

    private static int counter = 0;

    private JsCompat() {
    }

    public static String transform(String src) {
        String s = src;
        s = fixExponent(s);
        s = wrapCreateGraphics(s);
        s = s.replace(".drawString(", ".drawStr(");
        s = rewriteStringMethods(s);
        s = rewriteCollectionMethods(s);
        s = fixForOfDestructuring(s);
        s = fixParenObjectAssign(s);
        s = fixParenArrayAssign(s);
        s = fixParamDestructuring(s);
        s = fixArrayDestructuring(s);
        s = fixObjectDestructuring(s);
        return s;
    }

    // ------------------------------------------------- 0) 幂运算符 **
    //
    // Nashorn 不支持 ES2016 的 `a ** b`，改写为 Math.pow(a, b)。
    // 只处理常见的单项形式（标识符/下标/调用/括号表达式/数字），
    // 足以覆盖 LCD 脚本里的 vec[0] ** 2 这类写法。

    static String fixExponent(String s) {
        if (s.indexOf("**") < 0) return s;
        StringBuilder out = new StringBuilder(s.length() + 64);
        int i = 0;
        while (i < s.length()) {
            int p = s.indexOf("**", i);
            if (p < 0) {
                out.append(s, i, s.length());
                break;
            }
            // 排除 **= 与注释里的 **
            if (p + 2 < s.length() && s.charAt(p + 2) == '=') {
                out.append(s, i, p + 3);
                i = p + 3;
                continue;
            }
            int lhsEnd = p;
            while (lhsEnd > i && Character.isWhitespace(s.charAt(lhsEnd - 1))) lhsEnd--;
            int lhsStart = scanOperandBackward(s, lhsEnd, i);
            int rhsStart = p + 2;
            while (rhsStart < s.length() && Character.isWhitespace(s.charAt(rhsStart))) rhsStart++;
            int rhsEnd = scanOperandForward(s, rhsStart);

            if (lhsStart < 0 || rhsEnd <= rhsStart) {
                out.append(s, i, p + 2);
                i = p + 2;
                continue;
            }
            out.append(s, i, lhsStart);
            out.append("Math.pow(")
               .append(s, lhsStart, lhsEnd)
               .append(", ")
               .append(s, rhsStart, rhsEnd)
               .append(")");
            i = rhsEnd;
        }
        return out.toString();
    }

    /** 从 end 向前扫一个操作数的起点（支持 a.b、a[i]、f(x)、(expr)）。 */
    private static int scanOperandBackward(String s, int end, int limit) {
        if (end <= limit) return -1;
        int i = end;
        char c = s.charAt(i - 1);
        if (c == ')' || c == ']') {
            int depth = 0;
            char open = c == ')' ? '(' : '[';
            while (i > limit) {
                char ch = s.charAt(--i);
                if (ch == c) depth++;
                else if (ch == open) {
                    depth--;
                    if (depth == 0) break;
                }
            }
            // 前面紧跟的标识符（如 f(...) 或 arr[...]）
            while (i > limit && (Character.isLetterOrDigit(s.charAt(i - 1))
                    || s.charAt(i - 1) == '_' || s.charAt(i - 1) == '$'
                    || s.charAt(i - 1) == '.')) {
                i--;
            }
            return i;
        }
        if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.') {
            while (i > limit) {
                char ch = s.charAt(i - 1);
                if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$' || ch == '.') {
                    i--;
                } else if (ch == ']' || ch == ')') {
                    int d = 0;
                    char cl = ch, op = ch == ')' ? '(' : '[';
                    while (i > limit) {
                        char c2 = s.charAt(--i);
                        if (c2 == cl) d++;
                        else if (c2 == op) {
                            d--;
                            if (d == 0) break;
                        }
                    }
                } else {
                    break;
                }
            }
            return i;
        }
        return -1;
    }

    /** 从 start 向后扫一个操作数的终点。 */
    private static int scanOperandForward(String s, int start) {
        int i = start;
        if (i >= s.length()) return start;
        char c = s.charAt(i);
        if (c == '-' || c == '+') i++;
        if (i < s.length() && (s.charAt(i) == '(' || s.charAt(i) == '[')) {
            char open = s.charAt(i), close = open == '(' ? ')' : ']';
            int depth = 0;
            while (i < s.length()) {
                char ch = s.charAt(i++);
                if (ch == open) depth++;
                else if (ch == close) {
                    depth--;
                    if (depth == 0) break;
                }
            }
            return i;
        }
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$' || ch == '.') {
                i++;
            } else if (ch == '(' || ch == '[') {
                char open = ch, close = open == '(' ? ')' : ']';
                int depth = 0;
                while (i < s.length()) {
                    char c2 = s.charAt(i++);
                    if (c2 == open) depth++;
                    else if (c2 == close) {
                        depth--;
                        if (depth == 0) break;
                    }
                }
            } else {
                break;
            }
        }
        return i;
    }

    // ---------------------------------------------------- 1) 函数参数解构

    private static final Pattern PARAM_DESTRUCT =
            Pattern.compile("function\\s+(\\w+)\\s*\\(\\s*\\[([^\\]]+)\\]\\s*\\)");

    static String fixParamDestructuring(String s) {
        StringBuilder out = new StringBuilder();
        Matcher m = PARAM_DESTRUCT.matcher(s);
        int last = 0;
        while (m.find()) {
            out.append(s, last, m.start());
            String fn = m.group(1);
            String[] names = splitNames(m.group(2));
            String tmp = "__dp" + (counter++);

            // 定位函数体的 '{'
            int idx = m.end();
            while (idx < s.length() && Character.isWhitespace(s.charAt(idx))) idx++;

            StringBuilder decl = new StringBuilder();
            for (int i2 = 0; i2 < names.length; i2++) {
                decl.append(i2 == 0 ? " var " : ", ")
                    .append(names[i2]).append(" = ").append(tmp).append("[").append(i2).append("]");
            }
            decl.append(";");

            out.append("function ").append(fn).append("(").append(tmp).append(") {").append(decl);
            if (idx < s.length() && s.charAt(idx) == '{') {
                last = idx + 1;   // 跳过原有的 '{'
            } else {
                last = m.end();
            }
        }
        out.append(s.substring(last));
        return out.toString();
    }
    // ---------------------------------------------------- 2) 数组解构赋值

    private static final Pattern ARRAY_DESTRUCT =
            Pattern.compile("\\b(?:let|var|const)\\s*\\[([^\\]]+)\\]\\s*=\\s*([^;]+);");

    static String fixArrayDestructuring(String s) {
        Matcher m = ARRAY_DESTRUCT.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String[] names = splitNames(m.group(1));
            String expr = m.group(2).trim();
            String tmp = "__da" + (counter++);
            StringBuilder sb = new StringBuilder();
            sb.append("var ").append(tmp).append(" = ").append(expr).append("; var ");
            for (int i = 0; i < names.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(names[i]).append(" = ").append(tmp).append("[").append(i).append("]");
            }
            sb.append(";");
            m.appendReplacement(out, Matcher.quoteReplacement(sb.toString()));
        }
        m.appendTail(out);
        return out.toString();
    }

    // ---------------------------------------------------- 3) 对象解构赋值

    private static final Pattern OBJECT_DESTRUCT =
            Pattern.compile("\\b(?:let|var|const)\\s*\\{([^{}]+)\\}\\s*=\\s*([^;]+);");

    static String fixObjectDestructuring(String s) {
        Matcher m = OBJECT_DESTRUCT.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String[] fields = splitNames(m.group(1));
            String expr = m.group(2).trim();
            String tmp = "__do" + (counter++);
            StringBuilder sb = new StringBuilder();
            sb.append("var ").append(tmp).append(" = ").append(expr).append("; var ");
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) sb.append(", ");
                String f = fields[i];
                String key = f, alias = f;
                int colon = f.indexOf(':');
                if (colon > 0) {
                    key = f.substring(0, colon).trim();
                    alias = f.substring(colon + 1).trim();
                }
                sb.append(alias).append(" = ").append(tmp).append(".").append(key);
            }
            sb.append(";");
            m.appendReplacement(out, Matcher.quoteReplacement(sb.toString()));
        }
        m.appendTail(out);
        return out.toString();
    }

    // ------------------------------------------- 4) for (let [k,v] of map)

    private static final Pattern FOROF_DESTRUCT =
            Pattern.compile("for\\s*\\(\\s*(?:let|var|const)\\s*\\[([^\\]]+)\\]\\s*of\\s*([^)]+)\\)\\s*\\{");

    static String fixForOfDestructuring(String s) {
        Matcher m = FOROF_DESTRUCT.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String[] names = splitNames(m.group(1));
            String iterable = m.group(2).trim();
            String tmp = "__de" + (counter++);
            StringBuilder sb = new StringBuilder();
            // Java Map 需要用 entrySet 才能得到 [k,v]；用 __entries 辅助函数统一处理
            sb.append("for (var ").append(tmp).append(" of __entries(").append(iterable).append(")) { var ");
            for (int i = 0; i < names.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(names[i]).append(" = ").append(tmp).append("[").append(i).append("]");
            }
            sb.append(";");
            m.appendReplacement(out, Matcher.quoteReplacement(sb.toString()));
        }
        m.appendTail(out);
        return out.toString();
    }

    // -------------------------------- 5) ({a, b} = expr);  括号包裹的解构赋值
    // 用于给「已声明」的变量批量赋值，因此不能再加 var。

    private static final Pattern PAREN_OBJ_ASSIGN =
            Pattern.compile("\\(\\s*\\{([^{}]+)\\}\\s*=\\s*([^;)]+)\\)\\s*;");

    static String fixParenObjectAssign(String s) {
        Matcher m = PAREN_OBJ_ASSIGN.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String[] fields = splitNames(m.group(1));
            String expr = m.group(2).trim();
            String tmp = "__dq" + (counter++);
            StringBuilder sb = new StringBuilder();
            sb.append("{ var ").append(tmp).append(" = ").append(expr).append("; ");
            for (String f : fields) {
                String key = f, alias = f;
                int colon = f.indexOf(':');
                if (colon > 0) {
                    key = f.substring(0, colon).trim();
                    alias = f.substring(colon + 1).trim();
                }
                sb.append(alias).append(" = ").append(tmp).append(".").append(key).append("; ");
            }
            sb.append("}");
            m.appendReplacement(out, Matcher.quoteReplacement(sb.toString()));
        }
        m.appendTail(out);
        return out.toString();
    }

    // -------------------------------- 6) ([a, b] = expr);  数组形式
    private static final Pattern PAREN_ARR_ASSIGN =
            Pattern.compile("\\(\\s*\\[([^\\[\\]]+)\\]\\s*=\\s*([^;)]+)\\)\\s*;");

    static String fixParenArrayAssign(String s) {
        Matcher m = PAREN_ARR_ASSIGN.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String[] names = splitNames(m.group(1));
            String expr = m.group(2).trim();
            String tmp = "__dr" + (counter++);
            StringBuilder sb = new StringBuilder();
            sb.append("{ var ").append(tmp).append(" = ").append(expr).append("; ");
            for (int i = 0; i < names.length; i++) {
                sb.append(names[i]).append(" = ").append(tmp).append("[").append(i).append("]; ");
            }
            sb.append("}");
            m.appendReplacement(out, Matcher.quoteReplacement(sb.toString()));
        }
        m.appendTail(out);
        return out.toString();
    }
    // ------------------------- 7) createGraphics() -> SafeGraphics 包装
    //
    // Nashorn 调用 g.drawString(str, x, y) 时，若 x/y 是 JS number，
    // 无法在 (String,float,float) 与 (String,int,int) 之间消歧而抛异常。
    // 因此把脚本里所有 xxx.createGraphics() 的结果包成 SafeGraphics。

    private static final Pattern CREATE_GRAPHICS =
            Pattern.compile("([A-Za-z_$][\\w$.\\[\\]]*)\\s*\\.\\s*createGraphics\\s*\\(\\s*\\)");

    static String wrapCreateGraphics(String s) {
        Matcher m = CREATE_GRAPHICS.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String recv = m.group(1);
            m.appendReplacement(out,
                    Matcher.quoteReplacement("__wrapG(" + recv + ".createGraphics())"));
        }
        m.appendTail(out);
        return out.toString();
    }

    // ------------------- 8) Java String 上的 JS 字符串方法
    //
    // route.name / station.name 等返回 java.lang.String，
    // 而脚本按 JS 字符串使用 includes/startsWith/endsWith。
    // Nashorn 不做自动转换，故在源码层改写为全局辅助函数。

    private static final Pattern STR_INCLUDES =
            Pattern.compile("([A-Za-z_$][\\w$.\\[\\]]*(?:\\([^()]*\\))?)\\s*\\.\\s*includes\\s*\\(");
    private static final Pattern STR_STARTS =
            Pattern.compile("([A-Za-z_$][\\w$.\\[\\]]*(?:\\([^()]*\\))?)\\s*\\.\\s*startsWith\\s*\\(");
    private static final Pattern STR_ENDS =
            Pattern.compile("([A-Za-z_$][\\w$.\\[\\]]*(?:\\([^()]*\\))?)\\s*\\.\\s*endsWith\\s*\\(");

    static String rewriteStringMethods(String s) {
        s = rewriteCall(s, STR_INCLUDES, "__has");
        s = rewriteCall(s, STR_STARTS, "__startsWith");
        s = rewriteCall(s, STR_ENDS, "__endsWith");
        return s;
    }

    private static String rewriteCall(String s, Pattern p, String fn) {
        Matcher m = p.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String recv = m.group(1);
            // 数组也有 includes，只在明显是数组时跳过（保守起见仅跳过字面量数组）
            m.appendReplacement(out, Matcher.quoteReplacement(fn + "(" + recv + ", "));
        }
        m.appendTail(out);
        return out.toString();
    }
    // ------------------- 9) Java List 与 JS Array 统一适配
    private static final Pattern COLL_FIND =
            Pattern.compile("([A-Za-z_$][\\w$.\\[\\]]*)\\s*\\.\\s*find\\s*\\(");
    private static final Pattern COLL_FOREACH =
            Pattern.compile("([A-Za-z_$][\\w$.\\[\\]]*)\\s*\\.\\s*forEach\\s*\\(");
    private static final Pattern COLL_FINDINDEX =
            Pattern.compile("([A-Za-z_$][\\w$.\\[\\]]*)\\s*\\.\\s*findIndex\\s*\\(");
    private static final Pattern COLL_SOME =
            Pattern.compile("([A-Za-z_$][\\w$.\\[\\]]*)\\s*\\.\\s*some\\s*\\(");
    private static final Pattern COLL_EVERY =
            Pattern.compile("([A-Za-z_$][\\w$.\\[\\]]*)\\s*\\.\\s*every\\s*\\(");
    private static final Pattern COLL_MAP =
            Pattern.compile("([A-Za-z_$][\\w$.\\[\\]]*)\\s*\\.\\s*map\\s*\\(");
    private static final Pattern COLL_FILTER =
            Pattern.compile("([A-Za-z_$][\\w$.\\[\\]]*)\\s*\\.\\s*filter\\s*\\(");
    private static final Pattern STR_PADSTART =
            Pattern.compile("([A-Za-z_$][\\w$.\\[\\]]*(?:\\.toString\\(\\))?)\\s*\\.\\s*padStart\\s*\\(");
    private static final Pattern STR_PADEND =
            Pattern.compile("([A-Za-z_$][\\w$.\\[\\]]*(?:\\.toString\\(\\))?)\\s*\\.\\s*padEnd\\s*\\(");

    static String rewriteCollectionMethods(String s) {
        s = s.replace("Array.isArray(", "__isArrayLike(");
        s = rewriteCall(s, COLL_FIND, "__find");
        s = rewriteCall(s, COLL_FOREACH, "__forEach");
        s = rewriteCall(s, COLL_FINDINDEX, "__findIndex");
        s = rewriteCall(s, COLL_SOME, "__some");
        s = rewriteCall(s, COLL_EVERY, "__every");
        s = rewriteCall(s, COLL_MAP, "__map");
        s = rewriteCall(s, COLL_FILTER, "__filter");
        s = rewritePad(s, ".padStart(", "__padStart");
        s = rewritePad(s, ".padEnd(", "__padEnd");
        return s;
    }

    /** 专门处理 expr.padStart(...)，正确识别 (a+b).toString() 这类接收者。 */
    private static String rewritePad(String s, String suffix, String fn) {
        StringBuilder out = new StringBuilder(s.length() + 32);
        int cursor = 0;
        while (true) {
            int p = s.indexOf(suffix, cursor);
            if (p < 0) { out.append(s, cursor, s.length()); break; }
            int end = p;
            while (end > cursor && Character.isWhitespace(s.charAt(end - 1))) end--;
            int start = findReceiverStart(s, end, cursor);
            if (start < 0) {
                out.append(s, cursor, p + suffix.length());
                cursor = p + suffix.length();
                continue;
            }
            out.append(s, cursor, start)
               .append(fn).append('(')
               .append(s, start, end).append(", ");
            cursor = p + suffix.length();
        }
        return out.toString();
    }

    private static int findReceiverStart(String s, int end, int limit) {
        int i = end;
        // 连续向前吞掉 .method()、[index]、(group) 与标识符
        while (i > limit) {
            char ch = s.charAt(i - 1);
            if (ch == ')' || ch == ']') {
                char close = ch, open = ch == ')' ? '(' : '[';
                int depth = 0;
                do {
                    ch = s.charAt(--i);
                    if (ch == close) depth++;
                    else if (ch == open) depth--;
                } while (i > limit && depth > 0);
                // 若是方法调用，继续吞掉前面的 .method；若是分组表达式也允许继续
                while (i > limit && Character.isWhitespace(s.charAt(i - 1))) i--;
                while (i > limit) {
                    char c = s.charAt(i - 1);
                    if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.') i--;
                    else break;
                }
                continue;
            }
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$' || ch == '.') {
                i--;
                continue;
            }
            break;
        }
        return i < end ? i : -1;
    }

    /**
     * 把任意 operand.method(args) 改写为 fn(operand,args)。
     * operand 可为标识符、下标、函数调用或任意括号表达式。
     */
    private static String rewritePostfixCall(String s, String suffix, String fn) {
        StringBuilder out = new StringBuilder(s.length() + 32);
        int cursor = 0;
        while (true) {
            int p = s.indexOf(suffix, cursor);
            if (p < 0) {
                out.append(s, cursor, s.length());
                break;
            }
            int lhsEnd = p;
            while (lhsEnd > cursor && Character.isWhitespace(s.charAt(lhsEnd - 1))) lhsEnd--;
            int lhsStart = scanOperandBackward(s, lhsEnd, cursor);
            if (lhsStart < 0) {
                out.append(s, cursor, p + suffix.length());
                cursor = p + suffix.length();
                continue;
            }
            out.append(s, cursor, lhsStart);
            out.append(fn).append('(').append(s, lhsStart, lhsEnd).append(", ");
            cursor = p + suffix.length();
        }
        return out.toString();
    }
    private static String[] splitNames(String raw) {
        String[] parts = raw.split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    /**
     * 注入到 JS 环境的辅助函数：把 Java Map / JS 对象 / 数组统一转成 [k,v] 数组序列。
     */
    public static final String RUNTIME_HELPERS =
            // Java String 缺少 JS 的 includes 等方法（ANTE 用 GraalJS 会自动
            // 转换，Nashorn 不会）。改由源码层把 a.includes(b) 重写为
            // __has(a,b)，见 transform()。
            "if (typeof Number.isNaN !== 'function') {\n" +
            "  Number.isNaN = function(v) { return typeof v === 'number' && isNaN(v); };\n" +
            "}\n" +
            "if (typeof Number.isInteger !== 'function') {\n" +
            "  Number.isInteger = function(v) { return typeof v === 'number' && isFinite(v) && Math.floor(v) === v; };\n" +
            "}\n" +
            "if (typeof Number.isFinite !== 'function') {\n" +
            "  Number.isFinite = function(v) { return typeof v === 'number' && isFinite(v); };\n" +
            "}\n" +
            "if (typeof Object.assign !== 'function') {\n" +
            "  Object.assign = function(target) {\n" +
            "    if (target === null || target === undefined) throw new TypeError('Cannot convert undefined or null to object');\n" +
            "    var to = Object(target);\n" +
            "    for (var index = 1; index < arguments.length; index++) {\n" +
            "      var nextSource = arguments[index];\n" +
            "      if (nextSource !== null && nextSource !== undefined) {\n" +
            "        for (var nextKey in nextSource) {\n" +
            "          if (Object.prototype.hasOwnProperty.call(nextSource, nextKey)) to[nextKey] = nextSource[nextKey];\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "    return to;\n" +
            "  };\n" +
            "}\n" +
            "function __wrapDisplayHelper(target) {\n" +
            "  var fn = function(a, b, c) {\n" +
            "    if (typeof target === 'function') {\n" +
            "      try { return (this instanceof fn) ? new target(a, b, c) : target(a, b, c); } catch(e){}\n" +
            "    }\n" +
            "    return (this instanceof fn) ? new DH(a, b) : DH(a, b);\n" +
            "  };\n" +
            "  function DH(w, h) { this.width = w; this.height = h; }\n" +
            "  fn.createGraphics = function(w, h) {\n" +
            "    if (target && typeof target.createGraphics === 'function') try { return target.createGraphics(w, h); } catch(e){}\n" +
            "    return Resources.createTexture(w || 1024, h || 1024);\n" +
            "  };\n" +
            "  fn.graphicsFor = function(t) {\n" +
            "    if (target && typeof target.graphicsFor === 'function') try { return target.graphicsFor(t); } catch(e){}\n" +
            "    return t ? (t.createGraphics ? t.createGraphics() : (t.graphics ? t.graphics : t)) : null;\n" +
            "  };\n" +
            "  fn.prototype.createGraphics = function(w, h) { return fn.createGraphics(w || this.width, h || this.height); };\n" +
            "  fn.prototype.graphicsFor = function(t) { return fn.graphicsFor(t); };\n" +
            "  return fn;\n" +
            "}\n" +
            "\n" +
            "var __dhCurrent = __wrapDisplayHelper(null);\n" +
            "\n" +
            "if (typeof DisplayHelperRE !== 'function') {\n" +
            "  function DisplayHelperRE(cfg, stage, color) {\n" +
            "    this.cfg = cfg; this.stage = stage; this.color = color;\n" +
            "  }\n" +
            "  DisplayHelperRE.prototype.create = function() { return true; };\n" +
            "  DisplayHelperRE.prototype.upload = function() {};\n" +
            "  DisplayHelperRE.prototype.close = function() {};\n" +
            "  DisplayHelperRE.prototype.graphics = function() { return null; };\n" +
            "  DisplayHelperRE.prototype.graphicsFor = function(s) { return null; };\n" +
            "}\n" +
            "function __has(s, t) {\n" +
            "  if (s === null || s === undefined) return false;\n" +
            "  return String(s).indexOf(String(t)) >= 0;\n" +
            "}\n" +
            "function __startsWith(s, t) {\n" +
            "  if (s === null || s === undefined) return false;\n" +
            "  return String(s).lastIndexOf(String(t), 0) === 0;\n" +
            "}\n" +
            "function __endsWith(s, t) {\n" +
            "  if (s === null || s === undefined) return false;\n" +
            "  var a = String(s), b = String(t);\n" +
            "  return b.length <= a.length && a.indexOf(b, a.length - b.length) !== -1;\n" +
            "}\n" +
            "function __isArrayLike(o) {\n" +
            "  if (o === null || o === undefined) return false;\n" +
            "  try { if (Array.isArray(o)) return true; } catch(e) {}\n" +
            "  try { return typeof o.size === 'function' && typeof o.get === 'function'; } catch(e) {}\n" +
            "  return false;\n" +
            "}\n" +
            "function __toArray(o) {\n" +
            "  if (o === null || o === undefined) return [];\n" +
            "  try { if (Array.isArray(o)) return o; } catch(e) {}\n" +
            "  var a=[];\n" +
            "  try { if (typeof o.size === 'function' && typeof o.get === 'function') {\n" +
            "    for(var i=0;i<o.size();i++) a.push(o.get(i)); return a; } } catch(e) {}\n" +
            "  try { var it=o.iterator(); while(it.hasNext()) a.push(it.next()); } catch(e) {}\n" +
            "  return a;\n" +
            "}\n" +
            "function __find(o, fn) { var a=__toArray(o); for(var i=0;i<a.length;i++) if(fn(a[i],i,a)) return a[i]; return undefined; }\n" +
            "function __forEach(o, fn) { var a=__toArray(o); for(var i=0;i<a.length;i++) fn(a[i],i,a); }\n" +
            "function __findIndex(o, fn) { var a=__toArray(o); for(var i=0;i<a.length;i++) if(fn(a[i],i,a)) return i; return -1; }\n" +
            "function __some(o, fn) { return __findIndex(o,fn)>=0; }\n" +
            "function __every(o, fn) { var a=__toArray(o); for(var i=0;i<a.length;i++) if(!fn(a[i],i,a)) return false; return true; }\n" +
            "function __map(o, fn) { var a=__toArray(o),r=[]; for(var i=0;i<a.length;i++) r.push(fn(a[i],i,a)); return r; }\n" +
            "function __filter(o, fn) { var a=__toArray(o),r=[]; for(var i=0;i<a.length;i++) if(fn(a[i],i,a)) r.push(a[i]); return r; }\n" +
            "function __padStart(s,n,p) { s=String(s); p=p===undefined?' ':String(p); if(!p)p=' '; while(s.length<n)s=p+s; return s.substring(s.length-n); }\n" +
            "function __padEnd(s,n,p) { s=String(s); p=p===undefined?' ':String(p); if(!p)p=' '; while(s.length<n)s=s+p; return s.substring(0,n); }\n" +
            "function __entries(o) {\n" +
            "  var out = [];\n" +
            "  if (o === null || o === undefined) return out;\n" +
            "  try {\n" +
            "    if (typeof o.entrySet === 'function') {\n" +
            "      var it = o.entrySet().iterator();\n" +
            "      while (it.hasNext()) { var e = it.next(); out.push([e.getKey(), e.getValue()]); }\n" +
            "      return out;\n" +
            "    }\n" +
            "  } catch (ex) {}\n" +
            "  try { if (Array.isArray(o)) return o; } catch (ex) {}\n" +
            "  try {\n" +
            "    if (typeof o.iterator === 'function') {\n" +
            "      var it2 = o.iterator();\n" +
            "      while (it2.hasNext()) { out.push(it2.next()); }\n" +
            "      return out;\n" +
            "    }\n" +
            "  } catch (ex) {}\n" +
            "  try { for (var k in o) { out.push([k, o[k]]); } } catch (ex) {}\n" +
            "  return out;\n" +
            "}\n";
}