package com.lunamax.medassistant;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.lunamax.medassistant.databinding.FragmentTodayBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class TodayFragment extends BaseFragment {
    private FragmentTodayBinding binding;
    private OccurrenceAdapter adapter;

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container, @Nullable Bundle state) {
        binding = FragmentTodayBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        adapter = new OccurrenceAdapter(this::applyAction);
        binding.todayList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.todayList.setAdapter(adapter);
        binding.todayEmpty.setOnClickListener(v -> host().showTab(MainActivity.TAB_MEDICATIONS));
        refresh();
    }

    @Override public void onResume() { super.onResume(); if (binding != null) refresh(); }

    private void refresh() {
        scheduler().rebuild();
        List<LunaDatabase.OccurrenceRow> rows = database().todayOccurrences(System.currentTimeMillis());
        int pending = 0, completed = 0, missed = 0;
        for (LunaDatabase.OccurrenceRow row : rows) {
            if ("PENDING".equals(row.status) || "SNOOZED".equals(row.status)) pending++;
            if ("TAKEN".equals(row.status)) completed++;
            if ("MISSED".equals(row.status)) missed++;
        }
        binding.todayDate.setText(new SimpleDateFormat("M月d日 E", Locale.CHINA).format(new Date()));
        binding.todaySummary.setText(rows.isEmpty() ? "还没有今天的计划" : pending + " 项待确认 · " + completed + " 项已完成" + (missed > 0 ? " · " + missed + " 项漏服" : ""));
        binding.todayProgress.setProgress(rows.isEmpty() ? 0 : Math.round(completed * 100f / rows.size()));
        binding.todayEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        binding.todayList.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.submit(rows);
    }

    private void applyAction(LunaDatabase.OccurrenceRow row, String operation) {
        database().applyOccurrenceAction(row.id, operation, System.currentTimeMillis(), ReminderScheduler.OP_SNOOZE.equals(operation) ? 15 * 60 * 1000L : 0, "今日页操作");
        scheduler().rebuild();
        refresh();
        if (ReminderScheduler.OP_TAKEN.equals(operation)) {
            Snackbar.make(binding.getRoot(), "已记录这次服用", Snackbar.LENGTH_LONG).setAction("撤销", v -> {
                database().applyOccurrenceAction(row.id, ReminderScheduler.OP_UNDO, System.currentTimeMillis(), 0, "用户撤销");
                scheduler().rebuild(); refresh();
            }).show();
        } else feedback(ReminderScheduler.OP_SNOOZE.equals(operation) ? "已延后 15 分钟" : ReminderScheduler.OP_SKIP.equals(operation) ? "已跳过这次计划" : "状态已更新");
    }
}
