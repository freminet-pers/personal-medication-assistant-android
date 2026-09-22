package com.lunamax.medassistant;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLException;

/**
 * Credential-free transport boundary for DeepSeek. It owns endpoint, headers,
 * timeout and error mapping so repositories never duplicate or log secrets.
 */
final class DeepSeekTransport {
    static final String TEXT_MODEL = "deepseek-v4-flash";
    static final String VISION_MODEL = "deepseek-v4-flash-vision-exp";
    static final String DEFAULT_MESSAGES_BASE = "https://api.deepseek.com/anthropic/v1";
    static final String DEFAULT_CHAT_BASE = "https://api.deepseek.com";
    static final String ANTHROPIC_VERSION = "2023-06-01";
    static final int MAX_SEARCH_USES = 5;

    private String messagesBase = DEFAULT_MESSAGES_BASE;
    private String chatBase = DEFAULT_CHAT_BASE;

    void setBaseUrl(String value) {
        if (value == null || value.trim().isEmpty()) return;
        try {
            URL url = new URL(value.trim());
            boolean officialMessages = "https".equalsIgnoreCase(url.getProtocol())
                    && "api.deepseek.com".equalsIgnoreCase(url.getHost());
            boolean loopback = "http".equalsIgnoreCase(url.getProtocol())
                    && ("127.0.0.1".equals(url.getHost()) || "localhost".equalsIgnoreCase(url.getHost()));
            if (!officialMessages && !loopback) return;
            String root = value.replaceAll("/$", "");
            if (root.endsWith("/anthropic/v1")) {
                messagesBase = root;
                chatBase = root.substring(0, root.length() - "/anthropic/v1".length());
            } else if (root.endsWith("/anthropic")) {
                messagesBase = root + "/v1";
                chatBase = root.substring(0, root.length() - "/anthropic".length());
            } else {
                chatBase = root;
                messagesBase = root + "/anthropic/v1";
            }
        } catch (Exception ignored) {
            // The ordinary product UI never exposes this method. Invalid test
            // or development overrides simply keep the official endpoints.
        }
    }

    JSONObject postMessages(String apiKey, JSONObject body) throws DeepSeekException {
        return post(messagesBase + "/messages", apiKey, body, true);
    }

    JSONObject postChatCompletions(String apiKey, JSONObject body) throws DeepSeekException {
        return post(chatBase + "/chat/completions", apiKey, body, false);
    }

    private JSONObject post(String endpoint, String apiKey, JSONObject body, boolean anthropic) throws DeepSeekException {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new DeepSeekException("MISSING_CREDENTIAL", "未配置 DeepSeek API Key");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "personal-medication-assistant/0.3.0");
            if (anthropic) {
                // Official Harness sends both headers so the official endpoint
                // and compatible gateways resolve the same runtime credential.
                connection.setRequestProperty("x-api-key", apiKey);
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                connection.setRequestProperty("anthropic-version", ANTHROPIC_VERSION);
            } else {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String response = read(stream, 2_000_000);
            if (code < 200 || code >= 300) throw apiError(code);
            try {
                return new JSONObject(response);
            } catch (Exception invalidJson) {
                throw new DeepSeekException("INVALID_RESPONSE", "DeepSeek 返回了无法识别的响应", invalidJson);
            }
        } catch (DeepSeekException error) {
            throw error;
        } catch (SocketTimeoutException error) {
            throw new DeepSeekException("TIMEOUT", "连接超时：请检查网络后重试", error);
        } catch (UnknownHostException error) {
            throw new DeepSeekException("DNS", "DNS 失败：当前无法解析 DeepSeek 地址", error);
        } catch (SSLException error) {
            throw new DeepSeekException("TLS", "TLS 安全连接失败：请检查系统时间或网络证书", error);
        } catch (Exception error) {
            throw new DeepSeekException("NETWORK", "网络或服务连接失败：请稍后重试", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static DeepSeekException apiError(int code) {
        if (code == 401) return new DeepSeekException("AUTHENTICATION_FAILED", "认证失败：请检查 DeepSeek API Key");
        if (code == 402) return new DeepSeekException("BALANCE_OR_ACCESS", "余额或 API 权限不足：请检查账户状态");
        if (code == 403) return new DeepSeekException("PERMISSION_DENIED", "权限不足：当前 Key 无权访问该模型");
        if (code == 404) return new DeepSeekException("MODEL_NOT_FOUND", "模型不存在或当前接口未开放");
        if (code == 429) return new DeepSeekException("RATE_LIMITED", "请求过于频繁：请稍后重试");
        if (code >= 500) return new DeepSeekException("SERVICE_UNAVAILABLE", "DeepSeek 服务暂时不可用");
        return new DeepSeekException("HTTP_" + code, "服务端请求错误（HTTP " + code + "）");
    }

    private static String read(InputStream input, int maxBytes) throws Exception {
        if (input == null) return "";
        StringBuilder result = new StringBuilder();
        int total = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                total += line.getBytes(StandardCharsets.UTF_8).length;
                if (total > maxBytes) throw new IllegalStateException("响应超过允许大小");
                result.append(line);
            }
        }
        return result.toString();
    }

    static final class DeepSeekException extends Exception {
        final String code;

        DeepSeekException(String code, String message) { super(message); this.code = code; }
        DeepSeekException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    }
}
