package com.example.roleplaychat.util;

import java.util.UUID;

/**
 * 可注入 ID 生成器（架构文档 §14.1：禁止在业务代码直接调用随机 UUID
 * 静态方法，统一注入 {@link IdGenerator} 便于测试）。
 */
public interface IdGenerator {

    /** 生成一个 UUID 字符串。 */
    String newId();

    /** 生成一个形如 {@code uuid:counter} 的请求 ID，保证同一次生成中唯一。 */
    String newRequestId();

    static IdGenerator random() {
        return new IdGenerator() {
            private int counter;

            @Override
            public String newId() {
                return UUID.randomUUID().toString();
            }

            @Override
            public synchronized String newRequestId() {
                counter++;
                return UUID.randomUUID() + ":" + counter;
            }
        };
    }
}
