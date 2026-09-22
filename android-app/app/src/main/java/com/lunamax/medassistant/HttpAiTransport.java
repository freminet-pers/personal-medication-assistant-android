package com.lunamax.medassistant;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLException;

/** Shared HTTP safety boundary. Adapters only own protocol JSON mapping. */
abstract class HttpAiTransport implements AiTransport {
    static final int MAX_RESPONSE_BYTES = 2_000_000;
    final ProviderProfile profile;
    private final String endpoint;

    HttpAiTransport(ProviderProfile profile) {
        this.profile = profile;
        try { endpoint = EndpointResolver.endpoint(profile); }
        catch (IllegalArgumentException error) { throw error; }
    }

    @Override public final AiResponse send(AiRequest request, String apiKey) throws AiException {
        if (request == null) throw new AiException("LOCAL_CONFIG_ERROR", "请求配置为空");
        if (!request.images.isEmpty() && (!profile.supportsImage || !profile.imageInputEnabled)) throw new AiException("IMAGE_NOT_SUPPORTED", "当前模型未通过图片能力测试");
        if (!ProviderProfile.AUTH_NONE.equals(profile.authMode) && (apiKey == null || apiKey.trim().isEmpty())) throw new AiException("MISSING_CREDENTIAL", "未配置当前 Provider 的凭据");
        JSONObject body;
        try { body = buildBody(request); }
        catch (Exception error) { throw new AiException("LOCAL_CONFIG_ERROR", "请求字段无法生成", error); }
        JSONObject payload = postJson(endpoint, body, apiKey, profile.authMode, ProviderProfile.ANTHROPIC_MESSAGES.equals(protocol()));
        try { return parseResponse(payload); }
        catch (Exception error) { throw new AiException("INVALID_RESPONSE", "Provider 返回了无法识别的响应", error); }
    }

    protected abstract JSONObject buildBody(AiRequest request) throws Exception;
    protected abstract AiResponse parseResponse(JSONObject payload) throws Exception;

    protected final JSONObject postJson(String target, JSONObject body, String apiKey, String authMode, boolean anthropic) throws AiException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(target).openConnection();
            connection.setInstanceFollowRedirects(false); connection.setRequestMethod("POST");
            connection.setConnectTimeout(profile.connectTimeoutMs); connection.setReadTimeout(profile.readTimeoutMs); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json"); connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "personal-medication-assistant/0.4.0");
            applyAuth(connection, apiKey, authMode);
            if (anthropic) connection.setRequestProperty("anthropic-version", profile.anthropicVersion == null || profile.anthropicVersion.isEmpty() ? "2023-06-01" : profile.anthropicVersion);
            try (OutputStream output = connection.getOutputStream()) { output.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = connection.getResponseCode(); InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String response = read(stream, MAX_RESPONSE_BYTES);
            if (code < 200 || code >= 300) throw ApiErrorMapper.http(code, response);
            try { return new JSONObject(response); } catch (Exception invalidJson) { throw new AiException("INVALID_JSON", "Provider 返回了无效 JSON", invalidJson); }
        } catch (AiException error) { throw error; }
        catch (SocketTimeoutException error) { throw new AiException("TIMEOUT", "连接或读取超时：请检查网络后重试", error); }
        catch (UnknownHostException error) { throw new AiException("DNS", "DNS 失败：无法解析 Provider 地址", error); }
        catch (SSLException error) { throw new AiException("TLS", "TLS 安全连接失败：请检查系统时间或证书", error); }
        catch (IllegalStateException error) { throw new AiException("RESPONSE_TOO_LARGE", "Provider 响应超过允许大小", error); }
        catch (Exception error) { throw new AiException("NETWORK", "网络或 Provider 连接失败：请稍后重试", error); }
        finally { if (connection != null) connection.disconnect(); }
    }

    static void applyAuth(HttpURLConnection connection, String apiKey, String authMode) {
        if (apiKey == null) return;
        if (ProviderProfile.AUTH_BEARER.equals(authMode) || ProviderProfile.AUTH_BOTH.equals(authMode)) connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        if (ProviderProfile.AUTH_X_API_KEY.equals(authMode) || ProviderProfile.AUTH_BOTH.equals(authMode)) connection.setRequestProperty("x-api-key", apiKey);
    }

    static String read(InputStream input, int maxBytes) throws Exception {
        if (input == null) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int total = 0; int count;
        try (InputStream stream = new BufferedInputStream(input)) {
            while ((count = stream.read(buffer)) >= 0) { total += count; if (total > maxBytes) throw new IllegalStateException("RESPONSE_TOO_LARGE"); output.write(buffer, 0, count); }
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
