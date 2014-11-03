/*
 * Copyright (C) 2014 Gary O'Neall
 * 
 * Based on code from Google Cloud Messaging for Android:
 * 
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sourceauditor.sahomemonitor;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

/**
 * This {@code IntentService} does the actual handling of the GCM message.
 * {@code GcmBroadcastReceiver} (a {@code WakefulBroadcastReceiver}) holds a
 * partial wake lock for this service while the service does its work. When the
 * service is finished, it calls {@code completeWakefulIntent()} to release the
 * wake lock.
 */
public class GcmIntentService extends IntentService {
    public static final int NOTIFICATION_ID = 1;
    private static final boolean USE_NOTIFICATION = true;
    private NotificationManager mNotificationManager;
    NotificationCompat.Builder builder;

    public GcmIntentService() {
        super("GcmIntentService");
    }
    public static final String TAG = "SA Home Monitor GCM";

    @Override
    protected void onHandleIntent(Intent intent) {
    	try {
	       Bundle extras = intent.getExtras();
	        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
	        // The getMessageType() intent parameter must be the intent you received
	        // in your BroadcastReceiver.
	        String messageType = gcm.getMessageType(intent);

	        if (!extras.isEmpty()) {  // has effect of unparcelling Bundle
	            /*
	             * Filter messages based on message type. Since it is likely that GCM will be
	             * extended in the future with new message types, just ignore any message types you're
	             * not interested in, or that you don't recognize.
	             */
	            if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
	                sendNotification("Send error: " + extras.toString(), extras);
	            } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
	                sendNotification("Deleted messages on server: " + extras.toString(), extras);
	            // If it's a regular GCM message, do some work.
	            } else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
	                // Post notification of received message.
		            	if (USE_NOTIFICATION) {
		            	String msg = extras.getString(MainActivity.EXTRA_MESSAGE_FROM_HOME);
		            	if (msg == null || msg.trim().isEmpty()) {
		            		msg = "Home Alert (no message)";
		            	}
		            	sendNotification(msg, extras);
	            	} else {
		            	sendToMainActivity(extras);
	            	}
	                Log.i(TAG, "Received: " + extras.toString());
	            }
	        }	
    	} finally {
            // Release the wake lock provided by the WakefulBroadcastReceiver.
            GcmBroadcastReceiver.completeWakefulIntent(intent);
    	}
    }
    
    Intent buildIntentForMainActifity(Bundle extras) {
    	Intent retval = new Intent(this, MainActivity.class);
    	retval.setAction(MainActivity.ACTION_HOME_NOFICATION);
    	retval.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	if (extras != null) {
    		String homeMonitorUrl = extras.getString(MainActivity.EXTRA_HOME_MONITOR_URL);
    		if (homeMonitorUrl != null) {
    			homeMonitorUrl = homeMonitorUrl.trim();
        		if (!homeMonitorUrl.isEmpty()) {
        			retval.putExtra(MainActivity.EXTRA_HOME_MONITOR_URL, homeMonitorUrl);
        		}
    		}
    		
    		String homeMonitorAudioUrl = extras.getString(MainActivity.EXTRA_HOME_MONITOR_AUDIO_URL);
    		if (homeMonitorAudioUrl != null) {
    			homeMonitorAudioUrl = homeMonitorAudioUrl.trim();
        		if (!homeMonitorAudioUrl.isEmpty()) {
        			retval.putExtra(MainActivity.EXTRA_HOME_MONITOR_AUDIO_URL, homeMonitorAudioUrl);
        		}
    		}

        	String msg = extras.getString(MainActivity.EXTRA_MESSAGE_FROM_HOME);
        	if (msg != null) {
            	msg = msg.trim();
            	if (!msg.isEmpty()) {
            		retval.putExtra(MainActivity.EXTRA_MESSAGE_FROM_HOME, msg);
            	}   
        	}	
    	}
    	return retval;
    }
    
    /**
     * Send the GCM message information to the MainActivity for display
     * @param extras
     */
    private void sendToMainActivity(Bundle extras) {    	
    	startActivity(buildIntentForMainActifity(extras));
    }

    // Put the message into a notification and post it.
    private void sendNotification(String msg, Bundle extras) {
        mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);

        Intent notifyMainIntent = buildIntentForMainActifity(extras);

        // Figure out how to add extras for the URL and message
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
        		notifyMainIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
        .setSmallIcon(R.drawable.home_small)
        .setContentTitle("Home Monitor Notification")
        .setStyle(new NotificationCompat.BigTextStyle()
        .bigText(msg))
        .setExtras(extras)
        .setContentText(msg);

        mBuilder.setContentIntent(contentIntent);
        mBuilder.setPriority(Notification.PRIORITY_MAX);
        mBuilder.setLights(0xFF0000, 500, 500);
        long[] pattern = new long[] {0, 500, 0, 500, 0, 500, 0, 500, 0, 500};
        mBuilder.setVibrate(pattern);
        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        mBuilder.setSound(soundUri);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

}
