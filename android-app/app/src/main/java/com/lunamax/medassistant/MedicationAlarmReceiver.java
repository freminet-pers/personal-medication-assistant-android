package com.lunamax.medassistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;

/** Alarm receiver with notification actions; the lock-screen notification deliberately hides the drug name. */
public final class MedicationAlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "medication_reminders";

    @Override public void onReceive(Context context, Intent intent) {
        long occurrenceId = intent.getLongExtra("occurrence_id", 0);
        String operation = intent.getStringExtra("operation");
        LunaDatabase database = new LunaDatabase(context);
        if (occurrenceId > 0 && operation != null && !ReminderScheduler.OP_ALARM.equals(operation)) {
            database.applyOccurrenceAction(occurrenceId, operation, System.currentTimeMillis(), 15 * 60 * 1000L, "通知操作");
            if (ReminderScheduler.OP_SNOOZE.equals(operation)) new ReminderScheduler(context, database).rebuild();
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(new NotificationChannel(CHANNEL, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(context, 1, new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_today)
                .setContentTitle("个人用药助手 · 用药提醒")
                .setContentText("有一项用药计划需要确认")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(new Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_today).setContentTitle("个人用药助手 · 用药提醒").setContentText("有一项提醒需要确认").build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && occurrenceId > 0) {
            builder.addAction(action(context, occurrenceId, ReminderScheduler.OP_TAKEN, "已服用"));
            builder.addAction(action(context, occurrenceId, ReminderScheduler.OP_SKIP, "跳过"));
            builder.addAction(action(context, occurrenceId, ReminderScheduler.OP_SNOOZE, "稍后 15 分钟"));
        }
        manager.notify((int) (occurrenceId == 0 ? System.currentTimeMillis() & 0x7fffffff : occurrenceId), builder.build());
    }

    private static Notification.Action action(Context context, long id, String operation, String title) {
        Intent intent = new Intent(context, MedicationAlarmReceiver.class).putExtra("occurrence_id", id).putExtra("operation", operation);
        PendingIntent pending = PendingIntent.getBroadcast(context, (int) (id ^ operation.hashCode()), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_menu_edit), title, pending).build();
    }
}
