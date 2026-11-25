package com.ranju.fenbi.ui;

import com.ranju.fenbi.log.PracticeLog;
import com.ranju.fenbi.stats.Stats;
import com.ranju.fenbi.stats.Stats.ModuleStat;
import com.ranju.fenbi.stats.Stats.Point;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 统计页 HTML。图表是 Java 直接生成的 SVG，不依赖任何 JS 库，离线可用。 */
final class StatsRenderer {
    private static final String[] PALETTE = {"#8C8C8C", "#5B8FF9", "#5AD8A6", "#F6BD16", "#E8684A", "#9270CA", "#6DC8EC", "#FF9D4D"};
    private static final int W = 320;
    private static final DateTimeFormatter TIP = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M/d");

    private static final String CSS = """
            *{box-sizing:border-box}
            body{margin:0;padding:10px 12px 24px;font-family:var(--font);font-size:var(--fs);color:var(--fg);background:var(--bg);line-height:1.6}
            .range{display:flex;gap:6px;margin-bottom:10px}
            .range button{font:inherit;font-size:.88em;color:inherit;background:transparent;border:1px solid rgba(128,128,128,.35);border-radius:4px;padding:0 10px;cursor:pointer}
            .range button:hover{background:rgba(128,128,128,.12)}
            .range button.on{background:rgba(59,130,246,.18);border-color:#3b82f6}
            .kpis{display:grid;grid-template-columns:repeat(auto-fill,minmax(110px,1fr));gap:8px;margin-bottom:12px}
            .kpi{border:1px solid rgba(128,128,128,.25);border-radius:6px;padding:6px 10px}
            .kpi .v{font-size:1.3em;font-weight:600}
            .kpi .l{font-size:.8em;opacity:.65}
            .concl{border-left:3px solid #3b82f6;background:rgba(59,130,246,.08);padding:8px 10px;border-radius:0 6px 6px 0;margin-bottom:6px}
            .note{font-size:.8em;opacity:.6;margin-bottom:12px}
            h3{font-size:1em;margin:18px 0 6px}
            .cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:10px}
            .card{border:1px solid rgba(128,128,128,.25);border-radius:6px;padding:6px 8px}
            .card .t{display:flex;justify-content:space-between;align-items:baseline;gap:6px;flex-wrap:wrap}
            .tag{font-size:.8em;padding:0 6px;border-radius:8px;white-space:nowrap}
            .pos{color:#3fb950;border:1px solid rgba(63,185,80,.6)}
            .neu{opacity:.75;border:1px solid rgba(128,128,128,.4)}
            .neg{color:#f85149;border:1px solid rgba(248,81,73,.6)}
            .sub{font-size:.8em;opacity:.65}
            svg{display:block;width:100%;max-width:460px;height:auto}
            svg text{fill:currentColor;font-size:11px}
            .legend{display:flex;flex-wrap:wrap;gap:2px 12px;font-size:.85em;margin:4px 0}
            .legend i{display:inline-block;width:10px;height:10px;border-radius:2px;margin-right:4px;vertical-align:-1px}
            .tbl{overflow-x:auto}
            table{border-collapse:collapse;font-size:.88em;white-space:nowrap}
            th,td{border-bottom:1px solid rgba(128,128,128,.25);padding:3px 8px;text-align:right}
            th:first-child,td:first-child{text-align:left}
            th{font-weight:600;opacity:.8}
            .empty{opacity:.7;padding:30px 0;text-align:center}
            .foot{font-size:.78em;opacity:.55;margin-top:16px;word-break:break-all}
            """;

    private StatsRenderer() {
    }

