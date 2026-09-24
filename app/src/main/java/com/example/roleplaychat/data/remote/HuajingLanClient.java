package com.example.roleplaychat.data.remote;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.roleplaychat.data.security.SecretStore;
import com.example.roleplaychat.domain.model.ImageGenerationRequest;
import com.example.roleplaychat.domain.model.ImageGenerationStatus;
import com.example.roleplaychat.domain.model.HuajingPairingResult;
import com.example.roleplaychat.domain.repository.ImageGenerationGateway;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

/** Authenticated LAN client for Huajing's high-level generation API. */
public final class HuajingLanClient implements ImageGenerationGateway {
    private static final int DISCOVERY_PORT = 17891;
    private static final String KEY_BASE_URL = "huajing.base_url";
    private static final String KEY_DEVICE_ID = "huajing.device_id";
    private static final String KEY_TOKEN = "huajing.access_token";

    private final SecretStore secretStore;
    private final Context context;
    private final OkHttpClient client;
    private final Gson gson = new Gson();
    private volatile String baseUrl;
    private volatile String deviceId;
    private volatile String accessToken;
    private volatile long lastDiscoveryAt;

    public HuajingLanClient(Context context, SecretStore secretStore) {
        this.context = context.getApplicationContext();
        this.secretStore = secretStore;
        this.baseUrl = secretStore.getSecret(KEY_BASE_URL);
        this.deviceId = secretStore.getSecret(KEY_DEVICE_ID);
        this.accessToken = secretStore.getSecret(KEY_TOKEN);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override public boolean isConfigured() {
        return !TextUtils.isEmpty(baseUrl) && !TextUtils.isEmpty(deviceId) && !TextUtils.isEmpty(accessToken);
    }

    @Override public String getBaseUrl() { return baseUrl; }

    /** 在应用启动后主动刷新一次地址，让用户进入聊天时已经完成无感换 IP。 */
    public void refreshAddress() {
        if (isConfigured()) discoverAndUpdate();
    }

    @Override public synchronized void updateBaseUrl(String url) {
        if (TextUtils.isEmpty(url) || !isConfigured()) return;
        baseUrl = normalize(url);
        secretStore.putSecret(KEY_BASE_URL, baseUrl);
    }

    @Override public HuajingPairingResult claimPairing(String url, String pairingCode,
                                                        String deviceName, String appInstanceId) throws IOException {
        Map<String, String> payload = new HashMap<>();
        payload.put("pairingCode", pairingCode); payload.put("deviceName", deviceName);
        payload.put("appInstanceId", appInstanceId);
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), gson.toJson(payload).getBytes(StandardCharsets.UTF_8));
        Request request = new Request.Builder().url(normalize(url) + "v1/pairing/claim").post(body).build();
        try (Response response = client.newCall(request).execute()) {
            String raw = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) throw new IOException("Huajing pairing failed: " + response.code());
            JsonObject json = gson.fromJson(raw, JsonObject.class);
            if (json == null || !json.has("deviceId") || !json.has("accessToken")) throw new IOException("配对响应无效");
            HuajingPairingResult result = new HuajingPairingResult(json.get("deviceId").getAsString(), json.get("accessToken").getAsString());
            configure(url, result.getDeviceId(), result.getAccessToken());
            return result;
        }
    }

    @Override public synchronized void configure(String url, String id, String token) {
        baseUrl = normalize(url); deviceId = id; accessToken = token;
        secretStore.putSecret(KEY_BASE_URL, baseUrl); secretStore.putSecret(KEY_DEVICE_ID, id); secretStore.putSecret(KEY_TOKEN, token);
    }

    @Override public synchronized void clearConfiguration() {
        baseUrl = null; deviceId = null; accessToken = null;
        secretStore.removeSecret(KEY_BASE_URL); secretStore.removeSecret(KEY_DEVICE_ID); secretStore.removeSecret(KEY_TOKEN);
    }

    @Override public ImageGenerationStatus health() throws IOException {
        Response response = execute(new Request.Builder().url(url("v1/health")).get().build());
        return parseStatus(response.body().string(), "health");
    }

    @Override public String uploadAsset(File file, String sha256) throws IOException {
        RequestBody fileBody = RequestBody.create(MediaType.parse("application/octet-stream"), file);
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody)
                .build();
        Request request = new Request.Builder().url(url("v1/assets/" + sha256)).put(body).build();
        Response response = execute(request);
        JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
        if (json == null || !json.has("assetId")) throw new IOException("Huajing asset response missing assetId");
        return json.get("assetId").getAsString();
    }

    @Override public ImageGenerationStatus create(ImageGenerationRequest request, String[] assetIds) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("clientJobId", request.getClientJobId()); payload.put("model", modelName(request.getModel()));
        payload.put("mode", modeName(request.getMode())); payload.put("prompt", request.getPrompt());
        payload.put("negativePrompt", request.getNegativePrompt()); payload.put("size", new Size(request.getWidth(), request.getHeight()));
        payload.put("seed", request.getSeed());
        payload.put("metadata", new Metadata(request.getScriptId(), request.getCharacterId(), request.getTrigger()));
        java.util.List<Reference> refs = new java.util.ArrayList<>();
        if (assetIds != null) for (int i = 0; i < assetIds.length; i++) refs.add(new Reference(assetIds[i], "identity", i + 1));
        payload.put("references", refs);
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), gson.toJson(payload).getBytes(StandardCharsets.UTF_8));
        Response response = execute(new Request.Builder().url(url("v1/generation/jobs")).post(body).build());
        return parseStatus(response.body().string(), "created");
    }

    @Override public ImageGenerationStatus status(String jobId) throws IOException {
        Response response = execute(new Request.Builder().url(url("v1/generation/jobs/" + jobId)).get().build());
        return parseStatus(response.body().string(), jobId);
    }

    @Override public File download(String assetId, File destination) throws IOException {
        Response response = execute(new Request.Builder().url(url("v1/assets/" + assetId + "/content")).get().build());
        if (destination.getParentFile() != null) destination.getParentFile().mkdirs();
        try (InputStream in = response.body().byteStream(); FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
        return destination;
    }

    private Response execute(Request request) throws IOException {
        if (!isConfigured()) throw new IOException("Huajing is not paired");
        Request authenticated = authenticated(request);
        Response response;
        try {
            response = client.newCall(authenticated).execute();
        } catch (IOException first) {
            // 电脑重启、DHCP 换地址或 Wi-Fi 重连后，原地址可能已经失效。
            // 用已保存的 deviceId + token 在局域网发现新地址，再只重试一次。
            if (!discoverAndUpdate()) throw first;
            response = client.newCall(authenticated(request.newBuilder()
                    .url(urlForPath(request.url().encodedPath()))
                    .build())).execute();
        }
        if (response.code() == 401 && discoverAndUpdate()) {
            response.close();
            response = client.newCall(authenticated(request.newBuilder()
                    .url(urlForPath(request.url().encodedPath())).build())).execute();
        }
        if (!response.isSuccessful()) {
            String message = response.body() == null ? "HTTP " + response.code() : response.body().string();
            response.close(); throw new IOException("Huajing request failed: " + response.code() + " " + message);
        }
        return response;
    }

    private Request authenticated(Request request) {
        return request.newBuilder().header("Authorization", "Bearer " + accessToken)
                .header("X-Huajing-Device", deviceId).build();
    }

    /**
     * 监听 Huajing 的无凭据 UDP 服务公告。公告只包含服务名和端口，
     * 真正的身份验证仍由 /v1/connection 使用已保存令牌完成。
     */
    private synchronized boolean discoverAndUpdate() {
        if (!isConfigured()) return false;
        long now = System.currentTimeMillis();
        if (now - lastDiscoveryAt < 2500L) return false;
        lastDiscoveryAt = now;
        String old = baseUrl;
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = wifi == null ? null : wifi.createMulticastLock("huajing-discovery");
        if (lock != null) { lock.setReferenceCounted(false); lock.acquire(); }
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(DISCOVERY_PORT));
            socket.setBroadcast(true);
            socket.setSoTimeout(350);
            byte[] buffer = new byte[512];
            long deadline = now + 4500L;
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException timeout) {
                    continue;
                }
                String payload = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                if (!payload.contains("\"service\":\"huajing\"") || !payload.contains("\"protocol\":1")) continue;
                int port = 17890;
                int marker = payload.indexOf("\"port\":");
                if (marker >= 0) {
                    try { port = Integer.parseInt(payload.substring(marker + 7).replaceAll("[^0-9].*", "")); }
                    catch (RuntimeException ignored) { }
                }
                String candidate = "http://" + packet.getAddress().getHostAddress() + ":" + port + "/";
                if (probeCandidate(candidate)) {
                    baseUrl = candidate;
                    secretStore.putSecret(KEY_BASE_URL, candidate);
                    return true;
                }
            }
        } catch (IOException ignored) {
            // 保留原地址，调用方继续返回原始网络错误。
        } finally {
            if (lock != null && lock.isHeld()) lock.release();
        }
        baseUrl = old;
        return false;
    }

    private boolean probeCandidate(String candidate) {
        Request request = new Request.Builder().url(candidate + "v1/connection")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Huajing-Device", deviceId).get().build();
        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException ignored) {
            return false;
        }
    }

    private String urlForPath(String encodedPath) {
        String path = encodedPath == null ? "" : encodedPath;
        while (path.startsWith("/")) path = path.substring(1);
        return baseUrl + path;
    }

    private ImageGenerationStatus parseStatus(String raw, String fallbackId) {
        JsonObject json = gson.fromJson(raw, JsonObject.class);
        String id = json != null && json.has("jobId") ? json.get("jobId").getAsString() : fallbackId;
        String state = json != null && json.has("status") && !json.get("status").isJsonNull()
                ? json.get("status").getAsString() : "FAILED_FINAL";
        ImageGenerationStatus.State parsed;
        try { parsed = ImageGenerationStatus.State.valueOf(state.toUpperCase(java.util.Locale.ROOT)); }
        catch (Exception ignored) { parsed = ImageGenerationStatus.State.FAILED_FINAL; }
        float progress = json != null && json.has("progress") ? json.get("progress").getAsFloat() : 0f;
        String stage = json != null && json.has("stage") ? json.get("stage").getAsString() : "";
        String asset = nullableString(json, "resultAssetId");
        String error = nullableString(json, "errorCode");
        return new ImageGenerationStatus(id, parsed, stage, progress, asset, error);
    }

    /** Gson 的 JsonNull 也满足 has()，必须先判断 null，避免轮询状态时崩溃。 */
    @Nullable
    private static String nullableString(@Nullable JsonObject json, String name) {
        if (json == null || !json.has(name) || json.get(name).isJsonNull()) return null;
        try { return json.get(name).getAsString(); }
        catch (RuntimeException ignored) { return null; }
    }

    private String url(String path) { return baseUrl + path; }
    private static String normalize(String url) { if (url == null) return null; return url.endsWith("/") ? url : url + "/"; }
    private static String modelName(ImageGenerationRequest.Model model) { return model == ImageGenerationRequest.Model.ZIMAGE ? "zimage" : "qwenimage2.1"; }
    private static String modeName(ImageGenerationRequest.Mode mode) { return mode == ImageGenerationRequest.Mode.TXT2IMG ? "txt2img" : mode == ImageGenerationRequest.Mode.MULTIREF ? "multiref" : "edit"; }
    private static final class Size { final int width, height; Size(int w, int h) { width = w; height = h; } }
    private static final class Metadata { final String scriptId, characterId, trigger; Metadata(String s, @Nullable String c, String t) { scriptId=s; characterId=c; trigger=t; } }
    private static final class Reference { final String assetId, role; final int order; Reference(String a, String r, int o) { assetId=a; role=r; order=o; } }
}
