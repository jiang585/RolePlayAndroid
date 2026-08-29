package com.example.roleplaychat.domain.repository;

/**
 * 可取消请求句柄（架构文档 §7.1）。
 */
public interface CancellableRequest {

    void cancel();
}
