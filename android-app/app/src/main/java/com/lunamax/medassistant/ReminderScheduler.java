package com.lunamax.medassistant;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.List;

final class ReminderScheduler {
    static final String OP_ALARM = "ALARM";
    static final String OP_TAKEN = "TAKEN";
    static final String OP_SKIP = "SKIPPED";
    static final String OP_SNOOZE = "SNOOZE";
    static final String OP_UNDO = "UNDO";
    private final Context context;
    private final LunaDatabase database;

    ReminderScheduler(Context context, LunaDatabase database) { this.context = context.getApplicationContext(); this.database = database; }

    void rebuild() { rebuild(false); }

    void rebuild(boolean resetForSystemTimeChange) {
        long now = System.currentTimeMillis();
        if (resetForSystemTimeChange) database.resetPendingSchedules(now);
        database.rebuildOccurrences(now, 30);
        List<LunaDatabase.OccurrenceRow> rows = database.futureOccurrences(now, now + 30L * 24L * 60L * 60L * 1000L);
        for (LunaDatabase.OccurrenceRow row : rows) if ("PENDING".equals(row.status) || "SNOOZED".equals(row.status)) schedule(row);
    }

    void schedule(LunaDatabase.OccurrenceRow row) {
        if (row.scheduledAtMs <= System.currentTimeMillis()) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent intent = new Intent(context, MedicationAlarmReceiver.class).putExtra("occurrence_id", row.id).putExtra("operation", OP_ALARM);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode(row.id), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, row.scheduledAtMs, pending);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, row.scheduledAtMs, pending);
        } else {
            alarms.setExact(AlarmManager.RTC_WAKEUP, row.scheduledAtMs, pending);
        }
    }

    boolean canScheduleExact() {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarms == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms();
    }

    Intent exactAlarmSettingsIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null;
        return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.getPackageName()));
    }

    private static int requestCode(long id) { return (int) (id ^ (id >>> 32)); }
}
