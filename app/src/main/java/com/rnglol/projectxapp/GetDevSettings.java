package com.rnglol.projectxapp;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

public class GetDevSettings extends AsyncTask<String, Void, String> {
    private final static String TAG = "ProjectX/GetSettings";

    // MainActivity pointer
    private WeakReference<MainActivity> mainActivityWeak;

    GetDevSettings(WeakReference<MainActivity> main_act) {
        super();
        mainActivityWeak = main_act;
    }

    @Override
    protected String doInBackground(String... params) {
        try {
            Log.v(TAG, "Getting settings...");

            // input parameters
            String getSettingsUrl = params[0];
            String androidId = params[1];

            // create new utility
            MultipartUtility utility = new MultipartUtility(getSettingsUrl,"US-ASCII");

            // send JSON
            utility.addFormField("android_id",androidId);

            // Stop utility and get responses from the server
            List<String> responses = utility.finish();

            // print responses
            for(int i=0; i < responses.size(); i++) {
                Log.v(TAG, "Server output: " + responses.get(i));
            }
            if(responses.size()>0)
                return responses.get(0);

        } catch (Exception ex) {
            Log.e(TAG, "Error occurred during getting settings.");
            ex.printStackTrace();
        }
        return "";

    }

    @Override
    protected void onPostExecute(String result) {
        mainActivityWeak.get().receiveSettings(result);
    }
}
