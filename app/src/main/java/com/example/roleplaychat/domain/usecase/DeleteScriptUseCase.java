package com.example.roleplaychat.domain.usecase;

import com.example.roleplaychat.domain.repository.ScriptRepository;

/**
 * 删除剧本用例（架构文档 §7.2）：DB 事务级联删除子记录，
 * 之后由 CleanupWorker 清理孤儿文件（§6.3）。
 */
public class DeleteScriptUseCase {

    private final ScriptRepository scriptRepository;

    public DeleteScriptUseCase(ScriptRepository scriptRepository) {
        this.scriptRepository = scriptRepository;
    }

    public void execute(String scriptId) {
        scriptRepository.deleteScript(scriptId);
    }
}
