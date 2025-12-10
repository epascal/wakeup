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
    private static final long CHECK_INTERVAL = 30000; // Check every 30 seconds
    private static final long NOTIFICATION_CHECK_INTERVAL = 5000; // Check notification every 5 seconds

    private Handler handler;
    private Runnable checkRunnable;
    private Runnable notificationCheckRunnable;
    private Set<String> shownReminders; // To avoid showing the same reminder multiple times
    private PowerManager.WakeLock wakeLock; // To prevent sleep mode

    @Override
    public void onCreate() {
        super.onCreate();
        long startTime = System.currentTimeMillis();
        Log.d(TAG, "Service onCreate() started at " + startTime);
        
        // ABSOLUTE PRIORITY: Create channel and start in foreground IMMEDIATELY
        // This must be done within 5 seconds to avoid ANR crashes
        long channelStart = System.currentTimeMillis();
        createNotificationChannelFast();
        long channelEnd = System.currentTimeMillis();
            Log.d(TAG, "Channel created in " + (channelEnd - channelStart) + " ms");
        
        long notificationStart = System.currentTimeMillis();
        try {
            Notification notification = createNotificationFast();
            long notificationCreated = System.currentTimeMillis();
            Log.d(TAG, "Notification created in " + (notificationCreated - notificationStart) + " ms");
            
            startForeground(NOTIFICATION_ID, notification);
            long foregroundEnd = System.currentTimeMillis();
            Log.d(TAG, "startForeground() called in " + (foregroundEnd - notificationCreated) + " ms");
            Log.d(TAG, "Service started in foreground IMMEDIATELY - Total: " + (foregroundEnd - startTime) + " ms");
            // Quickly verify that the notification is visible
            new Handler(Looper.getMainLooper()).postDelayed(this::ensureNotificationIsVisible, 2000);
        } catch (Exception e) {
            Log.e(TAG, "Error starting in foreground", e);
        }

        // Initialize the rest in background to not block notification display
        new Thread(() -> {
            // Acquire a WakeLock to prevent sleep mode
            acquireWakeLock();

            shownReminders = new HashSet<>();
            handler = new Handler(Looper.getMainLooper());
            
            // Runnable to check reminders
            checkRunnable = new Runnable() {
                @Override
                public void run() {
                    checkUpcomingReminders();
                    handler.postDelayed(this, CHECK_INTERVAL);
                }
            };
            
            // Runnable to check notification
            notificationCheckRunnable = new Runnable() {
                @Override
                public void run() {
                    ensureNotificationIsVisible();
                    handler.postDelayed(this, NOTIFICATION_CHECK_INTERVAL);
                }
            };

            // Start checks
            handler.post(checkRunnable);
            handler.post(notificationCheckRunnable);
            
            // Start periodic monitoring to restart service if killed
            ServiceKeepAliveReceiver.startMonitoring(CalendarMonitorService.this);
            
            Log.d(TAG, "Background initialization completed");
        }).start();
        
        Log.d(TAG, "Service created with instant notification");
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "WakeUp::ServiceWakeLock");
                wakeLock.acquire();
                Log.d(TAG, "WakeLock acquired");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring WakeLock", e);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
            Log.d(TAG, "WakeLock released");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long startTime = System.currentTimeMillis();
        Log.d(TAG, "onStartCommand() called at " + startTime);
        
        // PRIORITY: Ensure notification is displayed IMMEDIATELY
        // Use createNotificationFast() to avoid any delay
        // Do not recreate channel here as we cannot delete a channel used by a foreground service
        try {
            long notificationStart = System.currentTimeMillis();
            Notification notification = createNotificationFast();
            long notificationCreated = System.currentTimeMillis();
            Log.d(TAG, "Notification created in onStartCommand() in " + (notificationCreated - notificationStart) + " ms");
            
            startForeground(NOTIFICATION_ID, notification);
            long foregroundEnd = System.currentTimeMillis();
            Log.d(TAG, "startForeground() called in onStartCommand() in " + (foregroundEnd - notificationCreated) + " ms");
            Log.d(TAG, "Service in foreground in onStartCommand() - Total: " + (foregroundEnd - startTime) + " ms");
            new Handler(Looper.getMainLooper()).postDelayed(this::ensureNotificationIsVisible, 2000);
        } catch (Exception e) {
            Log.e(TAG, "Error starting in foreground in onStartCommand()", e);
        }
        
        // Ensure WakeLock is active (in background to not block)
        new Thread(() -> {
            if (wakeLock == null || !wakeLock.isHeld()) {
                acquireWakeLock();
            }
        }).start();

        // Check if this is a notification recreation request
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_RECREATE_NOTIFICATION.equals(action)) {
                Log.d(TAG, "Notification recreation requested");
                // Recreate notification in foreground with full version
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.notify(NOTIFICATION_ID, createNotification());
                    Log.d(TAG, "Service notification recreated");
                }
                // Check immediately and cancel fallbacks if everything is fine
                ensureNotificationIsVisible();
            } else if (ACTION_FORCE_NOTIFICATION_CHECK.equals(action)) {
                Log.d(TAG, "Force notification check requested via AlarmManager");
                ensureNotificationIsVisible();
            }
        }

        // Restart service if killed and redeliver intent
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Ultra-fast version of notification channel creation
     * Used at startup to display notification instantly
     */
    private void createNotificationChannelFast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    // Check if channel already exists and has correct importance
                    NotificationChannel existingChannel = manager.getNotificationChannel(CHANNEL_ID);
                    if (existingChannel != null) {
                        // If importance is not DEFAULT, delete and recreate
                        if (existingChannel.getImportance() != NotificationManager.IMPORTANCE_DEFAULT) {
                            Log.d(TAG, "Existing channel with wrong importance (" + existingChannel.getImportance() + "), deleting...");
                            manager.deleteNotificationChannel(CHANNEL_ID);
                            // Wait a bit for deletion to be effective
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                // Ignore
                            }
                        } else {
                            Log.d(TAG, "Notification channel already exists with IMPORTANCE_DEFAULT, no need to recreate");
                            return;
                        }
                    }
                    
                    // IMPORTANCE_DEFAULT to display notification immediately
                    // IMPORTANCE_MIN may delay notification display
                    NotificationChannel channel = new NotificationChannel(
                            CHANNEL_ID,
                            "Wake Up Service",
                            NotificationManager.IMPORTANCE_DEFAULT
                    );
                    channel.setShowBadge(false);
                    channel.enableLights(false);
                    channel.enableVibration(false);
                    manager.createNotificationChannel(channel);
                    Log.d(TAG, "Notification channel created quickly with IMPORTANCE_DEFAULT");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating channel quickly", e);
            }
        }
    }

    /**
     * Ultra-fast version of notification creation
     * Used at startup to display notification instantly
     */
    private Notification createNotificationFast() {
        long startTime = System.currentTimeMillis();
        
        long intentStart = System.currentTimeMillis();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        long intentEnd = System.currentTimeMillis();
        Log.d(TAG, "PendingIntent created in " + (intentEnd - intentStart) + " ms");

        PendingIntent deletePendingIntent = createDeletePendingIntent();

        long builderStart = System.currentTimeMillis();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Wake Up")
                .setContentText("Calendar monitoring active")
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
                    // Delete existing channel if it exists to recreate with correct
                    // importance
                    NotificationChannel existingChannel = manager.getNotificationChannel(CHANNEL_ID);
                    if (existingChannel != null) {
                        // Check if badge is enabled
                        if (existingChannel.canShowBadge()) {
                            manager.deleteNotificationChannel(CHANNEL_ID);
                            Log.d(TAG, "Old channel deleted (badge was enabled)");
                            // Wait a bit for deletion to be effective
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                // Ignore
                            }
                        } else {
                            Log.d(TAG, "Existing channel already configured without badge");
                            return; // Channel is already correct, no need to recreate
                        }
                    }

                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Wake Up Service",
                        NotificationManager.IMPORTANCE_DEFAULT // IMPORTANCE_DEFAULT for immediate display
                );
                channel.setDescription("Calendar monitoring service");
                channel.setShowBadge(false);
                channel.enableLights(false);
                channel.enableVibration(false);

                manager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created: " + CHANNEL_ID + " with DEFAULT importance");
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
                .setContentText("Calendar monitoring active")
                .setSmallIcon(R.drawable.ic_clock)
                .setContentIntent(pendingIntent)
                .setOngoing(true) // Persistent notification (cannot be dismissed)
                .setDeleteIntent(deletePendingIntent) // Detect if it is still dismissed
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(false)
                .setAutoCancel(false) // Do not auto-dismiss
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible even on locked screen
                .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE); // No notification badge

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }

        Notification notification = builder.build();
        Log.d(TAG, "Notification created with ongoing=true, deleteIntent configured");
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
                // Check if notification is still active
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
                        Log.w(TAG, "Missing notification detected, recreating...");
                        // Recreate notification
                        startForeground(NOTIFICATION_ID, createNotification());
                        Log.d(TAG, "Notification recreated successfully");
                        ServiceNotificationDismissReceiver.cancelFallback(this);
                    } else {
                        Log.d(TAG, "Notification still present");
                        ServiceNotificationDismissReceiver.cancelFallback(this);
                    }
                } else {
                    // For older versions, simply update the notification
                    manager.notify(NOTIFICATION_ID, createNotification());
                    ServiceNotificationDismissReceiver.cancelFallback(this);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking notification", e);
            // On error, try to recreate notification
            try {
                startForeground(NOTIFICATION_ID, createNotification());
            } catch (Exception e2) {
                Log.e(TAG, "Error recreating notification", e2);
            }
        }
    }

    private void checkUpcomingReminders() {
        try {
            ContentResolver contentResolver = getContentResolver();
            Calendar now = Calendar.getInstance();
            long currentTime = now.getTimeInMillis();

            // Check next 5 minutes
            long futureTime = currentTime + (5 * 60 * 1000);

            // Get all visible and synced calendars
            Set<Long> visibleCalendarIds = getVisibleCalendarIds(contentResolver);
            Log.d(TAG, "Visible calendars found: " + visibleCalendarIds.size());

            // Query for events with reminders
            Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(builder, currentTime);
            ContentUris.appendId(builder, futureTime);

            String[] projection = {
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.CALENDAR_ID
            };

            // Include all visible and synced calendars
            // CalendarContract.Instances normally includes all visible calendars,
            // but we explicitly ensure with VISIBLE = 1
            String selection = CalendarContract.Instances.BEGIN + " >= ? AND " +
                    CalendarContract.Instances.BEGIN + " <= ?";

            Cursor cursor = contentResolver.query(
                    builder.build(),
                    projection,
                    selection,
                    new String[] { String.valueOf(currentTime), String.valueOf(futureTime) },
                    CalendarContract.Instances.BEGIN + " ASC");

            if (cursor != null) {
                Set<Long> foundCalendarIds = new HashSet<>();
                while (cursor.moveToNext()) {
                    long eventId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE));
                    long begin = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN));
                    long calendarId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID));
                    foundCalendarIds.add(calendarId);

                    // Check reminders for this event
                    checkRemindersForEvent(eventId, title, begin);
                }
                cursor.close();
                
                // Log for debugging: check if all visible calendars have events
                if (visibleCalendarIds.size() > foundCalendarIds.size()) {
                    Set<Long> missingCalendars = new HashSet<>(visibleCalendarIds);
                    missingCalendars.removeAll(foundCalendarIds);
                    Log.d(TAG, "Visible calendars without events in period: " + missingCalendars);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking calendar", e);
        }
    }

    /**
     * Gets all visible and synced calendars from the device
     */
    private Set<Long> getVisibleCalendarIds(ContentResolver contentResolver) {
        Set<Long> calendarIds = new HashSet<>();
        try {
            Uri calendarsUri = CalendarContract.Calendars.CONTENT_URI;
            String[] projection = {
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.VISIBLE,
                    CalendarContract.Calendars.SYNC_EVENTS
            };
            
            // Get all visible and synced calendars
            String selection = CalendarContract.Calendars.VISIBLE + " = ? AND " +
                    CalendarContract.Calendars.SYNC_EVENTS + " = ?";
            String[] selectionArgs = { "1", "1" };
            
            Cursor cursor = contentResolver.query(
                    calendarsUri,
                    projection,
                    selection,
                    selectionArgs,
                    null);
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long calendarId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID));
                    String displayName = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME));
                    calendarIds.add(calendarId);
                    Log.d(TAG, "Calendar found: " + displayName + " (ID: " + calendarId + ")");
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving calendars", e);
        }
        return calendarIds;
    }

    private void checkRemindersForEvent(long eventId, String title, long eventStartTime) {
        try {
            ContentResolver contentResolver = getContentResolver();
            Calendar now = Calendar.getInstance();
            long currentTime = now.getTimeInMillis();

            // Check reminders for this event
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

                    // If reminder is within next 30 seconds
                    long timeDiff = reminderTime - currentTime;
                    if (timeDiff >= 0 && timeDiff <= 30000) {
                        // Create unique key for this reminder
                        String reminderKey = eventId + "_" + minutes + "_" + (reminderTime / 1000);

                        // Check if this reminder has not already been shown
                        if (!shownReminders.contains(reminderKey)) {
                            shownReminders.add(reminderKey);
                            // Show reminder activity
                            showReminderActivity(eventId, title, eventStartTime, minutes);
                            Log.d(TAG, "Reminder triggered for event " + eventId + " at " + minutes + " minutes before");

                            // Clean old reminders after 1 hour
                            if (shownReminders.size() > 100) {
                                shownReminders.clear();
                            }
                            // Do not use break here to allow all reminders to trigger
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking reminders", e);
        }
    }

    private void showReminderActivity(long eventId, String title, long eventStartTime, int minutes) {
        // Use same mechanism as test button: BroadcastReceiver
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra(ReminderActivity.EXTRA_EVENT_TITLE, title);
        intent.putExtra(ReminderActivity.EXTRA_EVENT_ID, eventId);
        intent.putExtra(ReminderActivity.EXTRA_EVENT_START_TIME, eventStartTime);

        // Use AlarmManager to ensure activity displays even if screen
        // is locked
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        // Use unique requestCode that combines eventId and minutes to avoid conflicts
        // between multiple reminders of the same event
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

        Log.d(TAG, "Reminder scheduled for event: " + title + " (ID: " + eventId + ")");
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
        // Do not cancel monitoring here as we want it to continue even if service is killed
        // ServiceKeepAliveReceiver will automatically restart the service
        Log.d(TAG, "Service destroyed");
    }
}
