package com.example.roleplaychat.domain.usecase;

/**
 * 停止生成用例（架构文档 §8.7）：取消本地 Call，落库取消状态。
 */
public class StopGenerationUseCase {

    private final AdvanceAiUseCase advanceAiUseCase;

    public StopGenerationUseCase(AdvanceAiUseCase advanceAiUseCase) {
        this.advanceAiUseCase = advanceAiUseCase;
    }

    public void execute(String scriptId) {
        advanceAiUseCase.stop(scriptId);
    }
}
