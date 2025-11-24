package com.calendarreminder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.CalendarContract;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class CalendarMonitorService extends Service {

    private static final String TAG = "CalendarMonitorService";
    private static final String CHANNEL_ID = "CalendarReminderChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final long CHECK_INTERVAL = 30000; // Vérifier toutes les 30 secondes

    private Handler handler;
    private Runnable checkRunnable;
    private Set<String> shownReminders; // Pour éviter d'afficher le même rappel plusieurs fois
    private PowerManager.WakeLock wakeLock; // Pour empêcher la mise en veille

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Acquérir un WakeLock pour empêcher la mise en veille
        acquireWakeLock();
        
        shownReminders = new HashSet<>();
        handler = new Handler(Looper.getMainLooper());
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                checkUpcomingReminders();
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        
        handler.post(checkRunnable);
        Log.d(TAG, "Service créé avec WakeLock");
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "CalendarReminder::ServiceWakeLock"
                );
                wakeLock.acquire();
                Log.d(TAG, "WakeLock acquis");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'acquisition du WakeLock", e);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
            Log.d(TAG, "WakeLock libéré");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // S'assurer que le WakeLock est actif
        if (wakeLock == null || !wakeLock.isHeld()) {
            acquireWakeLock();
        }
        
        // Redémarrer le service s'il est tué et ne pas le tuer même en cas de manque de mémoire
        return START_STICKY | START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                // Supprimer le canal existant s'il existe pour le recréer avec la bonne importance
                NotificationChannel existingChannel = manager.getNotificationChannel(CHANNEL_ID);
                if (existingChannel != null) {
                    // Vérifier si le badge est activé
                    if (existingChannel.canShowBadge()) {
                        manager.deleteNotificationChannel(CHANNEL_ID);
                        Log.d(TAG, "Ancien canal supprimé (badge était activé)");
                        // Attendre un peu pour que la suppression soit effective
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            // Ignorer
                        }
                    } else {
                        Log.d(TAG, "Canal existant déjà configuré sans badge");
                        return; // Le canal est déjà correct, pas besoin de le recréer
                    }
                }
                
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Calendar Reminder Service",
                        NotificationManager.IMPORTANCE_LOW // IMPORTANCE_LOW pour qu'elle soit visible
                );
                channel.setDescription("Service de surveillance du calendrier");
                channel.setShowBadge(false); // Désactiver la pastille de notification
                channel.enableLights(false); // Désactiver la LED
                channel.enableVibration(false); // Désactiver la vibration pour cette notification
                // IMPORTANCE_LOW : visible dans la barre de notification, silencieuse mais visible
                // Elle sera persistante grâce à setOngoing(true)
                manager.createNotificationChannel(channel);
                Log.d(TAG, "Canal de notification créé: " + CHANNEL_ID + " avec importance LOW, badge=false");
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Calendar Reminder")
                .setContentText("Surveillance du calendrier active")
                .setSmallIcon(R.drawable.ic_clock)
                .setContentIntent(pendingIntent)
                .setOngoing(true) // Notification persistante (ne peut pas être supprimée)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(false)
                .setAutoCancel(false) // Ne pas supprimer automatiquement
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible même sur écran verrouillé
                .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE); // Pas de pastille de notification
        
        Notification notification = builder.build();
        Log.d(TAG, "Notification créée avec ongoing=true, importance=LOW");
        return notification;
    }

    private void checkUpcomingReminders() {
        try {
            ContentResolver contentResolver = getContentResolver();
            Calendar now = Calendar.getInstance();
            long currentTime = now.getTimeInMillis();
            
            // Vérifier les 5 prochaines minutes
            long futureTime = currentTime + (5 * 60 * 1000);

            // Requête pour les événements avec rappels
            Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(builder, currentTime);
            ContentUris.appendId(builder, futureTime);

            String[] projection = {
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END
            };

            String selection = CalendarContract.Instances.BEGIN + " >= ? AND " +
                    CalendarContract.Instances.BEGIN + " <= ?";

            Cursor cursor = contentResolver.query(
                    builder.build(),
                    projection,
                    selection,
                    new String[]{String.valueOf(currentTime), String.valueOf(futureTime)},
                    CalendarContract.Instances.BEGIN + " ASC"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long eventId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE));
                    long begin = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN));
                    
                    // Vérifier les rappels pour cet événement
                    checkRemindersForEvent(eventId, title, begin);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la vérification du calendrier", e);
        }
    }

    private void checkRemindersForEvent(long eventId, String title, long eventStartTime) {
        try {
            ContentResolver contentResolver = getContentResolver();
            Calendar now = Calendar.getInstance();
            long currentTime = now.getTimeInMillis();
            
            // Vérifier les rappels de cet événement
            Uri remindersUri = CalendarContract.Reminders.CONTENT_URI;
            String[] projection = {
                    CalendarContract.Reminders.MINUTES,
                    CalendarContract.Reminders.METHOD
            };
            String selection = CalendarContract.Reminders.EVENT_ID + " = ? AND " +
                    CalendarContract.Reminders.METHOD + " = ?";
            String[] selectionArgs = {
                    String.valueOf(eventId),
                    String.valueOf(CalendarContract.Reminders.METHOD_ALERT)
            };

            Cursor cursor = contentResolver.query(
                    remindersUri,
                    projection,
                    selection,
                    selectionArgs,
                    null
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int minutes = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Reminders.MINUTES));
                    long reminderTime = eventStartTime - (minutes * 60 * 1000L);
                    
                    // Si le rappel est dans les 30 secondes à venir
                    long timeDiff = reminderTime - currentTime;
                    if (timeDiff >= 0 && timeDiff <= 30000) {
                        // Créer une clé unique pour ce rappel
                        String reminderKey = eventId + "_" + minutes + "_" + (reminderTime / 1000);
                        
                        // Vérifier si ce rappel n'a pas déjà été affiché
                        if (!shownReminders.contains(reminderKey)) {
                            shownReminders.add(reminderKey);
                            // Afficher l'activité de rappel
                            showReminderActivity(eventId, title, eventStartTime);
                            
                            // Nettoyer les anciens rappels après 1 heure
                            if (shownReminders.size() > 100) {
                                shownReminders.clear();
                            }
                            break; // Ne montrer qu'une fois
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la vérification des rappels", e);
        }
    }

    private void showReminderActivity(long eventId, String title, long eventStartTime) {
        // Utiliser le même mécanisme que le bouton de test : BroadcastReceiver
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra(ReminderActivity.EXTRA_EVENT_TITLE, title);
        intent.putExtra(ReminderActivity.EXTRA_EVENT_ID, eventId);
        intent.putExtra(ReminderActivity.EXTRA_EVENT_START_TIME, eventStartTime);
        
        // Utiliser AlarmManager pour s'assurer que l'activité s'affiche même si l'écran est verrouillé
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                (int) eventId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, 1);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(), pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(), pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(), pendingIntent);
        }
        
        Log.d(TAG, "Rappel programmé pour l'événement: " + title + " (ID: " + eventId + ")");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && checkRunnable != null) {
            handler.removeCallbacks(checkRunnable);
        }
        releaseWakeLock();
        Log.d(TAG, "Service détruit");
    }
}

