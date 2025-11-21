package com.calendarreminder;

import android.Manifest;
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
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 101;
    private static final long UPDATE_INTERVAL = 30000; // Mettre à jour toutes les 30 secondes

    private LinearLayout linearLayoutEvents;
    private TextView textViewUpcomingTitle;
    private Handler handler;
    private Runnable updateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        linearLayoutEvents = findViewById(R.id.linearLayoutEvents);
        textViewUpcomingTitle = findViewById(R.id.textViewUpcomingTitle);
        handler = new Handler(Looper.getMainLooper());

        checkPermissions();
    }

    private void checkPermissions() {
        // Vérifier la permission de calendrier
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CALENDAR},
                    PERMISSION_REQUEST_CODE);
        } else {
            checkOverlayPermission();
        }
    }

    private void checkOverlayPermission() {
        // Vérifier la permission d'affichage au-dessus d'autres applications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
            } else {
                startService();
            }
        } else {
            startService();
        }
    }

    private void startService() {
        Intent serviceIntent = new Intent(this, CalendarMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Service de surveillance du calendrier démarré", Toast.LENGTH_SHORT).show();
        
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
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
            
            // Vérifier les événements dans les 7 prochains jours
            long futureTime = currentTime + (7 * 24 * 60 * 60 * 1000L);

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
                while (cursor.moveToNext() && reminders.size() < 3) {
                    long eventId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE));
                    long begin = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN));
                    
                    // Vérifier les rappels pour cet événement
                    List<EventReminder> eventReminders = getRemindersForEvent(eventId, title, begin);
                    reminders.addAll(eventReminders);
                }
                cursor.close();
            }
            
            // Trier par heure de rappel et prendre les 3 premiers
            reminders.sort((a, b) -> Long.compare(a.reminderTime, b.reminderTime));
            if (reminders.size() > 3) {
                reminders = reminders.subList(0, 3);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return reminders;
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
                    null
            );

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
        LinearLayout eventLayout = new LinearLayout(this);
        eventLayout.setOrientation(LinearLayout.VERTICAL);
        eventLayout.setPadding(16, 12, 16, 12);
        eventLayout.setBackgroundResource(android.R.drawable.dialog_holo_dark_frame);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 12);
        eventLayout.setLayoutParams(params);
        
        // Titre de l'événement
        TextView titleView = new TextView(this);
        titleView.setText(reminder.title.isEmpty() ? "(Sans titre)" : reminder.title);
        titleView.setTextColor(getResources().getColor(android.R.color.white));
        titleView.setTextSize(16);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        eventLayout.addView(titleView);
        
        // Heure du rappel
        TextView reminderTimeView = new TextView(this);
        Date reminderDate = new Date(reminder.reminderTime);
        Date eventDate = new Date(reminder.eventStartTime);
        
        Calendar now = Calendar.getInstance();
        Calendar reminderCal = Calendar.getInstance();
        reminderCal.setTime(reminderDate);
        Calendar eventCal = Calendar.getInstance();
        eventCal.setTime(eventDate);
        
        String reminderText;
        long diffMinutes = (reminder.reminderTime - now.getTimeInMillis()) / (60 * 1000);
        
        if (diffMinutes < 60) {
            reminderText = "Rappel dans " + diffMinutes + " min";
        } else if (diffMinutes < 1440) {
            long hours = diffMinutes / 60;
            long mins = diffMinutes % 60;
            reminderText = "Rappel dans " + hours + "h" + (mins > 0 ? mins + "min" : "");
        } else {
            reminderText = "Rappel le " + dateFormat.format(reminderDate);
        }
        
        reminderTimeView.setText(reminderText);
        reminderTimeView.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
        reminderTimeView.setTextSize(14);
        reminderTimeView.setPadding(0, 4, 0, 0);
        eventLayout.addView(reminderTimeView);
        
        // Heure de l'événement
        TextView eventTimeView = new TextView(this);
        eventTimeView.setText("Événement: " + dateFormat.format(eventDate));
        eventTimeView.setTextColor(getResources().getColor(android.R.color.darker_gray));
        eventTimeView.setTextSize(12);
        eventTimeView.setPadding(0, 4, 0, 0);
        eventLayout.addView(eventTimeView);
        
        return eventLayout;
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
                checkOverlayPermission();
            } else {
                Toast.makeText(this, "Permission calendrier requise pour fonctionner", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    startService();
                } else {
                    Toast.makeText(this, "Permission d'affichage requise", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Mettre à jour les événements quand l'activité revient au premier plan
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED) {
            loadUpcomingEvents();
        }
    }
}

