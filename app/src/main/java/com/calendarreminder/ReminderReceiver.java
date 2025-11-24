package com.calendarreminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "ReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "ReminderReceiver.onReceive() appelé");
        Log.d(TAG, "Intent: " + intent);
        
        // Récupérer les données de l'intent
        String eventTitle = intent.getStringExtra(ReminderActivity.EXTRA_EVENT_TITLE);
        long eventId = intent.getLongExtra(ReminderActivity.EXTRA_EVENT_ID, -1);
        long eventStartTime = intent.getLongExtra(ReminderActivity.EXTRA_EVENT_START_TIME, 0);
        
        Log.d(TAG, "Titre: " + eventTitle);
        Log.d(TAG, "Event ID: " + eventId);
        Log.d(TAG, "Event Start Time: " + eventStartTime);
        
        // Créer un intent pour lancer ReminderActivity
        Intent reminderIntent = new Intent(context, ReminderActivity.class);
        reminderIntent.putExtra(ReminderActivity.EXTRA_EVENT_TITLE, eventTitle);
        reminderIntent.putExtra(ReminderActivity.EXTRA_EVENT_ID, eventId);
        reminderIntent.putExtra(ReminderActivity.EXTRA_EVENT_START_TIME, eventStartTime);
        reminderIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        
        Log.d(TAG, "Lancement de ReminderActivity");
        context.startActivity(reminderIntent);
        Log.d(TAG, "ReminderActivity lancée");
    }
}

