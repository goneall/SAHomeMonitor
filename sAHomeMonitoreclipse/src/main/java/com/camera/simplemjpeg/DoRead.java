package com.camera.simplemjpeg;    

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.os.AsyncTask;
import android.util.Log;

/**
 * Original code was in the MainActivity class - refactored into this package
 *
 */
public class DoRead extends AsyncTask<String, Void, MjpegInputStream> {
    private static final String TAG = null;
    
    private MjpegView mv = null;
    
    public DoRead(MjpegView mv) {
    	this.mv = mv;
    }

	protected MjpegInputStream doInBackground(String... params) {
        //TODO: if camera has authentication deal with it and don't just not work
        HttpURLConnection connection;
        try {
            URL url = new URL(params[0]);
            connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(5*1000);
            connection.setDoOutput(true);
            Log.d(TAG, "1. Sending http request");
            connection.connect();
            if (connection.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                //You must turn off camera User Access Control before this will work
                Log.e(TAG, "Camera connection requires authorization.  Turn off camera for this to work.");
                return null;
            }
            Log.d(TAG, "2. Request finished, status = " + connection.getResponseCode());
            return new MjpegInputStream(connection.getInputStream());
        } catch (MalformedURLException e) {
            Log.e(TAG, "Invalid URL for mpeg stream");
            return null;
        } catch (IOException e) {
            Log.e(TAG, "IO error opening mpeg stream");
            return null;
        }
    }

    protected void onPostExecute(MjpegInputStream result) {
        mv.setSource(result);
        if(result!=null) result.setSkip(1);
        mv.setDisplayMode(MjpegView.SIZE_BEST_FIT);
        mv.showFps(false);
    }
}