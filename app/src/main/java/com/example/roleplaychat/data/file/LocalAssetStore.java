package com.example.roleplaychat.data.file;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.example.roleplaychat.util.FileNameSanitizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

/**
 * 本地资源存储（架构文档 §4.1）：用户图片复制到应用私有目录，
 * 数据库仅保存资源 ID 或相对路径。所有文件名经 {@link FileNameSanitizer} 净化，
 * 解析相对引用时拒绝目录穿越（架构文档 §9.7）。
 */
public final class LocalAssetStore {

    public static final String DIR_AVATARS = "avatars";
    public static final String DIR_BACKGROUNDS = "backgrounds";
    public static final String DIR_COVERS = "covers";
    public static final String DIR_EXPORTS = "exports";
    public static final String DIR_TMP = "tmp";
    public static final String DIR_PACKAGES = "packages";

    /** 单个资产大小上限（导入单文件 20 MB，架构文档 §2.3）。 */
    public static final long MAX_ASSET_BYTES = 20L * 1024 * 1024;

    private final File baseDir;
    private final ContentResolver contentResolver;

    public LocalAssetStore(Context context) {
        this.baseDir = new File(context.getFilesDir(), "assets");
        this.contentResolver = context.getContentResolver();
    }

    /** 从内容 URI 复制资产，返回相对引用（如 avatars/xxx.webp）。 */
    @Nullable
    public String storeFromUri(String subDir, Uri uri, @Nullable String extension) {
        try (InputStream in = contentResolver.openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            return storeStream(subDir, uri.getLastPathSegment(), extension, in);
        } catch (IOException e) {
            return null;
        }
    }

    /** 从输入流复制资产到子目录，返回相对引用。 */
    @Nullable
    public String storeStream(String subDir, String rawName, @Nullable String extension, InputStream in) {
        File dir = ensureDir(subDir);
        String ext = extension == null ? "" : extension;
        String safe = FileNameSanitizer.sanitize(rawName, "asset") + ext;
        File target = uniqueFile(dir, safe);
        try (OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_ASSET_BYTES) {
                    throw new IOException("asset too large");
                }
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            target.delete();
            return null;
        }
        return subDir + "/" + target.getName();
    }

    /** 将文本写入导出目录，返回目标文件。 */
    public File writeTextToExports(String rawName, String extension, String content) throws IOException {
        File dir = ensureDir(DIR_EXPORTS);
        File target = uniqueFile(dir, FileNameSanitizer.sanitize(rawName, "export") + extension);
        Files.write(target.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return target;
    }

    /** 将字节写入导出目录，返回目标文件。 */
    public File writeBytesToExports(String rawName, String extension, byte[] content) throws IOException {
        File dir = ensureDir(DIR_EXPORTS);
        File target = uniqueFile(dir, FileNameSanitizer.sanitize(rawName, "export") + extension);
        Files.write(target.toPath(), content);
        return target;
    }

    /** 解析相对引用为文件；非法引用（穿越、不存在）返回 null。 */
    @Nullable
    public File resolve(String relativeRef) {
        if (relativeRef == null || relativeRef.isEmpty()) {
            return null;
        }
        File file = new File(baseDir, relativeRef);
        String canonicalBase;
        String canonicalFile;
        try {
            canonicalBase = baseDir.getCanonicalPath();
            canonicalFile = file.getCanonicalPath();
        } catch (IOException e) {
            return null;
        }
        if (!canonicalFile.startsWith(canonicalBase + File.separator)) {
            return null;
        }
        return file.exists() ? file : null;
    }

    public File exportsDir() {
        return ensureDir(DIR_EXPORTS);
    }

    public File tmpDir() {
        return ensureDir(DIR_TMP);
    }

    public File packagesDir() {
        return ensureDir(DIR_PACKAGES);
    }

    /** 删除某个相对引用指向的资产。 */
    public void deleteAsset(String relativeRef) {
        File file = resolve(relativeRef);
        if (file != null) {
            file.delete();
        }
    }

    /** 删除不再被引用的孤儿资产（CleanupWorker 使用，架构文档 §6.3）。 */
    public void deleteOrphanAssets(Set<String> referencedRefs) {
        for (String subDir : new String[]{DIR_AVATARS, DIR_BACKGROUNDS, DIR_COVERS}) {
            File dir = new File(baseDir, subDir);
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String ref = subDir + "/" + file.getName();
                if (!referencedRefs.contains(ref)) {
                    file.delete();
                }
            }
        }
    }

    /** 递归删除目录（导入失败清理、临时目录清理）。 */
    public void deleteRecursively(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        dir.delete();
    }

    private File ensureDir(String subDir) {
        File dir = new File(baseDir, subDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File uniqueFile(File dir, String name) {
        File file = new File(dir, name);
        if (!file.exists()) {
            return file;
        }
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        String ext = dot >= 0 ? name.substring(dot) : "";
        int i = 1;
        while (file.exists()) {
            file = new File(dir, base + "_" + i + ext);
            i++;
        }
        return file;
    }
}
