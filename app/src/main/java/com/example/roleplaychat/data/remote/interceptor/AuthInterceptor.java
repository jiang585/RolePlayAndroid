package com.example.roleplaychat.data.remote.interceptor;

import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 认证拦截器：附加 Authorization: Bearer &lt;key&gt;。
 * 日志不得记录 Authorization 头（架构文档 §11.2）。
 */
public final class AuthInterceptor implements Interceptor {

    private volatile String apiKey;

    public AuthInterceptor(@Nullable String apiKey) {
        this.apiKey = apiKey;
    }

    public void setApiKey(@Nullable String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        if (apiKey == null || apiKey.isEmpty()) {
            return chain.proceed(original);
        }
        Request request = original.newBuilder()
                .header("Authorization", "Bearer " + apiKey)
                .build();
        return chain.proceed(request);
    }
}