    static String render(Stats st, int rangeDays, String bridge, SheetStyle style) {
        StringBuilder b = new StringBuilder(24 * 1024);
        b.append("<!DOCTYPE html><html><head><meta charset='utf-8'><style>").append(style.cssVars()).append(CSS)
                .append("</style></head><body><div class='range'>");
        range(b, 7, "近 7 天", rangeDays);
        range(b, 30, "近 30 天", rangeDays);
        range(b, 0, "全部", rangeDays);
        b.append("</div>");

        if (st.sessions == 0) {
            b.append("<div class='empty'>").append(rangeDays > 0 ? "这段时间没有练习记录。" : "还没有练习记录。")
                    .append("<br>在「练习」页交一次卷，这里就会出现统计。</div>");
        } else {
            kpis(b, st);
            conclusion(b, st);

            b.append("<h3>练习时长 vs 正确率</h3><div class='sub'>每个点是一次练习；横轴是这次之前该模块累计练了多久，虚线是趋势线</div><div class='cards'>");
            for (int i = 0; i < st.modules.size(); i++) scatterCard(b, st.modules.get(i), i);
            b.append("</div>");

            b.append("<h3>各模块累计练习时长</h3>");
            List<String> labels = new ArrayList<>();
            List<String> colors = new ArrayList<>();
            double[] minutes = new double[st.modules.size()];
            String[] minuteText = new String[st.modules.size()];
            double[] acc = new double[st.modules.size()];
            String[] accText = new String[st.modules.size()];
            double maxMin = 0;
            for (int i = 0; i < st.modules.size(); i++) {
                ModuleStat ms = st.modules.get(i);
                labels.add(ms.module);
                colors.add(colorOf(ms.module));
                minutes[i] = ms.seconds / 60.0;
                minuteText[i] = duration(ms.seconds);
                maxMin = Math.max(maxMin, minutes[i]);
                acc[i] = Double.isNaN(ms.accuracy()) ? 0 : ms.accuracy() * 100;
                accText[i] = pct(ms.accuracy()) + "（近5次 " + pct(ms.recentAccuracy) + "）";
            }
            b.append(hbars(labels, minutes, colors, minuteText, maxMin));
            b.append("<h3>各模块正确率</h3>").append(hbars(labels, acc, colors, accText, 100));

            b.append("<h3>近 14 天每日练习时长</h3>");
            daily(b, st);

            table(b, st);
        }

        b.append("<div class='foot'>数据来自 ").append(esc(PracticeLog.file().toString()))
                .append("，每次交卷追加一行；可以用 Excel 打开或自己分析。</div>")
                .append("<script>document.addEventListener('click',function(e){var t=e.target.closest&&e.target.closest('[data-r]');")
                .append("if(!t)return;var p='range|'+t.getAttribute('data-r');").append(bridge).append("});</script>")
                .append("</body></html>");
        return b.toString();
    }

    // ---------------------------------------------------------------- 区块

    private static void range(StringBuilder b, int days, String label, int current) {
        b.append("<button data-r='").append(days).append("'").append(days == current ? " class='on'" : "")
                .append(">").append(label).append("</button>");
    }

    private static void kpis(StringBuilder b, Stats st) {
        b.append("<div class='kpis'>");
        kpi(b, duration(st.seconds), "练习时长");
        kpi(b, st.sessions + " 次", "练习次数");
        kpi(b, String.valueOf(st.questions), "做题数");
        kpi(b, pct(st.accuracy()), "总正确率");
        kpi(b, st.days + " 天", "练习天数");
        b.append("</div>");
    }

    private static void kpi(StringBuilder b, String value, String label) {
        b.append("<div class='kpi'><div class='v'>").append(esc(value)).append("</div><div class='l'>")
                .append(label).append("</div></div>");
    }

