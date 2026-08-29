package com.example.roleplaychat.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

/**
 * 文件选择助手（架构文档 §10.4）：优先系统 Photo Picker / SAF，
 * 无需长期存储权限。
 */
public final class FilePickerHelper {

    private FilePickerHelper() {
    }

    /** 注册图片选择器（mime 固定 image/*）。 */
    public static ActivityResultLauncher<String> registerImagePicker(Fragment fragment,
                                                                     java.util.function.Consumer<Uri> onResult) {
        return fragment.registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        onResult.accept(uri);
                    }
                });
    }

    /** 注册任意文件选择器（角色卡/世界观/聊天导入）。 */
    public static ActivityResultLauncher<String[]> registerFilePicker(Fragment fragment,
                                                                      java.util.function.Consumer<Uri> onResult) {
        return fragment.registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        onResult.accept(uri);
                    }
                });
    }

    /** 注册创建文件（导出）。 */
    public static ActivityResultLauncher<String> registerCreateDocument(Fragment fragment,
                                                                        java.util.function.Consumer<Uri> onResult) {
        return fragment.registerForActivityResult(
                new ActivityResultContracts.CreateDocument("*/*"),
                uri -> {
                    if (uri != null) {
                        onResult.accept(uri);
                    }
                });
    }

    /** 通过系统分享发送文件（FileProvider，架构文档 §9.7）。 */
    public static void shareFile(Activity activity, Uri fileUri, String mimeType) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mimeType);
        share.putExtra(Intent.EXTRA_STREAM, fileUri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(share, null));
    }
}
