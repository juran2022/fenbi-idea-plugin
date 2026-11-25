package com.ranju.fenbi.stats;

import com.ranju.fenbi.log.PracticeLog.Row;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按模块统计练习记录，并计算“此前累计练习时长”与“单次正确率”的相关系数。
 * 纯计算，不依赖 IDE，方便单测。
 */
public final class Stats {
    /** 行测模块的常见顺序（也决定图表配色）。 */
    public static final List<String> ORDER = List.of("政治理论", "常识判断", "言语理解与表达", "数量关系", "判断推理", "资料分析");
    /** 判断相关性至少需要的练习次数 */
    public static final int MIN_SAMPLES = 5;
    public static final int DAILY_DAYS = 14;

    /** 一次练习：x = 这次练习之前该模块累计练了多少分钟，y = 这次的正确率。 */
    public record Point(LocalDateTime time, double cumMinutes, double accuracy, int correct, int total) {
    }

    public static final class ModuleStat {
        public final String module;
        public int sessions;
        public int questions;
        public int correct;
        public int seconds;
        public final List<Point> points = new ArrayList<>();
        public double r = Double.NaN;
        /** 回归斜率：每多练 1 小时，正确率变化多少个百分点 */
        public double slopePerHour = Double.NaN;
        /** 回归截距（%） */
        public double intercept = Double.NaN;
        public double recentAccuracy = Double.NaN;

        ModuleStat(String module) {
            this.module = module;
        }

        public double accuracy() {
            return questions == 0 ? Double.NaN : correct * 1.0 / questions;
        }

        public double secondsPerQuestion() {
            return questions == 0 ? Double.NaN : seconds * 1.0 / questions;
        }

        public boolean enough() {
            return points.size() >= MIN_SAMPLES;
        }

        public String verdict() {
            if (!enough()) return "样本不足";
            if (Double.isNaN(r)) return "无法计算";
            if (r >= 0.5) return "明显正相关";
            if (r >= 0.3) return "弱正相关";
            if (r > -0.3) return "无明显相关";
            return "负相关";
        }
    }

    public final List<ModuleStat> modules = new ArrayList<>();
    public final LocalDate today = LocalDate.now();
    /** 近 14 天每天各模块练习秒数 */
    public final Map<LocalDate, Map<String, Integer>> daily = new LinkedHashMap<>();
    public int sessions;
    public int questions;
    public int correct;
    public int seconds;
    public int days;

    private Stats() {
    }

    public static int orderOf(String module) {
        int i = ORDER.indexOf(module);
        return i < 0 ? ORDER.size() : i;
    }

    /** @param rangeDays 只统计最近 N 天（0 = 全部）。累计时长始终从第一条记录算起。 */
    public static Stats compute(List<Row> records, int rangeDays) {
        Stats st = new Stats();
        for (int i = DAILY_DAYS - 1; i >= 0; i--) st.daily.put(st.today.minusDays(i), new LinkedHashMap<>());
        LocalDateTime from = rangeDays > 0 ? st.today.minusDays(rangeDays - 1L).atStartOfDay() : null;

        List<Row> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparing(Row::time));
        Map<String, Double> cum = new HashMap<>();
        Map<String, ModuleStat> map = new HashMap<>();
        Set<LocalDate> dates = new HashSet<>();

        for (Row r : sorted) {
            String m = r.module() == null || r.module().isBlank() ? "未分类" : r.module();
            double before = cum.getOrDefault(m, 0.0);
            cum.put(m, before + r.seconds() / 60.0);
            Map<String, Integer> day = st.daily.get(r.time().toLocalDate());
            if (day != null) day.merge(m, r.seconds(), Integer::sum);

            if (from != null && r.time().isBefore(from)) continue;
            if (r.total() <= 0) continue;
            ModuleStat ms = map.computeIfAbsent(m, ModuleStat::new);
            ms.sessions++;
            ms.questions += r.total();
            ms.correct += r.correct();
            ms.seconds += r.seconds();
            ms.points.add(new Point(r.time(), before, r.correct() * 1.0 / r.total(), r.correct(), r.total()));
            st.sessions++;
            st.questions += r.total();
            st.correct += r.correct();
            st.seconds += r.seconds();
            dates.add(r.time().toLocalDate());
        }
        st.days = dates.size();
        for (ModuleStat ms : map.values()) {
            fit(ms);
            int c = 0;
            int t = 0;
            List<Point> pts = ms.points;
            for (int i = Math.max(0, pts.size() - 5); i < pts.size(); i++) {
                c += pts.get(i).correct();
                t += pts.get(i).total();
            }
            ms.recentAccuracy = t == 0 ? Double.NaN : c * 1.0 / t;
        }
        st.modules.addAll(map.values());
        st.modules.sort(Comparator.comparingInt((ModuleStat ms) -> orderOf(ms.module)).thenComparing(ms -> ms.module));
        return st;
    }

    /** 样本足够且 r ≥ 0.3 的模块里，r 最大的那个；没有则返回 null。 */
    public ModuleStat strongest() {
        ModuleStat best = null;
        for (ModuleStat ms : modules) {
            if (!ms.enough() || Double.isNaN(ms.r) || ms.r < 0.3) continue;
            if (best == null || ms.r > best.r) best = ms;
        }
        return best;
    }

    public double accuracy() {
        return questions == 0 ? Double.NaN : correct * 1.0 / questions;
    }

    /** 最小二乘拟合：x = 累计小时，y = 正确率（%）。 */
    static void fit(ModuleStat ms) {
        int n = ms.points.size();
        if (n < 3) return;
        double mx = 0;
        double my = 0;
        for (Point p : ms.points) {
            mx += p.cumMinutes() / 60;
            my += p.accuracy() * 100;
        }
        mx /= n;
        my /= n;
        double sxx = 0;
        double syy = 0;
        double sxy = 0;
        for (Point p : ms.points) {
            double dx = p.cumMinutes() / 60 - mx;
            double dy = p.accuracy() * 100 - my;
            sxx += dx * dx;
            syy += dy * dy;
            sxy += dx * dy;
        }
        if (sxx > 0) {
            ms.slopePerHour = sxy / sxx;
            ms.intercept = my - ms.slopePerHour * mx;
        }
        if (sxx > 0 && syy > 0) ms.r = sxy / Math.sqrt(sxx * syy);
    }
}
