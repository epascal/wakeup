package com.calendarreminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ServiceNotificationDismissReceiver extends BroadcastReceiver {
    private static final String TAG = "ServiceNotifDismiss";

    public static final String ACTION_SERVICE_NOTIFICATION_DISMISSED = "com.calendarreminder.SERVICE_NOTIFICATION_DISMISSED";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Service notification dismissed, recreating...");

        if (ACTION_SERVICE_NOTIFICATION_DISMISSED.equals(intent.getAction())) {
            // Relancer le service pour qu'il recrée la notification
            Intent serviceIntent = new Intent(context, CalendarMonitorService.class);
            serviceIntent.setAction("RECREATE_NOTIFICATION");

            // Démarrer le service (il est déjà en cours, donc onStartCommand sera appelé)
            context.startService(serviceIntent);

            Log.d(TAG, "Service notification recreation requested");
        }
    }
}
