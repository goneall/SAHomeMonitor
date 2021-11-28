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
import com.google.android.gms.common.GoogleApiAvailabilityLight;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

public class MainActivity extends Activity implements OnPreparedListener, OnBufferingUpdateListener, OnErrorListener, OnAudioFocusChangeListener {

    public static final String PROPERTY_HOME_MONITOR_URL = "monitor_url";
    public static final String PROPERTY_HOME_MONITOR_AUDIO_URL = "home_monitor_audio_url";
    public static final String PROPERTY_PLAYER_WIDTH = "playwidth";
    public static final String PROPERTY_PLAYER_HEIGHT = "playheight";
	public static final String ACTION_HOME_NOFICATION = "com.sourceauditor.sahomemonitor.homenotification";
	public static final String ACTION_UPDATDIRECT_NOTIFICATION = "com.sourceauditor.sahomemonitor.homenotificationdirect";

	public static final String EXTRA_MESSAGE_FROM_HOME = "com.sourceauditor.sahomemonitor.messagefromhome";
	public static final String EXTRA_HOME_MONITOR_URL = "com.sourceauditor.sahomemonitor.homemonitorurl";
	public static final String EXTRA_HOME_MONITOR_AUDIO_URL = "com.sourceauditor.sahomemonitor.homemonitoraudiourl";
    
	public static final int DEFAULT_PLAYER_WIDTH = 640;
	public static final int DEFAULT_PLAYER_HEIGHT = 480;

    static final String TAG = "SAHomeMonitor";

    Context context;
	SharedPreferences prefs = null;
	
	private MediaPlayer audioPlayer = null;
	private MjpegView mv = null;
	private Button playPauseButton = null;
	AudioManager audioManager = null;
	private boolean pauseBeforePlaying;

