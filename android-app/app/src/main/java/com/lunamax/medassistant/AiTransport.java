package com.lunamax.medassistant;

/** Common contract for the three supported chat protocols. */
interface AiTransport {
    AiResponse send(AiRequest request, String apiKey) throws AiException;
    String protocol();
}

final class AiException extends Exception {
    final String code;

    AiException(String code, String message) { super(message); this.code = code; }
    AiException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
}
