package com.example.roleplaychat.data.local.converter;

import androidx.room.TypeConverter;

import com.example.roleplaychat.util.JsonUtils;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Room 类型转换器：复杂数组与 Map 以 JSON TEXT 存储（架构文档 §6.1）。
 */
public class RoomConverters {

    private static final Type STRING_LIST = new TypeToken<List<String>>() {
    }.getType();
    private static final Type STRING_MAP = new TypeToken<Map<String, String>>() {
    }.getType();

    @TypeConverter
    public static String fromStringList(List<String> values) {
        return values == null ? "[]" : JsonUtils.toJson(values);
    }

    @TypeConverter
    public static List<String> toStringList(String json) {
        if (json == null || json.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        List<String> result = JsonUtils.fromJson(json, STRING_LIST);
        return result == null ? new java.util.ArrayList<>() : result;
    }

    @TypeConverter
    public static String fromStringMap(Map<String, String> values) {
        return values == null ? "{}" : JsonUtils.toJson(values);
    }

    @TypeConverter
    public static Map<String, String> toStringMap(String json) {
        if (json == null || json.isEmpty()) {
            return new java.util.LinkedHashMap<>();
        }
        Map<String, String> result = JsonUtils.fromJson(json, STRING_MAP);
        return result == null ? new java.util.LinkedHashMap<>() : result;
    }
}
