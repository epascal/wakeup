package com.calendarreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;

public class ReminderActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_TITLE = "event_title";
    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_EVENT_START_TIME = "event_start_time";

    private TextView textViewEventTitle;
    private Button buttonReminder5m, buttonReminder10m, buttonReminder30m, buttonReminder1h, buttonDone;
    private String eventTitle;
    private long eventId;
    private long eventStartTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Activer l'écran et déverrouiller si nécessaire
        turnScreenOn();
        
        setContentView(R.layout.activity_reminder);

        // Récupérer les données de l'intent
        eventTitle = getIntent().getStringExtra(EXTRA_EVENT_TITLE);
        eventId = getIntent().getLongExtra(EXTRA_EVENT_ID, -1);
        eventStartTime = getIntent().getLongExtra(EXTRA_EVENT_START_TIME, 0);

        initViews();
        setupListeners();
    }

    private void turnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
            getWindow().setAttributes(params);
        }

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CalendarReminder::WakeLock");
        wakeLock.acquire(10 * 60 * 1000L); // 10 minutes
    }

    private void initViews() {
        textViewEventTitle = findViewById(R.id.textViewEventTitle);
        buttonReminder5m = findViewById(R.id.buttonReminder5m);
        buttonReminder10m = findViewById(R.id.buttonReminder10m);
        buttonReminder30m = findViewById(R.id.buttonReminder30m);
        buttonReminder1h = findViewById(R.id.buttonReminder1h);
        buttonDone = findViewById(R.id.buttonDone);

        if (eventTitle != null) {
            textViewEventTitle.setText(eventTitle);
        }
    }

    private void setupListeners() {
        buttonReminder5m.setOnClickListener(v -> scheduleReminder(5));
        buttonReminder10m.setOnClickListener(v -> scheduleReminder(10));
        buttonReminder30m.setOnClickListener(v -> scheduleReminder(30));
        buttonReminder1h.setOnClickListener(v -> scheduleReminder(60));
        buttonDone.setOnClickListener(v -> finish());
    }

    private void scheduleReminder(int minutes) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderActivity.class);
        intent.putExtra(EXTRA_EVENT_TITLE, eventTitle);
        intent.putExtra(EXTRA_EVENT_ID, eventId);
        intent.putExtra(EXTRA_EVENT_START_TIME, eventStartTime);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) (eventId + minutes),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, minutes);

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

        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}

