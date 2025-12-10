package org.wakeup;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "ReminderReceiver";
    private static final String REMINDER_CHANNEL_ID = "ReminderNotificationChannel";
    private static final int REMINDER_NOTIFICATION_ID_BASE = 1000;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "ReminderReceiver.onReceive() called");
        Log.d(TAG, "Intent: " + intent);

        // Get data from intent
        String eventTitle = intent.getStringExtra(ReminderActivity.EXTRA_EVENT_TITLE);
        long eventId = intent.getLongExtra(ReminderActivity.EXTRA_EVENT_ID, -1);
        long eventStartTime = intent.getLongExtra(ReminderActivity.EXTRA_EVENT_START_TIME, 0);

        Log.d(TAG, "Titre: " + eventTitle);
        Log.d(TAG, "Event ID: " + eventId);
        Log.d(TAG, "Event Start Time: " + eventStartTime);

        // Create notification channel for reminders
        createReminderNotificationChannel(context);

        // Send notification that will be synced with Garmin watch
        sendReminderNotification(context, eventTitle, eventId);

        // Create intent to launch ReminderActivity
        Intent reminderIntent = new Intent(context, ReminderActivity.class);
        reminderIntent.putExtra(ReminderActivity.EXTRA_EVENT_TITLE, eventTitle);
        reminderIntent.putExtra(ReminderActivity.EXTRA_EVENT_ID, eventId);
        reminderIntent.putExtra(ReminderActivity.EXTRA_EVENT_START_TIME, eventStartTime);
        reminderIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        Log.d(TAG, "Launching ReminderActivity");
        context.startActivity(reminderIntent);
        Log.d(TAG, "ReminderActivity launched");
    }

    private void createReminderNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                // Delete existing channel to force recreation with new pattern
                NotificationChannel existingChannel = manager.getNotificationChannel(REMINDER_CHANNEL_ID);
                if (existingChannel != null) {
                    manager.deleteNotificationChannel(REMINDER_CHANNEL_ID);
                    Log.d(TAG, "Old notification channel deleted for recreation");
                }

                // More intense and longer vibration pattern for Garmin watch
                // Format: {delay, vibration1, pause1, vibration2, pause2, ...}
                // 5 long vibrations (500ms) with short pauses (150ms), then final pause
                // (500ms)
                long[] intensiveVibrationPattern = {
                        0, // Start immediately
                        500, // Long vibration 500ms
                        150, // Short pause
                        500, // Long vibration 500ms
                        150, // Short pause
                        500, // Long vibration 500ms
                        150, // Short pause
                        500, // Long vibration 500ms
                        150, // Short pause
                        500, // Long vibration 500ms
                        500 // Final pause before repeat
                };

                NotificationChannel channel = new NotificationChannel(
                        REMINDER_CHANNEL_ID,
                        context.getString(R.string.reminder_channel_name),
                        NotificationManager.IMPORTANCE_HIGH // IMPORTANCE_HIGH pour garantir la synchronisation avec
                                                            // Garmin
                );
                channel.setDescription(context.getString(R.string.reminder_channel_description));
                channel.setShowBadge(true);
                channel.enableLights(true);
                channel.enableVibration(true);
                channel.setVibrationPattern(intensiveVibrationPattern);

                manager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel for reminders created with intense vibration pattern (5x500ms)");
            }
        }
    }

    private void sendReminderNotification(Context context, String eventTitle, long eventId) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            Log.e(TAG, "NotificationManager est null");
            return;
        }

        // Create intent to open main activity when clicking on
        // notification
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) eventId,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Construire la notification
        String defaultEventTitle = context.getString(R.string.event_title);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_clock)
                .setContentTitle(context.getString(R.string.reminder_notification_title))
                .setContentText(eventTitle != null ? eventTitle : defaultEventTitle)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(eventTitle != null ? eventTitle : defaultEventTitle))
                .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for Android < 8
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // Notification disappears when clicked
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible on locked screen
                .setVibrate(new long[] { 0, 500, 150, 500, 150, 500, 150, 500, 150, 500, 500 }) // Intense vibration
                                                                                                // pattern
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS); // Default LED

        // Use unique ID based on eventId to allow multiple
        // notifications
        int notificationId = REMINDER_NOTIFICATION_ID_BASE + (int) (eventId % 1000);

        manager.notify(notificationId, builder.build());
        Log.d(TAG, "Notification sent for event: " + eventTitle + " (ID: " + notificationId + ")");
        Log.d(TAG,
                "This notification will be synced with your Garmin watch if connected via Garmin Connect");
    }
}
