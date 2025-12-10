package org.wakeup;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
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

    private static final String TAG = "ReminderActivity";
    private static final long VIBRATION_DURATION = 2 * 60 * 1000L; // 2 minutes maximum
    // Optimized pattern for alarms: 3 short vibrations (300ms) with pauses (200ms), then long pause (800ms)
    // Format: {initial delay, vibration1, pause1, vibration2, pause2, vibration3, pause3, ...}
    // This pattern is repeated in loop for better perception
    private static final long[] VIBRATION_PATTERN = {
        0,      // Start immediately
        300,    // Vibrate 300ms
        200,    // Pause 200ms
        300,    // Vibrate 300ms
        200,    // Pause 200ms
        300,    // Vibrate 300ms
        800     // Long pause 800ms before repeating
    };
    
    private Vibrator vibrator;
    private Handler vibrationHandler;
    private Runnable stopVibrationRunnable;
    private long vibrationStartTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "ReminderActivity.onCreate() called");
        Log.d(TAG, "Intent: " + getIntent());
        Log.d(TAG, "Extras: " + getIntent().getExtras());

        // Turn screen on and unlock if necessary
        turnScreenOn();
        
        setContentView(R.layout.activity_reminder);

        // Get data from intent
        eventTitle = getIntent().getStringExtra(EXTRA_EVENT_TITLE);
        eventId = getIntent().getLongExtra(EXTRA_EVENT_ID, -1);
        eventStartTime = getIntent().getLongExtra(EXTRA_EVENT_START_TIME, 0);
        
        Log.d(TAG, "Titre: " + eventTitle);
        Log.d(TAG, "Event ID: " + eventId);
        Log.d(TAG, "Event Start Time: " + eventStartTime);

        initViews();
        setupListeners();
        startVibration();
        
        Log.d(TAG, "ReminderActivity initialized successfully");
    }

    private void startVibration() {
        try {
            // Obtenir le Vibrator selon la version d'Android
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = vibratorManager.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
            
            if (vibrator == null || !vibrator.hasVibrator()) {
                Log.w(TAG, "Phone does not support vibration");
                return;
            }
            
            vibrationStartTime = System.currentTimeMillis();
            vibrationHandler = new Handler(Looper.getMainLooper());
            
            // Repetitive vibration pattern
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect vibrationEffect = VibrationEffect.createWaveform(
                        VIBRATION_PATTERN,
                        0 // Repeat from index 0
                );
                vibrator.vibrate(vibrationEffect);
            } else {
                vibrator.vibrate(VIBRATION_PATTERN, 0);
            }
            
            Log.d(TAG, "Vibration started");
            
            // Stop vibration after maximum 2 minutes
            stopVibrationRunnable = new Runnable() {
                @Override
                public void run() {
                    stopVibration();
                    Log.d(TAG, "Vibration stopped automatically after 2 minutes");
                }
            };
            vibrationHandler.postDelayed(stopVibrationRunnable, VIBRATION_DURATION);
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting vibration", e);
        }
    }

    private void stopVibration() {
        if (vibrator != null) {
            try {
                vibrator.cancel();
                Log.d(TAG, "Vibration stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping vibration", e);
            }
        }
        
        if (vibrationHandler != null && stopVibrationRunnable != null) {
            vibrationHandler.removeCallbacks(stopVibrationRunnable);
        }
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
                "WakeUp::WakeLock");
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
        buttonReminder5m.setOnClickListener(v -> {
            stopVibration();
            scheduleReminder(5);
        });
        buttonReminder10m.setOnClickListener(v -> {
            stopVibration();
            scheduleReminder(10);
        });
        buttonReminder30m.setOnClickListener(v -> {
            stopVibration();
            scheduleReminder(30);
        });
        buttonReminder1h.setOnClickListener(v -> {
            stopVibration();
            scheduleReminder(60);
        });
        buttonDone.setOnClickListener(v -> {
            stopVibration();
            finish();
        });
    }

    private void scheduleReminder(int minutes) {
        Log.d(TAG, "Scheduling reminder in " + minutes + " minutes");
        
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null!");
            return;
        }
        
        // Calculate new event start time (even if event has passed)
        // Add minutes to current time for next reminder
        long newEventStartTime = System.currentTimeMillis() + (minutes * 60 * 1000L);
        
        // Use ReminderReceiver as for other reminders
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra(EXTRA_EVENT_TITLE, eventTitle);
        intent.putExtra(EXTRA_EVENT_ID, eventId);
        intent.putExtra(EXTRA_EVENT_START_TIME, newEventStartTime);
        
        // Use unique ID based on eventId and minutes to avoid conflicts
        int requestCode = (int) (eventId + minutes + System.currentTimeMillis() % 100000);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, minutes);
        long triggerTime = calendar.getTimeInMillis();
        
        Log.d(TAG, "Reminder scheduled for: " + triggerTime + " (in " + minutes + " minutes)");

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        triggerTime, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                        triggerTime, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP,
                        triggerTime, pendingIntent);
            }
            
            Log.d(TAG, "Reminder scheduled successfully");
        } catch (SecurityException e) {
            Log.e(TAG, "Security error scheduling reminder", e);
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling reminder", e);
        }

        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVibration();
        Log.d(TAG, "ReminderActivity destroyed, vibration stopped");
    }
}

