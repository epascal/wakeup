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
    private static final long UPDATE_INTERVAL = 30000; // Update every 30 seconds

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

        // Configure test button
        buttonTestReminder.setOnClickListener(v -> scheduleTestReminder());

        checkPermissions();
    }

    private void checkPermissions() {
        // 1. Check calendar permission
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
        // 2. Check notification permission (Android 13+)
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
        // 3. Check overlay permission (display above other apps)
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
        // 4. Check battery optimization
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("MainActivity", "Error requesting battery optimization", e);
                }
            }
        }

        // Start service regardless
        startService();
        
        // Start periodic monitoring to ensure service remains active
        ServiceKeepAliveReceiver.startMonitoring(this);
    }

    private void startService() {
        long startTime = System.currentTimeMillis();
        Log.d("MainActivity", "startService() called at " + startTime);
        
        Intent serviceIntent = new Intent(this, CalendarMonitorService.class);
        long intentCreated = System.currentTimeMillis();
        Log.d("MainActivity", "Intent created in " + (intentCreated - startTime) + " ms");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            long serviceStart = System.currentTimeMillis();
            startForegroundService(serviceIntent);
            long serviceEnd = System.currentTimeMillis();
            Log.d("MainActivity", "startForegroundService() called in " + (serviceEnd - serviceStart) + " ms");
            Log.d("MainActivity", "startService() total: " + (serviceEnd - startTime) + " ms");
        } else {
            long serviceStart = System.currentTimeMillis();
            startService(serviceIntent);
            long serviceEnd = System.currentTimeMillis();
            Log.d("MainActivity", "startService() called in " + (serviceEnd - serviceStart) + " ms");
            Log.d("MainActivity", "startService() total: " + (serviceEnd - startTime) + " ms");
        }
        Toast.makeText(this, R.string.service_started, Toast.LENGTH_SHORT).show();

        // Start periodic event updates
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

            // Check events in next 30 days to cover long
            // reminders
            // (e.g., an event in 20 days with a 7-day reminder)
            long futureTime = currentTime + (30 * 24 * 60 * 60 * 1000L);

            // Get all visible and synced calendars
            Set<Long> visibleCalendarIds = getVisibleCalendarIds(contentResolver);
            Log.d("MainActivity", "Visible calendars found: " + visibleCalendarIds.size());

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
                // Collect ALL reminders from all events
                while (cursor.moveToNext()) {
                    long eventId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE));
                    long begin = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN));
                    long calendarId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID));
                    foundCalendarIds.add(calendarId);

                    // Check reminders for this event
                    List<EventReminder> eventReminders = getRemindersForEvent(eventId, title, begin);
                    reminders.addAll(eventReminders);
                }
                cursor.close();
                
                // Log for debugging: check if all visible calendars have events
                if (visibleCalendarIds.size() > foundCalendarIds.size()) {
                    Set<Long> missingCalendars = new HashSet<>(visibleCalendarIds);
                    missingCalendars.removeAll(foundCalendarIds);
                    Log.d("MainActivity", "Visible calendars without events in period: " + missingCalendars);
                }
            }

            // Sort by reminder time (not event time) and take the first 3
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
                    Log.d("MainActivity", "Calendar found: " + displayName + " (ID: " + calendarId + ")");
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error retrieving calendars", e);
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

                    // Keep only future reminders
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

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE d MMM 'at' HH:mm", Locale.getDefault());
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

        // Event title
        titleView.setText(reminder.title.isEmpty() ? getString(R.string.no_title) : reminder.title);

        // Reminder time
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

        // Event time
        eventTimeView.setText(getString(R.string.event_time, dateFormat.format(eventDate), reminder.reminderMinutes));

        return view;
    }

    private void scheduleTestReminder() {
        Log.d("MainActivity", "scheduleTestReminder() called");

        // Create a test reminder in 15 seconds
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e("MainActivity", "AlarmManager est null!");
            Toast.makeText(this, R.string.alarm_manager_error, Toast.LENGTH_LONG).show();
            return;
        }

        // Check permissions for exact alarms (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("MainActivity", "SCHEDULE_EXACT_ALARM permission not granted");
                Toast.makeText(this, R.string.exact_alarm_permission_required, Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
                return;
            }
        }

        // Create notification channel for test
        createTestNotificationChannel();

        // Use BroadcastReceiver to bypass Android BAL restrictions
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

        Log.d("MainActivity", "Current time: " + currentTime);
        Log.d("MainActivity", "Trigger time: " + triggerTime);
        Log.d("MainActivity", "Delay: " + (triggerTime - currentTime) + " ms");

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        triggerTime, pendingIntent);
                Log.d("MainActivity", "Alarm scheduled with setExactAndAllowWhileIdle");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                        triggerTime, pendingIntent);
                Log.d("MainActivity", "Alarm scheduled with setExact");
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP,
                        triggerTime, pendingIntent);
                Log.d("MainActivity", "Alarm scheduled with set");
            }

            Toast.makeText(this, R.string.test_reminder_scheduled, Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            Log.e("MainActivity", "Security error scheduling alarm", e);
            Toast.makeText(this, R.string.exact_alarm_permission_denied, Toast.LENGTH_LONG).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent settingsIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(settingsIntent);
            }
            return;
        } catch (Exception e) {
            Log.e("MainActivity", "Error scheduling alarm", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        // Disable button temporarily to avoid multiple clicks
        buttonTestReminder.setEnabled(false);
        buttonTestReminder.setText(R.string.test_reminder_scheduled_state);

        // Re-enable button after 20 seconds
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
            channel.setDescription("Channel for test reminders");
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
            // Whether permission is granted or not, continue
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
                    checkBatteryOptimization(); // Continue anyway
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update events when activity returns to foreground
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            loadUpcomingEvents();
        }
    }
}
