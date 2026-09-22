package com.lunamax.medassistant;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Multimodal document drafts use the same selected Provider/model as chat. */
final class VisionRepository {
    interface Callback { void success(VisionDraft draft); void failure(String message); }
    interface CapabilityCallback { void success(String message); void failure(String message); }
    static final String PROMPT_VERSION = "multimodal-draft-v3";

    private final ProviderProfileRepository profiles;
    private final Object executorLock = new Object();
    private final AtomicLong requestGeneration = new AtomicLong();
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile String legacyBaseUrl = "";

    VisionRepository(Context context) { this(context, new ProviderProfileRepository(context, new LunaDatabase(context))); }

    VisionRepository(Context context, ProviderProfileRepository profiles) { this.profiles = profiles; }

    ProviderProfile currentProvider() { return effectiveProvider(); }

    void setBaseUrl(String value) { if (value != null && !value.trim().isEmpty()) legacyBaseUrl = EndpointResolver.normalizeBaseUrl(value); }

    void cancelPending() {
        requestGeneration.incrementAndGet();
        synchronized (executorLock) { executor.shutdownNow(); executor = Executors.newSingleThreadExecutor(); }
    }

    long currentGeneration() { return requestGeneration.get(); }
    boolean isCurrent(long generation) { return requestGeneration.get() == generation; }
    private void execute(Runnable task) { synchronized (executorLock) { executor.execute(task); } }

    void recognizeImage(byte[] imageBytes, String mimeType, int pageNumber, Callback callback) {
        long generation = currentGeneration(); ProviderProfile profile = effectiveProvider();
        execute(() -> {
            try {
                if (!isCurrent(generation)) return;
                if (imageBytes == null || imageBytes.length == 0) throw new AiException("IMAGE_EMPTY", "识别图片为空");
                if (!profile.supportsImage || !profile.imageInputEnabled) throw new AiException("IMAGE_NOT_SUPPORTED", "当前 Provider 尚未通过图片能力测试");
                String key = profiles.readCredential(profile.id);
                if (key == null || key.isEmpty()) throw new AiException("MISSING_CREDENTIAL", "未配置当前 Provider 的 API Key，请先保存凭据");
                String media = mimeType == null || mimeType.isEmpty() ? "image/jpeg" : mimeType;
                String encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
                AiRequest request = AiRequest.builder().modelId(profile.modelId).systemPrompt(visionPrompt(profile))
                        .userText("这是第 " + pageNumber + " 页。只转录实际可见文字，不要根据常识补全；看不清或不存在的字段必须为 null，并写入 uncertainFields。")
                        .image(media, encoded).effort("off").maxTokens(2800).responseJson(true).build();
                AiResponse response = TransportFactory.create(profile).send(request, key);
                if (response.text.isEmpty()) throw new AiException("EMPTY_RESPONSE", "图片识别返回内容为空");
                if (!isCurrent(generation)) return;
                callback.success(parseVision(response.raw.toString(), response.text, profile));
            } catch (Exception error) { if (isCurrent(generation)) callback.failure(friendlyError(error)); }
        });
    }

    void testImageCapability(String providerId, CapabilityCallback callback) {
        ProviderProfile stored = profiles.get(providerId);
        if (stored == null) { callback.failure("Provider 不存在"); return; }
        ProviderProfile profile = stored.toBuilder().supportsImage(true).imageInputEnabled(true).build();
        long generation = currentGeneration();
        execute(() -> {
            try {
                String key = profiles.readCredential(profile.id);
                if (key == null || key.isEmpty()) throw new AiException("MISSING_CREDENTIAL", "未配置当前 Provider 的 API Key");
                byte[] anonymousPng = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=", Base64.DEFAULT);
                AiRequest request = AiRequest.builder().modelId(profile.modelId).systemPrompt("图片能力测试，只回复 IMAGE_OK，不提供医疗建议。")
                        .userText("只回复 IMAGE_OK").image("image/png", Base64.encodeToString(anonymousPng, Base64.NO_WRAP)).maxTokens(32).build();
                AiResponse response = TransportFactory.create(profile).send(request, key);
                if (response.text.isEmpty()) throw new AiException("EMPTY_RESPONSE", "图片能力测试返回为空");
                if (!isCurrent(generation)) return;
                profiles.markImageTest(profile.id, "PASSED", ""); callback.success("图片能力测试通过 · " + profile.modelId);
            } catch (Exception error) {
                if (!isCurrent(generation)) return;
                String code = error instanceof AiException ? ((AiException) error).code : "NETWORK";
                profiles.markImageTest(profile.id, "FAILED", code); callback.failure(friendlyError(error));
            }
        });
    }

