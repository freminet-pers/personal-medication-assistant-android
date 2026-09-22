package com.lunamax.medassistant;

import android.os.Bundle;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lunamax.medassistant.databinding.FragmentAssistantBinding;

import org.json.JSONObject;

import java.util.List;

public final class AssistantFragment extends BaseFragment {
    private FragmentAssistantBinding binding;
    private AssistantMessageAdapter historyAdapter;
    private long sessionId;
    private boolean requestInFlight;

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container, @Nullable Bundle state) {
        binding = FragmentAssistantBinding.inflate(inflater, container, false);
        if (state != null) sessionId = state.getLong("sessionId", 0);
        return binding.getRoot();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) { outState.putLong("sessionId", sessionId); super.onSaveInstanceState(outState); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        binding.assistantEffort.setAdapter(new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new String[]{"快速", "深入", "最大"}));
        binding.assistantEffort.setText("快速", false);
        historyAdapter = new AssistantMessageAdapter(); binding.assistantHistory.setLayoutManager(new LinearLayoutManager(requireContext())); binding.assistantHistory.setAdapter(historyAdapter);
        binding.assistantManageKey.setOnClickListener(v -> host().showAiServices());
        binding.assistantTestKey.setOnClickListener(v -> testConnection());
        binding.assistantSend.setOnClickListener(v -> send()); refresh();
    }

    @Override public void onResume() { super.onResume(); if (binding != null) refresh(); }

    private void refresh() {
        ProviderProfile provider = assistant().currentProvider(); boolean has = assistant().hasKey();
        binding.assistantModel.setText(provider.displayName + " · " + provider.modelLabel());
        binding.assistantCapabilities.setText("能力：" + capability(provider.supportsText, "文字") + " · " + capability(provider.supportsImage && provider.imageInputEnabled, "图片") + " · " + searchLabel(provider));
        binding.assistantKeyStatus.setText("凭据：" + assistant().keyStatus()); binding.assistantKeyStatus.setTextColor(has ? palette().primary : palette().warning);
        binding.assistantConnectionStatus.setText(assistant().lastConnectionStatus());
        SearchProfile search = host().providers().currentSearch(); boolean searchReady = searchReady(search, provider);
        binding.assistantWebSearch.setEnabled(searchReady); if (!searchReady) binding.assistantWebSearch.setChecked(false);
        binding.assistantSearchStatus.setText(searchStatus(search, provider));
        binding.assistantPreview.setText("发送前会显示：" + personalPreview());
        if (sessionId > 0) historyAdapter.submit(database().assistantMessages(sessionId));
    }

    private void testConnection() {
        if (!assistant().hasKey()) { host().showAiServices(); return; }
        if (requestInFlight) return; requestInFlight = true; binding.assistantTestKey.setEnabled(false); binding.assistantStatus.setText("正在测试连接；不会发送个人资料或图片。");
        long generation = assistant().currentGeneration(); assistant().testConnection(new AssistantRepository.ConnectionCallback() {
            @Override public void success(String message) { requireActivity().runOnUiThread(() -> finishTest(generation, message)); }
            @Override public void failure(String message) { requireActivity().runOnUiThread(() -> finishTest(generation, message)); }
        });
    }

    private void finishTest(long generation, String message) { if (!assistant().isCurrent(generation)) return; requestInFlight = false; binding.assistantTestKey.setEnabled(true); refresh(); binding.assistantStatus.setText(message); }

    private void send() {
        String question = value(binding.assistantQuestion); if (question.isEmpty()) { binding.assistantQuestionLayout.setError("请先填写当前问题"); return; }
        binding.assistantQuestionLayout.setError(null); if (!assistant().hasKey()) { host().showAiServices(); return; }
        SearchProfile search = host().providers().currentSearch(); ProviderProfile provider = assistant().currentProvider();
        if (binding.assistantWebSearch.isChecked() && !searchReady(search, provider)) { binding.assistantStatus.setText("请先在 AI 服务中配置并测试搜索服务"); return; }
        new MaterialAlertDialogBuilder(requireContext()).setTitle("发送前确认").setMessage("本次将使用：\n" + provider.displayName + " · " + provider.modelId + "\n" + personalPreview() + "\n\n不会发送：API Key、完整本地文档和无关健康资料。确认后才会调用模型。")
                .setNegativeButton("取消", null).setPositiveButton("确认发送", (d, w) -> performAsk(question)).show();
    }

    private void performAsk(String question) {
        if (requestInFlight) return; requestInFlight = true; binding.assistantSend.setEnabled(false); binding.assistantStatus.setText("正在请求模型；失败时可以重试。");
        String effort = effortCode(binding.assistantEffort.getText().toString()); long generation = assistant().currentGeneration();
        assistant().answer(question, personalPreview(), effort, binding.assistantWebSearch.isChecked(), new AssistantRepository.Callback() {
            @Override public void success(AssistantRepository.Response response) { requireActivity().runOnUiThread(() -> { if (!assistant().isCurrent(generation)) return; requestInFlight = false; binding.assistantSend.setEnabled(true); if (sessionId == 0) sessionId = database().createAssistantSession(question.length() > 24 ? question.substring(0, 24) + "…" : question); try { database().addAssistantMessage(sessionId, "user", question, ""); database().addAssistantMessage(sessionId, "assistant", response.answer, response.sources.toString()); } catch (Exception ignored) { } renderResult(response); historyAdapter.submit(database().assistantMessages(sessionId)); binding.assistantStatus.setText("回答已返回。请结合来源和不确定性阅读，不要把回答当作诊断。"); }); }
            @Override public void failure(String message) { requireActivity().runOnUiThread(() -> { if (!assistant().isCurrent(generation)) return; requestInFlight = false; binding.assistantSend.setEnabled(true); binding.assistantStatus.setText(message); }); }
        });
    }

    private void renderResult(AssistantRepository.Response response) {
        StringBuilder text = new StringBuilder(response.answer); text.append("\n\nProvider：").append(response.providerId).append(" · ").append(response.modelId).append("\n证据：").append(response.evidence).append("\n不确定性：").append(response.uncertainty).append("\n本次使用的本地资料：").append(response.personalDataUsed);
        if (response.sources.length() > 0) { text.append("\n\n来源："); for (int i = 0; i < response.sources.length(); i++) { JSONObject source = response.sources.optJSONObject(i); if (source != null) text.append("\n").append(source.optString("title")).append("\n").append(source.optString("url")); } }
        binding.assistantResult.setText(text.toString()); binding.assistantResult.setAutoLinkMask(Linkify.WEB_URLS); binding.assistantResult.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private String personalPreview() { StringBuilder result = new StringBuilder("当前有效药物："); List<LunaDatabase.MedicationRow> meds = database().medications(false); for (int i = 0; i < meds.size() && i < 8; i++) { if (i > 0) result.append("、"); result.append(meds.get(i).displayName()); } String allergies = database().allergiesSummary(); result.append("\n过敏记录：").append(allergies.isEmpty() ? "无已确认记录" : allergies).append("\n其他健康资料：仅在明确选择并确认后发送"); return result.toString(); }

    private static boolean searchReady(SearchProfile search, ProviderProfile provider) { return search != null && search.enabled && "PASSED".equals(search.testState) && !(SearchProfile.DEEPSEEK_NATIVE.equals(search.type) && !provider.isBuiltIn()); }
    private static String searchStatus(SearchProfile search, ProviderProfile provider) { if (search == null || !search.enabled) return "搜索：未配置 · 管理 AI 服务以添加独立 Search Profile"; if (SearchProfile.DEEPSEEK_NATIVE.equals(search.type) && !provider.isBuiltIn()) return "搜索：当前自定义模型不能复用 DeepSeek 官方搜索"; if ("PASSED".equals(search.testState)) return "搜索：已测试通过 · " + search.name; if ("FAILED".equals(search.testState)) return "搜索：测试失败 · 请在 AI 服务中重试"; return "搜索：已配置但未测试 · 先单独测试才会联网"; }
    private static String searchLabel(ProviderProfile provider) { return "搜索能力按独立配置启用"; }
    private static String capability(boolean enabled, String label) { return enabled ? label : label + "（未启用）"; }
    private static String value(android.widget.EditText field) { return field.getText() == null ? "" : field.getText().toString().trim(); }
    private static String effortCode(String value) { return "深入".equals(value) ? "high" : "最大".equals(value) ? "max" : "off"; }
}
