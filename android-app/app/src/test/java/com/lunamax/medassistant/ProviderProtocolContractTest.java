package com.lunamax.medassistant;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** MockWebServer contracts for each supported provider protocol. */
public final class ProviderProtocolContractTest {
    private MockWebServer server;

    @Before public void setUp() throws Exception { server = new MockWebServer(); server.start(); }
    @After public void tearDown() throws Exception { server.shutdown(); }

    @Test public void openAiChatMapsTextImageAndBearer() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"model\":\"mock-chat\",\"choices\":[{\"message\":{\"content\":\"OK\"}}]}"));
        String key = credential();
        ProviderProfile profile = ProviderProfile.builder().displayName("Chat").protocol(ProviderProfile.OPENAI_CHAT_COMPLETIONS)
                .baseUrl(server.url("/v1").toString()).modelId("mock-chat").supportsImage(true).imageInputEnabled(true)
                .authMode(ProviderProfile.AUTH_BEARER).build();
        AiRequest request = AiRequest.builder().modelId(profile.modelId).systemPrompt("system").userText("read image")
                .image("image/png", "aW1hZ2U=").maxTokens(64).build();
        AiResponse response = new OpenAiChatCompletionsTransport(profile).send(request, key);
        assertEquals("OK", response.text);
        RecordedRequest sent = takeRequest(); assertEquals("/v1/chat/completions", sent.getPath());
        assertEquals("Bearer " + key, sent.getHeader("Authorization"));
        JSONObject body = new JSONObject(sent.getBody().readUtf8()); assertEquals("mock-chat", body.getString("model"));
        assertEquals("image_url", body.getJSONArray("messages").getJSONObject(1).getJSONArray("content").getJSONObject(1).getString("type"));
    }

    @Test public void openAiResponsesReadsAllOutputItemsAndUsesResponsesEndpoint() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"model\":\"mock-responses\",\"output\":[{\"type\":\"reasoning\",\"content\":[{\"type\":\"reasoning_text\",\"text\":\"hidden\"}]},{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"answer\"}]}]}"));
        String key = credential();
        ProviderProfile profile = ProviderProfile.builder().displayName("Responses").protocol(ProviderProfile.OPENAI_RESPONSES)
                .baseUrl(server.url("/v1/responses").toString()).modelId("mock-responses").authMode(ProviderProfile.AUTH_X_API_KEY).build();
        AiResponse response = new OpenAiResponsesTransport(profile).send(AiRequest.builder().modelId(profile.modelId).userText("hello").build(), key);
        assertEquals("answer", response.text);
        RecordedRequest sent = takeRequest(); assertEquals("/v1/responses", sent.getPath());
        assertEquals(key, sent.getHeader("x-api-key")); assertTrue(new JSONObject(sent.getBody().readUtf8()).has("input"));
    }

    @Test public void anthropicMapsSystemImageAndHeaders() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"model\":\"mock-anthropic\",\"content\":[{\"type\":\"text\",\"text\":\"OK\"}]}"));
        String key = credential();
        ProviderProfile profile = ProviderProfile.builder().displayName("Anthropic").protocol(ProviderProfile.ANTHROPIC_MESSAGES)
                .baseUrl(server.url("/anthropic/v1").toString()).modelId("mock-anthropic").supportsImage(true).imageInputEnabled(true)
                .authMode(ProviderProfile.AUTH_X_API_KEY).anthropicVersion("2023-06-01").build();
        AiResponse response = new AnthropicMessagesTransport(profile).send(AiRequest.builder().modelId(profile.modelId).systemPrompt("safe").userText("read")
                .image("image/jpeg", "aW1hZ2U=").build(), key);
        assertEquals("OK", response.text); RecordedRequest sent = takeRequest(); assertEquals("/anthropic/v1/messages", sent.getPath());
        assertEquals(key, sent.getHeader("x-api-key")); assertEquals("2023-06-01", sent.getHeader("anthropic-version"));
        JSONObject body = new JSONObject(sent.getBody().readUtf8()); assertEquals("safe", body.getString("system"));
        assertEquals("image", body.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(1).getString("type"));
    }

    @Test public void endpointResolverStripsFullEndpointAndRejectsPublicHttp() throws Exception {
        assertEquals("https://example.test/v1", EndpointResolver.normalizeBaseUrl("https://example.test/v1/chat/completions///"));
        assertEquals("https://example.test/v1", EndpointResolver.normalizeBaseUrl("https://example.test/v1/responses"));
        try { EndpointResolver.normalizeBaseUrl("http://example.test/v1"); fail("public HTTP must fail"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("HTTP")); }
    }

    @Test public void adapterHidesProviderErrorBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("secret provider detail"));
        ProviderProfile profile = ProviderProfile.builder().protocol(ProviderProfile.OPENAI_RESPONSES).baseUrl(server.url("/").toString()).modelId("mock").build();
        try { new OpenAiResponsesTransport(profile).send(AiRequest.builder().modelId("mock").userText("x").build(), credential()); fail("Expected error"); }
        catch (AiException error) { assertEquals("AUTHENTICATION_FAILED", error.code); assertTrue(error.getMessage().indexOf("secret provider detail") < 0); }
        takeRequest();
    }

    private RecordedRequest takeRequest() throws Exception { RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS); if (request == null) fail("request missing"); return request; }
    private static String credential() { return "contract-" + Long.toUnsignedString(System.nanoTime()); }
}
