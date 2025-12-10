package org.wakeup;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 102;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 101;
    private static final long UPDATE_INTERVAL = 30000; // Mettre à jour toutes les 30 secondes

    private LinearLayout linearLayoutEvents;
    private TextView textViewUpcomingTitle;
    private Button buttonTestReminder;
    private Handler handler;
    private Runnable updateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        linearLayoutEvents = findViewById(R.id.linearLayoutEvents);
        textViewUpcomingTitle = findViewById(R.id.textViewUpcomingTitle);
        buttonTestReminder = findViewById(R.id.buttonTestReminder);
        handler = new Handler(Looper.getMainLooper());

        // Configurer le bouton de test
        buttonTestReminder.setOnClickListener(v -> scheduleTestReminder());

        checkPermissions();
    }

    private void checkPermissions() {
        // 1. Vérifier la permission de calendrier
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.READ_CALENDAR },
                    PERMISSION_REQUEST_CODE);
        } else {
            checkNotificationPermission();
        }
    }

    private void checkNotificationPermission() {
        // 2. Vérifier la permission de notification (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.POST_NOTIFICATIONS },
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            } else {
                checkOverlayPermission();
            }
        } else {
            checkOverlayPermission();
        }
    }

    private void checkOverlayPermission() {
        // 3. Vérifier la permission d'affichage au-dessus d'autres applications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                showOverlayPermissionDialog();
            } else {
                checkBatteryOptimization();
            }
        } else {
            checkBatteryOptimization();
        }
    }

    private void showOverlayPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.overlay_permission_title)
                .setMessage(R.string.overlay_permission_message)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    Uri uri = Uri.parse("package:" + getPackageName());
                    intent.setData(uri);
                    try {
                        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
                    } catch (Exception e) {
                        // Fallback: open general settings if specific intent fails
                        Intent fallbackIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        startActivityForResult(fallbackIntent, OVERLAY_PERMISSION_REQUEST_CODE);
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show();
                    checkBatteryOptimization(); // Continue anyway
                })
                .setCancelable(false)
                .show();
    }

    private void checkBatteryOptimization() {
        // 4. Vérifier l'optimisation de la batterie
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("MainActivity", "Erreur lors de la demande d'optimisation batterie", e);
                }
            }
        }

        // Démarrer le service quoi qu'il arrive
        startService();
        
        // Démarrer la surveillance périodique pour s'assurer que le service reste actif
        ServiceKeepAliveReceiver.startMonitoring(this);
    }

    private void startService() {
        long startTime = System.currentTimeMillis();
        Log.d("MainActivity", "startService() appelé à " + startTime);
        
        Intent serviceIntent = new Intent(this, CalendarMonitorService.class);
        long intentCreated = System.currentTimeMillis();
        Log.d("MainActivity", "Intent créé en " + (intentCreated - startTime) + " ms");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            long serviceStart = System.currentTimeMillis();
            startForegroundService(serviceIntent);
            long serviceEnd = System.currentTimeMillis();
            Log.d("MainActivity", "startForegroundService() appelé en " + (serviceEnd - serviceStart) + " ms");
            Log.d("MainActivity", "startService() total: " + (serviceEnd - startTime) + " ms");
        } else {
            long serviceStart = System.currentTimeMillis();
            startService(serviceIntent);
            long serviceEnd = System.currentTimeMillis();
            Log.d("MainActivity", "startService() appelé en " + (serviceEnd - serviceStart) + " ms");
            Log.d("MainActivity", "startService() total: " + (serviceEnd - startTime) + " ms");
        }
        Toast.makeText(this, R.string.service_started, Toast.LENGTH_SHORT).show();

        // Démarrer la mise à jour périodique des événements
        loadUpcomingEvents();
        startPeriodicUpdate();
    }

    private void startPeriodicUpdate() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                loadUpcomingEvents();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        handler.postDelayed(updateRunnable, UPDATE_INTERVAL);
    }

    private void loadUpcomingEvents() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        new Thread(() -> {
            List<EventReminder> upcomingReminders = getUpcomingReminders();
            runOnUiThread(() -> displayUpcomingReminders(upcomingReminders));
        }).start();
    }

    private List<EventReminder> getUpcomingReminders() {
        List<EventReminder> reminders = new ArrayList<>();

        try {
            ContentResolver contentResolver = getContentResolver();
            Calendar now = Calendar.getInstance();
            long currentTime = now.getTimeInMillis();

            // Vérifier les événements dans les 30 prochains jours pour couvrir les rappels
            // longs
            // (par exemple, un événement dans 20 jours avec un rappel de 7 jours)
            long futureTime = currentTime + (30 * 24 * 60 * 60 * 1000L);

            // Récupérer tous les calendriers visibles et synchronisés
            Set<Long> visibleCalendarIds = getVisibleCalendarIds(contentResolver);
            Log.d("MainActivity", "Calendriers visibles trouvés: " + visibleCalendarIds.size());

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

            // Inclure tous les calendriers visibles et synchronisés
            // CalendarContract.Instances inclut normalement tous les calendriers visibles,
            // mais on s'assure explicitement avec VISIBLE = 1
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
                // Collecter TOUS les rappels de tous les événements
                while (cursor.moveToNext()) {
                    long eventId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE));
                    long begin = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN));
                    long calendarId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID));
                    foundCalendarIds.add(calendarId);

                    // Vérifier les rappels pour cet événement
                    List<EventReminder> eventReminders = getRemindersForEvent(eventId, title, begin);
                    reminders.addAll(eventReminders);
                }
                cursor.close();
                
                // Log pour déboguer : vérifier si tous les calendriers visibles ont des événements
                if (visibleCalendarIds.size() > foundCalendarIds.size()) {
                    Set<Long> missingCalendars = new HashSet<>(visibleCalendarIds);
                    missingCalendars.removeAll(foundCalendarIds);
                    Log.d("MainActivity", "Calendriers visibles sans événements dans la période: " + missingCalendars);
                }
            }

            // Trier par heure de rappel (pas par heure d'événement) et prendre les 3
            // premiers
            reminders.sort((a, b) -> Long.compare(a.reminderTime, b.reminderTime));
            if (reminders.size() > 3) {
                reminders = reminders.subList(0, 3);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return reminders;
    }

    /**
     * Récupère tous les calendriers visibles et synchronisés du dispositif
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
            
            // Récupérer tous les calendriers visibles et synchronisés
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
                    Log.d("MainActivity", "Calendrier trouvé: " + displayName + " (ID: " + calendarId + ")");
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Erreur lors de la récupération des calendriers", e);
        }
        return calendarIds;
    }

    private List<EventReminder> getRemindersForEvent(long eventId, String title, long eventStartTime) {
        List<EventReminder> reminders = new ArrayList<>();

        try {
            ContentResolver contentResolver = getContentResolver();
            Calendar now = Calendar.getInstance();
            long currentTime = now.getTimeInMillis();

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

                    // Ne garder que les rappels futurs
                    if (reminderTime > currentTime) {
                        reminders.add(new EventReminder(title, eventStartTime, reminderTime, minutes));
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return reminders;
    }

    private void displayUpcomingReminders(List<EventReminder> reminders) {
        linearLayoutEvents.removeAllViews();

        if (reminders.isEmpty()) {
            textViewUpcomingTitle.setVisibility(View.GONE);
            return;
        }

        textViewUpcomingTitle.setVisibility(View.VISIBLE);

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE d MMM 'à' HH:mm", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        for (EventReminder reminder : reminders) {
            View eventView = createEventView(reminder, dateFormat, timeFormat);
            linearLayoutEvents.addView(eventView);
        }
    }

    private View createEventView(EventReminder reminder, SimpleDateFormat dateFormat, SimpleDateFormat timeFormat) {
        View view = getLayoutInflater().inflate(R.layout.item_event, linearLayoutEvents, false);

        TextView titleView = view.findViewById(R.id.textViewTitle);
        TextView reminderTimeView = view.findViewById(R.id.textViewReminderTime);
        TextView eventTimeView = view.findViewById(R.id.textViewEventTime);

        // Titre de l'événement
        titleView.setText(reminder.title.isEmpty() ? getString(R.string.no_title) : reminder.title);

        // Heure du rappel
        Date reminderDate = new Date(reminder.reminderTime);
        Date eventDate = new Date(reminder.eventStartTime);

        Calendar now = Calendar.getInstance();
        long currentTime = now.getTimeInMillis();
        long diffMinutes = (reminder.reminderTime - currentTime) / (60 * 1000);

        String reminderText;
        if (diffMinutes < 0) {
            reminderText = getString(R.string.reminder_past);
        } else if (diffMinutes < 1) {
            reminderText = getString(R.string.reminder_now);
        } else if (diffMinutes < 60) {
            reminderText = getString(R.string.reminder_at, timeFormat.format(reminderDate), diffMinutes);
        } else if (diffMinutes < 1440) {
            long hours = diffMinutes / 60;
            long mins = diffMinutes % 60;
            String minsStr = mins > 0 ? mins + "min" : "";
            reminderText = getString(R.string.reminder_at_hours, timeFormat.format(reminderDate), hours, minsStr);
        } else {
            reminderText = getString(R.string.reminder_on, dateFormat.format(reminderDate));
        }

        reminderTimeView.setText(reminderText);

        // Heure de l'événement
        eventTimeView.setText(getString(R.string.event_time, dateFormat.format(eventDate), reminder.reminderMinutes));

        return view;
    }

    private void scheduleTestReminder() {
        Log.d("MainActivity", "scheduleTestReminder() appelé");

        // Créer un rappel de test dans 15 secondes
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e("MainActivity", "AlarmManager est null!");
            Toast.makeText(this, R.string.alarm_manager_error, Toast.LENGTH_LONG).show();
            return;
        }

        // Vérifier les permissions pour les alarmes exactes (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("MainActivity", "Permission SCHEDULE_EXACT_ALARM non accordée");
                Toast.makeText(this, R.string.exact_alarm_permission_required, Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
                return;
            }
        }

        // Créer le canal de notification pour le test
        createTestNotificationChannel();

        // Utiliser un BroadcastReceiver pour contourner les restrictions BAL d'Android
        // 15
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra(ReminderActivity.EXTRA_EVENT_TITLE, getString(R.string.test_reminder_title));
        intent.putExtra(ReminderActivity.EXTRA_EVENT_ID, -999L); // ID de test
        intent.putExtra(ReminderActivity.EXTRA_EVENT_START_TIME, System.currentTimeMillis() + 15000);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                99999, // ID unique pour le test
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        long currentTime = calendar.getTimeInMillis();
        calendar.add(Calendar.SECOND, 15);
        long triggerTime = calendar.getTimeInMillis();

        Log.d("MainActivity", "Heure actuelle: " + currentTime);
        Log.d("MainActivity", "Heure de déclenchement: " + triggerTime);
        Log.d("MainActivity", "Délai: " + (triggerTime - currentTime) + " ms");

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        triggerTime, pendingIntent);
                Log.d("MainActivity", "Alarme programmée avec setExactAndAllowWhileIdle");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                        triggerTime, pendingIntent);
                Log.d("MainActivity", "Alarme programmée avec setExact");
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP,
                        triggerTime, pendingIntent);
                Log.d("MainActivity", "Alarme programmée avec set");
            }

            Toast.makeText(this, R.string.test_reminder_scheduled, Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            Log.e("MainActivity", "Erreur de sécurité lors de la programmation de l'alarme", e);
            Toast.makeText(this, R.string.exact_alarm_permission_denied, Toast.LENGTH_LONG).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent settingsIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(settingsIntent);
            }
            return;
        } catch (Exception e) {
            Log.e("MainActivity", "Erreur lors de la programmation de l'alarme", e);
            Toast.makeText(this, "Erreur: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        // Désactiver le bouton temporairement pour éviter les clics multiples
        buttonTestReminder.setEnabled(false);
        buttonTestReminder.setText(R.string.test_reminder_scheduled_state);

        // Réactiver le bouton après 20 secondes
        handler.postDelayed(() -> {
            buttonTestReminder.setEnabled(true);
            buttonTestReminder.setText(R.string.test_reminder_button);
        }, 20000);
    }

    private void createTestNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "TEST_REMINDER_CHANNEL",
                    "Test Reminder Channel",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Canal pour les rappels de test");
            channel.enableLights(true);
            channel.enableVibration(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    private static class EventReminder {
        String title;
        long eventStartTime;
        long reminderTime;
        int reminderMinutes;

        EventReminder(String title, long eventStartTime, long reminderTime, int reminderMinutes) {
            this.title = title;
            this.eventStartTime = eventStartTime;
            this.reminderTime = reminderTime;
            this.reminderMinutes = reminderMinutes;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkNotificationPermission();
            } else {
                Toast.makeText(this, R.string.calendar_permission_denied, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            // Que la permission soit accordée ou non, on passe à la suite
            checkOverlayPermission();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    checkBatteryOptimization();
                } else {
                    Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show();
                    checkBatteryOptimization(); // Continuer quand même
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Mettre à jour les événements quand l'activité revient au premier plan
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            loadUpcomingEvents();
        }
    }
}
