export const MODEL = 'deepseek-v4-flash';
export const API_BASE = process.env.DEEPSEEK_API_BASE || 'https://api.deepseek.com/anthropic/v1';
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
