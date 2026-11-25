package com.ranju.fenbi.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.ranju.fenbi.api.Models.Category;
import com.ranju.fenbi.api.Models.Exercise;
import com.ranju.fenbi.api.Models.QuestionSet;
import com.ranju.fenbi.api.Models.UnfinishedCache;
import com.ranju.fenbi.api.Models.UserAnswer;
import com.ranju.fenbi.settings.FenbiSettings;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 粉笔题库网页版接口（tiku.fenbi.com）。
 * 接口不是公开 API，参考自开源项目 maoguy/fenbi-client，粉笔改版后可能失效。
 * 所有方法都是阻塞的，不要在 EDT 上调用。
 */
public final class FenbiClient {
    private static final String BASE = "https://tiku.fenbi.com";
    private static final String COMMON = "app=web&kav=100&av=100&hav=100&version=3.0.0.0";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final Gson GSON = new Gson();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String course;

    public FenbiClient(String course) {
        this.course = course;
    }

    // ---------------------------------------------------------------- 业务接口

    public List<Category> categories() throws FenbiException {
        JsonElement e = get(api("/categories", "filter=keypoint"));
        if (!e.isJsonArray()) throw new FenbiException("目录格式不对：" + abbreviate(e.toString()));
        return GSON.fromJson(e, new TypeToken<List<Category>>() {
        }.getType());
    }

    public UnfinishedCache unfinished() throws FenbiException {
        int tag = ThreadLocalRandom.current().nextInt(1000);
        JsonElement e = get(api("/category-exercises-unfinished", "noCacheTag=" + tag));
        return e.isJsonObject() ? GSON.fromJson(e, UnfinishedCache.class) : null;
    }

