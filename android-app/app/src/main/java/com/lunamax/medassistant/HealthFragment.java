package com.lunamax.medassistant;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lunamax.medassistant.databinding.FragmentHealthBinding;

public final class HealthFragment extends BaseFragment {
    private FragmentHealthBinding binding;
    private HealthAdapter adapter;

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container, @Nullable Bundle state) {
        binding = FragmentHealthBinding.inflate(inflater, container, false); return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        adapter = new HealthAdapter(this::onAction);
        binding.healthList.setLayoutManager(new LinearLayoutManager(requireContext())); binding.healthList.setAdapter(adapter);
        binding.addHealth.setOnClickListener(v -> editHealth(null)); binding.exportHealth.setOnClickListener(v -> host().launchExportBackup()); refresh();
    }
    @Override public void onResume() { super.onResume(); if (binding != null) refresh(); }
    private void refresh() { java.util.List<LunaDatabase.HealthRow> rows = database().healthItems(); binding.healthEmpty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE); binding.healthList.setVisibility(rows.isEmpty()?View.GONE:View.VISIBLE); adapter.submit(rows); }
    private void onAction(LunaDatabase.HealthRow row, String action) { if ("edit".equals(action)) editHealth(row); else new MaterialAlertDialogBuilder(requireContext()).setTitle("删除这条健康记录？").setMessage("删除后不会再进入助手资料预览，不能撤销。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{database().deleteHealth(row.id);refresh();feedback("健康记录已删除");}).show(); }

    private void editHealth(LunaDatabase.HealthRow existing) {
        android.widget.LinearLayout form = verticalForm();
        String[] kinds = {"疾病或长期情况","过敏记录","手术史","不良反应","器官或生理情况","孕产信息","既往用药史","资料备注"};
        android.widget.AutoCompleteTextView kind = addChoice(form, "记录类型", kinds, displayHealthKind(existing == null ? "CONDITION" : existing.kind));
        com.google.android.material.textfield.TextInputEditText title = addField(form,"标题，例如青霉素过敏",existing==null?"":existing.title,false);
        com.google.android.material.textfield.TextInputEditText detail = addField(form,"详细说明",existing==null?"":existing.detail,true);
        com.google.android.material.textfield.TextInputEditText source = addField(form,"来源或核对方式",existing==null?"用户输入":existing.source,false);
        CheckBox confirmed = new CheckBox(requireContext()); confirmed.setText("我已核对这条资料，可以进入助手上下文"); confirmed.setChecked(existing != null && existing.confirmed); form.addView(confirmed);
        ScrollView scroll=new ScrollView(requireContext());scroll.addView(form);
        AlertDialog dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle(existing==null?"新增健康记录":"编辑健康记录").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{if(value(title).isEmpty()){title.setError("请填写标题");return;}LunaDatabase.HealthDraft draft=new LunaDatabase.HealthDraft();draft.kind=healthKindCode(kind.getText().toString());draft.title=value(title);draft.detail=value(detail);draft.source=value(source);draft.confirmed=confirmed.isChecked();if(existing==null)database().saveHealth(draft);else database().updateHealth(existing.id,draft);dialog.dismiss();refresh();feedback("健康记录已保存");}));dialog.show();
    }

    private String displayHealthKind(String value){return BaseFragment.healthKind(value);}
    private String healthKindCode(String value){if("疾病或长期情况".equals(value))return "CONDITION";if("过敏记录".equals(value))return "ALLERGY";if("手术史".equals(value))return "SURGERY";if("不良反应".equals(value))return "ADVERSE";if("器官或生理情况".equals(value))return "ORGAN";if("孕产信息".equals(value))return "PREGNANCY";if("既往用药史".equals(value))return "MED_HISTORY";return "NOTE";}
    private com.google.android.material.textfield.TextInputEditText addField(android.widget.LinearLayout form,String hint,String initial,boolean multi){com.google.android.material.textfield.TextInputLayout layout=new com.google.android.material.textfield.TextInputLayout(requireContext());layout.setHint(hint);layout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);com.google.android.material.textfield.TextInputEditText edit=new com.google.android.material.textfield.TextInputEditText(requireContext());edit.setSingleLine(!multi);if(multi){edit.setMinLines(3);edit.setGravity(android.view.Gravity.TOP);}edit.setText(initial);layout.addView(edit,new android.widget.LinearLayout.LayoutParams(-1,multi?dp(88):dp(58)));form.addView(layout);return edit;}
    private android.widget.AutoCompleteTextView addChoice(android.widget.LinearLayout form,String hint,String[] values,String initial){com.google.android.material.textfield.TextInputLayout layout=new com.google.android.material.textfield.TextInputLayout(requireContext());layout.setHint(hint);layout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);android.widget.AutoCompleteTextView field=new android.widget.AutoCompleteTextView(requireContext());field.setInputType(android.text.InputType.TYPE_NULL);field.setText(initial,false);field.setAdapter(new android.widget.ArrayAdapter<>(requireContext(),android.R.layout.simple_list_item_1,values));layout.addView(field,new android.widget.LinearLayout.LayoutParams(-1,dp(58)));form.addView(layout);return field;}
    private static String value(android.widget.EditText edit){return edit.getText()==null?"":edit.getText().toString().trim();}
}
