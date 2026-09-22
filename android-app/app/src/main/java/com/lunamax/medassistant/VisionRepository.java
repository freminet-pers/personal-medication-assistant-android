package com.lunamax.medassistant;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Vision-only repository: raw files stay private and responses remain drafts. */
final class VisionRepository {
    interface Callback { void success(VisionDraft draft); void failure(String message); }
    static final String PROMPT_VERSION = "vision-draft-v2";

    private final SecretStore secrets;
    private final DeepSeekTransport transport;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    VisionRepository(Context context) {
        secrets = new SecretStore(context);
        transport = new DeepSeekTransport();
    }

    void setBaseUrl(String value) { transport.setBaseUrl(value); }

    void recognizeImage(byte[] imageBytes, String mimeType, int pageNumber, Callback callback) {
        executor.execute(() -> {
            try {
                String key = secrets.readApiKey();
                if (key == null || key.isEmpty()) throw new IllegalStateException("未配置 DeepSeek API Key，请先保存 Key");
                if (imageBytes == null || imageBytes.length == 0) throw new IllegalArgumentException("识别图片为空");
                String media = mimeType == null || mimeType.isEmpty() ? "image/jpeg" : mimeType;
                String dataUrl = "data:" + media + ";base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP);
                JSONArray content = new JSONArray()
                        .put(new JSONObject().put("type", "text").put("text", "这是第 " + pageNumber + " 页。只转录实际可见文字，不要根据常识补全；看不清或不存在的字段必须为 null，并写入 uncertainFields。"))
                        .put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", dataUrl)));
                JSONObject body = new JSONObject()
                        .put("model", DeepSeekTransport.VISION_MODEL)
                        .put("messages", new JSONArray()
                                .put(new JSONObject().put("role", "system").put("content", visionPrompt()))
                                .put(new JSONObject().put("role", "user").put("content", content)))
                        .put("temperature", 0)
                        .put("max_tokens", 2800)
                        .put("response_format", new JSONObject().put("type", "json_object"));
                JSONObject payload = transport.postChatCompletions(key, body);
                String rawText = extractChatText(payload);
                callback.success(parseVision(payload.toString(), rawText));
            } catch (Exception error) {
                callback.failure(friendlyError(error));
            }
        });
    }

    private static VisionDraft parseVision(String rawResponse, String rawText) throws Exception {
        JSONObject root;
        try { root = new JSONObject(rawText); }
        catch (Exception error) { throw new IllegalArgumentException("AI_JSON_INVALID：识别结果不是有效 JSON"); }
        validateVision(root);
        JSONObject medication = object(root, "medication");
        JSONObject batch = object(root, "batch");
        JSONObject document = object(root, "document");
        return new VisionDraft(rawResponse, root.toString(),
                value(medication, "brandName"), value(medication, "genericName"), value(medication, "ingredients"),
                value(medication, "strength"), value(medication, "dosageForm"), value(medication, "manufacturer"),
                value(medication, "approvalNo"), value(medication, "traceabilityCode"), value(medication, "indication"),
                value(document, "contraindications"), value(document, "notes"), value(batch, "lotNo"),
                value(batch, "productionDate"), value(batch, "expiryDate"), value(batch, "storageConditions"),
                value(document, "title"), value(document, "chapter"), value(document, "version"),
                arrayText(root, "visibleText"), arrayText(root, "uncertainFields"));
    }

