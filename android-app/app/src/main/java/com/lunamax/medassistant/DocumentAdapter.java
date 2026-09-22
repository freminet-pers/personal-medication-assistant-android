package com.lunamax.medassistant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lunamax.medassistant.databinding.ItemDocumentBinding;

import java.util.ArrayList;
import java.util.List;

final class DocumentAdapter extends RecyclerView.Adapter<DocumentAdapter.Holder> {
    interface Listener { void action(LunaDatabase.DocumentRow row, String action); }
    private final Listener listener;
    private final List<LunaDatabase.DocumentRow> rows = new ArrayList<>();
    DocumentAdapter(Listener listener) { this.listener = listener; }
    void submit(List<LunaDatabase.DocumentRow> value) { rows.clear(); if (value != null) rows.addAll(value); notifyDataSetChanged(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemDocumentBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        LunaDatabase.DocumentRow row = rows.get(position);
        holder.binding.documentStatus.setText(documentStatus(row.recognitionStatus) + " · 第 " + row.pageNo + " 页 · " + textOr(row.model, "本地历史"));
        String title = row.chapter.isEmpty() ? (row.version.isEmpty() ? "本地资料" : row.version) : row.chapter;
        holder.binding.documentTitle.setText(title);
        String excerpt = row.correctedText.isEmpty() ? row.ocrText : row.correctedText;
        holder.binding.documentExcerpt.setText(excerpt.length() > 220 ? excerpt.substring(0, 220) + "…" : textOr(excerpt, "等待识别结果"));
        if (row.failureReason.isEmpty()) holder.binding.documentFailure.setVisibility(View.GONE);
        else { holder.binding.documentFailure.setVisibility(View.VISIBLE); holder.binding.documentFailure.setText("原因：" + row.failureReason); }
        boolean retry = !"CONFIRMED".equals(row.recognitionStatus);
        holder.binding.documentRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
        holder.binding.documentDetails.setVisibility(row.structuredJson.isEmpty() ? View.GONE : View.VISIBLE);
        holder.binding.documentRetry.setOnClickListener(v -> listener.action(row, "retry"));
        holder.binding.documentDetails.setOnClickListener(v -> listener.action(row, "details"));
        holder.binding.documentDelete.setOnClickListener(v -> listener.action(row, "delete"));
        UiKit.enter(holder.itemView.getContext(), holder.itemView, position);
    }
    @Override public int getItemCount() { return rows.size(); }
    static final class Holder extends RecyclerView.ViewHolder { final ItemDocumentBinding binding; Holder(ItemDocumentBinding binding) { super(binding.getRoot()); this.binding = binding; } }
    private static String documentStatus(String value) { return BaseFragment.documentStatus(value); }
    private static String textOr(String value, String fallback) { return BaseFragment.textOr(value, fallback); }
}
