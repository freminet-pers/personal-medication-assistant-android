package com.lunamax.medassistant;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lunamax.medassistant.databinding.ItemOccurrenceBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class OccurrenceAdapter extends RecyclerView.Adapter<OccurrenceAdapter.Holder> {
    interface Listener { void action(LunaDatabase.OccurrenceRow row, String operation); }
    private final Listener listener;
    private final List<LunaDatabase.OccurrenceRow> rows = new ArrayList<>();

    OccurrenceAdapter(Listener listener) { this.listener = listener; }
    void submit(List<LunaDatabase.OccurrenceRow> value) { rows.clear(); if (value != null) rows.addAll(value); notifyDataSetChanged(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemOccurrenceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        LunaDatabase.OccurrenceRow row = rows.get(position);
        holder.binding.occurrenceTime.setText(new SimpleDateFormat("HH:mm", Locale.US).format(new Date(row.scheduledAtMs)));
        holder.binding.occurrenceMedication.setText(row.medicationName);
        holder.binding.occurrenceDetail.setText(join(row.dose, row.meal));
        holder.binding.occurrenceStatus.setText(BaseFragment.statusText(row.status));
        holder.binding.occurrenceStatus.setTextColor("TAKEN".equals(row.status) ? 0xff0f665d : "MISSED".equals(row.status) ? 0xffa33c3c : 0xff8a5200);
        boolean done = "TAKEN".equals(row.status) || "SKIPPED".equals(row.status);
        holder.binding.occurrenceTaken.setVisibility(done ? android.view.View.GONE : android.view.View.VISIBLE);
        holder.binding.occurrenceSkip.setVisibility(done ? android.view.View.GONE : android.view.View.VISIBLE);
        holder.binding.occurrenceSnooze.setVisibility(done ? android.view.View.GONE : android.view.View.VISIBLE);
        holder.binding.occurrenceUndo.setVisibility("TAKEN".equals(row.status) ? android.view.View.VISIBLE : android.view.View.GONE);
        holder.binding.occurrenceTaken.setOnClickListener(v -> listener.action(row, ReminderScheduler.OP_TAKEN));
        holder.binding.occurrenceSkip.setOnClickListener(v -> listener.action(row, ReminderScheduler.OP_SKIP));
        holder.binding.occurrenceSnooze.setOnClickListener(v -> listener.action(row, ReminderScheduler.OP_SNOOZE));
        holder.binding.occurrenceUndo.setOnClickListener(v -> listener.action(row, ReminderScheduler.OP_UNDO));
        UiKit.enter(holder.itemView.getContext(), holder.itemView, position);
    }

    @Override public int getItemCount() { return rows.size(); }
    static final class Holder extends RecyclerView.ViewHolder { final ItemOccurrenceBinding binding; Holder(ItemOccurrenceBinding binding) { super(binding.getRoot()); this.binding = binding; } }
    private static String join(String a, String b) { return a == null || a.isEmpty() ? (b == null ? "" : b) : a + (b == null || b.isEmpty() ? "" : " · " + b); }
}
