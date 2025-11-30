package org.wakeup;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class ServiceNotificationDismissReceiver extends BroadcastReceiver {
    private static final String TAG = "ServiceNotifDismiss";

    private static final int REQUEST_CODE_FALLBACK = 9001;

    public static final String ACTION_SERVICE_NOTIFICATION_DISMISSED = "org.wakeup.SERVICE_NOTIFICATION_DISMISSED";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Service notification dismissed, recreating...");

        if (ACTION_SERVICE_NOTIFICATION_DISMISSED.equals(intent.getAction())) {
            // Relancer le service pour qu'il recrée la notification
            Intent serviceIntent = new Intent(context, CalendarMonitorService.class);
            serviceIntent.setAction(CalendarMonitorService.ACTION_RECREATE_NOTIFICATION);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            scheduleFallback(context);

            Log.d(TAG, "Service notification recreation requested");
        }
    }

    private static PendingIntent createFallbackPendingIntent(Context context) {
        Intent forceIntent = new Intent(context, CalendarMonitorService.class);
        forceIntent.setAction(CalendarMonitorService.ACTION_FORCE_NOTIFICATION_CHECK);

        return PendingIntent.getService(
                context,
                REQUEST_CODE_FALLBACK,
                forceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleFallback(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager indisponible, impossible de planifier le fallback");
            return;
        }

        cancelFallback(context);

        PendingIntent pendingIntent = createFallbackPendingIntent(context);
        long triggerAt = System.currentTimeMillis() + 5000; // 5 secondes

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }

        Log.d(TAG, "Fallback AlarmManager planifié dans 5s");
    }

    public static void cancelFallback(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent = createFallbackPendingIntent(context);
        alarmManager.cancel(pendingIntent);
        Log.d(TAG, "Fallback AlarmManager annulé");
    }
}
