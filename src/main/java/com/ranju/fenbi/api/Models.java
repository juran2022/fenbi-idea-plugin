package com.ranju.fenbi.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** 粉笔接口返回的数据结构（只保留用到的字段）。 */
public final class Models {
    private Models() {
    }

    public static class Category {
        public long id;
        public String name;
        public int count;
        public int answerCount;
        public List<Category> children;

        @Override
        public String toString() {
            return name;
        }
    }

    /** 未完成的练习（用于“继续作答”）。 */
    public static class UnfinishedCache {
        public long exerciseId;
        public List<Long> keypointIds;
    }

    public static class Material {
        public String content;
    }

    public static class Question {
        public long id;
        public String content;
        public int type;
        public List<JsonObject> accessories;
        public List<Integer> materialIndexes;
        public JsonElement correctAnswer;
        /** 只有解析接口才有 */
        public String solution;
        public String source;

        /** 选项（HTML 片段）；不是选择题时返回空列表。 */
        public List<String> options() {
            List<String> result = new ArrayList<>();
            if (accessories == null) return result;
            for (JsonObject acc : accessories) {
                JsonElement opts = acc.get("options");
                if (opts != null && opts.isJsonArray()) {
                    JsonArray arr = opts.getAsJsonArray();
                    for (JsonElement o : arr) result.add(o.isJsonNull() ? "" : o.getAsString());
                    if (!result.isEmpty()) return result;
                }
            }
            return result;
        }

        /** 正确答案，形如 "2"（第 3 个选项）。 */
        public String correctChoice() {
            if (correctAnswer == null || !correctAnswer.isJsonObject()) return null;
            JsonElement c = correctAnswer.getAsJsonObject().get("choice");
            return c == null || c.isJsonNull() ? null : c.getAsString();
        }
    }

    public static class QuestionSet {
        public List<Question> questions;
        public List<Question> solutions;
        public List<Material> materials;

        public List<Question> items() {
            if (questions != null) return questions;
            if (solutions != null) return solutions;
            return List.of();
        }

        public List<Material> materialList() {
            return materials == null ? List.of() : materials;
        }
    }

    public static class UserAnswer {
        public long questionId;
        public int questionIndex;
        public String choice;
    }

    public static class Exercise {
        public long id;
        public List<UserAnswer> userAnswers = new ArrayList<>();
    }
}
