package com.example.roleplaychat.domain.usecase;

import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.repository.ScriptRepository;
import com.example.roleplaychat.domain.validation.ScriptValidator;

/**
 * 创建剧本用例（架构文档 §7.2）：同事务创建 Script/World/Appearance/Player slot。
 */
public class CreateScriptUseCase {

    private final ScriptRepository scriptRepository;

    public CreateScriptUseCase(ScriptRepository scriptRepository) {
        this.scriptRepository = scriptRepository;
    }

    /** @return 新剧本 ID，或 null 表示校验失败（错误见 error）。 */
    public String execute(String name, String oneLine, long now, AppError[] error) {
        AppError nameError = ScriptValidator.validateName(name);
        if (nameError != null) {
            setError(error, nameError);
            return null;
        }
        AppError lineError = ScriptValidator.validateOneLine(oneLine);
        if (lineError != null) {
            setError(error, lineError);
            return null;
        }
        return scriptRepository.createScript(name.trim(), oneLine == null ? null : oneLine.trim(), now);
    }

    private void setError(AppError[] error, AppError appError) {
        if (error != null && error.length > 0) {
            error[0] = appError;
        }
    }
}
