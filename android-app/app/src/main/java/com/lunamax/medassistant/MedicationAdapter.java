package com.lunamax.medassistant;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lunamax.medassistant.databinding.ItemMedicationBinding;

import java.util.ArrayList;
import java.util.List;

final class MedicationAdapter extends RecyclerView.Adapter<MedicationAdapter.Holder> {
    interface Listener { void action(LunaDatabase.MedicationRow row, String action); }
    private final LunaDatabase database;
    private final Listener listener;
    private final List<LunaDatabase.MedicationRow> rows = new ArrayList<>();

    MedicationAdapter(LunaDatabase database, Listener listener) { this.database = database; this.listener = listener; }
    void submit(List<LunaDatabase.MedicationRow> value) { rows.clear(); if (value != null) rows.addAll(value); notifyDataSetChanged(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemMedicationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        LunaDatabase.MedicationRow row = rows.get(position);
        holder.binding.medicationName.setText(row.displayName());
        holder.binding.medicationMeta.setText(join(row.genericName, row.strength, row.dosageForm));
        List<LunaDatabase.BatchRow> batches = database.batches(row.id);
        double total = 0; String unit = ""; String expiry = "";
        for (LunaDatabase.BatchRow batch : batches) {
            if (!"EXPIRED".equals(batch.state) && !"DISCARDED".equals(batch.state)) { total += batch.quantity; if (unit.isEmpty()) unit = batch.quantityUnit; }
            if (!batch.expiryDate.isEmpty() && (expiry.isEmpty() || batch.expiryDate.compareTo(expiry) < 0)) expiry = batch.expiryDate;
        }
        holder.binding.medicationStock.setText(batches.isEmpty() ? "暂无实际批次" : "库存 " + format(total) + unit + (expiry.isEmpty() ? "" : " · 到期 " + expiry));
        holder.binding.medicationStatus.setText(BaseFragment.medicationStatus(row.status));
        holder.binding.medicationStatus.setTextColor("ACTIVE".equals(row.status) ? 0xff0f665d : 0xff8a5200);
        holder.binding.medicationEdit.setOnClickListener(v -> listener.action(row, "edit"));
        holder.binding.medicationBatches.setOnClickListener(v -> listener.action(row, "batches"));
        holder.binding.medicationPlans.setOnClickListener(v -> listener.action(row, "plans"));
        holder.binding.medicationMore.setOnClickListener(v -> listener.action(row, "more"));
        UiKit.enter(holder.itemView.getContext(), holder.itemView, position);
    }

    @Override public int getItemCount() { return rows.size(); }
    static final class Holder extends RecyclerView.ViewHolder { final ItemMedicationBinding binding; Holder(ItemMedicationBinding binding) { super(binding.getRoot()); this.binding = binding; } }
    private static String join(String... values) { StringBuilder result = new StringBuilder(); for (String value : values) if (value != null && !value.isEmpty()) { if (result.length() > 0) result.append(" · "); result.append(value); } return result.length() == 0 ? "未填写规格" : result.toString(); }
    private static String format(double value) { return value == Math.rint(value) ? Integer.toString((int) value) : String.format(java.util.Locale.US, "%.1f", value); }
}
