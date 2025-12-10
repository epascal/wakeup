package org.wakeup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "System boot detected, restarting service...");
            
            Intent serviceIntent = new Intent(context, CalendarMonitorService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            
            // Start periodic monitoring to ensure service remains active
            ServiceKeepAliveReceiver.startMonitoring(context);
            Log.d(TAG, "Service and monitoring started after reboot");
        }
    }
}

