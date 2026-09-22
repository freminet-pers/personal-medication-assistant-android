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

/** Search protocol contracts assert both the real tool request and URL mapping. */
public final class SearchProtocolContractTest {
    private MockWebServer server;

    @Before public void setUp() throws Exception { server = new MockWebServer(); server.start(); }
    @After public void tearDown() throws Exception { server.shutdown(); }

    @Test public void deepSeekNativeSearchUsesAnthropicToolAndStructuredSources() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"content\":[{\"type\":\"web_search_tool_result\",\"content\":[{\"type\":\"web_search_result\",\"title\":\"Label\",\"url\":\"https://example.test/label\",\"snippet\":\"dose\"}]}]}"));
        ProviderProfile profile = ProviderProfile.builder().protocol(ProviderProfile.ANTHROPIC_MESSAGES).baseUrl(server.url("/anthropic/v1").toString()).modelId("deepseek-flash").supportsImage(false).authMode(ProviderProfile.AUTH_BOTH).build();
        JSONArray tools = new JSONArray().put(new JSONObject().put("type", "web_search_20250305").put("name", "web_search").put("max_uses", 5));
        AiResponse response = new AnthropicMessagesTransport(profile).send(AiRequest.builder().modelId(profile.modelId).systemPrompt("search").userText("label").tools(tools).maxTokens(4096).build(), "contract-search");
        JSONArray sources = SearchRepository.extractSources(response, SearchProfile.DEEPSEEK_NATIVE);
        assertEquals("https://example.test/label", sources.getJSONObject(0).getString("url"));
        JSONObject request = new JSONObject(takeRequest().getBody().readUtf8());
        assertEquals("web_search_20250305", request.getJSONArray("tools").getJSONObject(0).getString("type"));
    }

    @Test public void openAiResponsesSearchUsesWebSearchAndCitationSources() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"answer\",\"annotations\":[{\"type\":\"url_citation\",\"url\":\"https://example.test/source\",\"title\":\"Source\"}]}]}]}"));
        ProviderProfile profile = ProviderProfile.builder().protocol(ProviderProfile.OPENAI_RESPONSES).baseUrl(server.url("/v1").toString()).modelId("search-model").authMode(ProviderProfile.AUTH_BEARER).build();
        JSONArray tools = new JSONArray().put(new JSONObject().put("type", "web_search"));
        AiResponse response = new OpenAiResponsesTransport(profile).send(AiRequest.builder().modelId(profile.modelId).userText("query").tools(tools).build(), "contract-search");
        JSONArray sources = SearchRepository.extractSources(response, SearchProfile.OPENAI_RESPONSES_NATIVE);
        assertEquals("https://example.test/source", sources.getJSONObject(0).getString("url"));
        assertEquals("web_search", new JSONObject(takeRequest().getBody().readUtf8()).getJSONArray("tools").getJSONObject(0).getString("type"));
    }

    private RecordedRequest takeRequest() throws Exception { RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS); assertNotNull(request); return request; }
}
