package com.lunamax.medassistant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Android mapping of the audited DeepSeek Harness web-search provider. */
final class HarnessSearchClient {
    private final DeepSeekTransport transport;

    HarnessSearchClient(DeepSeekTransport transport) { this.transport = transport; }

    JSONArray search(String query, String apiKey) throws Exception {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty() || normalized.length() > 400) {
            throw new DeepSeekTransport.DeepSeekException("SEARCH_INVALID_QUERY", "搜索问题必须为 1–400 个字符");
        }
        JSONObject body = new JSONObject()
                .put("model", DeepSeekTransport.TEXT_MODEL)
                .put("max_tokens", 4096)
                .put("messages", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("content", new JSONArray().put(new JSONObject()
                                .put("type", "text")
                                .put("text", "Perform a web search for the query: " + normalized)))))
                .put("tools", new JSONArray().put(new JSONObject()
                        .put("type", "web_search_20250305")
                        .put("name", "web_search")
                        .put("max_uses", DeepSeekTransport.MAX_SEARCH_USES)));
        return mapResponse(transport.postMessages(apiKey, body));
    }

    static JSONArray mapResponse(JSONObject payload) throws Exception {
        JSONArray blocks = payload == null ? null : payload.optJSONArray("content");
        Map<String, JSONObject> sources = new LinkedHashMap<>();
        Map<String, String> citations = new LinkedHashMap<>();
        if (blocks != null) {
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.optJSONObject(i);
                if (block == null) continue;
                if ("text".equals(block.optString("type"))) {
                    JSONArray blockCitations = block.optJSONArray("citations");
                    if (blockCitations != null) for (int j = 0; j < blockCitations.length(); j++) {
                        JSONObject citation = blockCitations.optJSONObject(j);
                        if (citation == null) continue;
                        String url = citation.optString("url", "");
                        if (!url.isEmpty() && !citations.containsKey(url)) citations.put(url, citation.optString("cited_text", ""));
                    }
                }
                if (!"web_search_tool_result".equals(block.optString("type"))) continue;
                JSONArray results = block.optJSONArray("content");
                if (results == null) continue;
                for (int j = 0; j < results.length(); j++) {
                    JSONObject result = results.optJSONObject(j);
                    if (result == null || !"web_search_result".equals(result.optString("type"))) continue;
                    String url = result.optString("url", "");
                    if (url.isEmpty() || sources.containsKey(url)) continue;
                    sources.put(url, new JSONObject()
                            .put("title", result.optString("title", url))
                            .put("url", url)
                            .put("page_age", result.optString("page_age", ""))
                            .put("snippet", result.optString("snippet", ""))
                            .put("evidence_level", "C"));
                }
            }
        }
        JSONArray output = new JSONArray();
        for (Map.Entry<String, JSONObject> entry : sources.entrySet()) {
            JSONObject item = entry.getValue();
            String cited = citations.get(entry.getKey());
            if (cited != null && !cited.isEmpty()) item.put("cited_text", cited);
            output.put(item);
        }
        if (output.length() == 0) throw new DeepSeekTransport.DeepSeekException("SEARCH_NO_SOURCES", "DeepSeek 未返回结构化搜索来源");
        return output;
    }
}
