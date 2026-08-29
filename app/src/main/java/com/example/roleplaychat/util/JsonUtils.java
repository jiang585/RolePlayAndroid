package com.example.roleplaychat.util;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.lang.reflect.Type;

/**
 * Gson 封装：统一配置（snake_case 由字段注解控制）、深度限制与安全解析。
 * 所有 JSON 解析必须经过本类，避免散布 Gson 实例。
 */
public final class JsonUtils {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    /** JSON 嵌套深度上限，防止恶意深层结构（架构文档 §9.7）。 */
    private static final int MAX_DEPTH = 64;

    private JsonUtils() {
    }

    public static Gson gson() {
        return GSON;
    }

    public static String toJson(Object src) {
        return GSON.toJson(src);
    }

    @Nullable
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        if (!isSafeJson(json)) {
            throw new JsonSyntaxException("JSON too deep or invalid");
        }
        return GSON.fromJson(json, clazz);
    }

    @Nullable
    public static <T> T fromJson(String json, Type type) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        if (!isSafeJson(json)) {
            throw new JsonSyntaxException("JSON too deep or invalid");
        }
        return GSON.fromJson(json, type);
    }

    /** 校验 JSON 深度与基本合法性，输入不可信时必须调用（架构文档 §9.7）。 */
    public static boolean isSafeJson(String json) {
        if (json == null) {
            return false;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            return depthOf(element) <= MAX_DEPTH;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    private static int depthOf(JsonElement element) {
        if (element.isJsonObject()) {
            int max = 0;
            for (JsonElement child : element.getAsJsonObject().asMap().values()) {
                max = Math.max(max, depthOf(child));
            }
            return max + 1;
        }
        if (element.isJsonArray()) {
            int max = 0;
            for (JsonElement child : element.getAsJsonArray()) {
                max = Math.max(max, depthOf(child));
            }
            return max + 1;
        }
        return 1;
    }
}
