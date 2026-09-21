package com.lunamax.medassistant;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Executable no-wait contract test for daily, weekly, interval, end-date and pause rules. */
public final class OccurrenceEngineContract {
    public static void main(String[] args) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long from = LocalDateTime.of(2026, 1, 5, 0, 0).atZone(zone).toInstant().toEpochMilli();
        long to = LocalDateTime.of(2026, 1, 8, 0, 0).atZone(zone).toInstant().toEpochMilli();
        OccurrenceEngine.SchedulePlan daily = new OccurrenceEngine.SchedulePlan();
        daily.type = "DAILY"; daily.timesCsv = "08:00,20:00"; daily.startDate = "2026-01-05"; daily.endDate = "2026-01-07";
        List<Long> dailyValues = OccurrenceEngine.generate(daily, from, to, zone);
        check(dailyValues.size() == 6, "daily multi-time generation");

        OccurrenceEngine.SchedulePlan weekly = new OccurrenceEngine.SchedulePlan();
        weekly.type = "WEEKLY"; weekly.timesCsv = "09:00"; weekly.weekdaysMask = 1 << 0; weekly.startDate = "2026-01-01";
        check(OccurrenceEngine.generate(weekly, from, to, zone).size() == 1, "weekly Monday generation");

        OccurrenceEngine.SchedulePlan interval = new OccurrenceEngine.SchedulePlan();
        interval.type = "INTERVAL"; interval.intervalHours = 6; interval.startAtMs = LocalDateTime.of(2026,1,5,23,0).atZone(zone).toInstant().toEpochMilli(); interval.startDate = "2026-01-05"; interval.endDate = "2026-01-06";
        check(OccurrenceEngine.generate(interval, from, to, zone).size() == 5, "cross-midnight interval and end date");

        daily.paused = true;
        check(OccurrenceEngine.generate(daily, from, to, zone).isEmpty(), "paused plan produces no alarms");
        System.out.println("OccurrenceEngineContract PASS");
    }

    private static void check(boolean condition, String name) { if (!condition) throw new AssertionError(name); }
}
