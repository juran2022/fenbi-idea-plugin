package com.ranju.fenbi.ui;

import com.ranju.fenbi.api.Models.Material;
import com.ranju.fenbi.api.Models.Question;
import com.ranju.fenbi.api.Models.QuestionSet;
import com.ranju.fenbi.book.WrongBook;
import com.ranju.fenbi.settings.FenbiSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 一张卷面的数据（作答 / 解析 / 错题重做三种模式共用），交给 SheetRenderer 渲染。 */
final class Sheet {
    enum Mode { ANSWER, SOLUTION, REVIEW }

    static final class Item {
        long id;
        String content;
        List<String> options = List.of();
        /** 引用的材料下标（指向 Sheet.materials） */
        final List<Integer> materialRefs = new ArrayList<>();
        String correct;
        String mine;
        String solution;
        String source;
        boolean timeout;
        Integer spentSeconds;
        /** 题目下方的小字说明（错题本用） */
        String meta;
    }

    final Mode mode;
    final String title;
    final List<String> materials = new ArrayList<>();
    final List<Item> items = new ArrayList<>();
    String scoreLine = "";
    long start = System.currentTimeMillis();
    long deadline;
    int questionSeconds;
    boolean autoNext;
    boolean autoSubmit;
    boolean pinMaterial;
    boolean hidePauseInfo;

    private Sheet(Mode mode, String title) {
        this.mode = mode;
        this.title = title;
        FenbiSettings.Options o = FenbiSettings.options();
        this.autoNext = o.autoNext;
        this.autoSubmit = o.autoSubmit;
        this.pinMaterial = o.pinMaterial;
        this.hidePauseInfo = o.hidePauseInfo;
    }

    static Sheet answer(Session s) {
        String limit = s.deadline > 0
                ? " · 限时 " + Math.round((s.deadline - s.startedAt) / 60000.0) + " 分钟"
                : s.questionSeconds > 0 ? " · 每题 " + s.questionSeconds + " 秒" : "";
        Sheet sh = new Sheet(Mode.ANSWER, s.module + " · " + s.keypoint + " · 共 " + s.questions.size() + " 题" + limit);
        sh.start = s.startedAt;
        sh.deadline = s.deadline;
        sh.questionSeconds = s.questionSeconds;
        for (Material m : s.materials) sh.materials.add(m == null || m.content == null ? "" : m.content);
        for (Question q : s.questions) {
            Item it = item(q, sh.materials.size());
            it.mine = s.answers.get(q.id);
            sh.items.add(it);
        }
        return sh;
    }

    static Sheet solution(Session s, QuestionSet sol) {
        List<Question> qs = sol.items().isEmpty() ? s.questions : sol.items();
        List<Material> mats = sol.materialList().isEmpty() ? s.materials : sol.materialList();
        Sheet sh = new Sheet(Mode.SOLUTION, s.module + " · " + s.keypoint);
        int n = qs.size();
        sh.scoreLine = "正确 " + s.correct + "/" + n
                + " · 正确率 " + (n == 0 ? 0 : Math.round(s.correct * 100.0 / n)) + "%"
                + " · 用时 " + Ui.mmss(s.elapsedSeconds());
        sh.start = s.startedAt;
        for (Material m : mats) sh.materials.add(m == null || m.content == null ? "" : m.content);
        for (Question q : qs) {
            Item it = item(q, sh.materials.size());
            it.correct = q.correctChoice();
            it.mine = s.answers.get(q.id);
            it.solution = q.solution;
            it.source = q.source;
            it.timeout = it.mine == null && s.timeouts.contains(q.id);
            it.spentSeconds = s.spent.get(q.id);
            sh.items.add(it);
        }
        return sh;
    }

    static Sheet review(List<WrongBook.Entry> entries) {
        Sheet sh = new Sheet(Mode.REVIEW, "错题重做 · " + entries.size() + " 题 · 选完立即判对错");
        Map<String, Integer> index = new HashMap<>();
        for (WrongBook.Entry e : entries) {
            Item it = new Item();
            it.id = e.questionId;
            it.content = e.content;
            it.options = e.options == null ? List.of() : e.options;
            it.correct = e.correct;
            it.solution = e.solution;
            it.source = e.source;
            if (e.materials != null) {
                for (String m : e.materials) {
                    if (m == null) continue;
                    Integer k = index.get(m);
                    if (k == null) {
                        k = sh.materials.size();
                        sh.materials.add(m);
                        index.put(m, k);
                    }
                    if (!it.materialRefs.contains(k)) it.materialRefs.add(k);
                }
            }
            it.meta = "错 " + e.wrongCount + " 次 · 上次"
                    + (e.lastAnswer == null ? "未作答" : "选 " + SheetRenderer.choiceLetter(e.lastAnswer))
                    + " · " + nz(e.module) + " / " + nz(e.keypoint)
                    + (e.streak > 0 ? " · 已连续答对 " + e.streak + " 次" : "");
            sh.items.add(it);
        }
        return sh;
    }

    private static Item item(Question q, int materialCount) {
        Item it = new Item();
        it.id = q.id;
        it.content = q.content;
        it.options = q.options();
        if (q.materialIndexes != null) {
            for (Integer k : q.materialIndexes) {
                if (k != null && k >= 0 && k < materialCount && !it.materialRefs.contains(k)) it.materialRefs.add(k);
            }
        }
        return it;
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "未分类" : s;
    }
}
