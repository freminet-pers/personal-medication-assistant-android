import { API_BASE, MAX_SEARCH_QUERIES, MAX_SEARCH_USES, MODEL, assertModel, requireRuntimeKey } from './config.mjs';

const ANTHROPIC_VERSION = '2023-06-01';

function normalizeQuery(query) {
  if (typeof query !== 'string') throw new Error('Each search query must be text.');
  const value = query.trim();
  if (!value || value.length > 400) throw new Error('Search query must contain 1-400 characters.');
  return value;
}

export function buildSearchRequest(query, maxUses = MAX_SEARCH_USES) {
  assertModel(MODEL);
  const key = requireRuntimeKey();
  return {
    url: `${API_BASE.replace(/\/$/, '')}/anthropic/v1/messages`,
    init: {
      method: 'POST',
      redirect: 'error',
      headers: {
        'content-type': 'application/json',
        'x-api-key': key,
        authorization: `Bearer ${key}`,
        'anthropic-version': ANTHROPIC_VERSION
      },
      body: JSON.stringify({
        model: MODEL,
        max_tokens: 4096,
        messages: [{ role: 'user', content: query }],
        tools: [{ type: 'web_search_20250305', name: 'web_search', max_uses: maxUses }]
      })
    }
  };
}

export function mapSearchResponse(payload) {
  const blocks = Array.isArray(payload?.content) ? payload.content : [];
  const byUrl = new Map();
  for (const block of blocks) {
    if (block?.type !== 'web_search_tool_result') continue;
    const results = Array.isArray(block.content) ? block.content : [];
    for (const result of results) {
      if (result?.type !== 'web_search_result' || typeof result.url !== 'string') continue;
      if (!byUrl.has(result.url)) {
        byUrl.set(result.url, {
          title: typeof result.title === 'string' ? result.title : result.url,
          url: result.url,
          snippet: typeof result.snippet === 'string' ? result.snippet : '',
          page_age: typeof result.page_age === 'string' ? result.page_age : undefined,
          cited_text: typeof result.cited_text === 'string' ? result.cited_text : undefined
        });
      }
    }
  }
  if (!byUrl.size) {
    const error = new Error('DeepSeek returned no structured web search result blocks.');
    error.code = 'WEB_PROVIDER_ERROR';
    throw error;
  }
  return [...byUrl.values()];
}

export async function nativeSearch(query, { fetchImpl = fetch, maxUses = MAX_SEARCH_USES } = {}) {
  const request = buildSearchRequest(normalizeQuery(query), Math.max(1, Math.min(MAX_SEARCH_USES, maxUses)));
  const response = await fetchImpl(request.url, request.init);
  if (!response.ok) {
    const error = new Error(`DeepSeek web search failed with HTTP ${response.status}.`);
    error.code = 'WEB_PROVIDER_ERROR';
    throw error;
  }
  return mapSearchResponse(await response.json());
}

export async function searchQueries(queries, options = {}) {
  if (!Array.isArray(queries) || queries.length < 1 || queries.length > MAX_SEARCH_QUERIES) {
    throw new Error(`Provide 1-${MAX_SEARCH_QUERIES} unique search queries.`);
  }
  const normalized = queries.map(normalizeQuery);
  if (new Set(normalized).size !== normalized.length) throw new Error('Search queries must be unique.');
  const results = [];
  for (const query of normalized) {
    const found = await nativeSearch(query, options);
    results.push(...found);
  }
  const unique = new Map(results.map(item => [item.url, item]));
  return [...unique.values()].slice(0, 8);
}