    /** 按考点组卷，返回 exerciseId。limit <= 0 时跟随粉笔账号里的题量设置。 */
    public long createExercise(long keypointId, int limit) throws FenbiException {
        JsonObject pref = null;
        try {
            JsonElement e = get(url("/activity/userquiz/getUserQuestionInfo", null));
            if (e.isJsonObject() && e.getAsJsonObject().get("data") instanceof JsonObject d) pref = d;
        } catch (FenbiException ignored) {
            // 拿不到个人设置就用默认值
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("type", "3");
        form.put("exerciseTimeMode", "2");
        form.put("keypointId", String.valueOf(keypointId));
        int n = limit > 0 ? limit : (pref != null && has(pref, "questionCount") ? pref.get("questionCount").getAsInt() : 5);
        form.put("limit", String.valueOf(n));
        if (pref != null) {
            for (String k : List.of("yearScope", "correctRatioLow", "correctRatioHigh")) {
                if (has(pref, k)) form.put(k, pref.get(k).getAsString());
            }
        }

        JsonElement r = postForm(api("/exercises", null), form);
        long id = r.isJsonObject() && has(r.getAsJsonObject(), "id") ? r.getAsJsonObject().get("id").getAsLong() : 0;
        if (id <= 0) throw new FenbiException("组卷失败，返回：" + abbreviate(r.toString()));
        return id;
    }

    public Exercise exercise(long exerciseId) throws FenbiException {
        JsonElement e = get(api("/exercises/" + exerciseId, null));
        Exercise ex = new Exercise();
        ex.id = exerciseId;
        if (!e.isJsonObject()) return ex;
        JsonElement ua = e.getAsJsonObject().get("userAnswers");
        if (ua == null || ua.isJsonNull()) return ex;
        if (ua.isJsonObject()) {
            for (Entry<String, JsonElement> en : ua.getAsJsonObject().entrySet()) addUserAnswer(ex, en.getValue());
        } else if (ua.isJsonArray()) {
            for (JsonElement el : ua.getAsJsonArray()) addUserAnswer(ex, el);
        }
        return ex;
    }

    public QuestionSet questions(long exerciseId) throws FenbiException {
        return questionSet(get(api("/universal/auth/questions", "type=0&id=" + exerciseId)));
    }

    public QuestionSet solutions(long exerciseId) throws FenbiException {
        return questionSet(get(api("/universal/auth/solutions", "type=0&id=" + exerciseId)));
    }

    /** 提交一道题的答案（粉笔网页版每选一次就会调用一次）。 */
    public void answer(long exerciseId, long questionId, int questionIndex, String choice, int seconds)
            throws FenbiException {
        JsonObject ans = new JsonObject();
        ans.addProperty("type", 201);
        ans.addProperty("choice", choice);
        JsonObject item = new JsonObject();
        item.add("answer", ans);
        item.addProperty("flag", 0);
        item.addProperty("questionId", questionId);
        item.addProperty("questionIndex", questionIndex);
        item.addProperty("time", seconds);
        JsonArray body = new JsonArray();
        body.add(item);
        postJson(api("/async/exercises/" + exerciseId + "/incr", null), body.toString());
    }

    public void submit(long exerciseId) throws FenbiException {
        postForm(api("/async/exercises/" + exerciseId + "/submit", null), Map.of("status", "3"));
    }

    public static String webSolutionUrl(long exerciseId) {
        return "https://www.fenbi.com/spa/tiku/report/exam/solution/xingce/xingce/" + exerciseId + "/2";
    }

    // ---------------------------------------------------------------- HTTP

    private String api(String path, String query) {
        return url("/api/" + course + path, query);
    }

    private static String url(String path, String query) {
        return BASE + path + "?" + (query == null || query.isEmpty() ? "" : query + "&") + COMMON;
    }

    private JsonElement get(String url) throws FenbiException {
        return send(HttpRequest.newBuilder(URI.create(url)).GET());
    }

    private JsonElement postForm(String url, Map<String, String> form) throws FenbiException {
        StringJoiner sj = new StringJoiner("&");
        form.forEach((k, v) -> sj.add(enc(k) + "=" + enc(v)));
        return send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(sj.toString(), StandardCharsets.UTF_8)));
    }

    private JsonElement postJson(String url, String json) throws FenbiException {
        return send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)));
    }

    private JsonElement send(HttpRequest.Builder builder) throws FenbiException {
        String cookie = FenbiSettings.getCookie();
        if (cookie == null || cookie.isBlank()) {
            throw new FenbiException("还没有设置 Cookie，请到 Settings → Tools → Fenbi 行测 填写");
        }
        HttpRequest req = builder
                .header("Cookie", cookie.trim().replaceAll("[\\r\\n]+", " "))
                .header("User-Agent", UA)
                .header("Referer", "https://www.fenbi.com/")
                .header("Origin", "https://www.fenbi.com")
                .header("Accept", "application/json, text/plain, */*")
                .timeout(Duration.ofSeconds(30))
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            if (code == 401 || code == 403) {
                throw new FenbiException("Cookie 无效或已过期（HTTP " + code + "），请重新登录粉笔网页版后复制");
            }
            if (code >= 400) throw new FenbiException("HTTP " + code + "：" + abbreviate(resp.body()));
            String body = resp.body();
            if (body == null || body.isBlank()) return JsonNull.INSTANCE;
            return JsonParser.parseString(body);
        } catch (IOException e) {
            throw new FenbiException("网络异常：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FenbiException("请求被中断", e);
        } catch (JsonSyntaxException e) {
            throw new FenbiException("返回的不是 JSON，接口可能已变更", e);
        }
    }

    // ---------------------------------------------------------------- 工具

    private static QuestionSet questionSet(JsonElement e) {
        QuestionSet qs = e.isJsonObject() ? GSON.fromJson(e, QuestionSet.class) : null;
        return qs == null ? new QuestionSet() : qs;
    }

    private static void addUserAnswer(Exercise ex, JsonElement el) {
        if (el == null || !el.isJsonObject()) return;
        JsonObject o = el.getAsJsonObject();
        UserAnswer ua = new UserAnswer();
        ua.questionId = has(o, "questionId") ? o.get("questionId").getAsLong() : 0;
        ua.questionIndex = has(o, "questionIndex") ? o.get("questionIndex").getAsInt() : 0;
        if (o.get("answer") instanceof JsonObject a && has(a, "choice")) ua.choice = a.get("choice").getAsString();
        if (ua.questionId > 0 && ua.choice != null) ex.userAnswers.add(ua);
    }

    private static boolean has(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