    private static void conclusion(StringBuilder b, Stats st) {
        b.append("<div class='concl'>");
        ModuleStat best = st.strongest();
        if (best != null) {
            b.append("与练习时长正相关最明显的是 <b>").append(esc(best.module)).append("</b>：r = ")
                    .append(num(best.r, 2)).append("（").append(best.points.size()).append(" 次练习）");
            if (!Double.isNaN(best.slopePerHour)) {
                b.append("，每多练 1 小时正确率约 ").append(signed(best.slopePerHour)).append(" 个百分点");
            }
            b.append("。");
        } else {
            List<ModuleStat> enough = st.modules.stream().filter(ModuleStat::enough).toList();
            if (enough.isEmpty()) {
                b.append("数据还不够：每个模块至少要 ").append(Stats.MIN_SAMPLES).append(" 次练习才能判断相关性。目前：");
                List<String> parts = new ArrayList<>();
                for (ModuleStat ms : st.modules) parts.add(esc(ms.module) + " " + ms.sessions + " 次");
                b.append(String.join("、", parts)).append("。");
            } else {
                b.append("目前没有模块和练习时长呈现明显的正相关（r 都低于 0.3）。继续积累记录再看。");
            }
        }
        b.append("</div><div class='note'>r 是“每次练习的正确率”与“此前该模块累计练习时长”的相关系数（≥0.5 明显正相关，0.3–0.5 弱正相关）。")
                .append("相关不等于因果，题目难度、组卷设置也会影响正确率。</div>");
    }

    private static void scatterCard(StringBuilder b, ModuleStat ms, int idx) {
        String verdict = ms.verdict();
        String cls = verdict.contains("正相关") ? "pos" : verdict.equals("负相关") ? "neg" : "neu";
        b.append("<div class='card'><div class='t'><b>").append(esc(ms.module)).append("</b><span class='tag ")
                .append(cls).append("'>");
        if (ms.enough() && !Double.isNaN(ms.r)) b.append("r = ").append(num(ms.r, 2)).append(" · ");
        b.append(verdict).append("</span></div><div class='sub'>").append(ms.sessions).append(" 次 · 正确率 ")
                .append(pct(ms.accuracy()));
        if (!ms.enough()) {
            b.append(" · 还需 ").append(Stats.MIN_SAMPLES - ms.points.size()).append(" 次才能判断");
        } else if (!Double.isNaN(ms.slopePerHour)) {
            b.append(" · 每多练 1 小时 ").append(signed(ms.slopePerHour)).append(" 个百分点");
        }
        b.append("</div>").append(scatter(ms, colorOf(ms.module), idx)).append("</div>");
    }

    private static void table(StringBuilder b, Stats st) {
        b.append("<h3>明细</h3><div class='tbl'><table><tr><th>模块</th><th>次数</th><th>题数</th><th>时长</th>")
                .append("<th>正确率</th><th>近5次</th><th>每题用时</th><th>r</th><th>结论</th></tr>");
        for (ModuleStat ms : st.modules) {
            b.append("<tr><td>").append(esc(ms.module)).append("</td><td>").append(ms.sessions)
                    .append("</td><td>").append(ms.questions)
                    .append("</td><td>").append(duration(ms.seconds))
                    .append("</td><td>").append(pct(ms.accuracy()))
                    .append("</td><td>").append(pct(ms.recentAccuracy))
                    .append("</td><td>").append(Double.isNaN(ms.secondsPerQuestion()) ? "—"
                            : Math.round(ms.secondsPerQuestion()) + " 秒")
                    .append("</td><td>").append(ms.enough() && !Double.isNaN(ms.r) ? num(ms.r, 2) : "—")
                    .append("</td><td>").append(ms.verdict()).append("</td></tr>");
        }
        b.append("</table></div>");
    }

    // ---------------------------------------------------------------- SVG

    private static String hbars(List<String> labels, double[] values, List<String> colors, String[] valueText,
                                double max) {
        int rowH = 24;
        int left = 92;
        int right = 8;
        int h = labels.size() * rowH + 6;
        StringBuilder s = svgOpen(W, h);
        double scale = max > 0 ? (W - left - right - 110) / max : 0;
        for (int i = 0; i < labels.size(); i++) {
            double y = 3 + i * rowH;
            double bw = values[i] > 0 ? Math.max(2, values[i] * scale) : 0;
            s.append(text(left - 6, y + rowH / 2.0 + 4, labels.get(i), "end"));
            s.append("<rect x='").append(f(left)).append("' y='").append(f(y + 5)).append("' width='").append(f(bw))
                    .append("' height='").append(rowH - 10).append("' rx='2' fill='").append(colors.get(i))
                    .append("'><title>").append(esc(labels.get(i) + "：" + valueText[i])).append("</title></rect>");
            s.append(text(left + bw + 5, y + rowH / 2.0 + 4, valueText[i], "start"));
        }
        return s.append("</svg>").toString();
    }

