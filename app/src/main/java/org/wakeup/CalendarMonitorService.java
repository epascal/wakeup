package org.wakeup;

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
    private static final String CHANNEL_ID = "WakeUpChannel";
    public static final String ACTION_RECREATE_NOTIFICATION = "RECREATE_NOTIFICATION";
    public static final String ACTION_FORCE_NOTIFICATION_CHECK = "FORCE_NOTIFICATION_CHECK";

    private static final int NOTIFICATION_ID = 1;
    private static final long CHECK_INTERVAL = 30000; // Vérifier toutes les 30 secondes
    private static final long NOTIFICATION_CHECK_INTERVAL = 5000; // Vérifier la notification toutes les 5 secondes

    private Handler handler;
    private Runnable checkRunnable;
    private Runnable notificationCheckRunnable;
    private Set<String> shownReminders; // Pour éviter d'afficher le même rappel plusieurs fois
    private PowerManager.WakeLock wakeLock; // Pour empêcher la mise en veille

    @Override
    public void onCreate() {
        super.onCreate();
        long startTime = System.currentTimeMillis();
        Log.d(TAG, "Service onCreate() démarré à " + startTime);
        
        // PRIORITÉ ABSOLUE: Créer le canal et démarrer en foreground IMMÉDIATEMENT
        // Ceci doit être fait dans les 5 secondes pour éviter les crashs ANR
        long channelStart = System.currentTimeMillis();
        createNotificationChannelFast();
        long channelEnd = System.currentTimeMillis();
        Log.d(TAG, "Canal créé en " + (channelEnd - channelStart) + " ms");
        
        long notificationStart = System.currentTimeMillis();
        try {
            Notification notification = createNotificationFast();
            long notificationCreated = System.currentTimeMillis();
            Log.d(TAG, "Notification créée en " + (notificationCreated - notificationStart) + " ms");
            
            startForeground(NOTIFICATION_ID, notification);
            long foregroundEnd = System.currentTimeMillis();
            Log.d(TAG, "startForeground() appelé en " + (foregroundEnd - notificationCreated) + " ms");
            Log.d(TAG, "Service démarré en foreground IMMÉDIATEMENT - Total: " + (foregroundEnd - startTime) + " ms");
            // Vérifier rapidement que la notification est bien visible
            new Handler(Looper.getMainLooper()).postDelayed(this::ensureNotificationIsVisible, 2000);
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du démarrage en foreground", e);
        }

        // Initialiser le reste en arrière-plan pour ne pas bloquer l'affichage de la notification
        new Thread(() -> {
            // Acquérir un WakeLock pour empêcher la mise en veille
            acquireWakeLock();

            shownReminders = new HashSet<>();
            handler = new Handler(Looper.getMainLooper());
            
            // Runnable pour vérifier les rappels
            checkRunnable = new Runnable() {
                @Override
                public void run() {
                    checkUpcomingReminders();
                    handler.postDelayed(this, CHECK_INTERVAL);
                }
            };
            
            // Runnable pour vérifier la notification
            notificationCheckRunnable = new Runnable() {
                @Override
                public void run() {
                    ensureNotificationIsVisible();
                    handler.postDelayed(this, NOTIFICATION_CHECK_INTERVAL);
                }
            };

            // Démarrer les vérifications
            handler.post(checkRunnable);
            handler.post(notificationCheckRunnable);
            
            Log.d(TAG, "Initialisation en arrière-plan terminée");
        }).start();
        
        Log.d(TAG, "Service créé avec notification instantanée");
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "WakeUp::ServiceWakeLock");
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
        long startTime = System.currentTimeMillis();
        Log.d(TAG, "onStartCommand() appelé à " + startTime);
        
        // PRIORITÉ: S'assurer que la notification est affichée IMMÉDIATEMENT
        // Utiliser createNotificationFast() pour éviter tout délai
        // Ne pas recréer le canal ici car on ne peut pas supprimer un canal utilisé par un service foreground
        try {
            long notificationStart = System.currentTimeMillis();
            Notification notification = createNotificationFast();
            long notificationCreated = System.currentTimeMillis();
            Log.d(TAG, "Notification créée dans onStartCommand() en " + (notificationCreated - notificationStart) + " ms");
            
            startForeground(NOTIFICATION_ID, notification);
            long foregroundEnd = System.currentTimeMillis();
            Log.d(TAG, "startForeground() appelé dans onStartCommand() en " + (foregroundEnd - notificationCreated) + " ms");
            Log.d(TAG, "Service en foreground dans onStartCommand() - Total: " + (foregroundEnd - startTime) + " ms");
            new Handler(Looper.getMainLooper()).postDelayed(this::ensureNotificationIsVisible, 2000);
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du démarrage en foreground dans onStartCommand()", e);
        }
        
        // S'assurer que le WakeLock est actif (en arrière-plan pour ne pas bloquer)
        new Thread(() -> {
            if (wakeLock == null || !wakeLock.isHeld()) {
                acquireWakeLock();
            }
        }).start();

        // Vérifier si c'est une demande de recréation de notification
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_RECREATE_NOTIFICATION.equals(action)) {
                Log.d(TAG, "Recréation de la notification demandée");
                // Recréer la notification en foreground avec la version complète
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.notify(NOTIFICATION_ID, createNotification());
                    Log.d(TAG, "Notification du service recréée");
                }
                // Vérifier immédiatement et annuler les fallbacks si tout va bien
                ensureNotificationIsVisible();
            } else if (ACTION_FORCE_NOTIFICATION_CHECK.equals(action)) {
                Log.d(TAG, "Force notification check demandé via AlarmManager");
                ensureNotificationIsVisible();
            }
        }

        // Redémarrer le service s'il est tué et redélivrer l'intent
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Version ultra-rapide de la création du canal de notification
     * Utilisée au démarrage pour afficher la notification instantanément
     */
    private void createNotificationChannelFast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    // Vérifier si le canal existe déjà et s'il a la bonne importance
                    NotificationChannel existingChannel = manager.getNotificationChannel(CHANNEL_ID);
                    if (existingChannel != null) {
                        // Si l'importance n'est pas DEFAULT, supprimer et recréer
                        if (existingChannel.getImportance() != NotificationManager.IMPORTANCE_DEFAULT) {
                            Log.d(TAG, "Canal existant avec mauvaise importance (" + existingChannel.getImportance() + "), suppression...");
                            manager.deleteNotificationChannel(CHANNEL_ID);
                            // Attendre un peu pour que la suppression soit effective
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                // Ignorer
                            }
                        } else {
                            Log.d(TAG, "Canal de notification existe déjà avec IMPORTANCE_DEFAULT, pas besoin de le recréer");
                            return;
                        }
                    }
                    
                    // IMPORTANCE_DEFAULT pour afficher la notification immédiatement
                    // IMPORTANCE_MIN peut retarder l'affichage de la notification
                    NotificationChannel channel = new NotificationChannel(
                            CHANNEL_ID,
                            "Wake Up Service",
                            NotificationManager.IMPORTANCE_DEFAULT
                    );
                    channel.setShowBadge(false);
                    channel.enableLights(false);
                    channel.enableVibration(false);
                    manager.createNotificationChannel(channel);
                    Log.d(TAG, "Canal de notification créé rapidement avec IMPORTANCE_DEFAULT");
                }
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de la création rapide du canal", e);
            }
        }
    }

    /**
     * Version ultra-rapide de la création de notification
     * Utilisée au démarrage pour afficher la notification instantanément
     */
    private Notification createNotificationFast() {
        long startTime = System.currentTimeMillis();
        
        long intentStart = System.currentTimeMillis();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        long intentEnd = System.currentTimeMillis();
        Log.d(TAG, "PendingIntent créé en " + (intentEnd - intentStart) + " ms");

        PendingIntent deletePendingIntent = createDeletePendingIntent();

        long builderStart = System.currentTimeMillis();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Wake Up")
                .setContentText("Surveillance du calendrier active")
                .setSmallIcon(R.drawable.ic_clock)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(false)
                .setAutoCancel(false)
                .setDeleteIntent(deletePendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }

        Notification notification = builder.build();
        long builderEnd = System.currentTimeMillis();
        Log.d(TAG, "Notification.Builder.build() en " + (builderEnd - builderStart) + " ms");
        Log.d(TAG, "createNotificationFast() total: " + (builderEnd - startTime) + " ms");
        
        return notification;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                // Supprimer le canal existant s'il existe pour le recréer avec la bonne
                // importance
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
                        "Wake Up Service",
                        NotificationManager.IMPORTANCE_DEFAULT // IMPORTANCE_DEFAULT pour affichage immédiat
                );
                channel.setDescription("Service de surveillance du calendrier");
                channel.setShowBadge(false);
                channel.enableLights(false);
                channel.enableVibration(false);

                manager.createNotificationChannel(channel);
                Log.d(TAG, "Canal de notification créé: " + CHANNEL_ID + " avec importance DEFAULT");
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent deletePendingIntent = createDeletePendingIntent();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Wake Up")
                .setContentText("Surveillance du calendrier active")
                .setSmallIcon(R.drawable.ic_clock)
                .setContentIntent(pendingIntent)
                .setOngoing(true) // Notification persistante (ne peut pas être supprimée)
                .setDeleteIntent(deletePendingIntent) // Détecter si elle est quand même supprimée
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(false)
                .setAutoCancel(false) // Ne pas supprimer automatiquement
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible même sur écran verrouillé
                .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE); // Pas de pastille de notification

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }

        Notification notification = builder.build();
        Log.d(TAG, "Notification créée avec ongoing=true, deleteIntent configuré");
        return notification;
    }

    private PendingIntent createDeletePendingIntent() {
        Intent deleteIntent = new Intent(this, ServiceNotificationDismissReceiver.class);
        deleteIntent.setAction(ServiceNotificationDismissReceiver.ACTION_SERVICE_NOTIFICATION_DISMISSED);

        return PendingIntent.getBroadcast(
                this,
                0,
                deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void ensureNotificationIsVisible() {
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                // Vérifier si la notification est toujours active
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.service.notification.StatusBarNotification[] notifications = manager.getActiveNotifications();
                    boolean notificationExists = false;
                    
                    for (android.service.notification.StatusBarNotification sbn : notifications) {
                        if (sbn.getId() == NOTIFICATION_ID) {
                            notificationExists = true;
                            break;
                        }
                    }
                    
                    if (!notificationExists) {
                        Log.w(TAG, "Notification manquante détectée, recréation...");
                        // Recréer la notification
                        startForeground(NOTIFICATION_ID, createNotification());
                        Log.d(TAG, "Notification recréée avec succès");
                        ServiceNotificationDismissReceiver.cancelFallback(this);
                    } else {
                        Log.d(TAG, "Notification toujours présente");
                        ServiceNotificationDismissReceiver.cancelFallback(this);
                    }
                } else {
                    // Pour les versions plus anciennes, simplement mettre à jour la notification
                    manager.notify(NOTIFICATION_ID, createNotification());
                    ServiceNotificationDismissReceiver.cancelFallback(this);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la vérification de la notification", e);
            // En cas d'erreur, essayer de recréer la notification
            try {
                startForeground(NOTIFICATION_ID, createNotification());
            } catch (Exception e2) {
                Log.e(TAG, "Erreur lors de la recréation de la notification", e2);
            }
        }
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
                    new String[] { String.valueOf(currentTime), String.valueOf(futureTime) },
                    CalendarContract.Instances.BEGIN + " ASC");

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
                    null);

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
                            showReminderActivity(eventId, title, eventStartTime, minutes);
                            Log.d(TAG, "Rappel déclenché pour l'événement " + eventId + " à " + minutes + " minutes avant");

                            // Nettoyer les anciens rappels après 1 heure
                            if (shownReminders.size() > 100) {
                                shownReminders.clear();
                            }
                            // Ne pas utiliser break ici pour permettre à tous les reminders de se déclencher
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la vérification des rappels", e);
        }
    }

    private void showReminderActivity(long eventId, String title, long eventStartTime, int minutes) {
        // Utiliser le même mécanisme que le bouton de test : BroadcastReceiver
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra(ReminderActivity.EXTRA_EVENT_TITLE, title);
        intent.putExtra(ReminderActivity.EXTRA_EVENT_ID, eventId);
        intent.putExtra(ReminderActivity.EXTRA_EVENT_START_TIME, eventStartTime);

        // Utiliser AlarmManager pour s'assurer que l'activité s'affiche même si l'écran
        // est verrouillé
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        // Utiliser un requestCode unique qui combine eventId et minutes pour éviter les conflits
        // entre plusieurs reminders du même événement
        int requestCode = (int) ((eventId % Integer.MAX_VALUE) * 1000 + (minutes % 1000));
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
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
        if (handler != null) {
            if (checkRunnable != null) {
                handler.removeCallbacks(checkRunnable);
            }
            if (notificationCheckRunnable != null) {
                handler.removeCallbacks(notificationCheckRunnable);
            }
        }
        releaseWakeLock();
        ServiceNotificationDismissReceiver.cancelFallback(this);
        Log.d(TAG, "Service détruit");
    }
}
