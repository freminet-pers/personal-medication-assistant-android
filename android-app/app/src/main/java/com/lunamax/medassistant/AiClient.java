package com.lunamax.medassistant;

import android.content.Context;

/**
 * Compatibility facade retained while the page layer migrates to repositories.
 * All storage and network behavior lives in SecretStore, AssistantRepository,
 * VisionRepository, DeepSeekTransport and HarnessSearchClient.
 */
final class AiClient {
    static final String MODEL = DeepSeekTransport.TEXT_MODEL;
    static final String VISION_MODEL = DeepSeekTransport.VISION_MODEL;
    static final String VISION_PROMPT_VERSION = VisionRepository.PROMPT_VERSION;

    interface Callback { void success(Response response); void failure(String message); }
    interface ConnectionCallback { void success(String message); void failure(String message); }
    interface VisionCallback { void success(VisionResult result); void failure(String message); }

    static final class Response {
        final String answer;
        final org.json.JSONArray sources;
        final String personalDataUsed, effort, evidence, uncertainty;

        Response(AssistantRepository.Response value) {
            answer = value.answer; sources = value.sources; personalDataUsed = value.personalDataUsed;
            effort = value.effort; evidence = value.evidence; uncertainty = value.uncertainty;
        }
    }

    static final class VisionResult {
        final String rawResponse, structuredJson, brandName, genericName, ingredients, strength, dosageForm, manufacturer, approvalNo, traceabilityCode, indication, contraindications, notes;
        final String lotNo, productionDate, expiryDate, storageConditions, documentTitle, chapter, version, visibleText, uncertainFields;

        VisionResult(VisionRepository.VisionDraft value) {
            rawResponse = value.rawResponse; structuredJson = value.structuredJson; brandName = value.brandName; genericName = value.genericName;
            ingredients = value.ingredients; strength = value.strength; dosageForm = value.dosageForm; manufacturer = value.manufacturer;
            approvalNo = value.approvalNo; traceabilityCode = value.traceabilityCode; indication = value.indication; contraindications = value.contraindications;
            notes = value.notes; lotNo = value.lotNo; productionDate = value.productionDate; expiryDate = value.expiryDate;
            storageConditions = value.storageConditions; documentTitle = value.documentTitle; chapter = value.chapter; version = value.version;
            visibleText = value.visibleText; uncertainFields = value.uncertainFields;
        }
    }

    private final AssistantRepository assistant;
    private final VisionRepository vision;

    AiClient(Context context) {
        assistant = new AssistantRepository(context);
        vision = new VisionRepository(context);
    }

    boolean hasKey() { return assistant.hasKey(); }
    String keyStatus() { return assistant.keyStatus(); }
    String lastConnectionStatus() { return assistant.lastConnectionStatus(); }
    void saveKey(String key) { assistant.saveKey(key); }
    void deleteKey() { assistant.deleteKey(); }
    void setBaseUrl(String value) { assistant.setBaseUrl(value); vision.setBaseUrl(value); }

    void testConnection(ConnectionCallback callback) {
        assistant.testConnection(new AssistantRepository.ConnectionCallback() {
            @Override public void success(String message) { callback.success(message); }
            @Override public void failure(String message) { callback.failure(message); }
        });
    }

    void answer(String question, String personalDataUsed, String effort, boolean webSearch, Callback callback) {
        assistant.answer(question, personalDataUsed, effort, webSearch, new AssistantRepository.Callback() {
            @Override public void success(AssistantRepository.Response response) { callback.success(new Response(response)); }
            @Override public void failure(String message) { callback.failure(message); }
        });
    }

    void recognizeImage(byte[] imageBytes, String mimeType, int pageNumber, VisionCallback callback) {
        vision.recognizeImage(imageBytes, mimeType, pageNumber, new VisionRepository.Callback() {
            @Override public void success(VisionRepository.VisionDraft draft) { callback.success(new VisionResult(draft)); }
            @Override public void failure(String message) { callback.failure(message); }
        });
    }
}