    private static void validateVision(JSONObject root) {
        for (String key : new String[]{"medication", "batch", "document", "visibleText", "uncertainFields"}) {
            if (!root.has(key)) throw new IllegalArgumentException("AI_JSON_INVALID：缺少字段 " + key);
        }
        JSONObject medication = root.optJSONObject("medication");
        JSONObject batch = root.optJSONObject("batch");
        JSONObject document = root.optJSONObject("document");
        if (medication == null || batch == null || document == null) throw new IllegalArgumentException("AI_JSON_INVALID：结构化对象类型错误");
        validateKeys(root, new String[]{"medication", "batch", "document", "visibleText", "uncertainFields"});
        validateTextObject(medication, new String[]{"brandName", "genericName", "ingredients", "strength", "dosageForm", "manufacturer", "approvalNo", "traceabilityCode", "indication"});
        validateTextObject(batch, new String[]{"lotNo", "productionDate", "expiryDate", "storageConditions"});
        validateTextObject(document, new String[]{"title", "chapter", "version", "manufacturer", "contraindications", "notes"});
        validateTextArray(root, "visibleText");
        validateTextArray(root, "uncertainFields");
        for (String key : new String[]{"productionDate", "expiryDate"}) {
            String value = batch.optString(key, "");
            if (!value.isEmpty() && !value.matches("20\\d{2}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("AI_JSON_INVALID：日期格式不可确认");
        }
    }

    private static void validateKeys(JSONObject object, String[] allowed) {
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            boolean known = false;
            for (String value : allowed) if (value.equals(key)) { known = true; break; }
            if (!known) throw new IllegalArgumentException("AI_JSON_INVALID：不允许的字段 " + key);
        }
    }

    private static void validateTextObject(JSONObject object, String[] required) {
        validateKeys(object, required);
        for (String key : required) {
            if (!object.has(key)) throw new IllegalArgumentException("AI_JSON_INVALID：缺少字段 " + key);
            Object value = object.opt(key);
            if (!(value == null || value == JSONObject.NULL || value instanceof String)) throw new IllegalArgumentException("AI_JSON_INVALID：字段类型错误 " + key);
            if (value instanceof String && ((String) value).length() > 2000) throw new IllegalArgumentException("AI_JSON_INVALID：字段过长 " + key);
        }
    }

    private static void validateTextArray(JSONObject root, String key) {
        JSONArray array = root.optJSONArray(key);
        if (array == null || array.length() > 40) throw new IllegalArgumentException("AI_JSON_INVALID：数组不可用 " + key);
        for (int i = 0; i < array.length(); i++) {
            Object value = array.opt(i);
            if (!(value instanceof String) || ((String) value).length() > 1000) throw new IllegalArgumentException("AI_JSON_INVALID：数组元素类型错误 " + key);
        }
    }

    private static JSONObject object(JSONObject parent, String key) { return parent.optJSONObject(key); }
    private static String value(JSONObject object, String key) { String value = object.optString(key, ""); return value.length() > 2000 ? value.substring(0, 2000) : value; }

    private static String arrayText(JSONObject root, String key) {
        JSONArray array = root.optJSONArray(key);
        if (array == null) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "");
            if (!value.isEmpty()) { if (result.length() > 0) result.append("\n"); result.append(value.length() > 1000 ? value.substring(0, 1000) : value); }
        }
        return result.toString();
    }

    private static String extractChatText(JSONObject payload) {
        JSONArray choices = payload.optJSONArray("choices");
        JSONObject first = choices == null ? null : choices.optJSONObject(0);
        JSONObject message = first == null ? null : first.optJSONObject("message");
        if (message == null) return "";
        Object content = message.opt("content");
        if (content instanceof String) return (String) content;
        if (content instanceof JSONArray) {
            StringBuilder result = new StringBuilder();
            JSONArray blocks = (JSONArray) content;
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.optJSONObject(i);
                if (block != null) result.append(block.optString("text", ""));
            }
            return result.toString();
        }
        return "";
    }

    private static String visionPrompt() {
        return "你是个人用药助手中的视觉转录模块，模型为 " + DeepSeekTransport.VISION_MODEL + "。"
                + "只读取图片中实际可见内容，严禁根据常识、药品知识或上下文补全看不清、缺失或不存在的字段；看不清必须返回 null 并列入 uncertainFields。"
                + "只返回 JSON，不提供医疗结论。所有内容都是待用户核对的草稿。" + visionSchema();
    }

    private static String visionSchema() {
        return "严格 JSON：顶层仅有 medication、batch、document、visibleText、uncertainFields；"
                + "medication 包含 brandName,genericName,ingredients,strength,dosageForm,manufacturer,approvalNo,traceabilityCode,indication；"
                + "batch 包含 lotNo,productionDate,expiryDate,storageConditions；"
                + "document 包含 title,chapter,version,manufacturer,contraindications,notes；"
                + "字段值只能为字符串或 null，visibleText 和 uncertainFields 是字符串数组。";
    }

    private static String friendlyError(Exception error) {
        if (error instanceof DeepSeekTransport.DeepSeekException) return error.getMessage();
        String message = error.getMessage();
        return message == null || message.isEmpty() ? "识别失败：请稍后重试" : message.replaceAll("(?i)(api[- ]?key|bearer)\\s*[:=]?\\s*\\S+", "$1 [已隐藏]");
    }

    static final class VisionDraft {
        final String rawResponse, structuredJson, brandName, genericName, ingredients, strength, dosageForm, manufacturer, approvalNo, traceabilityCode, indication, contraindications, notes;
        final String lotNo, productionDate, expiryDate, storageConditions, documentTitle, chapter, version, visibleText, uncertainFields;

        VisionDraft(String rawResponse, String structuredJson, String brandName, String genericName, String ingredients, String strength, String dosageForm,
                    String manufacturer, String approvalNo, String traceabilityCode, String indication, String contraindications, String notes, String lotNo,
                    String productionDate, String expiryDate, String storageConditions, String documentTitle, String chapter, String version, String visibleText,
                    String uncertainFields) {
            this.rawResponse = rawResponse; this.structuredJson = structuredJson; this.brandName = brandName; this.genericName = genericName; this.ingredients = ingredients;
            this.strength = strength; this.dosageForm = dosageForm; this.manufacturer = manufacturer; this.approvalNo = approvalNo; this.traceabilityCode = traceabilityCode;
            this.indication = indication; this.contraindications = contraindications; this.notes = notes; this.lotNo = lotNo; this.productionDate = productionDate;
            this.expiryDate = expiryDate; this.storageConditions = storageConditions; this.documentTitle = documentTitle; this.chapter = chapter; this.version = version;
            this.visibleText = visibleText; this.uncertainFields = uncertainFields;
        }
    }
}
