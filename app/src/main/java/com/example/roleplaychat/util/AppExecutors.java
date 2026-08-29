package com.example.roleplaychat.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 统一线程执行器（架构文档 §12.1）。
 * <ul>
 *   <li>{@link #diskIO()}：Room 事务、JSON、压缩、文件复制。</li>
 *   <li>{@link #networkIO()}：Retrofit/OkHttp 请求。</li>
 *   <li>{@link #mainThread()}：LiveData 与 View 更新。</li>
 * </ul>
 * 测试中可替换为同步执行器。
 */
public final class AppExecutors {

    private final Executor diskIo;
    private final Executor networkIo;
    private final Executor mainThread;

    public AppExecutors() {
        this(Executors.newSingleThreadExecutor(), Executors.newFixedThreadPool(4), new MainThreadExecutor());
    }

    public AppExecutors(ExecutorService diskIo, ExecutorService networkIo, Executor mainThread) {
        this.diskIo = diskIo;
        this.networkIo = networkIo;
        this.mainThread = mainThread;
    }

    public Executor diskIO() {
        return diskIo;
    }

    public Executor networkIO() {
        return networkIo;
    }

    public Executor mainThread() {
        return mainThread;
    }

    /** 供测试使用的同步执行器（立即执行）。 */
    public static AppExecutors synchronous() {
        return new AppExecutors(Runnable::run, Runnable::run, Runnable::run);
    }

    /** 全同步构造（测试）。 */
    public AppExecutors(Executor diskIo, Executor networkIo, Executor mainThread) {
        this.diskIo = diskIo;
        this.networkIo = networkIo;
        this.mainThread = mainThread;
    }

    private static final class MainThreadExecutor implements Executor {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            handler.post(command);
        }
    }
}
