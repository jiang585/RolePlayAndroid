package com.example.roleplaychat.data.file;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 图片导入（架构文档 §2.3/§9.7）：解码前采样，最长边限制 4096 px，
 * 统一转为 WebP 存私有目录。损坏图片返回 null 不崩溃。
 */
public final class ImageImporter {

    /** 最长边上限（架构文档 §2.3：图片解码像素 4096 px）。 */
    public static final int MAX_EDGE_PX = 4096;

    private final Context context;
    private final LocalAssetStore assetStore;

    public ImageImporter(Context context, LocalAssetStore assetStore) {
        this.context = context.getApplicationContext();
        this.assetStore = assetStore;
    }

    /** 导入 URI 图片到指定子目录，返回相对引用；失败返回 null。 */
    @Nullable
    public String importImage(String subDir, Uri uri) {
        Bitmap bitmap = decodeSampled(uri);
        if (bitmap == null) {
            return null;
        }
        try {
            return writeWebp(subDir, bitmap);
        } finally {
            bitmap.recycle();
        }
    }

    @Nullable
    private Bitmap decodeSampled(Uri uri) {
        // 先读取边界
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (IOException e) {
            return null;
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_EDGE_PX) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            return BitmapFactory.decodeStream(in, null, opts);
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    private String writeWebp(String subDir, Bitmap bitmap) {
        File dir = new File(assetStore.tmpDir(), "img");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File tmp = new File(dir, "img_" + System.nanoTime() + ".webp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? Bitmap.CompressFormat.WEBP_LOSSY
                    : Bitmap.CompressFormat.WEBP;
            if (!bitmap.compress(format, 88, out)) {
                throw new IOException("WebP encode failed");
            }
        } catch (IOException e) {
            tmp.delete();
            return null;
        }
        try (InputStream in = new java.io.FileInputStream(tmp)) {
            String ref = assetStore.storeStream(subDir, tmp.getName(), ".webp", in);
            tmp.delete();
            return ref;
        } catch (IOException e) {
            tmp.delete();
            return null;
        }
    }
}
