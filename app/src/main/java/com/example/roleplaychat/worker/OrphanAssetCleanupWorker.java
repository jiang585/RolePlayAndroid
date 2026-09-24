package com.example.roleplaychat.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.roleplaychat.RolePlayChatApp;
import com.example.roleplaychat.data.local.AppDatabase;
import com.example.roleplaychat.data.local.entity.AppearanceEntity;
import com.example.roleplaychat.data.local.entity.CharacterEntity;
import com.example.roleplaychat.data.local.entity.ScriptEntity;
import com.example.roleplaychat.data.local.entity.MessageAttachmentEntity;
import com.example.roleplaychat.data.local.entity.CharacterVisualAssetEntity;
import com.example.roleplaychat.data.file.ImageFavoriteStore;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 孤儿资产清理 Worker（架构文档 §6.3）：删除剧本后清理无引用资源。
 * 删除剧本 DB 事务成功后异步执行。
 */
public class OrphanAssetCleanupWorker extends Worker {

    public OrphanAssetCleanupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        RolePlayChatApp app = (RolePlayChatApp) getApplicationContext();
        Set<String> referenced = collectReferencedAssets(app.container().database, app.getApplicationContext());
        app.container().assetStore.deleteOrphanAssets(referenced);
        return Result.success();
    }

    public static Set<String> collectReferencedAssets(AppDatabase database) {
        return collectReferencedAssets(database, null);
    }

    private static Set<String> collectReferencedAssets(AppDatabase database, @androidx.annotation.Nullable Context context) {
        Set<String> referenced = new HashSet<>();

        List<ScriptEntity> scripts = database.scriptDao().getAll();
        if (scripts != null) {
            for (ScriptEntity script : scripts) {
                if (script.cover_ref != null) {
                    referenced.add(script.cover_ref);
                }
            }
        }

        // 遍历全部角色头像引用
        Set<String> allScriptIds = new HashSet<>();
        if (scripts != null) {
            for (ScriptEntity script : scripts) {
                allScriptIds.add(script.id);
            }
        }
        for (String scriptId : allScriptIds) {
            List<CharacterEntity> byScript = database.characterDao()
                    .getAllByScriptId(scriptId);
            for (CharacterEntity character : byScript) {
                if (character.avatar_ref != null) {
                    referenced.add(character.avatar_ref);
                }
            }
        }

        // 背景图由装扮表引用，清理时必须与封面、头像同等保留。
        List<AppearanceEntity> appearances = database.appearanceDao().getAll();
        for (AppearanceEntity appearance : appearances) {
            if (appearance.background_ref != null) {
                referenced.add(appearance.background_ref);
            }
        }

        // 聊天生图和角色身份图由附件/视觉身份表引用，不能被清理任务误删。
        List<MessageAttachmentEntity> attachments = database.messageAttachmentDao().getAll();
        for (MessageAttachmentEntity attachment : attachments) {
            if (attachment.local_path != null) referenced.add(attachment.local_path);
        }
        List<CharacterVisualAssetEntity> visualAssets = database.characterVisualAssetDao().getAll();
        for (CharacterVisualAssetEntity asset : visualAssets) {
            if (asset.local_path != null) referenced.add(asset.local_path);
        }

        // 收藏是独立于剧本的用户资产，即使剧本被删除，也应继续保留。
        if (context != null) referenced.addAll(new ImageFavoriteStore(context).all());

        return referenced;
    }
}
