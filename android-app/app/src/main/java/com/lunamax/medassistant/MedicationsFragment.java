package com.lunamax.medassistant;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.lunamax.medassistant.databinding.FragmentMedicationsBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MedicationsFragment extends BaseFragment {
    private FragmentMedicationsBinding binding;
    private MedicationAdapter adapter;
    private int filter = 0;

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container, @Nullable Bundle state) {
        binding = FragmentMedicationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        adapter = new MedicationAdapter(database(), this::onMedicationAction);
        binding.medicationsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.medicationsList.setAdapter(adapter);
        binding.addMedication.setOnClickListener(v -> host().showMedicationEditor(null));
        binding.medicationSearch.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){refresh();} public void afterTextChanged(Editable e){} });
        binding.medicationFilters.setOnCheckedStateChangeListener((group, checkedIds) -> { if (checkedIds.isEmpty()) return; int id = checkedIds.get(0); filter = id == com.lunamax.medassistant.R.id.filter_all ? 1 : id == R.id.filter_archived ? 2 : id == R.id.filter_stock ? 3 : 0; refresh(); });
        refresh();
    }

    @Override public void onResume() { super.onResume(); if (binding != null) refresh(); }

    private void refresh() {
        String query = binding.medicationSearch == null || binding.medicationSearch.getText() == null ? "" : binding.medicationSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<LunaDatabase.MedicationRow> all = database().medications(true);
        List<LunaDatabase.MedicationRow> result = new ArrayList<>();
        for (LunaDatabase.MedicationRow row : all) {
            if (filter == 0 && !"ACTIVE".equals(row.status)) continue;
            if (filter == 2 && !"ARCHIVED".equals(row.status)) continue;
            if (filter == 3 && !hasStock(row.id)) continue;
            String haystack = (row.displayName() + " " + row.genericName + " " + row.strength).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            result.add(row);
        }
        binding.medicationsEmpty.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
        binding.medicationsList.setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.submit(result);
    }

    private boolean hasStock(long medicationId) {
        for (LunaDatabase.BatchRow row : database().batches(medicationId)) if (row.quantity > 0 && !"EXPIRED".equals(row.state) && !"DISCARDED".equals(row.state)) return true;
        return false;
    }

    private void onMedicationAction(LunaDatabase.MedicationRow row, String action) {
        if ("edit".equals(action)) host().showMedicationEditor(row);
        else if ("batches".equals(action)) host().showBatchManager(row);
        else if ("plans".equals(action)) host().showPlanManager(row);
        else host().showMedicationMenu(row);
    }
}
