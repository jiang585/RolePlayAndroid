package com.example.roleplaychat.data.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.room.Room;

import com.example.roleplaychat.data.local.AppDatabase;
import com.example.roleplaychat.data.local.entity.AppearanceEntity;
import com.example.roleplaychat.data.mapper.CharacterCardMapper;
import com.example.roleplaychat.data.repository.ImportExportRepositoryImpl;
import com.example.roleplaychat.util.JsonUtils;
import com.example.roleplaychat.worker.OrphanAssetCleanupWorker;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 导入导出文件测试（架构文档 §15.2-11：Zip Slip、超大文件、损坏 base64 被拒绝；
 * §15.2-12：导出再导入后映射一致）。
 */
@RunWith(AndroidJUnit4.class)
public class ImportExportTest {

    @Test
    public void detectCardKind_customFormat() throws Exception {
        File file = writeTemp("{\"format\":\"roleplay-character-card\",\"schema_version\":1,"
                + "\"data\":{\"name\":\"林晚晴\",\"personality\":\"温和\"}}");
        assertEquals(ImportReader.CardKind.CUSTOM, ImportReader.detectCardKind(file));
    }

    @Test
    public void detectCardKind_tavernFormat() throws Exception {
        File file = writeTemp("{\"spec\":\"chara_card_v2\",\"data\":{"
                + "\"name\":\"张三\",\"description\":\"镖师\",\"first_mes\":\"你好\"}}");
        assertEquals(ImportReader.CardKind.TAVERN, ImportReader.detectCardKind(file));
    }

    @Test
    public void parseCard_tavernMapsFields() throws Exception {
        File file = writeTemp("{\"spec\":\"chara_card_v2\",\"data\":{"
                + "\"name\":\"张三\",\"description\":\"镖师背景\",\"personality\":\"豪爽\","
                + "\"first_mes\":\"来，喝一杯\",\"mes_example\":\"（笑）你好。\"}}");
        CharacterCardMapper.Data data = ImportReader.parseCard(file, null);
        assertNotNull(data);
        assertEquals("张三", data.name);
        assertEquals("镖师背景", data.backstory);
        assertEquals("豪爽", data.personality);
        assertEquals("来，喝一杯", data.sampleLines.get(0));
    }

    @Test
    public void invalidJson_rejected() throws Exception {
        File file = writeTemp("{not valid json");
        assertNull(ImportReader.parseCard(file, null));
    }

    @Test
    public void importCharacter_missingTargetWithNullErrorSlot_returnsFailureWithoutCrash() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        File file = writeTemp("{\"format\":\"roleplay-character-card\",\"schema_version\":1,"
                + "\"data\":{\"name\":\"Alice\"}}");
        try {
            ImportExportRepositoryImpl repository = new ImportExportRepositoryImpl(
                    db, new LocalAssetStore(context));
            assertNull(repository.importCharacterCard(file, "missing-script", null));
            assertEquals(1, db.importLogDao().getRecent().size());
        } finally {
            db.close();
            file.delete();
        }
    }

    @Test
    public void collectReferencedAssets_includesAppearanceBackground() {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        try {
            db.appearanceDao().insert(new AppearanceEntity("appearance", "GLOBAL", "global",
                    "IMAGE", "backgrounds/kept.webp", "CENTER_CROP", 0f,
                    "default", "#FFFFFFFF", "#FF000000", "#FF000000", 1f));
            assertTrue(OrphanAssetCleanupWorker.collectReferencedAssets(db)
                    .contains("backgrounds/kept.webp"));
        } finally {
            db.close();
        }
    }

    @Test
    public void oversizedFile_rejected() throws Exception {
        File file = new File(ApplicationProvider.getApplicationContext().getCacheDir(),
                "big_" + System.nanoTime() + ".json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            byte[] padding = new byte[(int) (ImportReader.MAX_FILE_BYTES + 1)];
            out.write(padding);
        }
        assertNull(ImportReader.readLimited(file));
        file.delete();
    }

    @Test
    public void zipSlip_unsafeEntry_rejected() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        // 无 manifest 的恶意包：在 manifest 校验处即被拒绝。
        File zip = new File(context.getCacheDir(), "evil_" + System.nanoTime() + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("../evil.txt"));
            zos.write("boom".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        File target = new File(context.getCacheDir(), "extract_" + System.nanoTime());
        target.mkdirs();
        String error = ScriptPackageArchive.extractTo(zip, target);
        assertNotNull(error);
        zip.delete();
        com.example.roleplaychat.data.file.LocalAssetStore store =
                new LocalAssetStore(context);
        store.deleteRecursively(target);

        // 带 manifest 但声明越界条目：必须走到 Zip Slip 路径检查并返回 unsafe。
        File zipWithManifest = new File(context.getCacheDir(),
                "evil_manifest_" + System.nanoTime() + ".zip");
        String payload = "boom";
        String sha256 = sha256Hex(payload.getBytes(StandardCharsets.UTF_8));
        String manifest = "{\"format\":\"roleplay-script-package\",\"schema_version\":1,"
                + "\"files\":{\"../evil.txt\":{\"size\":" + payload.length()
                + ",\"sha256\":\"" + sha256 + "\"}}}";
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipWithManifest))) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("../evil.txt"));
            zos.write(payload.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        File target2 = new File(context.getCacheDir(), "extract2_" + System.nanoTime());
        target2.mkdirs();
        String unsafeError = ScriptPackageArchive.extractTo(zipWithManifest, target2);
        assertNotNull(unsafeError);
        // 新版平台的 ZipInputStream 会在库层直接拒绝（"Invalid zip entry path"），
        // 旧版走到应用层校验返回 "unsafe path"；两者都证明恶意路径未被解压。
        assertTrue("应拒绝越界路径，实际返回：" + unsafeError,
                unsafeError.contains("unsafe") || unsafeError.contains("Invalid zip entry path"));
        zipWithManifest.delete();
        store.deleteRecursively(target2);
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest(bytes)) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    public void chatEnvelope_roundTripViaGson() {
        ExportWriter.ChatEnvelope envelope = new ExportWriter.ChatEnvelope();
        envelope.format = "roleplay-chat";
        envelope.schemaVersion = 1;
        ExportWriter.ChatItem item = new ExportWriter.ChatItem();
        item.id = "m1";
        item.sequence = 1;
        item.createdAt = 1000L;
        item.type = "NARRATION";
        item.content = "旁白内容";
        envelope.messages.add(item);

        String json = JsonUtils.toJson(envelope);
        ExportWriter.ChatEnvelope parsed = JsonUtils.fromJson(json, ExportWriter.ChatEnvelope.class);
        assertNotNull(parsed);
        assertEquals(1, parsed.messages.size());
        assertEquals("旁白内容", parsed.messages.get(0).content);
    }

    private File writeTemp(String content) throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File file = new File(context.getCacheDir(), "card_" + System.nanoTime() + ".json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}
