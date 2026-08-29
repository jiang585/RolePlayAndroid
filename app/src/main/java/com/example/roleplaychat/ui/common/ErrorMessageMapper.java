package com.example.roleplaychat.ui.common;

import android.content.Context;

import androidx.annotation.StringRes;

import com.example.roleplaychat.R;
import com.example.roleplaychat.domain.model.AppError;
import com.example.roleplaychat.domain.model.AppErrorCode;

/**
 * 错误码 -> 本地化文案映射（架构文档 §13.3/§8.8）。
 */
public final class ErrorMessageMapper {

    private ErrorMessageMapper() {
    }

    @StringRes
    public static int map(AppErrorCode code) {
        switch (code) {
            case NETWORK_UNAVAILABLE:
                return R.string.error_network_unavailable;
            case AUTH_INVALID:
                return R.string.error_auth_invalid;
            case RATE_LIMITED:
                return R.string.error_rate_limited;
            case MODEL_NOT_FOUND:
                return R.string.error_model_not_found;
            case OUTPUT_INVALID:
                return R.string.error_output_invalid;
            case UNKNOWN_CHARACTER:
                return R.string.error_unknown_character;
            case CANCELLED_BY_USER:
                return R.string.error_cancelled;
            case PROCESS_INTERRUPTED:
                return R.string.error_process_interrupted;
            case VALIDATION_FAILED:
            case IMPORT_INVALID:
            case UNKNOWN:
            default:
                return R.string.error_unknown;
        }
    }

    public static String resolve(Context context, AppError error) {
        if (error == null) {
            return context.getString(R.string.error_unknown);
        }
        if (error.getMessage() != null && !error.getMessage().isEmpty()) {
            return error.getMessage();
        }
        return context.getString(map(error.getCode()));
    }
}
