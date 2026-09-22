package com.lunamax.medassistant;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

/** Shared fragment accessors and restrained local-first UI helpers. */
abstract class BaseFragment extends Fragment {
    protected MainActivity host() { return (MainActivity) requireActivity(); }
    protected LunaDatabase database() { return host().database(); }
    protected ReminderScheduler scheduler() { return host().scheduler(); }
    protected AssistantRepository assistant() { return host().assistant(); }
    protected VisionRepository vision() { return host().vision(); }
    protected Palette palette() { return host().palette(); }
    protected void feedback(String message) { host().feedback(message); }
    protected int dp(int value) { return Math.round(value * requireContext().getResources().getDisplayMetrics().density); }

    protected MaterialAlertDialogBuilder dialog(String title) {
        return new MaterialAlertDialogBuilder(requireContext()).setTitle(title);
    }

    protected TextInputEditText textField(String hint, boolean multiLine) {
        TextInputEditText field = new TextInputEditText(requireContext());
        field.setHint(hint);
        field.setSingleLine(!multiLine);
        if (multiLine) { field.setMinLines(3); field.setGravity(android.view.Gravity.TOP); }
        field.setTextSize(15);
        return field;
    }

    protected LinearLayout verticalForm() {
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), dp(4), dp(8), dp(4));
        return form;
    }

    protected static String textOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    protected static String statusText(String status) {
        if ("TAKEN".equals(status)) return "已服用";
        if ("SKIPPED".equals(status)) return "已跳过";
        if ("MISSED".equals(status)) return "漏服";
        if ("SNOOZED".equals(status)) return "稍后";
        return "待确认";
    }

    protected static String medicationStatus(String status) {
        return "ACTIVE".equals(status) ? "正在服用" : "ARCHIVED".equals(status) ? "已停用" : "未分类";
    }

    protected static String documentStatus(String status) {
        if ("CONFIRMED".equals(status)) return "已确认";
        if ("FAILED".equals(status)) return "识别失败";
        if ("WAITING_KEY".equals(status)) return "等待识别";
        if ("PROCESSING".equals(status)) return "正在识别";
        if ("DRAFT".equals(status)) return "待确认";
        return "旧版待整理";
    }

    protected static String healthKind(String kind) {
        if ("CONDITION".equals(kind)) return "疾病或长期情况";
        if ("ALLERGY".equals(kind)) return "过敏记录";
        if ("SURGERY".equals(kind)) return "手术史";
        if ("ADVERSE".equals(kind)) return "不良反应";
        if ("ORGAN".equals(kind)) return "器官或生理情况";
        if ("PREGNANCY".equals(kind)) return "孕产信息";
        if ("MED_HISTORY".equals(kind)) return "既往用药史";
        return "资料备注";
    }
}
