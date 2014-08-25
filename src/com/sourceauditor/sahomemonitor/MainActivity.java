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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.sourceauditor.sahomemonitor.R;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

public class MainActivity extends Activity {
	
    public static final String PROPERTY_REG_ID = "registration_id";
    public static final String PROPERTY_HOME_MONITOR_URL = "monitor_url";
    private static final String PROPERTY_APP_VERSION = "app_version";
	public static final String ACTION_HOME_NOFICATION = "com.sourceauditor.sahomemonitor.homenotification";

	public static final String EXTRA_MESSAGE_FROM_HOME = "com.sourceauditor.sahomemonitor.messagefromhome";
	public static final String EXTRA_HOME_MONITOR_URL = "com.sourceauditor.sahomemonitor.homemonitorurl";
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    static final String TAG = "SAHomeMonitor";
    
    private static boolean DONT_USE_GCM=false;	// set to true for debugging on emulator
    
    GoogleCloudMessaging gcm;
    Context context;
    String regid = "NOT SET";
	private String lastHomeMessage = "No Message";
    

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		context = getApplicationContext();
		setContentView(R.layout.activity_main);
		registerGcm();
		if (savedInstanceState == null) {
			getFragmentManager().beginTransaction()
					.add(R.id.container, new HomeMonitorUIFragment()).commit();
		}
	}
	
	private void registerGcm() {
		if (DONT_USE_GCM) {
			return;
		}
		if (checkPlayServices()) {
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
				TextView messageText = (TextView) findViewById(R.id.messageFromHomeText);
				if (messageText != null) {
					messageText.setText(result);
				}
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
	    final SharedPreferences prefs = getSAHomeMonitorPreferences(context);
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

	private void showRegistrationId() {
		setMessageText(getString(R.string.label_registration_id) + this.regid);
	}

	@Override
	    protected void onStart() {
	        super.onStart();
	    }
	  
	    @Override
	    protected void onResume() {
	        super.onResume();
	    }
	    
	    @Override
		protected void onPause() {
	    	VideoView homeMonitorView = (VideoView) findViewById(R.id.homeMonitorVideoView);
	    	if (homeMonitorView != null) {
	    		homeMonitorView.pause();
	    	}
	    	super.onPause();
	    }
	    
	    @Override
	    protected void onDestroy() {
	    	// Not sure if the following is required.  The JavaDocs state close should be called, however,
	    	// there is no close in any of the Google example close
	    	if (this.gcm != null) {
	    		gcm.close();
	    		gcm = null;
	    	}
	    	super.onDestroy();
	    }
	    

	/**
	 * UI Fragment for the Home Monitor UI Elements
	 */
	public class HomeMonitorUIFragment extends Fragment {
		
		MediaController homeMonitorControl;

		public HomeMonitorUIFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);
			homeMonitorControl = new MediaController(getActivity());
			return rootView;
		}
		
		@Override
		public void onResume() {
	        super.onResume();
			Intent intent = getActivity().getIntent();
			lastHomeMessage = getString(R.string.default_message);
			final SharedPreferences prefs = getSAHomeMonitorPreferences(context);
			String homeMonitorUrl = prefs.getString(PROPERTY_HOME_MONITOR_URL, getString(R.string.home_monitor_url));
			if (intent != null && intent.getAction().equals(ACTION_HOME_NOFICATION) && intent.getExtras() != null) {
				String intentMessage = intent.getExtras().getString(EXTRA_MESSAGE_FROM_HOME);
				if (intentMessage != null &&!intentMessage.trim().isEmpty()) {
					lastHomeMessage = intentMessage;
				}
				String intentHomeMonitorUrl = intent.getExtras().getString(EXTRA_HOME_MONITOR_URL);
				if (intentHomeMonitorUrl != null && !intentHomeMonitorUrl.trim().isEmpty()) {
					homeMonitorUrl = intentHomeMonitorUrl;
					// add the last home monitor URL to the preferences
					SharedPreferences.Editor editor = prefs.edit();
				    editor.putString(PROPERTY_HOME_MONITOR_URL, intentHomeMonitorUrl);
				    editor.commit();
				}
			}
			setMessageText(lastHomeMessage);
			VideoView homeMonitorView = (VideoView) getActivity().findViewById(R.id.homeMonitorVideoView);
			if (homeMonitorView != null) {	
				Uri videoUri = Uri.parse(homeMonitorUrl);				
				homeMonitorView.setVideoURI(videoUri);
				homeMonitorView.start();
			}
			homeMonitorControl.setAnchorView(homeMonitorView);
			homeMonitorView.setMediaController(homeMonitorControl);
		}
	}
}