    /**
     * For recieving messages from the home monitor if the activity is running in the foreground
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            processMessageFromHome(intent);
        }
    };

    /**
     * Processes a message from the home monitor - takes care of message recieved while the application is in the foreground
     * @param messageIntent Intent from the message - contains data in the extras
     */
    private void processMessageFromHome(Intent messageIntent) {
        if (messageIntent.hasExtra(EXTRA_MESSAGE_FROM_HOME)) {
            this.getIntent().putExtra(EXTRA_MESSAGE_FROM_HOME, messageIntent.getStringExtra(EXTRA_MESSAGE_FROM_HOME));
            setMessageText(messageIntent.getStringExtra(EXTRA_MESSAGE_FROM_HOME));
        }
        boolean updatedUrls = false;
        if (messageIntent.hasExtra(EXTRA_HOME_MONITOR_URL) &&
                (!this.getIntent().hasExtra(EXTRA_HOME_MONITOR_URL) ||
                    !this.getIntent().getStringExtra(EXTRA_HOME_MONITOR_URL).equals(messageIntent.getStringExtra(EXTRA_HOME_MONITOR_URL)))) {
                this.getIntent().putExtra(EXTRA_HOME_MONITOR_URL, messageIntent.getStringExtra(EXTRA_HOME_MONITOR_URL));
                updatedUrls = true;
            }
        if (messageIntent.hasExtra(EXTRA_HOME_MONITOR_AUDIO_URL) &&
                (!this.getIntent().hasExtra(EXTRA_HOME_MONITOR_AUDIO_URL) ||
                        !this.getIntent().getStringExtra(EXTRA_HOME_MONITOR_AUDIO_URL).equals(messageIntent.getStringExtra(EXTRA_HOME_MONITOR_AUDIO_URL)))) {
        this.getIntent().putExtra(EXTRA_HOME_MONITOR_AUDIO_URL, messageIntent.getStringExtra(EXTRA_HOME_MONITOR_AUDIO_URL));
        updatedUrls = true;
        }
        if (updatedUrls) {
            stop();
            play();
        }
    }
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		context = getApplicationContext();
		prefs = getSAHomeMonitorPreferences();
		setContentView(R.layout.activity_main);
		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		playPauseButton = findViewById(R.id.playpause);
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
		// Create channel to show notifications.
		String channelId  = getString(R.string.default_notification_channel_id);
		String channelName = getString(R.string.default_notification_channel_name);
		NotificationManager notificationManager =
				getSystemService(NotificationManager.class);
		if (notificationManager != null) {
notificationManager.createNotificationChannel(new NotificationChannel(channelId,
channelName, NotificationManager.IMPORTANCE_LOW));
}
        if (GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
        	showDialog("Error", "Note that some features of the home monitor may not work correctly");
        }
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
		if (audioPlayer.isPlaying()) {
			audioPlayer.pause();
		} else {
			pauseBeforePlaying = true;
		}
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
			pauseBeforePlaying = false;
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
				pauseBeforePlaying = false;
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
			    editor.putString(PROPERTY_HOME_MONITOR_AUDIO_URL, intentHomeMonitorAudioUrl);
			    editor.apply();
			}
		}
		return Uri.parse(homeAudioUrl);
	}

	private void playVideo() {
		int width = prefs.getInt(PROPERTY_PLAYER_WIDTH, DEFAULT_PLAYER_WIDTH);
		int height = prefs.getInt(PROPERTY_PLAYER_HEIGHT, DEFAULT_PLAYER_HEIGHT);
		if (mv == null) {
			mv = findViewById(R.id.mjpegView);
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
			    editor.apply();
			}
		}
		return homeMonitorUrl;
	}
	
	private String getHomeMessage() {
		Intent intent = getIntent();
		String homeMessage = getString(R.string.default_message);
		if (intent != null) {
			String intentHomeMessage = intent.getStringExtra(EXTRA_MESSAGE_FROM_HOME);
			if (intentHomeMessage != null && !intentHomeMessage.trim().isEmpty()) {
				homeMessage = intentHomeMessage;
			}
		}
		return homeMessage;
	}
	
	/**
	 * @return Application's {@code SharedPreferences}.
	 */
	private SharedPreferences getSAHomeMonitorPreferences() {
	    return getSharedPreferences(MainActivity.class.getSimpleName(),
	            Context.MODE_PRIVATE);
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
			if (audioManager.isSpeakerphoneOn()) {
				audioManager.setMode(AudioManager.MODE_NORMAL);
				audioManager.setSpeakerphoneOn(false);
				item.setChecked(false);
			} else {
				audioManager.setMode(AudioManager.MODE_NORMAL);
				audioManager.setSpeakerphoneOn(true);
				item.setChecked(true);
			}
			
		} else if (id == R.id.action_show_home_monitor_url) {
			showUrl();
			return true;
		} else if (id == R.id.action_register_monitor) {
		    registerWithHomeMonitor();
		    return true;
        }
		return super.onOptionsItemSelected(item);
	}

	private void showLastHomeMessage() {
		setMessageText(getHomeMessage());
	}

	/**
	 * Set the text box message to msg
	 * @param msg message to set
	 */
	private void setMessageText(String msg) {
		TextView messageText = findViewById(R.id.messageFromHomeText);
		if (messageText != null) {
			messageText.setText(msg);
		}
	}

	private void showDialog(String title, String msg) {
		TextView dialogText = new TextView(this);
		dialogText.setText(msg);
		dialogText.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				// Copy the Text to the clipboard
				ClipboardManager manager =
						(ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
				TextView tv = (TextView) v;
				manager.setPrimaryClip(ClipData.newPlainText("Message", tv.getText()));
				// Show a message:
				Toast.makeText(v.getContext(), "Copied to clipboard",
						Toast.LENGTH_SHORT)
						.show();
				return true;
			}
		});
		AlertDialog.Builder adb = new AlertDialog.Builder(this);
		adb.setTitle(title)
				.setView(dialogText)
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

	private void showRegistrationId() {
		FirebaseMessaging.getInstance().getToken().addOnCompleteListener(
				new OnCompleteListener<String>() {
					@Override
					public void onComplete(@NonNull Task<String> task) {
						if (!task.isSuccessful()) {
							Log.w(TAG, "Fetching FCM registration token failed", task.getException());
							return;
						}

						// Get new FCM registration token
						String token = task.getResult();
						showDialog(getString(R.string.title_show_reg), token);
					}
				});
    }

    private void registerWithHomeMonitor() {
		FirebaseMessaging.getInstance().getToken().addOnCompleteListener(
				new OnCompleteListener<String>() {
					@Override
					public void onComplete(@NonNull Task<String> task) {
						if (!task.isSuccessful()) {
							Log.w(TAG, "Fetching FCM registration token failed", task.getException());
							return;
						}

						// Get new FCM registration token
						String token = task.getResult();
						RegisterNewClientJob.sendRegistrationToServer(token, getApplicationContext());
					}
				});
    }
	
	private void showUrl() {
		showDialog(getString(R.string.title_show_home_monitor_url), this.getHomeMonitorUrl());
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
            registerReceiver(mReceiver, new IntentFilter(ACTION_UPDATDIRECT_NOTIFICATION));
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
            unregisterReceiver(mReceiver);
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
					sb.append(" Unknown error (");
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
			} else if (percent >= 0){
				String percentStr = String.valueOf(percent);
				this.playPauseButton.setText(percentStr + "%");
			}
		}

		@Override
		public void onPrepared(MediaPlayer mp) {
			if (!mp.isPlaying() && !pauseBeforePlaying) {
				try {
					mp.start();
					this.playPauseButton.setText(getString(R.string.label_pause));
				} catch (Exception e) {
					Log.e(TAG, "Error trying to start media player: "+e.getMessage());
				}
			}
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
