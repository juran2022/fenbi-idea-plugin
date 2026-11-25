package com.ranju.fenbi.ui;

import com.google.gson.Gson;
import com.ranju.fenbi.ui.Sheet.Item;
import com.ranju.fenbi.ui.Sheet.Mode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把 Sheet 渲染成 HTML，交给 JCEF 显示。
 * 交互（键盘作答、计时、材料固定、高亮、看大图）在 resources/sheet/sheet.js 里。
 */
final class SheetRenderer {
    private static final String LETTERS = "ABCDEFGHIJ";
    private static final String CN = "一二三四五六七八九十";
    private static final String CSS = resource("/sheet/sheet.css");
    private static final String JS = resource("/sheet/sheet.js");
    private static final Gson GSON = new Gson();

    private static final String HELP = "<div id='help' class='overlay'><div class='box'><b>快捷键</b><table>"
            + "<tr><td><kbd>1</kbd>–<kbd>4</kbd> / <kbd>A</kbd>–<kbd>D</kbd></td><td>选择选项</td></tr>"
            + "<tr><td><kbd>←</kbd> <kbd>→</kbd> / <kbd>K</kbd> <kbd>J</kbd></td><td>上一题 / 下一题</td></tr>"
            + "<tr><td><kbd>Home</kbd> <kbd>End</kbd></td><td>第一题 / 最后一题</td></tr>"
            + "<tr><td><kbd>空格</kbd></td><td>暂停 / 继续计时</td></tr>"
            + "<tr><td><kbd>Ctrl</kbd>+<kbd>Enter</kbd></td><td>交卷</td></tr>"
            + "<tr><td><kbd>W</kbd></td><td>下一道错题（解析页）</td></tr>"
            + "<tr><td><kbd>M</kbd></td><td>材料固定在顶部 / 取消</td></tr>"
            + "<tr><td><kbd>H</kbd></td><td>高亮选中的文字（双击高亮处取消）</td></tr>"
            + "<tr><td>点击图片</td><td>放大查看（选项里的图片要按住 Alt 点）</td></tr>"
            + "<tr><td><kbd>Esc</kbd></td><td>关闭弹层</td></tr>"
            + "<tr><td><kbd>?</kbd></td><td>显示 / 隐藏本帮助</td></tr>"
            + "</table><div class='tip'>先点一下卷面，键盘才会生效</div></div></div>";

    private SheetRenderer() {
    }

    static String render(Sheet s, String bridge, SheetStyle st) {
        Map<Integer, int[]> ranges = materialRanges(s);
        String mode = s.mode.name().toLowerCase();
        StringBuilder b = new StringBuilder(32 * 1024);
        b.append("<!DOCTYPE html><html><head><meta charset='utf-8'><style>").append(st.cssVars())
                .append("</style><style>").append(CSS).append("</style></head>")
                .append("<body class='mode-").append(mode)
                .append(st.dark() ? " dark" : "").append(st.stealth() ? " stealth" : "")
                .append(s.hidePauseInfo ? " pause-quiet" : "").append("'>");

        // 顶部固定栏：进度 / 计时 / 按钮 + 固定材料区
        b.append("<div id='top'><div id='bar'><span id='prog'></span><span id='clock'>")
                .append(s.mode == Mode.SOLUTION ? esc(s.scoreLine) : "")
                .append("</span><span id='flash'></span><span class='sp'></span>");
        if (!ranges.isEmpty()) b.append("<button id='b-pin' title='把材料固定在顶部（M）'>📄 材料</button>");
        if (s.mode == Mode.SOLUTION) b.append("<button id='b-wrong' title='下一道错题（W）'>下一道错题</button>");
        if (s.mode == Mode.ANSWER) b.append("<button id='b-pause' title='暂停计时（空格）'>⏸ 暂停</button>");
        b.append("<button id='b-help' title='快捷键（?）'>?</button></div>")
                .append("<div id='pin'><div id='pin-hd'><span id='pin-title'></span><span class='sp'></span>")
                .append("<button id='b-pinsize' title='放大 / 缩小'>↕</button>")
                .append("<button id='b-unpin' title='取消固定（M）'>✕</button></div><div id='pin-body'></div></div></div>");

        b.append("<div class='hd'>").append(esc(s.title)).append("</div>");

        Set<Integer> shown = new HashSet<>();
        for (int i = 0; i < s.items.size(); i++) {
            Item it = s.items.get(i);
            for (Integer k : it.materialRefs) {
                if (shown.add(k)) appendMaterial(b, k, s.materials.get(k), ranges.get(k));
            }
            appendItem(b, s, it, i);
        }
        if (s.mode == Mode.ANSWER) {
            b.append("<div class='tip'>点一下卷面即可用键盘作答：1–4 / A–D 选择，←→ 切题，空格暂停，Ctrl+Enter 交卷，? 查看全部快捷键</div>");
        } else if (s.mode == Mode.REVIEW) {
            b.append("<div class='tip'>选完立即显示答案和解析；连续答对达到设定次数的题会自动标记为「已掌握」</div>");
        }
        b.append(HELP).append("<div id='lightbox' class='overlay'><img alt=''></div>");
        if (s.mode == Mode.ANSWER) {
            b.append("<div id='pause-ov'><div class='box'><div class='big'>⏸ 已暂停</div>")
                    .append("<div id='pause-reason'></div><div id='pause-time'></div>")
                    .append("<div class='tip'>点击任意位置或按 空格 继续 · 暂停期间不计时，题目已隐藏</div></div></div>");
        }

        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("mode", mode);
        cfg.put("start", s.start);
        cfg.put("deadline", s.deadline);
        cfg.put("qsec", s.questionSeconds);
        cfg.put("autoNext", s.autoNext);
        cfg.put("autoSubmit", s.autoSubmit);
        cfg.put("pin", s.pinMaterial);
        b.append("<script>window.CFG=").append(GSON.toJson(cfg))
                .append(";window.__send=function(p){").append(bridge).append("};</script>")
                .append("<script>").append(JS).append("</script></body></html>");
        return b.toString();
    }

