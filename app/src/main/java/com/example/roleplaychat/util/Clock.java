package com.example.roleplaychat.util;

/**
 * 可注入时钟，便于测试（架构文档 §14.1：禁止在业务代码直接调用
 * {@code System.currentTimeMillis()}，统一注入 {@link Clock}）。
 */
public interface Clock {

    long currentTimeMillis();

    static Clock system() {
        return System::currentTimeMillis;
    }
}