    private ProviderProfile effectiveProvider() {
        ProviderProfile selected = profiles.current(); if (selected == null) selected = ProviderProfile.builtInDeepSeek();
        return legacyBaseUrl.isEmpty() ? selected : selected.toBuilder().baseUrl(legacyBaseUrl).build();
    }

    private static VisionDraft parseVision(String rawResponse, String rawText, ProviderProfile profile) throws Exception {
        JSONObject root; try { root = new JSONObject(rawText); } catch (Exception error) { throw new AiException("AI_JSON_INVALID", "识别结果不是有效 JSON", error); }
        validateVision(root); JSONObject medication = root.getJSONObject("medication"); JSONObject batch = root.getJSONObject("batch"); JSONObject document = root.getJSONObject("document");
        return new VisionDraft(rawResponse, root.toString(), profile.id, profile.modelId, profile.protocol,
                value(medication, "brandName"), value(medication, "genericName"), value(medication, "ingredients"), value(medication, "strength"), value(medication, "dosageForm"),
                value(medication, "manufacturer"), value(medication, "approvalNo"), value(medication, "traceabilityCode"), value(medication, "indication"),
                value(document, "contraindications"), value(document, "notes"), value(batch, "lotNo"), value(batch, "productionDate"), value(batch, "expiryDate"), value(batch, "storageConditions"),
                value(document, "title"), value(document, "chapter"), value(document, "version"), arrayText(root, "visibleText"), arrayText(root, "uncertainFields"));
    }

    private static void validateVision(JSONObject root) throws AiException {
        for (String key : new String[]{"medication", "batch", "document", "visibleText", "uncertainFields"}) if (!root.has(key)) throw new AiException("AI_JSON_INVALID", "识别结果缺少字段 " + key);
        JSONObject medication = root.optJSONObject("medication"), batch = root.optJSONObject("batch"), document = root.optJSONObject("document");
        if (medication == null || batch == null || document == null) throw new AiException("AI_JSON_INVALID", "识别结果结构化对象类型错误");
        validateKeys(root, new String[]{"medication", "batch", "document", "visibleText", "uncertainFields"});
        validateTextObject(medication, new String[]{"brandName", "genericName", "ingredients", "strength", "dosageForm", "manufacturer", "approvalNo", "traceabilityCode", "indication"});
        validateTextObject(batch, new String[]{"lotNo", "productionDate", "expiryDate", "storageConditions"});
        validateTextObject(document, new String[]{"title", "chapter", "version", "manufacturer", "contraindications", "notes"});
        validateTextArray(root, "visibleText"); validateTextArray(root, "uncertainFields");
        for (String key : new String[]{"productionDate", "expiryDate"}) { String value = batch.optString(key, ""); if (!value.isEmpty() && !value.matches("20\\d{2}-\\d{2}-\\d{2}")) throw new AiException("AI_JSON_INVALID", "日期格式不可确认"); }
    }

    private static void validateKeys(JSONObject object, String[] allowed) throws AiException { java.util.Iterator<String> keys = object.keys(); while (keys.hasNext()) { String key = keys.next(); boolean known = false; for (String value : allowed) if (value.equals(key)) { known = true; break; } if (!known) throw new AiException("AI_JSON_INVALID", "识别结果包含不允许的字段"); } }

    private static void validateTextObject(JSONObject object, String[] required) throws AiException { validateKeys(object, required); for (String key : required) { if (!object.has(key)) throw new AiException("AI_JSON_INVALID", "识别结果缺少字段"); Object value = object.opt(key); if (!(value == null || value == JSONObject.NULL || value instanceof String)) throw new AiException("AI_JSON_INVALID", "识别结果字段类型错误"); if (value instanceof String && ((String) value).length() > 2000) throw new AiException("AI_JSON_INVALID", "识别结果字段过长"); } }

