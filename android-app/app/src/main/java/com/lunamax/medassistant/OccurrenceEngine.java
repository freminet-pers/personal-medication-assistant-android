package com.lunamax.medassistant;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure-Java recurrence engine. It accepts an injected instant so rollover tests never wait. */
final class OccurrenceEngine {
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    interface Clock { long now(); ZoneId zone(); }
    static final class SystemClock implements Clock {
        @Override public long now() { return System.currentTimeMillis(); }
        @Override public ZoneId zone() { return ZoneId.systemDefault(); }
    }

    static final class SchedulePlan {
        long id;
        String type = "DAILY"; // DAILY, WEEKLY, INTERVAL, PRN, TEMP
        String timesCsv = "08:00";
        int weekdaysMask = 127; // Monday bit 0.
        int intervalHours = 8;
        String startDate = "";
        String endDate = "";
        long startAtMs;
        boolean enabled = true;
        boolean paused;
    }

    private OccurrenceEngine() { }

    static List<Long> generate(SchedulePlan plan, long fromMs, long toMs, ZoneId zone) {
        if (!plan.enabled || plan.paused || "PRN".equals(plan.type) || toMs <= fromMs) return Collections.emptyList();
        Set<Long> unique = new HashSet<>();
        if ("INTERVAL".equals(plan.type)) {
            long start = plan.startAtMs > 0 ? plan.startAtMs : firstDateTime(plan, zone);
            long step = Math.max(1, plan.intervalHours) * 60L * 60L * 1000L;
            if (start <= 0) return Collections.emptyList();
            long first = start;
            if (first < fromMs) first += ((fromMs - first + step - 1) / step) * step;
            for (long value = first; value < toMs; value += step) {
                if (inPlanDateRange(value, plan, zone)) unique.add(value);
                if (value > Long.MAX_VALUE - step) break;
            }
        } else {
            ZonedDateTime from = Instant.ofEpochMilli(fromMs).atZone(zone).minusDays(1);
            ZonedDateTime to = Instant.ofEpochMilli(toMs).atZone(zone).plusDays(1);
            LocalDate startDate = parseDate(plan.startDate, from.toLocalDate());
            LocalDate endDate = parseDate(plan.endDate, to.toLocalDate());
            LocalDate cursor = startDate.isAfter(from.toLocalDate()) ? startDate : from.toLocalDate();
            List<LocalTime> times = parseTimes(plan.timesCsv);
            while (!cursor.isAfter(to.toLocalDate()) && !cursor.isAfter(endDate)) {
                boolean dayAllowed = "DAILY".equals(plan.type) || "TEMP".equals(plan.type)
                        ? true : (plan.weekdaysMask & (1 << (cursor.getDayOfWeek().getValue() - 1))) != 0;
                if (dayAllowed && !cursor.isBefore(startDate)) {
                    for (LocalTime time : times) {
                        long value = cursor.atTime(time).atZone(zone).toInstant().toEpochMilli();
                        if (value >= fromMs && value < toMs && inPlanDateRange(value, plan, zone)) unique.add(value);
                    }
                }
                cursor = cursor.plusDays(1);
            }
        }
        List<Long> result = new ArrayList<>(unique);
        Collections.sort(result);
        return result;
    }

    static boolean validTimeList(String timesCsv) {
        try { parseTimes(timesCsv); return true; } catch (RuntimeException error) { return false; }
    }

    private static List<LocalTime> parseTimes(String csv) {
        if (csv == null || csv.trim().isEmpty()) throw new IllegalArgumentException("at least one time is required");
        List<LocalTime> values = new ArrayList<>();
        for (String raw : csv.split(",")) {
            String value = raw.trim();
            if (!value.matches("\\d{2}:\\d{2}")) throw new IllegalArgumentException("time must be HH:mm");
            values.add(LocalTime.parse(value, TIME));
        }
        return values;
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try { return LocalDate.parse(value, DATE); } catch (DateTimeParseException error) { return fallback; }
    }

    private static long firstDateTime(SchedulePlan plan, ZoneId zone) {
        try {
            LocalDate date = parseDate(plan.startDate, LocalDate.now(zone));
            return date.atTime(parseTimes(plan.timesCsv).get(0)).atZone(zone).toInstant().toEpochMilli();
        } catch (RuntimeException error) { return 0; }
    }

    private static boolean inPlanDateRange(long value, SchedulePlan plan, ZoneId zone) {
        LocalDate date = Instant.ofEpochMilli(value).atZone(zone).toLocalDate();
        LocalDate start = parseDate(plan.startDate, date);
        LocalDate end = parseDate(plan.endDate, date.plusYears(10));
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
