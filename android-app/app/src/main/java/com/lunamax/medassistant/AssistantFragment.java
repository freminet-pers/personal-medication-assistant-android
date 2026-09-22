package com.lunamax.medassistant;

import android.os.Bundle;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lunamax.medassistant.databinding.FragmentAssistantBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public final class AssistantFragment extends BaseFragment {
    private FragmentAssistantBinding binding;
    private AssistantMessageAdapter historyAdapter;
    private long sessionId;
    private boolean requestInFlight;

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container, @Nullable Bundle state) { binding=FragmentAssistantBinding.inflate(inflater,container,false);if(state!=null)sessionId=state.getLong("sessionId",0);return binding.getRoot(); }
    @Override public void onSaveInstanceState(@NonNull Bundle outState){outState.putLong("sessionId",sessionId);super.onSaveInstanceState(outState);}
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){
        binding.assistantEffort.setAdapter(new android.widget.ArrayAdapter<>(requireContext(),android.R.layout.simple_list_item_1,new String[]{"快速","深入","最大"})); binding.assistantEffort.setText("快速",false);
        historyAdapter=new AssistantMessageAdapter();binding.assistantHistory.setLayoutManager(new LinearLayoutManager(requireContext()));binding.assistantHistory.setAdapter(historyAdapter);
        binding.assistantManageKey.setOnClickListener(v->manageKey());binding.assistantTestKey.setOnClickListener(v->testConnection());binding.assistantSend.setOnClickListener(v->send());refresh();
    }
    @Override public void onResume(){super.onResume();if(binding!=null)refresh();}
    private void refresh(){boolean has=assistant().hasKey();binding.assistantKeyStatus.setText("Key 状态："+assistant().keyStatus());binding.assistantKeyStatus.setTextColor(has?palette().primary:palette().warning);binding.assistantConnectionStatus.setText(assistant().lastConnectionStatus());binding.assistantManageKey.setText(has?"更换或删除 Key":"输入运行时 Key");binding.assistantPreview.setText("发送前会显示："+personalPreview());if(sessionId>0)historyAdapter.submit(database().assistantMessages(sessionId));}
    private void manageKey(){
        android.widget.LinearLayout form=verticalForm();form.addView(infoLabel("Key 只在本机安全存储中加密保存；保存动作不联网。"));com.google.android.material.textfield.TextInputLayout layout=new com.google.android.material.textfield.TextInputLayout(requireContext());layout.setHint("输入 DeepSeek API Key");layout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);com.google.android.material.textfield.TextInputEditText key=new com.google.android.material.textfield.TextInputEditText(requireContext());key.setSingleLine(true);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);layout.addView(key,new android.widget.LinearLayout.LayoutParams(-1,dp(58)));form.addView(layout);ScrollView scroll=new ScrollView(requireContext());scroll.addView(form);MaterialAlertDialogBuilder builder=new MaterialAlertDialogBuilder(requireContext()).setTitle("管理运行时 Key").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null);if(assistant().hasKey())builder.setNeutralButton("删除 Key",(d,w)->{assistant().deleteKey();refresh();feedback("本地 Key 已删除，在线功能已降级");});AlertDialog dialog=builder.create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{try{assistant().saveKey(value(key));dialog.dismiss();refresh();feedback("Key 已安全保存；还未联网测试");}catch(Exception error){key.setError(host().safeMessage(error));}}));dialog.show();
    }
    private void testConnection(){if(!assistant().hasKey()){manageKey();return;}if(requestInFlight)return;requestInFlight=true;binding.assistantTestKey.setEnabled(false);binding.assistantStatus.setText("正在测试连接；不会发送个人资料或图片。");long generation=assistant().currentGeneration();assistant().testConnection(new AssistantRepository.ConnectionCallback(){public void success(String message){requireActivity().runOnUiThread(()->{if(!assistant().isCurrent(generation))return;requestInFlight=false;binding.assistantTestKey.setEnabled(true);refresh();binding.assistantStatus.setText(message);});}public void failure(String message){requireActivity().runOnUiThread(()->{if(!assistant().isCurrent(generation))return;requestInFlight=false;binding.assistantTestKey.setEnabled(true);refresh();binding.assistantStatus.setText(message);});}});}
    private void send(){String question=value(binding.assistantQuestion);if(question.isEmpty()){binding.assistantQuestionLayout.setError("请先填写当前问题");return;}binding.assistantQuestionLayout.setError(null);if(!assistant().hasKey()){manageKey();return;}String preview=personalPreview();new MaterialAlertDialogBuilder(requireContext()).setTitle("发送前确认").setMessage("本次将使用：\n"+preview+"\n\n不会发送：API Key、完整本地文档和无关健康资料。确认后才会调用模型。").setNegativeButton("取消",null).setPositiveButton("确认发送",(d,w)->performAsk(question)).show();}
    private void performAsk(String question){if(requestInFlight)return;requestInFlight=true;binding.assistantSend.setEnabled(false);binding.assistantStatus.setText("正在请求模型；失败时可以重试。");String effort=effortCode(binding.assistantEffort.getText().toString());long generation=assistant().currentGeneration();assistant().answer(question,personalPreview(),effort,binding.assistantWebSearch.isChecked(),new AssistantRepository.Callback(){public void success(AssistantRepository.Response response){requireActivity().runOnUiThread(()->{if(!assistant().isCurrent(generation))return;requestInFlight=false;binding.assistantSend.setEnabled(true);if(sessionId==0)sessionId=database().createAssistantSession(question.length()>24?question.substring(0,24)+"…":question);try{database().addAssistantMessage(sessionId,"user",question,"");database().addAssistantMessage(sessionId,"assistant",response.answer,response.sources.toString());}catch(Exception ignored){}renderResult(response);historyAdapter.submit(database().assistantMessages(sessionId));binding.assistantStatus.setText("回答已返回。请结合来源和不确定性阅读，不要把回答当作诊断。");});}public void failure(String message){requireActivity().runOnUiThread(()->{if(!assistant().isCurrent(generation))return;requestInFlight=false;binding.assistantSend.setEnabled(true);binding.assistantStatus.setText(message);});}});}
    private void renderResult(AssistantRepository.Response response){StringBuilder text=new StringBuilder(response.answer);text.append("\n\n证据：").append(response.evidence).append("\n不确定性：").append(response.uncertainty).append("\n本次使用的本地资料：").append(response.personalDataUsed);if(response.sources.length()>0){text.append("\n\n来源：");for(int i=0;i<response.sources.length();i++){JSONObject source=response.sources.optJSONObject(i);if(source!=null)text.append("\n").append(source.optString("title")).append("\n").append(source.optString("url"));}}binding.assistantResult.setText(text.toString());binding.assistantResult.setAutoLinkMask(Linkify.WEB_URLS);binding.assistantResult.setMovementMethod(LinkMovementMethod.getInstance());}
    private String personalPreview(){StringBuilder result=new StringBuilder("当前有效药物：");List<LunaDatabase.MedicationRow> meds=database().medications(false);for(int i=0;i<meds.size()&&i<8;i++){if(i>0)result.append("、");result.append(meds.get(i).displayName());}String allergies=database().allergiesSummary();result.append("\n过敏记录：").append(allergies.isEmpty()?"无已确认记录":allergies).append("\n其他健康资料：仅在明确选择并确认后发送");return result.toString();}
    private static String value(android.widget.EditText field){return field.getText()==null?"":field.getText().toString().trim();}
    private static String effortCode(String value){return "深入".equals(value)?"high":"最大".equals(value)?"max":"off";}
    private android.widget.TextView infoLabel(String value){android.widget.TextView text=new android.widget.TextView(requireContext());text.setText(value);text.setTextColor(palette().secondary);text.setTextSize(14);return text;}
}
