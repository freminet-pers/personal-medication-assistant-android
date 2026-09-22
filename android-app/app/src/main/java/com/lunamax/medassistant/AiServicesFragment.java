package com.lunamax.medassistant;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/** Full-screen provider/search settings; API keys are never rendered back. */
public final class AiServicesFragment extends BaseFragment {
    private LinearLayout content;
    private ProviderProfile editingProvider;
    private SearchProfile editingSearch;

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container, @Nullable Bundle state) {
        ScrollView scroll = new ScrollView(requireContext()); scroll.setFillViewport(true); content = new LinearLayout(requireContext()); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16), dp(8), dp(16), dp(24)); scroll.addView(content); return scroll;
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) { renderList(); }

    private void renderList() {
        editingProvider = null; editingSearch = null; content.removeAllViews();
        MaterialButton back = button("‹  返回助手", false); back.setOnClickListener(v -> host().showTab(MainActivity.TAB_ASSISTANT)); content.addView(back, margin(0, 0, 0, 4));
        content.addView(title("AI 服务")); content.addView(body("管理文字、图片和独立搜索服务。Key 只显示状态，不会回显；保存配置与连接测试是两个动作。"));
        content.addView(sectionTitle("Provider"));
        for (ProviderProfile profile : host().providers().providers()) content.addView(providerCard(profile));
        MaterialButton add = button("添加 AI 服务", true); add.setOnClickListener(v -> renderProviderEditor(null)); content.addView(add, margin(0, 8, 0, 16));
        renderSearchSection();
    }

    private View providerCard(ProviderProfile profile) {
        MaterialCardView card = new MaterialCardView(requireContext()); card.setCardBackgroundColor(palette().surface); card.setRadius(dp(18)); card.setStrokeWidth(dp(1)); card.setStrokeColor(palette().primarySoft); card.setCardElevation(dp(1));
        LinearLayout box = verticalForm(); box.setPadding(dp(16), dp(14), dp(16), dp(14));
        String current = profile.isDefault ? "当前使用" : "可切换"; box.addView(cardTitle(profile.displayName + (profile.isDefault ? "  ·  当前" : ""))); box.addView(body(current + "  ·  " + protocolLabel(profile.protocol) + "  ·  " + profile.modelId));
        box.addView(body("能力：" + capability(profile.supportsText, "文字") + " · " + capability(profile.supportsImage && profile.imageInputEnabled, "图片") + "\n凭据：" + host().providers().credentialStatus(profile.id) + "\n连接：" + host().providers().lastConnectionStatus(profile.id)));
        LinearLayout actions = new LinearLayout(requireContext()); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setGravity(Gravity.CENTER_VERTICAL);
        if (!profile.isDefault) { MaterialButton select = button("设为当前", false); select.setOnClickListener(v -> { host().providers().select(profile.id); host().showTab(MainActivity.TAB_ASSISTANT); feedback("已切换到 " + profile.displayName); }); actions.addView(select, weight(1, 4)); }
        MaterialButton edit = button("编辑", false); edit.setOnClickListener(v -> renderProviderEditor(profile)); actions.addView(edit, weight(1, 4));
        if (profile.isBuiltIn()) { MaterialButton copy = button("复制为自定义", false); copy.setOnClickListener(v -> { ProviderProfile copyProfile = host().providers().copyAsCustom(profile.id); host().providers().save(copyProfile, ""); renderList(); feedback("已复制为自定义 Provider；请填写并测试凭据"); }); actions.addView(copy, weight(1, 5)); }
        else { MaterialButton delete = button("删除", false); delete.setTextColor(palette().danger); delete.setOnClickListener(v -> confirmDeleteProvider(profile)); actions.addView(delete, weight(1, 4)); }
        box.addView(actions, margin(0, 8, 0, 0)); card.addView(box); return cardWithMargin(card, 0, 0, 0, 10);
    }

    private void confirmDeleteProvider(ProviderProfile profile) {
        new MaterialAlertDialogBuilder(requireContext()).setTitle("删除此 AI 服务？").setMessage("会删除该 Provider 的本地配置与对应 Key，不会影响其他 Provider、药物、提醒或健康资料。")
                .setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> { try { host().providers().delete(profile.id); renderList(); feedback("Provider 已删除"); } catch (Exception error) { feedback(host().safeMessage(error)); } }).show();
    }

    private void renderProviderEditor(ProviderProfile existing) {
        editingProvider = existing; content.removeAllViews(); MaterialButton back = button("‹  返回 AI 服务", false); back.setOnClickListener(v -> renderList()); content.addView(back, margin(0, 0, 0, 4));
        content.addView(title(existing == null ? "添加 AI 服务" : "编辑 AI 服务")); content.addView(body("普通设置只需要名称、协议、base URL、模型 ID 和 Key。高级兼容项保持最小默认值；base URL 不要填完整 endpoint。"));
        LinearLayout form = verticalForm();
        TextInputEditText name = field(form, "显示名称", existing == null ? "" : existing.displayName, false);
        MaterialAutoCompleteTextView protocol = choice(form, "请求协议", new String[]{"OpenAI Chat Completions", "OpenAI Responses", "Anthropic Messages"}, protocolLabel(existing == null ? ProviderProfile.OPENAI_CHAT_COMPLETIONS : existing.protocol));
        TextInputEditText base = field(form, "base URL", existing == null ? "" : existing.baseUrl, false);
        TextInputEditText model = field(form, "模型 ID", existing == null ? "" : existing.modelId, false);
        TextInputEditText key = field(form, "API Key（不会回显）", "", true); key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        SwitchMaterial image = new SwitchMaterial(requireContext()); image.setText("声明支持图片输入（保存后请单独测试）"); image.setChecked(existing != null && existing.supportsImage); form.addView(image, margin(0, 4, 0, 0));
        SwitchMaterial developer = new SwitchMaterial(requireContext()); developer.setText("OpenAI 兼容：优先使用 developer role"); developer.setChecked(existing != null && existing.useDeveloperRole); form.addView(developer, margin(0, 0, 0, 0));
        TextInputEditText auth = choiceEdit(form, "认证头", new String[]{"Bearer", "x-api-key", "二者同时", "无鉴权"}, authLabel(existing == null ? ProviderProfile.AUTH_BEARER : existing.authMode));
        TextInputEditText maxTokens = choiceEdit(form, "最大输出字段", new String[]{"max_tokens", "max_completion_tokens"}, maxTokensLabel(existing == null ? ProviderProfile.MAX_TOKENS : existing.maxTokensField));
        TextInputEditText reasoning = choiceEdit(form, "推理参数", new String[]{"不发送", "reasoning_effort", "DeepSeek thinking"}, reasoningLabel(existing == null ? ProviderProfile.REASONING_NONE : existing.reasoningMode));
        form.addView(body("协议示例：OpenAI Chat 通常使用 https://api.example.com/v1；Responses 使用同一 base URL 自动拼接 /responses；Anthropic 使用 https://api.example.com/v1 自动拼接 /messages。HTTP 明文只允许本机回环地址。"));
        content.addView(form);
        LinearLayout actions = new LinearLayout(requireContext()); actions.setOrientation(LinearLayout.HORIZONTAL); MaterialButton save = button("保存配置", true); MaterialButton test = button("测试连接", false); actions.addView(save, weight(1, 1)); actions.addView(test, weight(1, 1)); content.addView(actions, margin(0, 12, 0, 4));
        MaterialButton imageTest = button("测试图片能力（匿名小图）", false); imageTest.setEnabled(existing != null); content.addView(imageTest, margin(0, 0, 0, 12));
        TextView status = body(existing == null ? "保存成功后仍需单独测试连接。" : "当前状态：" + existing.testState + " · " + existing.lastTestType); content.addView(status);
        save.setOnClickListener(v -> { ProviderProfile saved = saveProvider(existing, name, protocol, base, model, key, image, developer, auth, maxTokens, reasoning); if (saved != null) { status.setText("已保存，但尚未完成连接测试"); imageTest.setEnabled(true); editingProvider = saved; } });
        test.setOnClickListener(v -> { ProviderProfile saved = saveProvider(existing, name, protocol, base, model, key, image, developer, auth, maxTokens, reasoning); if (saved == null) return; editingProvider = saved; host().providers().select(saved.id); test.setEnabled(false); status.setText("正在测试连接；不会发送个人资料或图片。"); assistant().testConnection(saved.id, new AssistantRepository.ConnectionCallback() { @Override public void success(String message) { requireActivity().runOnUiThread(() -> { test.setEnabled(true); status.setText(message); }); } @Override public void failure(String message) { requireActivity().runOnUiThread(() -> { test.setEnabled(true); status.setText(message); }); } }); });
        imageTest.setOnClickListener(v -> { if (editingProvider == null) { feedback("请先保存配置"); return; } imageTest.setEnabled(false); status.setText("正在发送匿名小图做能力测试。"); vision().testImageCapability(editingProvider.id, new VisionRepository.CapabilityCallback() { @Override public void success(String message) { requireActivity().runOnUiThread(() -> { imageTest.setEnabled(true); status.setText(message); }); } @Override public void failure(String message) { requireActivity().runOnUiThread(() -> { imageTest.setEnabled(true); status.setText(message); }); } }); });
    }

    private ProviderProfile saveProvider(ProviderProfile existing, TextInputEditText name, MaterialAutoCompleteTextView protocol, TextInputEditText base, TextInputEditText model, TextInputEditText key, SwitchMaterial image, SwitchMaterial developer, TextInputEditText auth, TextInputEditText maxTokens, TextInputEditText reasoning) {
        try {
            String display = value(name), url = EndpointResolver.normalizeBaseUrl(value(base)), modelId = value(model); if (display.isEmpty() || modelId.isEmpty()) throw new IllegalArgumentException("名称和模型 ID 不能为空");
            String protocolCode = protocolCode(protocol.getText().toString()); ProviderProfile.Builder builder = existing == null ? ProviderProfile.builder().kind(ProviderProfile.KIND_CUSTOM) : existing.toBuilder();
            ProviderProfile profile = builder.displayName(display).protocol(protocolCode).baseUrl(url).modelId(modelId).authMode(authCode(auth.getText().toString())).useDeveloperRole(developer.isChecked()).maxTokensField(maxTokensCode(maxTokens.getText().toString())).reasoningMode(reasoningCode(reasoning.getText().toString())).supportsImage(image.isChecked()).imageInputEnabled(existing != null && existing.imageInputEnabled && image.isChecked()).testState(existing == null ? "NOT_TESTED" : existing.testState).updatedAt(System.currentTimeMillis()).build();
            host().providers().save(profile, value(key)); feedback("配置已保存；连接尚未测试"); return profile;
        } catch (Exception error) { feedback(host().safeMessage(error)); return null; }
    }

    private void renderSearchSection() {
        content.addView(sectionTitle("独立联网搜索")); content.addView(body("除内置 DeepSeek 官方搜索外，其他模型必须单独配置搜索 API，并且搜索测试返回结构化 URL 后才会启用。搜索凭据与聊天凭据分开保存；明确选择复用当前 Provider Key 时才会读取它。"));
        List<SearchProfile> searches = host().providers().searchProfiles(); for (SearchProfile search : searches) content.addView(searchCard(search));
        MaterialButton add = button("添加搜索服务", true); add.setOnClickListener(v -> renderSearchEditor(null)); content.addView(add, margin(0, 8, 0, 8));
    }

    private View searchCard(SearchProfile search) {
        MaterialCardView card = new MaterialCardView(requireContext()); card.setCardBackgroundColor(palette().surface); card.setRadius(dp(18)); card.setStrokeWidth(dp(1)); card.setStrokeColor(palette().primarySoft);
        LinearLayout box = verticalForm(); box.setPadding(dp(16), dp(14), dp(16), dp(14)); box.addView(cardTitle(search.name)); box.addView(body(searchTypeLabel(search.type) + "\n状态：" + (search.enabled ? search.testState : "未启用") + (SearchProfile.DEEPSEEK_NATIVE.equals(search.type) ? " · 复用内置 DeepSeek Key" : "")));
        SwitchMaterial enabled = new SwitchMaterial(requireContext()); enabled.setText("允许助手使用此搜索"); enabled.setChecked(search.enabled); enabled.setOnCheckedChangeListener((b, checked) -> { SearchProfile saved = search.toBuilder().enabled(checked).updatedAt(System.currentTimeMillis()).build(); host().providers().saveSearch(saved, ""); if (checked) host().providers().selectSearch(saved.id); else if (search.enabled) host().providers().selectSearch(""); }); box.addView(enabled);
        LinearLayout actions = new LinearLayout(requireContext()); actions.setOrientation(LinearLayout.HORIZONTAL); MaterialButton test = button("单独测试", false); test.setOnClickListener(v -> testSearch(search, test)); actions.addView(test, weight(1, 1)); if (!SearchProfile.DEEPSEEK_NATIVE.equals(search.type)) { MaterialButton edit = button("编辑", false); edit.setOnClickListener(v -> renderSearchEditor(search)); actions.addView(edit, weight(1, 1)); MaterialButton delete = button("删除", false); delete.setTextColor(palette().danger); delete.setOnClickListener(v -> { host().providers().deleteSearch(search.id); renderList(); }); actions.addView(delete, weight(1, 1)); } box.addView(actions, margin(0, 6, 0, 0)); card.addView(box); return cardWithMargin(card, 0, 0, 0, 10);
    }

    private void testSearch(SearchProfile search, MaterialButton button) { if (!search.enabled) { feedback("先启用此搜索服务"); return; } button.setEnabled(false); new Thread(() -> { try { JSONArrayHolder holder = new JSONArrayHolder(new SearchRepository(host().providers()).test("official medicine label")); requireActivity().runOnUiThread(() -> { button.setEnabled(true); renderList(); feedback("搜索测试通过，返回 " + holder.value.length() + " 个结构化来源"); }); } catch (Exception error) { requireActivity().runOnUiThread(() -> { button.setEnabled(true); renderList(); feedback(host().safeMessage(error)); }); } }).start(); }

    private void renderSearchEditor(SearchProfile existing) {
        editingSearch = existing; content.removeAllViews(); MaterialButton back = button("‹  返回 AI 服务", false); back.setOnClickListener(v -> renderList()); content.addView(back, margin(0, 0, 0, 4)); content.addView(title(existing == null ? "添加搜索服务" : "编辑搜索服务")); content.addView(body("只支持 OpenAI Responses 原生 web_search 和 Anthropic Messages web_search_20250305。不存在通用任意 JSON 搜索兼容。")); LinearLayout form = verticalForm(); TextInputEditText name = field(form, "显示名称", existing == null ? "" : existing.name, false); TextInputEditText type = choiceEdit(form, "搜索协议", new String[]{"OpenAI Responses 原生搜索", "Anthropic Messages 原生搜索"}, searchTypeLabel(existing == null ? SearchProfile.OPENAI_RESPONSES_NATIVE : existing.type)); TextInputEditText base = field(form, "搜索 base URL", existing == null ? "" : existing.baseUrl, false); TextInputEditText model = field(form, "搜索模型 ID", existing == null ? "" : existing.modelId, false); TextInputEditText key = field(form, "独立搜索 API Key（不会回显）", "", true); key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); TextInputEditText auth = choiceEdit(form, "认证头", new String[]{"Bearer", "x-api-key", "二者同时", "无鉴权"}, authLabel(existing == null ? ProviderProfile.AUTH_BEARER : existing.authMode)); MaterialCheckBox reuse = new MaterialCheckBox(requireContext()); reuse.setText("明确复用当前 Provider Key（仍作为独立搜索配置测试）"); reuse.setChecked(existing != null && existing.providerId != null && !existing.providerId.isEmpty()); form.addView(reuse); content.addView(form); MaterialButton save = button("保存搜索配置", true); save.setOnClickListener(v -> { try { String url = EndpointResolver.normalizeBaseUrl(value(base)), modelId = value(model), display = value(name); if (display.isEmpty() || modelId.isEmpty()) throw new IllegalArgumentException("名称和模型 ID 不能为空"); SearchProfile.Builder builder = existing == null ? SearchProfile.builder() : existing.toBuilder(); String id = existing == null ? java.util.UUID.randomUUID().toString() : existing.id; SearchProfile profile = builder.id(id).name(display).type(searchTypeCode(value(type))).providerId(reuse.isChecked() ? host().providers().current().id : "").baseUrl(url).modelId(modelId).authMode(authCode(value(auth))).enabled(true).testState("NOT_TESTED").lastErrorCode("").updatedAt(System.currentTimeMillis()).build(); host().providers().saveSearch(profile, value(key)); host().providers().selectSearch(profile.id); renderList(); feedback("搜索配置已保存；请单独测试后才会联网"); } catch (Exception error) { feedback(host().safeMessage(error)); } }); content.addView(save, margin(0, 12, 0, 0)); }

    private TextInputEditText field(LinearLayout form, String hint, String initial, boolean password) { TextInputLayout layout = new TextInputLayout(requireContext()); layout.setHint(hint); layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE); TextInputEditText edit = new TextInputEditText(requireContext()); edit.setSingleLine(true); edit.setText(initial == null ? "" : initial); if (password) edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); layout.addView(edit, new LinearLayout.LayoutParams(-1, dp(58))); form.addView(layout, margin(0, 4, 0, 2)); return edit; }
    private TextInputEditText choiceEdit(LinearLayout form, String hint, String[] values, String initial) { TextInputEditText edit = field(form, hint, initial, false); edit.setInputType(InputType.TYPE_NULL); edit.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext()).setTitle(hint).setSingleChoiceItems(values, indexOf(values, initial), (d, which) -> { edit.setText(values[which]); d.dismiss(); }).show()); return edit; }
    private MaterialAutoCompleteTextView choice(LinearLayout form, String hint, String[] values, String initial) { TextInputLayout layout = new TextInputLayout(requireContext()); layout.setHint(hint); layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE); MaterialAutoCompleteTextView edit = new MaterialAutoCompleteTextView(requireContext()); edit.setInputType(InputType.TYPE_NULL); edit.setText(initial, false); edit.setAdapter(new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, values)); layout.addView(edit, new LinearLayout.LayoutParams(-1, dp(58))); form.addView(layout, margin(0, 4, 0, 2)); return edit; }
    private TextView title(String text) { TextView view = cardTitle(text); view.setTextSize(22); view.setTextColor(palette().text); view.setPadding(0, dp(4), 0, dp(2)); return view; }
    private TextView cardTitle(String text) { TextView view = new TextView(requireContext()); view.setText(text); view.setTextColor(palette().text); view.setTextSize(17); view.setTypeface(null, android.graphics.Typeface.BOLD); return view; }
    private TextView sectionTitle(String text) { TextView view = cardTitle(text); view.setTextSize(15); view.setPadding(0, dp(6), 0, dp(4)); return view; }
    private TextView body(String text) { TextView view = new TextView(requireContext()); view.setText(text); view.setTextColor(palette().secondary); view.setTextSize(13); view.setLineSpacing(dp(2), 1f); return view; }
    private MaterialButton button(String text, boolean primary) {
        MaterialButton view = new MaterialButton(requireContext());
        view.setText(text); view.setAllCaps(false); view.setMinHeight(dp(48));
        view.setBackgroundTintList(ColorStateList.valueOf(primary ? palette().primary : palette().surface));
        view.setTextColor(primary ? palette().white : palette().primary);
        view.setStrokeWidth(primary ? 0 : dp(1));
        view.setStrokeColor(ColorStateList.valueOf(palette().border));
        UiKit.press(view); return view;
    }
    @Override protected LinearLayout verticalForm() { LinearLayout form = new LinearLayout(requireContext()); form.setOrientation(LinearLayout.VERTICAL); return form; }
    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p; }
    private LinearLayout.LayoutParams weight(int weight, int total) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, weight); p.setMargins(dp(2), 0, dp(2), 0); return p; }
    private View cardWithMargin(View view, int l, int t, int r, int b) { LinearLayout wrapper = new LinearLayout(requireContext()); wrapper.setOrientation(LinearLayout.VERTICAL); wrapper.addView(view, margin(l, t, r, b)); return wrapper; }
    @Override protected int dp(int value) { return Math.round(value * requireContext().getResources().getDisplayMetrics().density); }
    private static String value(android.widget.TextView view) { return view.getText() == null ? "" : view.getText().toString().trim(); }
    private static int indexOf(String[] values, String value) { for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i; return 0; }
    private static String protocolLabel(String value) { return ProviderProfile.OPENAI_RESPONSES.equals(value) ? "OpenAI Responses" : ProviderProfile.ANTHROPIC_MESSAGES.equals(value) ? "Anthropic Messages" : "OpenAI Chat Completions"; }
    private static String protocolCode(String value) { return value.startsWith("OpenAI Responses") ? ProviderProfile.OPENAI_RESPONSES : value.startsWith("Anthropic") ? ProviderProfile.ANTHROPIC_MESSAGES : ProviderProfile.OPENAI_CHAT_COMPLETIONS; }
    private static String authLabel(String value) { return ProviderProfile.AUTH_X_API_KEY.equals(value) ? "x-api-key" : ProviderProfile.AUTH_BOTH.equals(value) ? "二者同时" : ProviderProfile.AUTH_NONE.equals(value) ? "无鉴权" : "Bearer"; }
    private static String authCode(String value) { return value.contains("x-api-key") ? (value.contains("同时") ? ProviderProfile.AUTH_BOTH : ProviderProfile.AUTH_X_API_KEY) : value.contains("无鉴权") ? ProviderProfile.AUTH_NONE : ProviderProfile.AUTH_BEARER; }
    private static String maxTokensLabel(String value) { return ProviderProfile.MAX_COMPLETION_TOKENS.equals(value) ? "max_completion_tokens" : "max_tokens"; }
    private static String maxTokensCode(String value) { return value.contains("completion") ? ProviderProfile.MAX_COMPLETION_TOKENS : ProviderProfile.MAX_TOKENS; }
    private static String reasoningLabel(String value) { return ProviderProfile.REASONING_STANDARD.equals(value) ? "reasoning_effort" : ProviderProfile.REASONING_DEEPSEEK.equals(value) ? "DeepSeek thinking" : "不发送"; }
    private static String reasoningCode(String value) { return value.contains("reasoning") ? ProviderProfile.REASONING_STANDARD : value.contains("DeepSeek") ? ProviderProfile.REASONING_DEEPSEEK : ProviderProfile.REASONING_NONE; }
    private static String searchTypeLabel(String value) { return SearchProfile.ANTHROPIC_NATIVE.equals(value) ? "Anthropic Messages 原生搜索" : SearchProfile.OPENAI_RESPONSES_NATIVE.equals(value) ? "OpenAI Responses 原生搜索" : "DeepSeek 官方搜索"; }
    private static String searchTypeCode(String value) { return value.startsWith("Anthropic") ? SearchProfile.ANTHROPIC_NATIVE : SearchProfile.OPENAI_RESPONSES_NATIVE; }
    private static String capability(boolean enabled, String label) { return enabled ? label : label + "未通过"; }
    private static String searchTypeLabelOrCode(String value) { return searchTypeLabel(value); }
    private static final class JSONArrayHolder { final org.json.JSONArray value; JSONArrayHolder(org.json.JSONArray value) { this.value = value; } }
}
