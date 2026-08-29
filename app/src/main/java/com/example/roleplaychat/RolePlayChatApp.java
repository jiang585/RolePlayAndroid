package com.example.roleplaychat;

import android.app.Application;

import com.example.roleplaychat.di.AppContainer;

/**
 * Application 与 AppContainer 初始化（架构文档 §4 文件清单）。
 * 同时执行启动恢复：将遗留 STREAMING 消息标记为 PROCESS_INTERRUPTED（§6.3/§8.8）。
 */
public class RolePlayChatApp extends Application {

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
        // 启动恢复：遗留 STREAMING -> PROCESS_INTERRUPTED（可重试）
        container.executors.diskIO().execute(() ->
                container.chatRepository.markInterruptedStreams());
    }

    public AppContainer container() {
        return container;
    }
}