    private static String scatter(ModuleStat ms, String color, int idx) {
        int h = 150;
        int l = 34;
        int r = 10;
        int t = 8;
        int bottom = 30;
        double pw = W - l - r;
        double ph = h - t - bottom;
        double maxX = 0;
        for (Point p : ms.points) maxX = Math.max(maxX, p.cumMinutes() / 60);
        double xMax = niceMax(maxX * 1.05);
        StringBuilder s = svgOpen(W, h);
        s.append("<defs><clipPath id='c").append(idx).append("'><rect x='").append(l).append("' y='").append(t)
                .append("' width='").append(f(pw)).append("' height='").append(f(ph)).append("'/></clipPath></defs>");
        for (int v = 0; v <= 100; v += 25) {
            double y = t + ph - v / 100.0 * ph;
            s.append(line(l, y, W - r, y, "rgba(128,128,128,.22)", null));
            s.append(text(l - 4, y + 4, v + "%", "end"));
        }
        int nt = ticks(xMax);
        for (int i = 0; i <= nt; i++) {
            double xv = xMax * i / nt;
            double x = l + xv / xMax * pw;
            s.append(text(x, t + ph + 14, num(xv, xv < 10 && xv != Math.floor(xv) ? 1 : 0), "middle"));
        }
        s.append(text(l + pw / 2, h - 3, "此前累计练习时长（小时）", "middle"));

        if (ms.points.size() >= 3 && !Double.isNaN(ms.slopePerHour) && !Double.isNaN(ms.intercept)) {
            double x0 = Double.MAX_VALUE;
            double x1 = 0;
            for (Point p : ms.points) {
                x0 = Math.min(x0, p.cumMinutes() / 60);
                x1 = Math.max(x1, p.cumMinutes() / 60);
            }
            double y0 = ms.intercept + ms.slopePerHour * x0;
            double y1 = ms.intercept + ms.slopePerHour * x1;
            s.append("<g clip-path='url(#c").append(idx).append(")'>")
                    .append(line(l + x0 / xMax * pw, t + ph - y0 / 100 * ph, l + x1 / xMax * pw, t + ph - y1 / 100 * ph,
                            color, "5 3"))
                    .append("</g>");
        }
        for (Point p : ms.points) {
            double cx = l + p.cumMinutes() / 60 / xMax * pw;
            double cy = t + ph - p.accuracy() * ph;
            s.append("<circle cx='").append(f(cx)).append("' cy='").append(f(cy)).append("' r='3.2' fill='")
                    .append(color).append("' fill-opacity='.85'><title>")
                    .append(esc(p.time().format(TIP) + " · 此前累计 " + num(p.cumMinutes() / 60, 1) + " 小时 · 正确率 "
                            + pct(p.accuracy()) + "（" + p.correct() + "/" + p.total() + "）"))
                    .append("</title></circle>");
        }
        return s.append("</svg>").toString();
    }

