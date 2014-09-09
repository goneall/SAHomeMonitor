/*
 * Copyright 2014 Gary O'Neall
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Contains code from Google Cloud Message Service  Copyright 2013 Google Inc.
 */
package com.sourceauditor.sahomemonitor;

import java.io.IOException;

import com.camera.simplemjpeg.DoRead;
import com.camera.simplemjpeg.MjpegView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.sourceauditor.sahomemonitor.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity implements OnPreparedListener, OnBufferingUpdateListener, OnErrorListener, OnAudioFocusChangeListener {
	
    public static final String PROPERTY_REG_ID = "registration_id";
    public static final String PROPERTY_HOME_MONITOR_URL = "monitor_url";
    public static final String PROPERTY_HOME_MONITOR_AUDIO_URL = "home_monitor_audio_url";
    public static final String PROPERTY_APP_VERSION = "app_version";
    public static final String PROPERTY_PLAYER_WIDTH = "playwidth";
    public static final String PROPERTY_PLAYER_HEIGHT = "playheight";
	public static final String ACTION_HOME_NOFICATION = "com.sourceauditor.sahomemonitor.homenotification";

	public static final String EXTRA_MESSAGE_FROM_HOME = "com.sourceauditor.sahomemonitor.messagefromhome";
	public static final String EXTRA_HOME_MONITOR_URL = "com.sourceauditor.sahomemonitor.homemonitorurl";
	public static final String EXTRA_HOME_MONITOR_AUDIO_URL = "com.sourceauditor.sahomemonitor.homemonitoraudiourl";
    
	public static final int DEFAULT_PLAYER_WIDTH = 640;
	public static final int DEFAULT_PLAYER_HEIGHT = 480;
	
	private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    static final String TAG = "SAHomeMonitor";
    
    private static boolean DONT_USE_GCM=false;	// set to true for debugging on emulator
    
    GoogleCloudMessaging gcm;
    Context context;
    String regid = "NOT SET";
	private String lastHomeMessage = "No Message";
	SharedPreferences prefs = null;
	
	private MediaPlayer audioPlayer = null;
	private MjpegView mv = null;
	private Button playPauseButton = null;
	AudioManager audioManager = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		context = getApplicationContext();
		prefs = getSAHomeMonitorPreferences(context);
		setContentView(R.layout.activity_main);
		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		playPauseButton = (Button) findViewById(R.id.playpause);
		playPauseButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if (paused()) {
					play();
				} else {
					pause();
				}
			}
		});
		
		registerGcm();
	}
	
	protected boolean paused() {
		return playPauseButton.getText().equals(getString(R.string.label_play));
	}
	
	protected void pause() {
		pauseVideo();
		pauseAudio();
		playPauseButton.setText(getString(R.string.label_play));
	}
	
	private void pauseAudio() {
		audioPlayer.pause();
		audioManager.abandonAudioFocus(this);
	}

	private void pauseVideo() {
		if (mv != null && mv.isStreaming()) {
			mv.stopPlayback();
		}
	}

	protected void play() {
		playVideo();
		playAudio();
		playPauseButton.setText(getString(R.string.label_pause));
	}
	
	private void stopAudio() {
		if (audioPlayer != null) {
			audioPlayer.stop();
			audioPlayer.reset();
			audioPlayer.release();
			audioPlayer = null;
		}
	}
	
	private void stopVideo() {
		if (mv != null) {
			mv.freeCameraMemory();
			mv = null;			
		}
	}
	
	private void playAudio() {
		if (audioPlayer == null) {
			audioPlayer = new MediaPlayer();
			audioPlayer.setOnPreparedListener(this);
			audioPlayer.setOnBufferingUpdateListener(this);
			audioPlayer.setOnErrorListener(this);
		} else {
			audioPlayer.stop();
			audioPlayer.reset();
		}
		try {
			audioPlayer.setDataSource(this, getAudioUri());
			audioPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
				    AudioManager.AUDIOFOCUS_GAIN);
			if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
				logAndDisplayError("Audio in use by another app");
			} else {
				audioPlayer.prepareAsync();
			}
		} catch (IllegalArgumentException e) {
			logAndDisplayError("Illegal argument for audioPlayer");
		} catch (SecurityException e) {
			logAndDisplayError("Security error for audioPlayer");
		} catch (IllegalStateException e) {
			logAndDisplayError("Illegal state exception for audioPlayer");
		} catch (IOException e) {
			logAndDisplayError("IO Error for audio player: "+e.getMessage());
		}
	}
	
	private void logAndDisplayError(String msg) {
		Log.e(TAG, msg);
		AlertDialog.Builder adb = new AlertDialog.Builder(this);
		adb.setTitle("Error");
		adb.setMessage(msg)
			.setCancelable(true)
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
				
			})
			.create()
			.show();
	}

	private Uri getAudioUri() {
		Intent intent = getIntent();
		String homeAudioUrl = prefs.getString(PROPERTY_HOME_MONITOR_AUDIO_URL, getString(R.string.home_monitor_audio_url));
		if (intent != null && intent.getAction() != null &&
				intent.getAction().equals(ACTION_HOME_NOFICATION) && intent.getExtras() != null) {
			String intentHomeMonitorAudioUrl = intent.getExtras().getString(EXTRA_HOME_MONITOR_AUDIO_URL);
			if (intentHomeMonitorAudioUrl != null && !intentHomeMonitorAudioUrl.trim().isEmpty()) {
				homeAudioUrl = intentHomeMonitorAudioUrl;
				// add the last home monitor URL to the preferences
				SharedPreferences.Editor editor = prefs.edit();
			    editor.putString(PROPERTY_HOME_MONITOR_URL, intentHomeMonitorAudioUrl);
			    editor.commit();
			}
		}
		return Uri.parse(homeAudioUrl);
	}

	private void playVideo() {
		int width = prefs.getInt(PROPERTY_PLAYER_WIDTH, DEFAULT_PLAYER_WIDTH);
		int height = prefs.getInt(PROPERTY_PLAYER_HEIGHT, DEFAULT_PLAYER_HEIGHT);
		if (mv == null) {
			mv = (MjpegView) findViewById(R.id.mjpegView);
		}
        mv.setResolution(width, height);
        new DoRead(mv).execute(getHomeMonitorUrl());
	}

	private String getHomeMonitorUrl() {
		Intent intent = getIntent();
		String homeMonitorUrl = prefs.getString(PROPERTY_HOME_MONITOR_URL, getString(R.string.home_monitor_url));
		if (intent != null && intent.getAction() != null &&
				intent.getAction().equals(ACTION_HOME_NOFICATION) && intent.getExtras() != null) {
			String intentHomeMonitorUrl = intent.getExtras().getString(EXTRA_HOME_MONITOR_URL);
			if (intentHomeMonitorUrl != null && !intentHomeMonitorUrl.trim().isEmpty()) {
				homeMonitorUrl = intentHomeMonitorUrl;
				// add the last home monitor URL to the preferences
				SharedPreferences.Editor editor = prefs.edit();
			    editor.putString(PROPERTY_HOME_MONITOR_URL, intentHomeMonitorUrl);
			    editor.commit();
			}
		}
		return homeMonitorUrl;
	}
	
	private String getHomeMessage() {
		Intent intent = getIntent();
		String homeMessage = getString(R.string.default_message);
		if (intent != null && intent.getAction() != null &&
				intent.getAction().equals(ACTION_HOME_NOFICATION) && intent.getExtras() != null) {
			String intentHomeMessage = intent.getExtras().getString(EXTRA_MESSAGE_FROM_HOME);
			if (intentHomeMessage != null && !intentHomeMessage.trim().isEmpty()) {
				homeMessage = intentHomeMessage;
				this.lastHomeMessage = homeMessage;
			}
		}
		return homeMessage;
	}

	private void registerGcm() {
		if (DONT_USE_GCM) {
			return;
		}
        // Check device for Play Services APK. If check succeeds, proceed with
        //  GCM registration.
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = getRegistrationId(context);

            if (regid.isEmpty()) {
                registerInBackground();
            }
        } else {
            Log.i(TAG, "No valid Google Play Services APK found.");
        }
	}

	/**
	 * @return Application's version code from the {@code PackageManager}.
	 */
	private static int getAppVersion(Context context) {
	    try {
	        PackageInfo packageInfo = context.getPackageManager()
	                .getPackageInfo(context.getPackageName(), 0);
	        return packageInfo.versionCode;
	    } catch (NameNotFoundException e) {
	        // should never happen
	        throw new RuntimeException("Could not get package name: " + e);
	    }
	}
	
	/**
	 * Registers the application with GCM servers asynchronously.
	 * <p>
	 * Stores the registration ID and app versionCode in the application's
	 * shared preferences.
	 */
	private void registerInBackground() {
	    new AsyncTask<Object, Integer, String>() {

			@Override
			protected String doInBackground(Object... params) {
	            String msg = "";
	            try {
	                if (gcm == null) {
	                    gcm = GoogleCloudMessaging.getInstance(context);
	                }
	                regid = gcm.register(GcmConstants.SENDER_ID);
	                msg = "Enter the following registration ID in the server:" + regid;

	                // Persist the regID - no need to register again.
	                storeRegistrationId(context, regid);
	            } catch (IOException ex) {
	                msg = "Error :" + ex.getMessage();
	                // If there is an error, don't just keep trying to register.
	                // Require the user to click a button again, or perform
	                // exponential back-off.
	            }
	            return msg;
			}
			
	        @Override
	        protected void onPostExecute(String result) {
				showRegistrationId();
	        }

	    }.execute(null, null, null);
	}
	
	/**
	 * Stores the registration ID and app versionCode in the application's
	 * {@code SharedPreferences}.
	 *
	 * @param context application's context.
	 * @param regId registration ID
	 */
	private void storeRegistrationId(Context context, String regId) {
	    final SharedPreferences prefs = getSAHomeMonitorPreferences(context);
	    int appVersion = getAppVersion(context);
	    Log.i(TAG, "Saving regId on app version " + appVersion);
	    SharedPreferences.Editor editor = prefs.edit();
	    editor.putString(PROPERTY_REG_ID, regId);
	    editor.putInt(PROPERTY_APP_VERSION, appVersion);
	    editor.commit();
	}
	
	/**
	 * Gets the current registration ID for application on GCM service.
	 * <p>
	 * If result is empty, the app needs to register.
	 *
	 * @return registration ID, or empty string if there is no existing
	 *         registration ID.
	 */
	private String getRegistrationId(Context context) {
	    String registrationId = prefs.getString(PROPERTY_REG_ID, "");
	    if (registrationId.isEmpty()) {
	        Log.i(TAG, "Registration not found.");
	        return "";
	    }
	    // Check if app was updated; if so, it must clear the registration ID
	    // since the existing regID is not guaranteed to work with the new
	    // app version.
	    int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
	    int currentVersion = getAppVersion(context);
	    if (registeredVersion != currentVersion) {
	        Log.i(TAG, "App version changed.");
	        return "";
	    }
	    return registrationId;
	}
	
	/**
	 * @return Application's {@code SharedPreferences}.
	 */
	private SharedPreferences getSAHomeMonitorPreferences(Context context) {
	    return getSharedPreferences(MainActivity.class.getSimpleName(),
	            Context.MODE_PRIVATE);
	}

	/**
	 * Check the device to make sure it has the Google Play Services APK. If
	 * it doesn't, display a dialog that allows users to download the APK from
	 * the Google Play Store or enable it in the device's system settings.
	 */
	private boolean checkPlayServices() {
	    int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
	    if (resultCode != ConnectionResult.SUCCESS) {
	        if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
	            GooglePlayServicesUtil.getErrorDialog(resultCode, this,
	                    PLAY_SERVICES_RESOLUTION_REQUEST).show();
	        } else {
	            Log.i(TAG, "This device is not supported.");
	            finish();
	        }
	        return false;
	    }
	    return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		} else if (id == R.id.action_display_reg) {
			showRegistrationId();
			return true;
		} else if (id == R.id.action_show_last_home_message) {
			showLastHomeMessage();
			return true;
		} else if (id == R.id.action_speaker) {
			int audioMode = audioManager.getMode();
			if (audioManager.isSpeakerphoneOn()) {
				audioManager.setMode(AudioManager.MODE_NORMAL);
				audioManager.setSpeakerphoneOn(false);
				item.setChecked(false);
			} else {
				audioManager.setMode(AudioManager.MODE_NORMAL);
				audioManager.setSpeakerphoneOn(true);
				item.setChecked(true);
			}
			
		}
		return super.onOptionsItemSelected(item);
	}

	private void showLastHomeMessage() {
		setMessageText(this.lastHomeMessage);
	}

	/**
	 * Set the text box message to msg
	 * @param msg
	 */
	private void setMessageText(String msg) {
		TextView messageText = (TextView) findViewById(R.id.messageFromHomeText);
		if (messageText != null) {
			messageText.setText(msg);
		}
	}
	
	public void setLastHomeMessage(String homeMessage) {
		this.lastHomeMessage = homeMessage;
	}
	
	public String getLastHomeMessage() {
		return this.lastHomeMessage;
	}

	private void showRegistrationId() {
		AlertDialog.Builder adb = new AlertDialog.Builder(this);
		adb.setTitle("Registration ID");
		adb.setMessage(getString(R.string.label_registration_id) + this.regid)
			.setCancelable(true)
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
				
			})
			.create()
			.show();
	}

	@Override
	    protected void onStart() {
	        super.onStart();
	    }
	  
	    @Override
	    protected void onResume() {
	        super.onResume();
	        play();
			String homeMessage = getHomeMessage();
			setMessageText(homeMessage);
			setLastHomeMessage(homeMessage);
	    }
	    
	    @Override
		protected void onPause() {
			super.onPause();
			pause();
	    }
	    
	    private void stop() {
	    	stopVideo();
	    	stopAudio();
	    }
	    
	    @Override
	    protected void onStop() {
	    	super.onStop();
	    	stop();
	    }
	    
	    
	    @Override
	    protected void onDestroy() {
	    	stopAudio();
	    	
	    	stopVideo();
			
	    	// Not sure if the following is required.  The JavaDocs state close should be called, however,
	    	// there is no close in any of the Google example close
	    	if (this.gcm != null) {
	    		gcm.close();
	    		gcm = null;
	    	}
	    	super.onDestroy();
	    }

		@Override
		public boolean onError(MediaPlayer mp, int what, int extra) {
		  
			StringBuilder sb = new StringBuilder();
			sb.append("Error playing Audio stream from home: ");
			switch (what) {
				case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
					sb.append("Not Valid for Progressive Playback");
					break;
				case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
					sb.append("Server Died");
					break;
				case MediaPlayer.MEDIA_ERROR_UNKNOWN:
					sb.append("Unknown");
					break;
				default:
					sb.append(" Unknow error (");
					sb.append(what);
					sb.append(")");
			}
			sb.append(extra);
			logAndDisplayError(sb.toString());
			return true;
		}

		@Override
		public void onBufferingUpdate(MediaPlayer mp, int percent) {
			if (percent >= 100) {
				this.playPauseButton.setText(getString(R.string.label_pause));
			} else if (percent >= 0 && percent < 100){
				String percentStr = String.valueOf(percent);
				this.playPauseButton.setText(percentStr + "%");
			}
		}

		@Override
		public void onPrepared(MediaPlayer mp) {
			this.playPauseButton.setText(getString(R.string.label_pause));
			mp.start();
		}

		@Override
		public void onAudioFocusChange(int focusChange) {
		    switch (focusChange) {
	        case AudioManager.AUDIOFOCUS_GAIN:
	        	if (paused()) {
	        		play();
	        	} else {
	        		audioPlayer.setVolume(1.0f, 1.0f);
	        	}
	            break;

	        case AudioManager.AUDIOFOCUS_LOSS:
	            // Lost focus for an unbounded amount of time: pause playback
	            pause();
	            break;

	        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
	            pause();
	            break;

	        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
	            // Lost focus for a short time, but it's ok to keep playing
	            // at an attenuated level
	        	if (!paused()) {
	        		audioPlayer.setVolume(0.1f, 0.1f);
		           
	        	}
	        	 break;
		    }
		}
}
