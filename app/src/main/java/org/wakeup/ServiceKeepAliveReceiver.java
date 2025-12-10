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
 * BroadcastReceiver qui surveille périodiquement si le service est actif
 * et le redémarre automatiquement s'il a été tué par le système.
 * 
 * Ce receiver est déclenché toutes les 5 minutes par AlarmManager pour
 * s'assurer que le service reste actif même pendant la nuit.
 */
public class ServiceKeepAliveReceiver extends BroadcastReceiver {
    private static final String TAG = "ServiceKeepAlive";
    private static final String ACTION_KEEP_ALIVE_CHECK = "org.wakeup.KEEP_ALIVE_CHECK";
    private static final int REQUEST_CODE_KEEP_ALIVE = 9002;
    
    // Vérifier toutes les 5 minutes
    private static final long CHECK_INTERVAL_MS = 5 * 60 * 1000;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_KEEP_ALIVE_CHECK.equals(intent.getAction())) {
            Log.d(TAG, "Vérification de l'état du service...");
            
            // Vérifier si le service est actif
            if (!isServiceRunning(context)) {
                Log.w(TAG, "Service non actif détecté, redémarrage...");
                restartService(context);
            } else {
                Log.d(TAG, "Service actif, pas de redémarrage nécessaire");
            }
            
            // Reprogrammer la prochaine vérification
            scheduleNextCheck(context);
        }
    }

    /**
     * Vérifie si le service CalendarMonitorService est actif
     */
    private boolean isServiceRunning(Context context) {
        try {
            // Vérifier via ActivityManager si le service est en cours d'exécution
            android.app.ActivityManager manager = (android.app.ActivityManager) 
                context.getSystemService(Context.ACTIVITY_SERVICE);
            
            if (manager != null) {
                for (android.app.ActivityManager.RunningServiceInfo service : 
                     manager.getRunningServices(Integer.MAX_VALUE)) {
                    if (CalendarMonitorService.class.getName().equals(
                            service.service.getClassName())) {
                        Log.d(TAG, "Service trouvé actif: " + service.service.getClassName());
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la vérification du service", e);
        }
        return false;
    }

    /**
     * Redémarre le service CalendarMonitorService
     */
    private void restartService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, CalendarMonitorService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent);
                Log.d(TAG, "Service redémarré en foreground");
            } else {
                context.startService(serviceIntent);
                Log.d(TAG, "Service redémarré");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du redémarrage du service", e);
        }
    }

    /**
     * Programme la prochaine vérification dans CHECK_INTERVAL_MS millisecondes
     */
    private void scheduleNextCheck(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager indisponible");
            return;
        }

        // Vérifier la permission pour les alarmes exactes (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Permission SCHEDULE_EXACT_ALARM non accordée, utilisation d'alarme inexacte");
                // Utiliser set() au lieu de setExact() si la permission n'est pas accordée
                Intent intent = new Intent(context, ServiceKeepAliveReceiver.class);
                intent.setAction(ACTION_KEEP_ALIVE_CHECK);
                
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context,
                        REQUEST_CODE_KEEP_ALIVE,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                long triggerAt = System.currentTimeMillis() + CHECK_INTERVAL_MS;
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                Log.d(TAG, "Alarme inexacte programmée (permission manquante)");
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
                // Utiliser setExactAndAllowWhileIdle pour fonctionner même en mode Doze
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                Log.d(TAG, "Prochaine vérification programmée dans " + (CHECK_INTERVAL_MS / 1000) + " secondes");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                Log.d(TAG, "Prochaine vérification programmée dans " + (CHECK_INTERVAL_MS / 1000) + " secondes");
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                Log.d(TAG, "Prochaine vérification programmée dans " + (CHECK_INTERVAL_MS / 1000) + " secondes");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Erreur de sécurité lors de la programmation de l'alarme", e);
        }
    }

    /**
     * Démarre la surveillance périodique du service
     * À appeler depuis MainActivity ou lors du démarrage du service
     */
    public static void startMonitoring(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager indisponible, impossible de démarrer la surveillance");
            return;
        }

        // Annuler toute surveillance existante
        cancelMonitoring(context);

        // Vérifier la permission pour les alarmes exactes (Android 12+)
        boolean canScheduleExact = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canScheduleExact = alarmManager.canScheduleExactAlarms();
            if (!canScheduleExact) {
                Log.w(TAG, "Permission SCHEDULE_EXACT_ALARM non accordée, utilisation d'alarme inexacte");
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
                Log.d(TAG, "Surveillance périodique démarrée avec alarme exacte");
            } else if (canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                Log.d(TAG, "Surveillance périodique démarrée avec alarme exacte");
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                Log.d(TAG, "Surveillance périodique démarrée avec alarme inexacte (permission manquante)");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Erreur de sécurité lors du démarrage de la surveillance", e);
        }
    }

    /**
     * Annule la surveillance périodique du service
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
        Log.d(TAG, "Surveillance périodique annulée");
    }
}

