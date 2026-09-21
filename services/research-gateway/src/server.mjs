import http from 'node:http';
import { MODEL, API_BASE, assertModel, requireRuntimeKey } from './config.mjs';
import { searchQueries } from './provider.mjs';
import { fetchPublicText } from './fetch-policy.mjs';
import { MEDICAL_SAFETY_SYSTEM_PROMPT, SAFETY_PROMPT_VERSION, buildPromptInput } from './prompt.mjs';

const PORT = Number(process.env.LUNA_GATEWAY_PORT || 8787);
const MAX_BODY = 64 * 1024;

function json(response, status, body) {
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
  response.end(JSON.stringify(body));
}

async function readJson(request) {
  let size = 0;
  const chunks = [];
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY) throw Object.assign(new Error('Request body is too large.'), { code: 'BAD_REQUEST' });
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}'); }
  catch { throw Object.assign(new Error('Request body must be valid JSON.'), { code: 'BAD_REQUEST' }); }
}

async function answer(question, context) {
  const key = requireRuntimeKey();
  const response = await fetch(`${API_BASE.replace(/\/$/, '')}/responses`, {
    method: 'POST', redirect: 'error',
    headers: { 'content-type': 'application/json', authorization: `Bearer ${key}` },
    body: JSON.stringify({ model: assertModel(MODEL), instructions: MEDICAL_SAFETY_SYSTEM_PROMPT, input: buildPromptInput(question, context), max_output_tokens: 1200 })
  });
  if (!response.ok) throw Object.assign(new Error(`DeepSeek answer failed with HTTP ${response.status}.`), { code: 'AI_PROVIDER_ERROR' });
  const payload = await response.json();
  const text = Array.isArray(payload.output)
    ? payload.output.flatMap(item => Array.isArray(item.content) ? item.content : []).map(item => item.text || '').join(' ').trim()
    : (typeof payload.output_text === 'string' ? payload.output_text : '');
  if (!text) throw Object.assign(new Error('DeepSeek returned no answer text.'), { code: 'AI_PROVIDER_ERROR' });
  return { answer: text, safety_prompt_version: SAFETY_PROMPT_VERSION };
}

async function route(request, response) {
  if (request.method === 'GET' && request.url === '/health') return json(response, 200, { ok: true, model: MODEL, safety_prompt_version: SAFETY_PROMPT_VERSION });
  if (request.method !== 'POST') return json(response, 405, { error: 'METHOD_NOT_ALLOWED' });
  const body = await readJson(request);
  if (body.model !== undefined) assertModel(body.model);
  if (request.url === '/v1/research/search') return json(response, 200, { results: await searchQueries(body.queries) });
  if (request.url === '/v1/research/fetch') return json(response, 200, await fetchPublicText(body.url));
  if (request.url === '/v1/assistant/answer') {
    if (typeof body.question !== 'string' || !body.question.trim()) throw Object.assign(new Error('question is required.'), { code: 'BAD_REQUEST' });
    return json(response, 200, await answer(body.question, body.context || {}));
  }
  return json(response, 404, { error: 'NOT_FOUND' });
}

const server = http.createServer((request, response) => {
  route(request, response).catch(error => {
    const known = new Set(['BAD_REQUEST', 'MODEL_NOT_ALLOWED', 'WEB_PROVIDER_CREDENTIAL_MISSING', 'WEB_PROVIDER_ERROR', 'AI_PROVIDER_ERROR']);
    const code = known.has(error.code) ? error.code : 'REQUEST_FAILED';
    const status = code === 'WEB_PROVIDER_CREDENTIAL_MISSING' ? 503 : code === 'MODEL_NOT_ALLOWED' || code === 'BAD_REQUEST' ? 400 : 502;
    json(response, status, { error: code, message: error.message });
  });
});

if (process.argv[1] === new URL(import.meta.url).pathname.replaceAll('\\', '/').replace(/^\/(?:[A-Za-z]:)/, match => match.slice(1))) {
  server.listen(PORT, '127.0.0.1', () => console.log(`个人用药助手 research gateway listening on 127.0.0.1:${PORT}`));
}

export { server, route };