    private static void daily(StringBuilder b, Stats st) {
        Set<String> modules = new LinkedHashSet<>();
        double maxMin = 0;
        for (Map<String, Integer> day : st.daily.values()) {
            modules.addAll(day.keySet());
            maxMin = Math.max(maxMin, day.values().stream().mapToInt(Integer::intValue).sum() / 60.0);
        }
        List<String> order = new ArrayList<>(modules);
        order.sort(Comparator.<String>comparingInt(Stats::orderOf).thenComparing(Comparator.naturalOrder()));

        int h = 150;
        int l = 32;
        int r = 6;
        int t = 8;
        int bottom = 22;
        double pw = W - l - r;
        double ph = h - t - bottom;
        double yMax = niceMax(maxMin);
        StringBuilder s = svgOpen(W, h);
        int nt = ticks(yMax);
        for (int i = 0; i <= nt; i++) {
            double v = yMax * i / nt;
            double y = t + ph - v / yMax * ph;
            s.append(line(l, y, W - r, y, "rgba(128,128,128,.22)", null));
            s.append(text(l - 4, y + 4, num(v, 0), "end"));
        }
        List<LocalDate> days = new ArrayList<>(st.daily.keySet());
        int n = days.size();
        double slot = pw / n;
        double bw = slot * 0.66;
        for (int i = 0; i < n; i++) {
            LocalDate d = days.get(i);
            Map<String, Integer> day = st.daily.get(d);
            double x = l + i * slot + (slot - bw) / 2;
            double acc = 0;
            for (String m : order) {
                Integer sec = day.get(m);
                if (sec == null || sec <= 0) continue;
                double hh = sec / 60.0 / yMax * ph;
                s.append("<rect x='").append(f(x)).append("' y='").append(f(t + ph - acc - hh)).append("' width='")
                        .append(f(bw)).append("' height='").append(f(hh)).append("' fill='").append(colorOf(m))
                        .append("'><title>").append(esc(d.format(DAY) + " " + m + " " + duration(sec)))
                        .append("</title></rect>");
                acc += hh;
            }
            if ((n - 1 - i) % 2 == 0) s.append(text(l + i * slot + slot / 2, h - 6, d.format(DAY), "middle"));
        }
        s.append("</svg>");
        b.append(s).append("<div class='sub'>纵轴：分钟</div><div class='legend'>");
        for (String m : order) {
            b.append("<span><i style='background:").append(colorOf(m)).append("'></i>").append(esc(m)).append("</span>");
        }
        b.append("</div>");
    }

    // ---------------------------------------------------------------- 小工具

    private static StringBuilder svgOpen(int w, int h) {
        return new StringBuilder(4096).append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ").append(w)
                .append(' ').append(h).append("'>");
    }

    private static String text(double x, double y, String s, String anchor) {
        return "<text x='" + f(x) + "' y='" + f(y) + "' text-anchor='" + anchor + "'>" + esc(s) + "</text>";
    }

    private static String line(double x1, double y1, double x2, double y2, String stroke, String dash) {
        return "<line x1='" + f(x1) + "' y1='" + f(y1) + "' x2='" + f(x2) + "' y2='" + f(y2) + "' stroke='" + stroke
                + "' stroke-width='" + (dash == null ? "1" : "1.6") + "'"
                + (dash == null ? "" : " stroke-dasharray='" + dash + "'") + "/>";
    }

    /** 模块配色：按行测模块固定顺序取色，其他模块用灰色。 */
    private static String colorOf(String module) {
        int i = Stats.ORDER.indexOf(module);
        return i < 0 ? PALETTE[0] : PALETTE[1 + i % (PALETTE.length - 1)];
    }

    private static double niceMax(double v) {
        if (v <= 0) return 1;
        double e = Math.pow(10, Math.floor(Math.log10(v)));
        double fr = v / e;
        double nf = fr <= 1 ? 1 : fr <= 2 ? 2 : fr <= 5 ? 5 : 10;
        return nf * e;
    }

    private static int ticks(double niceMax) {
        double e = Math.pow(10, Math.floor(Math.log10(niceMax)));
        long lead = Math.round(niceMax / e);
        return lead == 2 ? 4 : 5;
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String num(double v, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", v);
    }

    private static String signed(double v) {
        return (v >= 0 ? "+" : "") + num(v, 1);
    }

    private static String pct(double v) {
        return Double.isNaN(v) ? "—" : Math.round(v * 100) + "%";
    }

    private static String duration(int seconds) {
        if (seconds < 60) return seconds + " 秒";
        int m = seconds / 60;
        if (m < 60) return m + " 分钟";
        return m / 60 + " 小时 " + m % 60 + " 分";
    }

    private static String esc(String s) {
        return SheetRenderer.esc(s);
    }
}
