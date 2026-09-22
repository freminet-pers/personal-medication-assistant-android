package com.lunamax.medassistant;

import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lunamax.medassistant.databinding.ItemAssistantMessageBinding;

import java.util.ArrayList;
import java.util.List;

final class AssistantMessageAdapter extends RecyclerView.Adapter<AssistantMessageAdapter.Holder> {
    private final List<LunaDatabase.AssistantMessageRow> rows = new ArrayList<>();
    void submit(List<LunaDatabase.AssistantMessageRow> value) { rows.clear(); if (value != null) rows.addAll(value); notifyDataSetChanged(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { return new Holder(ItemAssistantMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false)); }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        LunaDatabase.AssistantMessageRow row = rows.get(position);
        holder.binding.messageRole.setText("assistant".equals(row.role) ? "助手回答" : "我的问题");
        holder.binding.messageContent.setText(row.content);
        holder.binding.messageContent.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
        holder.binding.messageContent.setMovementMethod(LinkMovementMethod.getInstance());
    }
    @Override public int getItemCount() { return rows.size(); }
    static final class Holder extends RecyclerView.ViewHolder { final ItemAssistantMessageBinding binding; Holder(ItemAssistantMessageBinding binding) { super(binding.getRoot()); this.binding = binding; } }
}
