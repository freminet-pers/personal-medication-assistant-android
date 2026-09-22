package com.lunamax.medassistant;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lunamax.medassistant.databinding.ItemHealthBinding;

import java.util.ArrayList;
import java.util.List;

final class HealthAdapter extends RecyclerView.Adapter<HealthAdapter.Holder> {
    interface Listener { void action(LunaDatabase.HealthRow row, String action); }
    private final Listener listener;
    private final List<LunaDatabase.HealthRow> rows = new ArrayList<>();
    HealthAdapter(Listener listener) { this.listener = listener; }
    void submit(List<LunaDatabase.HealthRow> value) { rows.clear(); if (value != null) rows.addAll(value); notifyDataSetChanged(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { return new Holder(ItemHealthBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false)); }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        LunaDatabase.HealthRow row = rows.get(position);
        holder.binding.healthStatus.setText((row.confirmed ? "已确认" : "待确认") + " · " + BaseFragment.healthKind(row.kind));
        holder.binding.healthStatus.setTextColor(row.confirmed ? 0xff0f665d : 0xff8a5200);
        holder.binding.healthTitle.setText(BaseFragment.textOr(row.title, "未命名记录"));
        holder.binding.healthDetail.setText(BaseFragment.textOr(row.detail, "没有补充说明") + (row.source.isEmpty() ? "" : "\n来源：" + row.source));
        holder.binding.healthEdit.setOnClickListener(v -> listener.action(row, "edit"));
        holder.binding.healthDelete.setOnClickListener(v -> listener.action(row, "delete"));
        UiKit.enter(holder.itemView.getContext(), holder.itemView, position);
    }
    @Override public int getItemCount() { return rows.size(); }
    static final class Holder extends RecyclerView.ViewHolder { final ItemHealthBinding binding; Holder(ItemHealthBinding binding) { super(binding.getRoot()); this.binding = binding; } }
}
