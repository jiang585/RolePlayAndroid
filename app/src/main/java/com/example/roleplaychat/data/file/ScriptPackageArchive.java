package com.example.roleplaychat.data.file;

import androidx.annotation.Nullable;

import com.example.roleplaychat.util.JsonUtils;
import com.example.roleplaychat.util.FileNameSanitizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 剧本包归档（架构文档 §9.5）：.rpczip = manifest.json + script/world/characters/messages/assets。
 * 解压必须规范化路径防 Zip Slip（§9.7），导入先验 manifest 再解压。
 */
public final class ScriptPackageArchive {

    public static final String FORMAT = "roleplay-script-package";
    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_ENTRIES = 256;
    private static final long MAX_ENTRY_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 100L * 1024L * 1024L;

    private ScriptPackageArchive() {
    }

    /** 打包剧本内容。entries 为相对路径 -> 内容字节/文件。 */
    @Nullable
    public static File createPackage(File targetDir, String scriptName,
                                     Map<String, String> textEntries,
                                     Map<String, File> assetEntries) {
        try {
            File zip = new File(targetDir, "script_"
                    + FileNameSanitizer.sanitize(scriptName, "untitled") + ".rpczip");
            Manifest manifest = new Manifest();
            manifest.format = FORMAT;
            manifest.schemaVersion = SCHEMA_VERSION;
            long totalBytes = 0;
            int entryCount = 0;

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
                for (Map.Entry<String, String> entry : textEntries.entrySet()) {
                    requireSafeEntry(entry.getKey());
                    byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
                    totalBytes = checkedPackageSize(totalBytes, bytes.length, ++entryCount);
                    putEntry(zos, entry.getKey(), bytes);
                    manifest.files.put(entry.getKey(), new FileInfo(bytes.length, sha256(bytes)));
                }
                for (Map.Entry<String, File> entry : assetEntries.entrySet()) {
                    requireSafeEntry(entry.getKey());
                    File file = entry.getValue();
                    if (file.length() > MAX_ENTRY_BYTES) {
                        throw new IOException("asset too large: " + entry.getKey());
                    }
                    byte[] bytes = readAll(file);
                    totalBytes = checkedPackageSize(totalBytes, bytes.length, ++entryCount);
                    putEntry(zos, entry.getKey(), bytes);
                    manifest.files.put(entry.getKey(), new FileInfo(bytes.length, sha256(bytes)));
                }
                byte[] manifestBytes = JsonUtils.toJson(manifest).getBytes(StandardCharsets.UTF_8);
                putEntry(zos, "manifest.json", manifestBytes);
            }
            return zip;
        } catch (IOException e) {
            return null;
        }
    }

    /** 解压到目标目录；拒绝绝对路径与 ..（Zip Slip 防护）。返回 null 表示安全。 */
    @Nullable
    public static String extractTo(File zipFile, File targetDir) {
        Manifest manifest = readManifest(zipFile);
        if (!isSupportedManifest(manifest)) {
            return "invalid manifest";
        }
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            Set<String> extracted = new HashSet<>();
            long totalBytes = 0;
            int entryCount = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!isSafeEntry(name)) {
                    return "unsafe path: " + name;
                }
                if (++entryCount > MAX_ENTRIES || !extracted.add(name)) {
                    return "invalid archive entries";
                }
                if ("manifest.json".equals(name)) {
                    continue;
                }
                FileInfo expected = manifest.files.get(name);
                if (expected == null || expected.size < 0 || expected.size > MAX_ENTRY_BYTES) {
                    return "undeclared or oversized entry: " + name;
                }
                File out = new File(targetDir, name);
                if (!out.getCanonicalPath().startsWith(targetDir.getCanonicalPath() + File.separator)) {
                    return "unsafe path: " + name;
                }
                File parent = out.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    int read;
                    long entryBytes = 0;
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    while ((read = zis.read(buffer)) != -1) {
                        entryBytes += read;
                        totalBytes += read;
                        if (entryBytes > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                            return "archive too large";
                        }
                        fos.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                    }
                    if (entryBytes != expected.size
                            || !toHex(digest.digest()).equalsIgnoreCase(expected.sha256)) {
                        return "entry checksum mismatch: " + name;
                    }
                }
            }
            Set<String> payloadEntries = new HashSet<>(extracted);
            payloadEntries.remove("manifest.json");
            if (!payloadEntries.equals(manifest.files.keySet())) {
                return "archive entries do not match manifest";
            }
            return null;
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            return "io error: " + e.getMessage();
        }
    }

    /** 读取包内 manifest。 */
    @Nullable
    public static Manifest readManifest(File zipFile) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("manifest.json")) {
                    byte[] bytes = readAll(zis, MAX_ENTRY_BYTES);
                    Manifest manifest = JsonUtils.fromJson(
                            new String(bytes, StandardCharsets.UTF_8), Manifest.class);
                    return isSupportedManifest(manifest) ? manifest : null;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /** 读取包内指定文本条目。 */
    @Nullable
    public static String readTextEntry(File zipFile, String path) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(path)) {
                    return new String(readAll(zis, MAX_ENTRY_BYTES), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static void putEntry(ZipOutputStream zos, String name, byte[] bytes) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(bytes);
        zos.closeEntry();
    }

    private static void requireSafeEntry(String name) throws IOException {
        if (!isSafeEntry(name) || "manifest.json".equals(name)) {
            throw new IOException("unsafe package entry: " + name);
        }
    }

    private static long checkedPackageSize(long current, long entrySize, int entryCount)
            throws IOException {
        long total = current + entrySize;
        if (entryCount > MAX_ENTRIES || entrySize > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
            throw new IOException("package size limit exceeded");
        }
        return total;
    }

    private static boolean isSupportedManifest(@Nullable Manifest manifest) {
        return manifest != null && FORMAT.equals(manifest.format)
                && manifest.schemaVersion == SCHEMA_VERSION && manifest.files != null
                && manifest.files.size() <= MAX_ENTRIES;
    }

    private static boolean isSafeEntry(String name) {
        if (name.startsWith("/") || name.contains("\\")) {
            return false;
        }
        for (String part : name.split("/")) {
            if (part.equals("..") || part.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static byte[] readAll(File file) throws IOException {
        return java.nio.file.Files.readAllBytes(file.toPath());
    }

    private static byte[] readAll(InputStream in, long limit) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if ((long) out.size() + read > limit) {
                throw new IOException("entry too large");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return toHex(hash);
        } catch (Exception e) {
            return "";
        }
    }

    private static String toHex(byte[] hash) {
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    /** manifest.json 模型。 */
    public static final class Manifest {
        @com.google.gson.annotations.SerializedName("format")
        public String format;
        @com.google.gson.annotations.SerializedName("schema_version")
        public int schemaVersion;
        @com.google.gson.annotations.SerializedName("exported_at")
        public String exportedAt;
        @com.google.gson.annotations.SerializedName("files")
        public Map<String, FileInfo> files = new LinkedHashMap<>();
    }

    public static final class FileInfo {
        @com.google.gson.annotations.SerializedName("size")
        public long size;
        @com.google.gson.annotations.SerializedName("sha256")
        public String sha256;

        public FileInfo() {
        }

        public FileInfo(long size, String sha256) {
            this.size = size;
            this.sha256 = sha256;
        }
    }
}