    // ---------------------------------------------------------------- 片段

    private static void appendMaterial(StringBuilder b, int k, String html, int[] range) {
        String title = "材料" + cn(k + 1) + (range == null ? ""
                : range[0] == range[1] ? "（第 " + range[0] + " 题）" : "（第 " + range[0] + "–" + range[1] + " 题）");
        b.append("<div class='mat' id='mat-").append(k).append("' data-title='").append(esc(title)).append("'>")
                .append("<div class='mat-hd'><b>").append(esc(title)).append("</b><span class='sp'></span></div>")
                .append("<div class='mat-body'>").append(clean(html)).append("</div></div>");
    }

    private static void appendItem(StringBuilder b, Sheet s, Item it, int i) {
        boolean sol = s.mode == Mode.SOLUTION;
        boolean review = s.mode == Mode.REVIEW;
        boolean right = it.correct != null && it.correct.equals(it.mine);

        b.append("<div class='q");
        if (sol) b.append(right ? " right" : " wrong");
        if (it.timeout) b.append(" timeout");
        b.append("' data-q='").append(it.id).append("' data-i='").append(i).append("'");
        if (!it.materialRefs.isEmpty()) b.append(" data-m='").append(it.materialRefs.get(0)).append("'");
        if (review && it.correct != null) b.append(" data-ans='").append(esc(it.correct)).append("'");
        b.append("><div class='stem'><span class='no'>").append(i + 1).append(".</span>")
                .append(clean(it.content)).append("</div>");
        if (it.meta != null) b.append("<div class='meta'>").append(esc(it.meta)).append("</div>");
        for (Integer k : it.materialRefs) {
            b.append("<span class='mref' title='把材料固定在顶部（M）'>📄 材料").append(cn(k + 1)).append("</span> ");
        }

        b.append("<ul class='opts'>");
        List<String> opts = it.options;
        for (int k = 0; k < opts.size(); k++) {
            String c = String.valueOf(k);
            String cls = "";
            if (s.mode == Mode.ANSWER && c.equals(it.mine)) cls = " sel";
            if (sol) cls = c.equals(it.correct) ? " ok" : (c.equals(it.mine) ? " bad" : "");
            b.append("<li class='opt").append(cls).append("' data-c='").append(c).append("'><span class='key'>")
                    .append(letter(k)).append(".</span> ").append(clean(opts.get(k))).append("</li>");
        }
        if (opts.isEmpty()) b.append("<li class='tip'>（这种题型暂不支持，请到粉笔网页作答）</li>");
        b.append("</ul>");

        if (sol) {
            String verdict = it.mine == null ? (it.timeout ? "⏱ 超时未作答" : "未作答")
                    : right ? "✔ 回答正确" : "✘ 回答错误";
            b.append("<div class='verdict ").append(right ? "v-ok" : "v-bad").append("'>").append(verdict)
                    .append(" · 你的答案：").append(choiceLetter(it.mine))
                    .append(" · 正确答案：").append(choiceLetter(it.correct));
            if (it.spentSeconds != null) b.append(" · 用时 ").append(it.spentSeconds).append(" 秒");
            b.append("</div>");
        }
        if (review) b.append("<div class='verdict'></div>");
        if ((sol || review) && it.solution != null && !it.solution.isBlank()) {
            b.append("<div class='sol'><b>解析</b>").append(clean(it.solution)).append("</div>");
        }
        if ((sol || review) && it.source != null && !it.source.isBlank()) {
            b.append("<div class='src'>来源：").append(esc(it.source)).append("</div>");
        }
        b.append("</div>");
    }

    /** 每份材料被哪些题引用：k -> [第一题, 最后一题]（从 1 开始）。 */
    private static Map<Integer, int[]> materialRanges(Sheet s) {
        Map<Integer, int[]> out = new LinkedHashMap<>();
        for (int i = 0; i < s.items.size(); i++) {
            for (Integer k : s.items.get(i).materialRefs) {
                int no = i + 1;
                int[] r = out.get(k);
                if (r == null) out.put(k, new int[]{no, no});
                else r[1] = Math.max(r[1], no);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 工具

    static String letter(int k) {
        return k < LETTERS.length() ? String.valueOf(LETTERS.charAt(k)) : String.valueOf(k + 1);
    }

    static String choiceLetter(String choice) {
        if (choice == null || choice.isBlank()) return "—";
        StringBuilder sb = new StringBuilder();
        for (String part : choice.split(",")) {
            try {
                sb.append(letter(Integer.parseInt(part.trim())));
            } catch (NumberFormatException e) {
                sb.append(part);
            }
        }
        return sb.toString();
    }

    private static String cn(int n) {
        return n >= 1 && n <= CN.length() ? String.valueOf(CN.charAt(n - 1)) : String.valueOf(n);
    }

    /** 粉笔返回的是 HTML 片段：去掉脚本和事件属性，补全协议相对地址。 */
    static String clean(String html) {
        if (html == null) return "";
        return html.replaceAll("(?is)<script.*?</script>", "")
                .replaceAll("(?i)\\son[a-z]+\\s*=", " data-x=")
                .replace("src=\"//", "src=\"https://")
                .replace("src='//", "src='https://");
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String resource(String path) {
        try (InputStream in = SheetRenderer.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("缺少资源 " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
