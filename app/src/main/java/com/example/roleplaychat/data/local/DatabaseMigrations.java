package com.example.roleplaychat.data.local;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据库迁移注册表（架构文档 §6.1）。
 * 任何 schema 变化必须在此追加迁移并补充迁移测试。
 */
public final class DatabaseMigrations {

    /** 当前数据库版本。 */
    public static final int CURRENT_VERSION = 5;

    /** v4 -> v5：旧剧本默认为无图模式；新增视觉身份、图片任务和消息附件表。 */
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE scripts ADD COLUMN media_mode TEXT NOT NULL DEFAULT 'TEXT_ONLY'");
            db.execSQL("CREATE TABLE IF NOT EXISTS `character_visual_profiles` ("
                    + "`id` TEXT NOT NULL, `character_id` TEXT NOT NULL, `status` TEXT NOT NULL, "
                    + "`source` TEXT NOT NULL, `version` INTEGER NOT NULL, `identity_prompt` TEXT, "
                    + "`appearance_json` TEXT, `negative_prompt` TEXT, `created_at` INTEGER NOT NULL, "
                    + "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), "
                    + "FOREIGN KEY(`character_id`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_character_visual_profiles_character_id` "
                    + "ON `character_visual_profiles` (`character_id`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `character_visual_assets` ("
                    + "`id` TEXT NOT NULL, `profile_id` TEXT NOT NULL, `local_path` TEXT NOT NULL, "
                    + "`sha256` TEXT, `asset_type` TEXT NOT NULL, `is_primary` INTEGER NOT NULL, "
                    + "`width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`id`), FOREIGN KEY(`profile_id`) REFERENCES `character_visual_profiles`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_visual_assets_profile_id` "
                    + "ON `character_visual_assets` (`profile_id`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `image_generation_jobs` ("
                    + "`id` TEXT NOT NULL, `script_id` TEXT NOT NULL, `character_id` TEXT, `message_id` TEXT, "
                    + "`client_job_id` TEXT NOT NULL, `huajing_job_id` TEXT, `trigger` TEXT NOT NULL, "
                    + "`intent` TEXT NOT NULL, `model` TEXT NOT NULL, `mode` TEXT NOT NULL, `status` TEXT NOT NULL, "
                    + "`prompt_snapshot` TEXT, `reference_snapshot_json` TEXT, `result_asset_id` TEXT, "
                    + "`retry_count` INTEGER NOT NULL, `error_code` TEXT, `created_at` INTEGER NOT NULL, "
                    + "`started_at` INTEGER, `finished_at` INTEGER, PRIMARY KEY(`id`), "
                    + "FOREIGN KEY(`script_id`) REFERENCES `scripts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, "
                    + "FOREIGN KEY(`character_id`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, "
                    + "FOREIGN KEY(`message_id`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_image_generation_jobs_client_job_id` "
                    + "ON `image_generation_jobs` (`client_job_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_generation_jobs_script_id_status` "
                    + "ON `image_generation_jobs` (`script_id`, `status`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_generation_jobs_character_id` "
                    + "ON `image_generation_jobs` (`character_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_generation_jobs_message_id` "
                    + "ON `image_generation_jobs` (`message_id`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `message_attachments` ("
                    + "`id` TEXT NOT NULL, `message_id` TEXT NOT NULL, `character_id` TEXT, "
                    + "`type` TEXT NOT NULL, `status` TEXT NOT NULL, `local_path` TEXT, `mime_type` TEXT, "
                    + "`width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `job_id` TEXT, `sort_index` INTEGER NOT NULL, "
                    + "`created_at` INTEGER NOT NULL, PRIMARY KEY(`id`), "
                    + "FOREIGN KEY(`message_id`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, "
                    + "FOREIGN KEY(`character_id`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, "
                    + "FOREIGN KEY(`job_id`) REFERENCES `image_generation_jobs`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_attachments_message_id_sort_index` "
                    + "ON `message_attachments` (`message_id`, `sort_index`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_attachments_job_id` ON `message_attachments` (`job_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_attachments_character_id` ON `message_attachments` (`character_id`)");
        }
    };

    /** v1 -> v2：world_settings 新增剧本级对话规则（扮演要求 + 每轮回复上限）。 */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE world_settings ADD COLUMN chat_style_directive TEXT");
            db.execSQL("ALTER TABLE world_settings ADD COLUMN max_responders_per_turn "
                    + "INTEGER NOT NULL DEFAULT 2");
        }
    };

    /** v2 -> v3：新增独立朋友圈与评论表；既有 messages 表完全不变。 */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `moments` (`id` TEXT NOT NULL, `script_id` TEXT, "
                    + "`author_character_id` TEXT, `author_type` TEXT, `author_name_snapshot` TEXT, "
                    + "`author_avatar_snapshot` TEXT, `content` TEXT, `created_at` INTEGER NOT NULL, "
                    + "`source_request_id` TEXT, `source_sequence` INTEGER NOT NULL, PRIMARY KEY(`id`), "
                    + "FOREIGN KEY(`script_id`) REFERENCES `scripts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, "
                    + "FOREIGN KEY(`author_character_id`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_moments_script_id_created_at` ON `moments` (`script_id`, `created_at`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_moments_author_character_id` ON `moments` (`author_character_id`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `moment_comments` (`id` TEXT NOT NULL, `moment_id` TEXT, "
                    + "`author_character_id` TEXT, `author_type` TEXT, `author_name_snapshot` TEXT, "
                    + "`author_avatar_snapshot` TEXT, `content` TEXT, `created_at` INTEGER NOT NULL, "
                    + "`source_request_id` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`moment_id`) REFERENCES `moments`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`author_character_id`) REFERENCES `characters`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE SET NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_moment_comments_moment_id_created_at` ON `moment_comments` (`moment_id`, `created_at`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_moment_comments_author_character_id_created_at` ON `moment_comments` (`author_character_id`, `created_at`)");
        }
    };

    /** v3 -> v4：评论增加可选父评论，支持玩家与角色在同一动态下继续对话。 */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE moment_comments ADD COLUMN parent_comment_id TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_moment_comments_parent_comment_id` ON `moment_comments` (`parent_comment_id`)");
        }
    };

    /** 全部迁移列表（按版本升序）。 */
    public static final List<Migration> ALL_MIGRATIONS = new ArrayList<>();

    static {
        ALL_MIGRATIONS.add(MIGRATION_1_2);
        ALL_MIGRATIONS.add(MIGRATION_2_3);
        ALL_MIGRATIONS.add(MIGRATION_3_4);
        ALL_MIGRATIONS.add(MIGRATION_4_5);
    }

    private DatabaseMigrations() {
    }
}
