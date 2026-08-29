package com.example.roleplaychat.domain.usecase;

import androidx.annotation.Nullable;

import com.example.roleplaychat.domain.ai.AiTurnOrchestrator;
import com.example.roleplaychat.domain.model.AiBatch;
import com.example.roleplaychat.domain.model.AiRequest;
import com.example.roleplaychat.domain.model.AppErrorCode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 自动推进用例（架构文档 §8.7）：
 * 1~20 轮；每轮独立请求与 requestId，共享 batchId；第 N 轮完成入库后才能开始 N+1；
 * 停止标记在每轮开始前与 SSE 回调中检查；达到轮数/停止/失败/空事件时结束。
 */
public class AdvanceAiUseCase {

    public static final int MIN_ROUNDS = 1;
    public static final int MAX_ROUNDS = 20;
    private static final int MAX_AUTOMATIC_ROUNDS = 100;

    public interface Callback {
        void onRoundStarted(int round, String requestId);

        void onRoundCommitted(int round, String requestId, AiBatch batch);

        void onFinished(int completedRounds);

        void onFailed(int round, @Nullable AppErrorCode errorCode);
    }

    private final AiTurnOrchestrator orchestrator;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object workerLock = new Object();
    private volatile boolean stopRequested;
    private volatile boolean continuous;
    @Nullable
    private volatile Thread worker;
    @Nullable
    private volatile String activeScriptId;

    public AdvanceAiUseCase(AiTurnOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * 启动自动推进（异步）。返回是否成功开始。
     */
    public boolean start(String scriptId, int rounds, Callback callback) {
        if (rounds < MIN_ROUNDS || rounds > MAX_ROUNDS) {
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        stopRequested = false;
        continuous = false;
        // 使用后台线程推进，避免阻塞调用线程
        Thread newWorker = new Thread(() -> run(scriptId, rounds, callback), "ai-advance");
        newWorker.setDaemon(true);
        synchronized (workerLock) {
            activeScriptId = scriptId;
            worker = newWorker;
        }
        newWorker.start();
        return true;
    }

    public void stop(String scriptId) {
        Thread workerToInterrupt = null;
        synchronized (workerLock) {
            if (scriptId != null && scriptId.equals(activeScriptId)) {
                stopRequested = true;
                continuous = false;
                workerToInterrupt = worker;
            }
        }
        orchestrator.stop(scriptId);
        if (workerToInterrupt != null) {
            workerToInterrupt.interrupt();
        }
    }

    /** 持续推进，直到用户点击停止或发生不可恢复错误。 */
    public boolean startContinuous(String scriptId, Callback callback) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        stopRequested = false;
        continuous = true;
        Thread newWorker = new Thread(() -> runContinuous(scriptId, callback), "ai-continuous-advance");
        newWorker.setDaemon(true);
        synchronized (workerLock) {
            activeScriptId = scriptId;
            worker = newWorker;
        }
        newWorker.start();
        return true;
    }

    private void run(String scriptId, int rounds, Callback callback) {
        int completed = 0;
        try {
            for (int round = 1; round <= rounds; round++) {
                if (stopRequested) {
                    break;
                }
                final int currentRound = round;
                CountDownLatch roundDone = new CountDownLatch(1);
                AtomicBoolean committed = new AtomicBoolean(false);
                AtomicReference<AppErrorCode> error = new AtomicReference<>();
                String requestId = orchestrator.start(scriptId, AiRequest.Mode.AUTO_ADVANCE, round,
                        null, new AiTurnOrchestrator.Callback() {
                            @Override
                            public void onBatchCommitted(String id, AiBatch batch) {
                                committed.set(true);
                                if (callback != null) {
                                    callback.onRoundCommitted(currentRound, id, batch);
                                }
                                roundDone.countDown();
                            }

                            @Override
                            public void onGenerationFailed(String id, @Nullable AppErrorCode code) {
                                error.set(code == null ? AppErrorCode.UNKNOWN : code);
                                roundDone.countDown();
                            }
                        });
                if (stopRequested) {
                    orchestrator.stop(scriptId, requestId);
                    break;
                }
                if (callback != null) {
                    callback.onRoundStarted(currentRound, requestId);
                }
                if (!awaitRound(roundDone) || !committed.get()) {
                    if (!stopRequested) {
                        orchestrator.stop(scriptId, requestId);
                    }
                    if (!stopRequested && callback != null
                            && error.get() != AppErrorCode.CANCELLED_BY_USER) {
                        callback.onFailed(currentRound, error.get());
                    }
                    break;
                }
                completed++;
            }
        } finally {
            finishWorker();
            if (callback != null) {
                callback.onFinished(completed);
            }
        }
    }

    private void runContinuous(String scriptId, Callback callback) {
        int completed = 0;
        try {
            while (continuous && !stopRequested && completed < MAX_AUTOMATIC_ROUNDS) {
                CountDownLatch roundDone = new CountDownLatch(1);
                AtomicBoolean committed = new AtomicBoolean(false);
                AtomicBoolean shouldContinue = new AtomicBoolean(false);
                AtomicReference<AppErrorCode> error = new AtomicReference<>();
                final int currentRound = completed + 1;
                String requestId = orchestrator.start(scriptId, AiRequest.Mode.AUTO_ADVANCE,
                        currentRound, null, new AiTurnOrchestrator.Callback() {
                            @Override
                            public void onBatchCommitted(String id, AiBatch batch) {
                                committed.set(true);
                                shouldContinue.set(batch.shouldContinueScene());
                                if (callback != null) {
                                    callback.onRoundCommitted(currentRound, id, batch);
                                }
                                roundDone.countDown();
                            }

                            @Override
                            public void onGenerationFailed(String id, @Nullable AppErrorCode code) {
                                error.set(code == null ? AppErrorCode.UNKNOWN : code);
                                roundDone.countDown();
                            }
                        });
                if (stopRequested) {
                    orchestrator.stop(scriptId, requestId);
                    break;
                }
                if (callback != null) {
                    callback.onRoundStarted(currentRound, requestId);
                }
                if (!awaitRound(roundDone) || !committed.get()) {
                    if (!stopRequested) {
                        orchestrator.stop(scriptId, requestId);
                    }
                    if (!stopRequested && callback != null
                            && error.get() != AppErrorCode.CANCELLED_BY_USER) {
                        callback.onFailed(currentRound, error.get());
                    }
                    break;
                }
                completed++;
                continuous = shouldContinue.get();
            }
        } finally {
            finishWorker();
            if (callback != null) {
                callback.onFinished(completed);
            }
        }
    }

    private boolean awaitRound(CountDownLatch roundDone) {
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
            while (!stopRequested && System.nanoTime() < deadline) {
                if (roundDone.await(100, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopRequested = true;
            return false;
        }
    }

    private void finishWorker() {
        synchronized (workerLock) {
            if (worker == Thread.currentThread()) {
                worker = null;
                activeScriptId = null;
                running.set(false);
            }
        }
    }
}
