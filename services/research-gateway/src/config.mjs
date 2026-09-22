const OFFICIAL_API_BASE = 'https://api.deepseek.com/anthropic/v1';

export function normalizeApiBase(rawValue) {
  const value = typeof rawValue === 'string' ? rawValue.trim() : '';
  if (!value) return OFFICIAL_API_BASE;
  let url;
  try {
    url = new URL(value);
  } catch {
    const error = new Error('DEEPSEEK_API_BASE must be the official DeepSeek Anthropic endpoint.');
    error.code = 'API_BASE_NOT_ALLOWED';
    throw error;
  }
  const pathname = url.pathname.replace(/\/+$/, '') || '/';
  const official = url.protocol === 'https:'
    && url.hostname.toLowerCase() === 'api.deepseek.com'
    && url.port === ''
    && !url.username && !url.password && !url.search && !url.hash
    && pathname === '/anthropic/v1';
  if (!official) {
    const error = new Error('DEEPSEEK_API_BASE must be the official DeepSeek Anthropic endpoint.');
    error.code = 'API_BASE_NOT_ALLOWED';
    throw error;
  }
  return OFFICIAL_API_BASE;
}

export const MODEL = 'deepseek-v4-flash';
export const API_BASE = normalizeApiBase(process.env.DEEPSEEK_API_BASE);
export const MAX_SEARCH_QUERIES = 4;
export const MAX_SEARCH_USES = 5;

export function assertModel(model = MODEL) {
  if (model !== MODEL) {
    const error = new Error('Only deepseek-v4-flash is permitted by the formal project policy.');
    error.code = 'MODEL_NOT_ALLOWED';
    throw error;
  }
  return MODEL;
}

export function readRuntimeKey() {
  const value = process.env.DEEPSEEK_API_KEY;
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

export function requireRuntimeKey() {
  const key = readRuntimeKey();
  if (!key) {
    const error = new Error('DEEPSEEK_API_KEY is not configured at runtime.');
    error.code = 'WEB_PROVIDER_CREDENTIAL_MISSING';
    throw error;
  }
  return key;
}
