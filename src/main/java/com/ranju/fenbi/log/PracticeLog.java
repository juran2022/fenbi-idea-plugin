package com.ranju.fenbi.log;

import com.intellij.openapi.diagnostic.Logger;
import com.ranju.fenbi.util.Listeners;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 每次交卷追加一行到 ~/.fenbi-idea/practice-log.csv，
 * 「统计」页读它来分析“各模块练习时长 vs 正确率”。
 */
public final class PracticeLog {
    private static final Logger LOG = Logger.getInstance(PracticeLog.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String HEADER = "时间,模块,考点,题数,已答,正确,正确率(%),用时(秒)\n";

    /** 有新记录时通知（统计页据此刷新）。 */
    public static final Listeners CHANGES = new Listeners();

    public record Row(LocalDateTime time, String module, String keypoint,
                         int total, int answered, int correct, int seconds) {
    }

    private PracticeLog() {
    }

    public static Path file() {
        return Path.of(System.getProperty("user.home"), ".fenbi-idea", "practice-log.csv");
    }

    public static synchronized void append(String module, String keypoint, int total, int answered,
                                           int correct, int seconds) {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            StringBuilder sb = new StringBuilder();
            if (!Files.exists(f)) sb.append('\uFEFF').append(HEADER); // BOM：Excel 打开不乱码
            double rate = total == 0 ? 0 : correct * 100.0 / total;
            sb.append(LocalDateTime.now().format(FMT)).append(',')
                    .append(csv(module)).append(',')
                    .append(csv(keypoint)).append(',')
                    .append(total).append(',')
                    .append(answered).append(',')
                    .append(correct).append(',')
                    .append(String.format(Locale.ROOT, "%.1f", rate)).append(',')
                    .append(seconds).append('\n');
            Files.writeString(f, sb, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warn("写练习记录失败", e);
        }
        CHANGES.fire();
    }

    /** 读取全部记录；坏行直接跳过。 */
    public static synchronized List<Row> read() {
        List<Row> out = new ArrayList<>();
        Path f = file();
        if (!Files.exists(f)) return out;
        List<String> lines;
        try {
            lines = Files.readAllLines(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("读练习记录失败", e);
            return out;
        }
        for (String raw : lines) {
            String line = raw.startsWith("\uFEFF") ? raw.substring(1) : raw;
            if (line.isBlank() || line.startsWith("时间")) continue;
            List<String> c = parseLine(line);
            if (c.size() < 8) continue;
            try {
                out.add(new Row(LocalDateTime.parse(c.get(0).trim(), FMT), c.get(1).trim(), c.get(2).trim(),
                        Integer.parseInt(c.get(3).trim()), Integer.parseInt(c.get(4).trim()),
                        Integer.parseInt(c.get(5).trim()), Integer.parseInt(c.get(7).trim())));
            } catch (RuntimeException ignored) {
                // 手工改坏的行忽略
            }
        }
        return out;
    }

    static List<String> parseLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
