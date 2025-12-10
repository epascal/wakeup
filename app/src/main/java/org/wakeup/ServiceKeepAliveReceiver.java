package org.wakeup;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * BroadcastReceiver that periodically monitors if the service is active
 * and automatically restarts it if it has been killed by the system.
 * 
 * This receiver is triggered every 5 minutes by AlarmManager to
 * ensure that the service remains active even during the night.
 */
public class ServiceKeepAliveReceiver extends BroadcastReceiver {
    private static final String TAG = "ServiceKeepAlive";
    private static final String ACTION_KEEP_ALIVE_CHECK = "org.wakeup.KEEP_ALIVE_CHECK";
    private static final int REQUEST_CODE_KEEP_ALIVE = 9002;
    
    // Check every 5 minutes
    private static final long CHECK_INTERVAL_MS = 5 * 60 * 1000;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_KEEP_ALIVE_CHECK.equals(intent.getAction())) {
            Log.d(TAG, "Checking service status...");
            
            // Check if the service is active
            if (!isServiceRunning(context)) {
                Log.w(TAG, "Inactive service detected, restarting...");
                restartService(context);
            } else {
                Log.d(TAG, "Service active, no restart needed");
            }
            
            // Schedule the next check
            scheduleNextCheck(context);
        }
    }

    /**
     * Checks if the CalendarMonitorService is active
     */
    private boolean isServiceRunning(Context context) {
        try {
            // Check via ActivityManager if the service is running
            android.app.ActivityManager manager = (android.app.ActivityManager) 
                context.getSystemService(Context.ACTIVITY_SERVICE);
            
            if (manager != null) {
                for (android.app.ActivityManager.RunningServiceInfo service : 
                     manager.getRunningServices(Integer.MAX_VALUE)) {
                    if (CalendarMonitorService.class.getName().equals(
                            service.service.getClassName())) {
                        Log.d(TAG, "Service found active: " + service.service.getClassName());
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking service", e);
        }
        return false;
    }

    /**
     * Restarts the CalendarMonitorService
     */
    private void restartService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, CalendarMonitorService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent);
                Log.d(TAG, "Service restarted in foreground");
            } else {
                context.startService(serviceIntent);
                Log.d(TAG, "Service restarted");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restarting service", e);
        }
    }

    /**
     * Schedules the next check in CHECK_INTERVAL_MS milliseconds
     */
    private void scheduleNextCheck(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager unavailable");
            return;
        }

        // Check permission for exact alarms (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "SCHEDULE_EXACT_ALARM permission not granted, using inexact alarm");
                // Use set() instead of setExact() if permission is not granted
                Intent intent = new Intent(context, ServiceKeepAliveReceiver.class);
                intent.setAction(ACTION_KEEP_ALIVE_CHECK);
                
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context,
                        REQUEST_CODE_KEEP_ALIVE,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                long triggerAt = System.currentTimeMillis() + CHECK_INTERVAL_MS;
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                Log.d(TAG, "Inexact alarm scheduled (permission missing)");
                return;
            }
        }

        Intent intent = new Intent(context, ServiceKeepAliveReceiver.class);
        intent.setAction(ACTION_KEEP_ALIVE_CHECK);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_KEEP_ALIVE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerAt = System.currentTimeMillis() + CHECK_INTERVAL_MS;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use setExactAndAllowWhileIdle to work even in Doze mode
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                Log.d(TAG, "Next check scheduled in " + (CHECK_INTERVAL_MS / 1000) + " seconds");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                Log.d(TAG, "Next check scheduled in " + (CHECK_INTERVAL_MS / 1000) + " seconds");
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                Log.d(TAG, "Next check scheduled in " + (CHECK_INTERVAL_MS / 1000) + " seconds");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security error scheduling alarm", e);
        }
    }

    /**
     * Starts periodic monitoring of the service
     * To be called from MainActivity or when starting the service
     */
    public static void startMonitoring(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager unavailable, cannot start monitoring");
            return;
        }

        // Cancel any existing monitoring
        cancelMonitoring(context);

        // Check permission for exact alarms (Android 12+)
        boolean canScheduleExact = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canScheduleExact = alarmManager.canScheduleExactAlarms();
            if (!canScheduleExact) {
                Log.w(TAG, "SCHEDULE_EXACT_ALARM permission not granted, using inexact alarm");
            }
        }

        Intent intent = new Intent(context, ServiceKeepAliveReceiver.class);
        intent.setAction(ACTION_KEEP_ALIVE_CHECK);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_KEEP_ALIVE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerAt = System.currentTimeMillis() + CHECK_INTERVAL_MS;

        try {
            if (canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                Log.d(TAG, "Periodic monitoring started with exact alarm");
            } else if (canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                Log.d(TAG, "Periodic monitoring started with exact alarm");
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                Log.d(TAG, "Periodic monitoring started with inexact alarm (permission missing)");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security error starting monitoring", e);
        }
    }

    /**
     * Cancels periodic monitoring of the service
     */
    public static void cancelMonitoring(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        Intent intent = new Intent(context, ServiceKeepAliveReceiver.class);
        intent.setAction(ACTION_KEEP_ALIVE_CHECK);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_KEEP_ALIVE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.cancel(pendingIntent);
        Log.d(TAG, "Periodic monitoring cancelled");
    }
}

