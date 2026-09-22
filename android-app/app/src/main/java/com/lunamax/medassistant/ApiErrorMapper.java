package com.lunamax.medassistant;

/** Stable, secret-free provider error mapping shared by all adapters. */
final class ApiErrorMapper {
    private ApiErrorMapper() { }

    static AiException http(int code, String responseBody) {
        if (code == 400) return new AiException("REQUEST_INCOMPATIBLE", "请求字段不兼容：请检查协议与高级兼容设置");
        if (code == 401) return new AiException("AUTHENTICATION_FAILED", "认证失败：请检查当前 Provider 的凭据");
        if (code == 402) return new AiException("BALANCE_OR_ACCESS", "余额或 API 权限不足：请检查服务账户");
        if (code == 403) return new AiException("PERMISSION_DENIED", "权限不足：当前凭据无权访问该模型");
        if (code == 404) {
            String lower = responseBody == null ? "" : responseBody.toLowerCase(java.util.Locale.ROOT);
            return new AiException(lower.contains("model") ? "MODEL_NOT_FOUND" : "ENDPOINT_NOT_FOUND", "接口地址或模型不存在，请检查 Provider 配置");
        }
        if (code == 408) return new AiException("TIMEOUT", "服务请求超时：请稍后重试");
        if (code == 429) return new AiException("RATE_LIMITED", "请求过于频繁：请稍后重试");
        if (code >= 500) return new AiException("SERVICE_UNAVAILABLE", "Provider 服务暂时不可用");
        return new AiException("HTTP_" + code, "Provider 请求失败（HTTP " + code + "）");
    }
}
