package com.example.roleplaychat.domain.usecase;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.roleplaychat.domain.ai.AiTurnOrchestrator;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * AI 自动推进用例测试（架构文档 §15.2-7：停止后不再启动下一轮，已完成消息保留）。
 * 本测试验证轮数边界校验与停止标记。
 */
public class AdvanceAiUseCaseTest {

    @Test
    public void start_invalidRounds_rejected() {
        AiTurnOrchestrator orchestrator = mock(AiTurnOrchestrator.class);
        AdvanceAiUseCase useCase = new AdvanceAiUseCase(orchestrator);

        assertFalse(useCase.start("script-1", 0, null));
        assertFalse(useCase.start("script-1", 21, null));
    }

    @Test
    public void stop_wakesWorkerAndDoesNotCancelAgainLater() throws Exception {
        AiTurnOrchestrator orchestrator = mock(AiTurnOrchestrator.class);
        AdvanceAiUseCase useCase = new AdvanceAiUseCase(orchestrator);
        CountDownLatch finished = new CountDownLatch(1);
        when(orchestrator.start(org.mockito.ArgumentMatchers.eq("script-1"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("request-1");

        assertTrue(useCase.start("script-1", 3, new NoOpCallback() {
            @Override
            public void onFinished(int completedRounds) {
                finished.countDown();
            }
        }));
        verify(orchestrator, timeout(1000)).start(org.mockito.ArgumentMatchers.eq("script-1"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any());

        useCase.stop("script-1");

        assertTrue(finished.await(1, TimeUnit.SECONDS));
        verify(orchestrator, times(1)).stop("script-1");
    }

    @Test
    public void start_whileAlreadyRunning_isRejected() {
        AiTurnOrchestrator orchestrator = mock(AiTurnOrchestrator.class);
        AdvanceAiUseCase useCase = new AdvanceAiUseCase(orchestrator);

        assertTrue(useCase.startContinuous("script-1", null));
        assertFalse(useCase.startContinuous("script-1", null));
        useCase.stop("script-1");
    }

    private static class NoOpCallback implements AdvanceAiUseCase.Callback {
        @Override public void onRoundStarted(int round, String requestId) { }
        @Override public void onRoundCommitted(int round, String requestId,
                com.example.roleplaychat.domain.model.AiBatch batch) { }
        @Override public void onFinished(int completedRounds) { }
        @Override public void onFailed(int round,
                com.example.roleplaychat.domain.model.AppErrorCode errorCode) { }
    }
}
