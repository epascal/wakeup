package com.calendarreminder;

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
        Log.d(TAG, "ReminderReceiver.onReceive() appelé");
        Log.d(TAG, "Intent: " + intent);

        // Récupérer les données de l'intent
        String eventTitle = intent.getStringExtra(ReminderActivity.EXTRA_EVENT_TITLE);
        long eventId = intent.getLongExtra(ReminderActivity.EXTRA_EVENT_ID, -1);
        long eventStartTime = intent.getLongExtra(ReminderActivity.EXTRA_EVENT_START_TIME, 0);

        Log.d(TAG, "Titre: " + eventTitle);
        Log.d(TAG, "Event ID: " + eventId);
        Log.d(TAG, "Event Start Time: " + eventStartTime);

        // Créer le canal de notification pour les rappels
        createReminderNotificationChannel(context);

        // Envoyer une notification qui sera synchronisée avec la montre Garmin
        sendReminderNotification(context, eventTitle, eventId);

        // Créer un intent pour lancer ReminderActivity
        Intent reminderIntent = new Intent(context, ReminderActivity.class);
        reminderIntent.putExtra(ReminderActivity.EXTRA_EVENT_TITLE, eventTitle);
        reminderIntent.putExtra(ReminderActivity.EXTRA_EVENT_ID, eventId);
        reminderIntent.putExtra(ReminderActivity.EXTRA_EVENT_START_TIME, eventStartTime);
        reminderIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        Log.d(TAG, "Lancement de ReminderActivity");
        context.startActivity(reminderIntent);
        Log.d(TAG, "ReminderActivity lancée");
    }

    private void createReminderNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                // Supprimer le canal existant pour forcer la recréation avec le nouveau pattern
                NotificationChannel existingChannel = manager.getNotificationChannel(REMINDER_CHANNEL_ID);
                if (existingChannel != null) {
                    manager.deleteNotificationChannel(REMINDER_CHANNEL_ID);
                    Log.d(TAG, "Ancien canal de notification supprimé pour recréation");
                }

                // Pattern de vibration plus intense et plus long pour la montre Garmin
                // Format: {délai, vibration1, pause1, vibration2, pause2, ...}
                // 5 vibrations longues (500ms) avec pauses courtes (150ms), puis pause finale
                // (500ms)
                long[] intensiveVibrationPattern = {
                        0, // Démarrer immédiatement
                        500, // Vibration longue 500ms
                        150, // Pause courte
                        500, // Vibration longue 500ms
                        150, // Pause courte
                        500, // Vibration longue 500ms
                        150, // Pause courte
                        500, // Vibration longue 500ms
                        150, // Pause courte
                        500, // Vibration longue 500ms
                        500 // Pause finale avant répétition
                };

                NotificationChannel channel = new NotificationChannel(
                        REMINDER_CHANNEL_ID,
                        "Rappels de calendrier",
                        NotificationManager.IMPORTANCE_HIGH // IMPORTANCE_HIGH pour garantir la synchronisation avec
                                                            // Garmin
                );
                channel.setDescription("Notifications pour les rappels d'événements du calendrier");
                channel.setShowBadge(true);
                channel.enableLights(true);
                channel.enableVibration(true);
                channel.setVibrationPattern(intensiveVibrationPattern);

                manager.createNotificationChannel(channel);
                Log.d(TAG, "Canal de notification pour rappels créé avec pattern de vibration intense (5x500ms)");
            }
        }
    }

    private void sendReminderNotification(Context context, String eventTitle, long eventId) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            Log.e(TAG, "NotificationManager est null");
            return;
        }

        // Créer un intent pour ouvrir l'activité principale quand on clique sur la
        // notification
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) eventId,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Construire la notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_clock)
                .setContentTitle("Rappel de calendrier")
                .setContentText(eventTitle != null ? eventTitle : "Événement")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(eventTitle != null ? eventTitle : "Événement"))
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Haute priorité pour Android < 8
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // La notification disparaît quand on clique dessus
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible sur écran verrouillé
                .setVibrate(new long[] { 0, 500, 150, 500, 150, 500, 150, 500, 150, 500, 500 }) // Pattern de vibration
                                                                                                // intense
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS); // LED par défaut

        // Utiliser un ID unique basé sur l'eventId pour permettre plusieurs
        // notifications
        int notificationId = REMINDER_NOTIFICATION_ID_BASE + (int) (eventId % 1000);

        manager.notify(notificationId, builder.build());
        Log.d(TAG, "Notification envoyée pour l'événement: " + eventTitle + " (ID: " + notificationId + ")");
        Log.d(TAG,
                "Cette notification sera synchronisée avec votre montre Garmin si elle est connectée via Garmin Connect");
    }
}
