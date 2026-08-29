package com.example.roleplaychat.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.roleplaychat.RolePlayChatApp;
import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.usecase.ExportDataUseCase;

import java.io.File;

/**
 * 导出 Worker（架构文档 §4 worker/ExportWorker.java）。
 * 后台执行导出，避免阻塞 UI。
 */
public class ExportWorker extends Worker {

    public static final String KEY_EXPORT_TYPE = "export_type";
    public static final String KEY_SCRIPT_ID = "script_id";
    public static final String KEY_CHARACTER_ID = "character_id";
    public static final String KEY_INCLUDE_HIDDEN = "include_hidden";

    public ExportWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String typeName = getInputData().getString(KEY_EXPORT_TYPE);
        String scriptId = getInputData().getString(KEY_SCRIPT_ID);
        String characterId = getInputData().getString(KEY_CHARACTER_ID);
        boolean includeHidden = getInputData().getBoolean(KEY_INCLUDE_HIDDEN, false);

        if (typeName == null) {
            return Result.failure();
        }
        ExportDataUseCase.ExportType type = ExportDataUseCase.ExportType.valueOf(typeName);
        RolePlayChatApp app = (RolePlayChatApp) getApplicationContext();
        File targetDir = app.container().assetStore.exportsDir();
        File target = new File(targetDir, "export_" + System.currentTimeMillis() + ".json");
        AppError error = app.container().exportDataUseCase.execute(
                type, scriptId, characterId, target, includeHidden);
        return error == null ? Result.success() : Result.failure();
    }
}
