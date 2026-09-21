package com.lunamax.medassistant;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic checks only. It intentionally does not claim to be a clinical interaction database. */
final class SafetyEngine {
    static final class Finding {
        final String kind, severity, level, message, source, scope;
        Finding(String kind, String severity, String level, String message, String source, String scope) {
            this.kind = kind; this.severity = severity; this.level = level; this.message = message; this.source = source; this.scope = scope;
        }
    }

    private SafetyEngine() { }

    static List<Finding> inspect(List<LunaDatabase.MedicationRow> medications,
                                 List<LunaDatabase.BatchRow> batches,
                                 List<LunaDatabase.PlanRow> plans,
                                 String allergies,
                                 long nowMs) {
        List<Finding> findings = new ArrayList<>();
        Map<String, String> ingredientOwners = new HashMap<>();
        for (LunaDatabase.MedicationRow medication : medications) {
            for (String ingredient : tokens(medication.ingredients)) {
                String previous = ingredientOwners.putIfAbsent(ingredient, medication.displayName());
                if (previous != null && !previous.equals(medication.displayName())) {
                    findings.add(new Finding("DUPLICATE_INGREDIENT", "HIGH", "C", "可能重复有效成分：" + ingredient,
                            "本机已确认药物成分字段", medication.displayName() + " / " + previous));
                }
                if (!allergies.isEmpty() && allergies.toLowerCase(Locale.ROOT).contains(ingredient)) {
                    findings.add(new Finding("ALLERGY_CONFLICT", "HIGH", "C", "过敏记录可能与有效成分冲突：" + ingredient,
                            "本机健康档案 + 本机药物成分", medication.displayName()));
                }
            }
        }
        LocalDate today = LocalDate.now();
        DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (LunaDatabase.BatchRow batch : batches) {
            if (batch.expiryDate != null && !batch.expiryDate.isEmpty()) {
                try {
                    LocalDate expiry = LocalDate.parse(batch.expiryDate, date);
                    if (expiry.isBefore(today)) findings.add(new Finding("EXPIRED", "HIGH", "C", "批次已过期：" + batch.expiryDate,
                            "本机批次记录", batch.lotNo));
                    else if (!expiry.isAfter(today.plusDays(30))) findings.add(new Finding("EXPIRING", "MEDIUM", "C", "批次将在 30 天内到期：" + batch.expiryDate,
                            "本机批次记录", batch.lotNo));
                } catch (RuntimeException ignored) { }
            }
            if (batch.quantity <= 3) findings.add(new Finding("LOW_STOCK", "MEDIUM", "C", "库存较低：" + batch.quantity + batch.quantityUnit,
                    "本机库存记录", batch.lotNo));
        }
        Map<String, String> times = new HashMap<>();
        for (LunaDatabase.PlanRow plan : plans) {
            if (plan.paused || !plan.enabled || "PRN".equals(plan.type)) continue;
            for (String time : plan.timesCsv.split(",")) {
                String owner = times.putIfAbsent(time.trim(), plan.medicationName);
                if (owner != null && !owner.equals(plan.medicationName)) findings.add(new Finding("TIME_CONFLICT", "LOW", "C", "多个计划在同一时间提醒：" + time,
                        "本机用药计划", owner + " / " + plan.medicationName));
            }
            if (plan.endDate != null && !plan.endDate.isEmpty()) {
                try {
                    LocalDate end = LocalDate.parse(plan.endDate, date);
                    if (!end.isBefore(today) && !end.isAfter(today.plusDays(7))) {
                        findings.add(new Finding("COURSE_END", "MEDIUM", "C", "疗程将在 7 天内结束：" + plan.endDate,
                                "本机用药计划", plan.medicationName));
                    }
                } catch (RuntimeException ignored) { }
            }
        }
        return findings;
    }

    private static Set<String> tokens(String text) {
        Set<String> result = new HashSet<>();
        if (text == null) return result;
        for (String raw : text.toLowerCase(Locale.ROOT).split("[,，;/、\\n\\s]+")) if (raw.trim().length() >= 2) result.add(raw.trim());
        return result;
    }
}
