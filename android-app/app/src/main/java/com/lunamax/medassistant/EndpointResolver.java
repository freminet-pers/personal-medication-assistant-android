package com.lunamax.medassistant;

import java.net.URL;

/** Validates and joins user-entered base URLs without duplicate endpoints. */
final class EndpointResolver {
    private EndpointResolver() { }

    static String normalizeBaseUrl(String raw) {
        if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("base URL 不能为空");
        try {
            URL url = new URL(raw.trim());
            String protocol = url.getProtocol().toLowerCase(java.util.Locale.ROOT);
            if (!("https".equals(protocol) || "http".equals(protocol))) throw new IllegalArgumentException("只支持 HTTPS；HTTP 仅允许本机回环地址");
            if (url.getHost() == null || url.getHost().isEmpty()) throw new IllegalArgumentException("base URL 缺少主机名");
            if (url.getUserInfo() != null || url.getQuery() != null || url.getRef() != null) throw new IllegalArgumentException("base URL 不得包含用户名、密码、查询参数或 fragment");
            if ("http".equals(protocol) && !isLoopback(url.getHost())) throw new IllegalArgumentException("HTTP 明文仅允许 localhost、127.0.0.1 或 10.0.2.2");
            String path = url.getPath() == null ? "" : url.getPath().replaceAll("/+$", "");
            for (String suffix : new String[]{"/chat/completions", "/responses", "/messages"}) if (path.endsWith(suffix)) { path = path.substring(0, path.length() - suffix.length()); break; }
            String authority = url.getProtocol() + "://" + url.getAuthority();
            return authority + (path.isEmpty() ? "" : path);
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException("base URL 格式无效"); }
    }

    static String endpoint(ProviderProfile profile) {
        if (profile == null) throw new IllegalArgumentException("Provider 配置为空");
        String base = normalizeBaseUrl(profile.baseUrl);
        String protocol = profile.protocol;
        if (ProviderProfile.OPENAI_CHAT_COMPLETIONS.equals(protocol)) return base + "/chat/completions";
        if (ProviderProfile.OPENAI_RESPONSES.equals(protocol)) return base + "/responses";
        if (ProviderProfile.ANTHROPIC_MESSAGES.equals(protocol)) {
            if (base.endsWith("/anthropic")) return base + "/v1/messages";
            return base + "/messages";
        }
        throw new IllegalArgumentException("不支持的 Provider 协议");
    }

    static String searchEndpoint(String baseUrl, String type) {
        String base = normalizeBaseUrl(baseUrl);
        if (SearchProfile.OPENAI_RESPONSES_NATIVE.equals(type)) return base + "/responses";
        if (SearchProfile.DEEPSEEK_NATIVE.equals(type)) {
            if (base.endsWith("/anthropic/v1")) return base + "/messages";
            if (base.endsWith("/anthropic")) return base + "/v1/messages";
            return base + "/anthropic/v1/messages";
        }
        if (SearchProfile.ANTHROPIC_NATIVE.equals(type)) {
            return base.endsWith("/anthropic") ? base + "/v1/messages" : base + "/messages";
        }
        throw new IllegalArgumentException("不支持的搜索协议");
    }

    static boolean isLoopback(String host) {
        String value = host == null ? "" : host.toLowerCase(java.util.Locale.ROOT);
        return "localhost".equals(value) || "127.0.0.1".equals(value) || "10.0.2.2".equals(value)
                || "::1".equals(value) || "0:0:0:0:0:0:0:1".equals(value);
    }
}
