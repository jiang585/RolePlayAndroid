package com.example.roleplaychat.data.file;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ScriptPackageArchiveTest {

    @Test
    public void createPackage_sanitizesScriptNameAndRoundTrips() throws Exception {
        File root = Files.createTempDirectory("rpc-package").toFile();
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("script.json", "{\"name\":\"demo\"}");

        File archive = ScriptPackageArchive.createPackage(root, "../unsafe", entries,
                Collections.emptyMap());

        assertNotNull(archive);
        assertTrue(archive.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator));
        assertFalse(archive.getName().contains(".."));
        File extracted = new File(root, "extracted");
        assertTrue(extracted.mkdirs());
        org.junit.Assert.assertNull(ScriptPackageArchive.extractTo(archive, extracted));
        assertTrue(new File(extracted, "script.json").isFile());
    }

    @Test
    public void extractTo_rejectsChecksumMismatch() throws Exception {
        File root = Files.createTempDirectory("rpc-package-tamper").toFile();
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("script.json", "original");
        File archive = ScriptPackageArchive.createPackage(root, "demo", entries,
                Collections.emptyMap());
        File tampered = new File(root, "tampered.rpczip");

        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(archive));
             java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(
                     new java.io.FileOutputStream(tampered))) {
            java.util.zip.ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                out.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                byte[] bytes = in.readAllBytes();
                out.write("script.json".equals(entry.getName())
                        ? "tampered".getBytes(StandardCharsets.UTF_8) : bytes);
                out.closeEntry();
            }
        }

        File extracted = new File(root, "tampered-output");
        assertTrue(extracted.mkdirs());
        String error = ScriptPackageArchive.extractTo(tampered, extracted);
        assertNotNull(error);
        assertTrue(error.contains("checksum"));
    }
}
