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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Contract tests for the pinned DeepSeek Messages and Harness search protocol. */
public final class DeepSeekProtocolContractTest {
    private MockWebServer server;
    private DeepSeekTransport transport;

    @Before public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        transport = new DeepSeekTransport();
        transport.setBaseUrl(server.url("/anthropic/v1").toString());
    }

    @After public void tearDown() throws Exception { server.shutdown(); }

    @Test public void messagesRequestUsesHarnessHeadersAndBody() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"content\":[{\"type\":\"text\",\"text\":\"OK\"}]}"));
        String runtimeCredential = runtimeCredential();
        JSONObject body = new JSONObject().put("model", DeepSeekTransport.TEXT_MODEL)
                .put("max_tokens", 32)
                .put("messages", new JSONArray().put(new JSONObject()
                        .put("role", "user").put("content", "只回复 OK")));

        JSONObject response = transport.postMessages(runtimeCredential, body);

        assertEquals("OK", response.getJSONArray("content").getJSONObject(0).getString("text"));
        RecordedRequest request = takeRequest();
        assertEquals("/anthropic/v1/messages", request.getPath());
        assertEquals(runtimeCredential, request.getHeader("x-api-key"));
        assertEquals("Bearer " + runtimeCredential, request.getHeader("Authorization"));
        assertEquals(DeepSeekTransport.ANTHROPIC_VERSION, request.getHeader("anthropic-version"));
        assertEquals("application/json", request.getHeader("Content-Type"));
        assertEquals("application/json", request.getHeader("Accept"));
        JSONObject sent = new JSONObject(request.getBody().readUtf8());
        assertEquals(DeepSeekTransport.TEXT_MODEL, sent.getString("model"));
        assertTrue(sent.has("messages"));
    }

    @Test public void harnessSearchRequestAndCitationMappingAreStructured() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"content\":["
                        + "{\"type\":\"text\",\"text\":\"result\",\"citations\":[{\"url\":\"https://example.test/label\",\"cited_text\":\"official text\"}]},"
                        + "{\"type\":\"web_search_tool_result\",\"content\":[{\"type\":\"web_search_result\",\"title\":\"Official label\",\"url\":\"https://example.test/label\",\"snippet\":\"dose\",\"page_age\":\"2026-01-01\"}]}"
                        + "]}"));
        JSONArray sources = new HarnessSearchClient(transport).search("official medicine label", runtimeCredential());

        assertEquals(1, sources.length());
        assertEquals("https://example.test/label", sources.getJSONObject(0).getString("url"));
        assertEquals("official text", sources.getJSONObject(0).getString("cited_text"));
        JSONObject sent = new JSONObject(takeRequest().getBody().readUtf8());
        assertEquals(DeepSeekTransport.TEXT_MODEL, sent.getString("model"));
        JSONObject tool = sent.getJSONArray("tools").getJSONObject(0);
        assertEquals("web_search_20250305", tool.getString("type"));
        assertEquals("web_search", tool.getString("name"));
        assertEquals(DeepSeekTransport.MAX_SEARCH_USES, tool.getInt("max_uses"));
    }

    @Test public void httpErrorsBecomeSafeStableCodes() throws Exception {
        assertHttpError(401, "AUTHENTICATION_FAILED");
        assertHttpError(402, "BALANCE_OR_ACCESS");
        assertHttpError(403, "PERMISSION_DENIED");
        assertHttpError(404, "MODEL_NOT_FOUND");
        assertHttpError(429, "RATE_LIMITED");
        assertHttpError(503, "SERVICE_UNAVAILABLE");
    }

    private void assertHttpError(int status, String expectedCode) throws Exception {
        server.enqueue(new MockResponse().setResponseCode(status).setBody("provider detail must not escape"));
        try {
            transport.postMessages(runtimeCredential(), new JSONObject().put("model", DeepSeekTransport.TEXT_MODEL));
            fail("Expected HTTP " + status);
        } catch (DeepSeekTransport.DeepSeekException error) {
            assertEquals(expectedCode, error.code);
            assertTrue(error.getMessage().indexOf("provider detail") < 0);
        }
        assertNotNull(takeRequest());
    }

    private RecordedRequest takeRequest() throws Exception {
        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        return request;
    }

    private static String runtimeCredential() {
        // Generated per test; it is only a mock-server header value, never a real API key.
        return "contract-" + Long.toUnsignedString(System.nanoTime());
    }
}
