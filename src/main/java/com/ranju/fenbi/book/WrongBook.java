package com.ranju.fenbi.book;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.ranju.fenbi.api.Models.Material;
import com.ranju.fenbi.api.Models.Question;
import com.ranju.fenbi.settings.FenbiSettings;
import com.ranju.fenbi.util.Listeners;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地错题本：~/.fenbi-idea/wrong-book.json。
 * 交卷时做错（或没做）的题自动收进来；之后再答对 N 次（默认 2 次）标记为已掌握。
 * 题干、选项、材料、解析都存了快照，重做不需要联网（图片除外）。
 */
public final class WrongBook {
    private static final Logger LOG = Logger.getInstance(WrongBook.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final WrongBook INSTANCE = new WrongBook();

    public static final class Entry {
        public long questionId;
        public String module;
        public String keypoint;
        public String content;
        public List<String> options = new ArrayList<>();
        public String correct;
        public String lastAnswer;
        public String solution;
        public String source;
        public List<String> materials = new ArrayList<>();
        public int wrongCount;
        public int reviewCount;
        /** 连续答对次数 */
        public int streak;
        public boolean mastered;
        public long firstWrongAt;
        public long lastWrongAt;
        public long lastReviewAt;

        /** 去掉 HTML 的题干，用于列表显示和搜索。 */
        public String plainText() {
            if (content == null) return "";
            String s = content.replaceAll("(?s)<[^>]+>", "")
                    .replace("&nbsp;", " ")
                    .replaceAll("&[a-zA-Z]+;", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            return s.isEmpty() ? "[图片题]" : s;
        }

        @Override
        public String toString() {
            return plainText();
        }
    }

    /** 错题本有变化时通知。 */
    public final Listeners changes = new Listeners();

    private final Map<Long, Entry> entries = new LinkedHashMap<>();
    private boolean loaded;

    private WrongBook() {
    }

    public static WrongBook getInstance() {
        return INSTANCE;
    }

    public static Path file() {
        return Path.of(System.getProperty("user.home"), ".fenbi-idea", "wrong-book.json");
    }

    public synchronized List<Entry> all() {
        ensureLoaded();
        return new ArrayList<>(entries.values());
    }

    /**
     * 交卷后调用：做错/没做的题记入错题本；已在错题本里的题这次答对了，算一次“答对”。
     *
     * @return 这套题里做错（含未作答）的题数
     */
    public synchronized int recordExercise(String module, String keypoint, List<Question> items,
                                           List<Material> materials, Map<Long, String> answers) {
        ensureLoaded();
        int streakToMaster = Math.max(1, FenbiSettings.options().masterStreak);
        long now = System.currentTimeMillis();
        int wrong = 0;
        for (Question q : items) {
            String right = q.correctChoice();
            if (right == null) continue;
            String mine = answers.get(q.id);
            Entry e = entries.get(q.id);
            if (right.equals(mine)) {
                if (e != null) {
                    e.streak++;
                    e.lastReviewAt = now;
                    if (e.streak >= streakToMaster) e.mastered = true;
                }
                continue;
            }
            wrong++;
            if (e == null) {
                e = new Entry();
                e.questionId = q.id;
                e.firstWrongAt = now;
                entries.put(q.id, e);
            }
            e.module = module;
            e.keypoint = keypoint;
            e.content = q.content;
            e.options = q.options();
            e.correct = right;
            e.lastAnswer = mine;
            e.solution = q.solution;
            e.source = q.source;
            e.materials = materialsOf(q, materials);
            e.wrongCount++;
            e.streak = 0;
            e.mastered = false;
            e.lastWrongAt = now;
        }
        save();
        changes.fire();
        return wrong;
    }

    /** 错题重做时作答。返回是否答对。 */
    public synchronized boolean recordReview(long questionId, String choice) {
        ensureLoaded();
        Entry e = entries.get(questionId);
        if (e == null) return false;
        long now = System.currentTimeMillis();
        boolean ok = e.correct != null && e.correct.equals(choice);
        e.reviewCount++;
        e.lastReviewAt = now;
        e.lastAnswer = choice;
        if (ok) {
            e.streak++;
            if (e.streak >= Math.max(1, FenbiSettings.options().masterStreak)) e.mastered = true;
        } else {
            e.wrongCount++;
            e.streak = 0;
            e.mastered = false;
            e.lastWrongAt = now;
        }
        save();
        changes.fire();
        return ok;
    }

    public synchronized void remove(Collection<Long> ids) {
        ensureLoaded();
        ids.forEach(entries::remove);
        save();
        changes.fire();
    }

    public synchronized int removeMastered() {
        ensureLoaded();
        int before = entries.size();
        entries.values().removeIf(e -> e.mastered);
        save();
        changes.fire();
        return before - entries.size();
    }

    // ---------------------------------------------------------------- 存取

    private static List<String> materialsOf(Question q, List<Material> materials) {
        List<String> out = new ArrayList<>();
        if (q.materialIndexes == null || materials == null) return out;
        for (Integer k : q.materialIndexes) {
            if (k == null || k < 0 || k >= materials.size()) continue;
            Material m = materials.get(k);
            if (m != null && m.content != null && !out.contains(m.content)) out.add(m.content);
        }
        return out;
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            String json = Files.readString(f, StandardCharsets.UTF_8);
            List<Entry> list = GSON.fromJson(json, new TypeToken<List<Entry>>() {
            }.getType());
            if (list != null) {
                for (Entry e : list) if (e != null && e.questionId > 0) entries.put(e.questionId, e);
            }
        } catch (Exception e) {
            LOG.warn("读取错题本失败，已备份为 wrong-book.json.bak", e);
            try {
                Files.copy(f, f.resolveSibling("wrong-book.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // 备份失败也不影响继续使用
            }
        }
    }

    private void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("wrong-book.json.tmp");
            Files.writeString(tmp, GSON.toJson(new ArrayList<>(entries.values())), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.warn("保存错题本失败", e);
        }
    }
}
