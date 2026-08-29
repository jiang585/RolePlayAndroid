package com.example.roleplaychat.ui.common;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

/**
 * 权限助手（架构文档 §10.4）：v1.0 不开启拍照入口，因此无需申请相机权限。
 * 相册/文件使用系统选择器，无需存储权限。
 */
public final class PermissionHelper {

    private PermissionHelper() {
    }

    public static boolean hasCameraPermission(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 是否应显示相机权限说明（v1.0 未启用，预留）。 */
    public static boolean shouldShowCameraRationale(Activity activity) {
        return activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
    }
}
