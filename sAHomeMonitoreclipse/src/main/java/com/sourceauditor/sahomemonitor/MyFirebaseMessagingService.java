package com.sourceauditor.sahomemonitor;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import static com.sourceauditor.sahomemonitor.MainActivity.ACTION_HOME_NOFICATION;
import static com.sourceauditor.sahomemonitor.MainActivity.ACTION_UPDATDIRECT_NOTIFICATION;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "MyFirebaseMsgService";
    public static final int NOTIFICATION_ID = 1;

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    // [START receive_message]
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        String msg = "Home Alert (no message)";
        // Check if message contains a data payload.
        Map<String, String> data = remoteMessage.getData();
        if (data != null && data.size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            msg = data.get(MainActivity.EXTRA_MESSAGE_FROM_HOME);
            if (msg == null || msg.trim().isEmpty()) {
                msg = "Home Alert (no message)";
            }
            updatePrefs(data);
        }

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
        }

        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
        sendNotification(msg, data);
    }

    /**
     * Update the preferences with any data received from the server
     * @param data data map from the message
     */
    private void updatePrefs(Map<String, String> data) {
        if (data != null && data.size() > 0) {
            SharedPreferences.Editor prefsEditor = getSharedPreferences(MainActivity.class.getSimpleName(),
                    Context.MODE_PRIVATE).edit();
            String homeMonitorUrl = data.get(MainActivity.EXTRA_HOME_MONITOR_URL);
            if (homeMonitorUrl != null) {
                homeMonitorUrl = homeMonitorUrl.trim();
                if (!homeMonitorUrl.isEmpty()) {
                    prefsEditor.putString(MainActivity.EXTRA_HOME_MONITOR_URL, homeMonitorUrl);
                }
            }

            String homeMonitorAudioUrl = data.get(MainActivity.EXTRA_HOME_MONITOR_AUDIO_URL);
            if (homeMonitorAudioUrl != null) {
                homeMonitorAudioUrl = homeMonitorAudioUrl.trim();
                if (!homeMonitorAudioUrl.isEmpty()) {
                    prefsEditor.putString(MainActivity.EXTRA_HOME_MONITOR_AUDIO_URL, homeMonitorAudioUrl);
                }
            }

            String msgFromHome = data.get(MainActivity.EXTRA_MESSAGE_FROM_HOME);
            if (msgFromHome != null) {
                msgFromHome = msgFromHome.trim();
                if (!msgFromHome.isEmpty()) {
                    prefsEditor.putString(MainActivity.EXTRA_MESSAGE_FROM_HOME, msgFromHome);
                }
            }
            prefsEditor.apply();
        }
    }

    /**
     * Adds parameter data found in the message data map to the intent extras
     * @param data Data map from the message
     * @param intent Intent for the main activity to recieve the notification
     */
    private void addDataToIntent(Map<String, String> data, Intent intent) {
        if (data != null) {
            String homeMonitorUrl = data.get(MainActivity.EXTRA_HOME_MONITOR_URL);
            if (homeMonitorUrl != null) {
                homeMonitorUrl = homeMonitorUrl.trim();
                if (!homeMonitorUrl.isEmpty()) {
                    intent.putExtra(MainActivity.EXTRA_HOME_MONITOR_URL, homeMonitorUrl);
                }
            }

            String homeMonitorAudioUrl = data.get(MainActivity.EXTRA_HOME_MONITOR_AUDIO_URL);
            if (homeMonitorAudioUrl != null) {
                homeMonitorAudioUrl = homeMonitorAudioUrl.trim();
                if (!homeMonitorAudioUrl.isEmpty()) {
                    intent.putExtra(MainActivity.EXTRA_HOME_MONITOR_AUDIO_URL, homeMonitorAudioUrl);
                }
            }

            String msgFromHome = data.get(MainActivity.EXTRA_MESSAGE_FROM_HOME);
            if (msgFromHome != null) {
                msgFromHome = msgFromHome.trim();
                if (!msgFromHome.isEmpty()) {
                    intent.putExtra(MainActivity.EXTRA_MESSAGE_FROM_HOME, msgFromHome);
                }
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);
        RegisterNewClientJob.sendRegistrationToServer(token, this);
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     *
     * @param msg Message to include in notification
     * @param data Data to include as extras in the notification
     */
    private void sendNotification(String msg, Map<String, String> data) {
        // Broadcast to foreground tasks
        Intent broadcastIntent = new Intent(ACTION_UPDATDIRECT_NOTIFICATION);
        addDataToIntent(data, broadcastIntent);
        sendBroadcast(broadcastIntent);      // Send broadcast message in case the main activity is in the foreground

        // Send notification to main tray
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(ACTION_HOME_NOFICATION);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        addDataToIntent(data, intent);

        // Send the pending intent for a top level notification
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
                PendingIntent.FLAG_ONE_SHOT);
        String channelId = getString(R.string.default_notification_channel_id);
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.home_small)
                        .setContentTitle(getString(R.string.home_notification_title))
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(msg))
                        .setContentText(msg)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setLights(0xFF0000, 500, 500)
                        .setVibrate(new long[] {0, 500, 0, 500, 0, 500, 0, 500, 0, 500})
                        .setExtras(intent.getExtras())
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            Log.e(TAG, "Notification manager is null!");
            return;
        }

        // Since android Oreo notification channel is needed.
        NotificationChannel channel = new NotificationChannel(channelId,
                getString(R.string.default_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        notificationManager.createNotificationChannel(channel);

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }
}