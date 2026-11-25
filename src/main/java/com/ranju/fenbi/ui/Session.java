package com.ranju.fenbi.ui;

import com.ranju.fenbi.api.Models.Exercise;
import com.ranju.fenbi.api.Models.Material;
import com.ranju.fenbi.api.Models.Question;
import com.ranju.fenbi.api.Models.QuestionSet;
import com.ranju.fenbi.api.Models.UserAnswer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 一次练习（一张卷子）的状态。 */
final class Session {
    final long exerciseId;
    final String module;
    final String keypoint;
    final List<Question> questions;
    final List<Material> materials;
    /** questionId -> 选项下标（"0" = A） */
    final Map<Long, String> answers = new ConcurrentHashMap<>();
    /** questionId -> 该题累计用时（秒） */
    final Map<Long, Integer> spent = new ConcurrentHashMap<>();
    /** 每题限时模式下超时的题 */
    final Set<Long> timeouts = ConcurrentHashMap.newKeySet();
    final long startedAt = System.currentTimeMillis();

    /** 整卷限时的截止时间（毫秒时间戳），0 = 不限 */
    long deadline;
    /** 每题限时秒数，0 = 不限 */
    int questionSeconds;

    volatile boolean submitted;
    volatile boolean submitting;
    volatile long finishedAt;
    volatile int correct;
    boolean warned;
    boolean timeUpNotified;
    /** 每题限时模式下当前题的状态（由页面上报，显示在状态栏），null = 还没上报 */
    volatile String questionStatus;

    /** 已结束的暂停累计时长（毫秒） */
    private long pausedMs;
    /** 当前这次暂停的开始时间，0 = 没有暂停 */
    private long pauseStartedAt;
    private String pauseReason = "";
    private boolean pauseAuto;
    private int pauseCount;

    Session(long exerciseId, String module, String keypoint, QuestionSet qs, Exercise ex) {
        this.exerciseId = exerciseId;
        this.module = module;
        this.keypoint = keypoint;
        this.questions = qs.items();
        this.materials = qs.materialList();
        for (UserAnswer ua : ex.userAnswers) answers.put(ua.questionId, ua.choice);
    }

    // ---------------------------------------------------------------- 暂停

    /** 开始暂停。已暂停或已交卷时返回 false。 */
    synchronized boolean pause(String reason, boolean auto) {
        if (submitted || pauseStartedAt > 0) return false;
        pauseStartedAt = System.currentTimeMillis();
        pauseReason = reason == null ? "" : reason;
        pauseAuto = auto;
        pauseCount++;
        return true;
    }

    /** 结束暂停。没在暂停时返回 false。 */
    synchronized boolean resume() {
        if (pauseStartedAt == 0) return false;
        pausedMs += System.currentTimeMillis() - pauseStartedAt;
        pauseStartedAt = 0;
        return true;
    }

    synchronized boolean isPaused() {
        return pauseStartedAt > 0;
    }

    synchronized boolean isAutoPaused() {
        return pauseStartedAt > 0 && pauseAuto;
    }

    synchronized String pauseReason() {
        return pauseReason;
    }

    synchronized int pauseCount() {
        return pauseCount;
    }

    /** 累计暂停时长（含正在进行的这次）。 */
    synchronized long totalPausedMs() {
        return pausedMs + (pauseStartedAt > 0 ? System.currentTimeMillis() - pauseStartedAt : 0);
    }

    /** “做题时钟”：墙上时间减去暂停时长。计时、限时、用时都按它算。 */
    long activeNow() {
        return System.currentTimeMillis() - totalPausedMs();
    }

    void markSubmitted(List<Question> solutions) {
        resume();
        int c = 0;
        for (Question q : solutions) {
            String right = q.correctChoice();
            if (right != null && Objects.equals(answers.get(q.id), right)) c++;
        }
        correct = c;
        finishedAt = activeNow();
        submitted = true;
    }

    int elapsedSeconds() {
        long end = submitted ? finishedAt : activeNow();
        return (int) ((end - startedAt) / 1000);
    }
}