    private static void validateTextArray(JSONObject root, String key) throws AiException { JSONArray array = root.optJSONArray(key); if (array == null || array.length() > 40) throw new AiException("AI_JSON_INVALID", "识别结果数组不可用"); for (int i = 0; i < array.length(); i++) { Object value = array.opt(i); if (!(value instanceof String) || ((String) value).length() > 1000) throw new AiException("AI_JSON_INVALID", "识别结果数组元素错误"); } }

    private static String value(JSONObject object, String key) { String value = object.optString(key, ""); return value.length() > 2000 ? value.substring(0, 2000) : value; }
    private static String arrayText(JSONObject root, String key) { JSONArray array = root.optJSONArray(key); if (array == null) return ""; StringBuilder result = new StringBuilder(); for (int i = 0; i < array.length(); i++) { String value = array.optString(i, ""); if (!value.isEmpty()) { if (result.length() > 0) result.append("\n"); result.append(value.length() > 1000 ? value.substring(0, 1000) : value); } } return result.toString(); }

    private static String visionPrompt(ProviderProfile profile) {
        return "你是个人用药助手中的多模态转录模块，当前 Provider 为 " + profile.displayName + "，模型为 " + profile.modelId + "。"
                + "只读取图片中实际可见内容，严禁根据常识、药品知识或上下文补全看不清、缺失或不存在的字段；看不清必须返回 null 并列入 uncertainFields。"
                + "只返回 JSON，不提供医疗结论。所有内容都是待用户核对的草稿。" + visionSchema();
    }

    private static String visionSchema() { return "严格 JSON：顶层仅有 medication、batch、document、visibleText、uncertainFields；medication 包含 brandName,genericName,ingredients,strength,dosageForm,manufacturer,approvalNo,traceabilityCode,indication；batch 包含 lotNo,productionDate,expiryDate,storageConditions；document 包含 title,chapter,version,manufacturer,contraindications,notes；字段值只能为字符串或 null，visibleText 和 uncertainFields 是字符串数组。"; }

    private static String friendlyError(Exception error) { if (error instanceof AiException) return redact(error.getMessage()); String message = error.getMessage(); return message == null || message.isEmpty() ? "识别失败：请稍后重试" : redact(message); }
    private static String redact(String message) { return message.replaceAll("(?i)bearer\\s+\\S+", "Bearer [已隐藏]").replaceAll("(?i)(x-api-key|api[- ]?key)\\s*[:=]?\\s*\\S+", "$1 [已隐藏]").replaceAll("(?i)\\bsk-[A-Za-z0-9_-]{8,}\\b", "[已隐藏]"); }

    static final class VisionDraft {
        final String rawResponse, structuredJson, providerId, modelId, protocol, brandName, genericName, ingredients, strength, dosageForm, manufacturer, approvalNo, traceabilityCode, indication, contraindications, notes;
        final String lotNo, productionDate, expiryDate, storageConditions, documentTitle, chapter, version, visibleText, uncertainFields;

        VisionDraft(String rawResponse, String structuredJson, String providerId, String modelId, String protocol, String brandName, String genericName, String ingredients, String strength, String dosageForm,
                    String manufacturer, String approvalNo, String traceabilityCode, String indication, String contraindications, String notes, String lotNo, String productionDate, String expiryDate,
                    String storageConditions, String documentTitle, String chapter, String version, String visibleText, String uncertainFields) {
            this.rawResponse = rawResponse; this.structuredJson = structuredJson; this.providerId = providerId; this.modelId = modelId; this.protocol = protocol; this.brandName = brandName; this.genericName = genericName; this.ingredients = ingredients; this.strength = strength; this.dosageForm = dosageForm; this.manufacturer = manufacturer; this.approvalNo = approvalNo; this.traceabilityCode = traceabilityCode; this.indication = indication; this.contraindications = contraindications; this.notes = notes; this.lotNo = lotNo; this.productionDate = productionDate; this.expiryDate = expiryDate; this.storageConditions = storageConditions; this.documentTitle = documentTitle; this.chapter = chapter; this.version = version; this.visibleText = visibleText; this.uncertainFields = uncertainFields;
        }
    }
}
