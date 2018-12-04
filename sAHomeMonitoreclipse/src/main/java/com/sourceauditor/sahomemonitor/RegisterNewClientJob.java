package com.sourceauditor.sahomemonitor;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.AsyncTask;
import android.os.PersistableBundle;
import android.util.Log;
import android.widget.Toast;

import com.eatthepath.otp.HmacOneTimePasswordGenerator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Base32;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class RegisterNewClientJob extends JobService {

    //NOTE: The following constants must be the same as the constants defined in the HomeMonitorPiServer project gcmrelay/constants.py file
    static final String KEY_AUTHENTICATION = "authentication";
    static final String KEY_REGISTRATION_IDS = "registration_ids";
    static final String KEY_REQUEST_NUMBER = "req_num";
    static final String KEY_REQUEST = "request";
    static final String REQUEST_REGISTER_TOKEN = "register_token";
    static final String KEY_STATUS = "status";
    static final String STATUS_SUCCESS = "success";
    static final String KEY_ERROR = "exception";
    static final String SERVER_HOST = "184.73.159.130";
    static final int SERVER_PORT = 13373;
    static final int SERVER_TIMEOUT = 2000; // 2 seconds
    static final int MAX_RETRIES = 3;

    int messageNumber = 5;
    int numRetries = 0;

    private class RegisterTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... tokens) {
            if (tokens == null || tokens.length == 0) {
                Log.e(TAG, "Can not run registration task - no tokens supplied");
                return false;
            }
            String token = tokens[0];
            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT), SERVER_TIMEOUT);
                socket.setSoTimeout(SERVER_TIMEOUT);
                OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream());
                InputStreamReader reader = new InputStreamReader(socket.getInputStream());
                HmacOneTimePasswordGenerator generator = new HmacOneTimePasswordGenerator();
                Base32 base32 = new Base32();
                byte[] keybytes = base32.decode("USEDTOVERIFY2254");
                SecretKey secret = new SecretKeySpec(keybytes, "SHA1");
                int auth = generator.generateOneTimePassword(secret, messageNumber);
                JSONObject msg = new JSONObject();
                msg.put(KEY_REQUEST, REQUEST_REGISTER_TOKEN);
                msg.put(KEY_AUTHENTICATION, auth);
                JSONArray registrationIds = new JSONArray(new String[] {token});
                msg.put(KEY_REGISTRATION_IDS, registrationIds);
                msg.put(KEY_REQUEST_NUMBER, messageNumber++);
                writer.write(msg.toString());
                writer.flush();
                String result = readResponse(reader);
                JSONObject resultJson = new JSONObject(result);
                if (!resultJson.has(KEY_STATUS)) {
                    Log.e(TAG, "Missing status return from request to add token");
                    return false;
                }
                String status = resultJson.getString(KEY_STATUS);
                if (status == null || status.isEmpty()) {
                    Log.e(TAG, "Missing status return from request to add token");
                    return false;
                } else if (!STATUS_SUCCESS.equals(status)) {
                    if (resultJson.has(KEY_ERROR)) {
                        Log.e(TAG, "Server reported the following error: "+resultJson.getString(KEY_ERROR));
                    } else {
                        Log.e(TAG, "Server returned non-success status but no error information");
                    }
                    return false;
                }
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "Error generating password - no such algorithm");
                return false;
            } catch (InvalidKeyException e) {
                Log.e(TAG, "Error generating password - invalid key");
                return false;
            } catch (JSONException e) {
                Log.e(TAG, "Error creating the message to send to the server",e);
                return false;
            } catch (UnknownHostException e) {
                Log.e(TAG, "Unable to connect to registration server", e);
                return false;
            } catch (IOException e) {
               Log.e(TAG, "IO error communicating with server",e);
               return false;
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        Log.w(TAG, "Error closing socket", e);
                    }
                }
            }
            return true;
        }

        /**
         * Reads a JSON string from the reader, returning when complete
         * @param reader
         * @return JSON string
         * @throws IOException
         */
        String readResponse(Reader reader) throws IOException {
            StringBuilder sb = new StringBuilder();
            char ch;
            try {
                int r = reader.read();
                ch = (char)r;
                if (ch != '{') {
                    Log.e(TAG, "Invalid response - expected '{', found '"+ch+"'");
                    return "";
                }
                int braceCount = 1;
                sb.append(ch);
                while (r != -1 && braceCount > 0) {
                    r = reader.read();
                    ch = (char)r;
                    if (ch == '{') {
                        braceCount++;
                    } else if (ch == '}') {
                        braceCount--;
                    }
                    sb.append(ch);
                }
            } catch (SocketTimeoutException timeout) {
                Log.e(TAG, "Timeout on read from the server response");
            }
            return sb.toString();
        }
    }

    private RegisterTask mRegisterTask = null;
    private static final String TAG = "RegisterNewClientJob";

    @Override
    public boolean onStartJob(final JobParameters params) {
        numRetries = 0;
        if (params == null) {
            Log.e(TAG, "Missing token parameter");
            return false;
        }
        String token = params.getExtras().getString("token");
        if (token == null || token.isEmpty()) {
            Log.e(TAG, "Missing token parameter");
            return false;
        }
        mRegisterTask = new RegisterTask() {
            @Override
            protected void onPostExecute(Boolean success) {
                if (!success) {
                    Toast.makeText(getApplicationContext(), R.string.register_monitor_failed, Toast.LENGTH_SHORT);
                }
                jobFinished(params, false);
            }
        };
        mRegisterTask.execute(token);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (mRegisterTask == null) {
            return false;
        }
        mRegisterTask.cancel(true);
        return true;
    }

    public static void sendRegistrationToServer(String token, Context context) {
        Log.i(TAG, "Sending new registration token'"+token+"'");
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            Log.e(TAG, "Unable to obtain job scheduler");
            return;
        }
        ComponentName componentName = new ComponentName(context, RegisterNewClientJob.class);
        PersistableBundle extras = new PersistableBundle();
        extras.putString("token", token);
        JobInfo.Builder builder = new JobInfo.Builder(0, componentName)
                .setExtras(extras)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        scheduler.schedule(builder.build());
    }
}
