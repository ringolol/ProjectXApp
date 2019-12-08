package com.rnglol.projectxapp;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.util.List;

class UploadState extends AsyncTask<String, Void, String> {

    private final static String TAG = "ProjectX/UploadJSON";

    @Override
    protected String doInBackground(String... params) {
        try {
            // input paremeters
            String sendJsonUrl = params[0];
            String androidId = params[1];
            String json = params[2];

            // create new utility
            MultipartUtility utility = new MultipartUtility(sendJsonUrl,"US-ASCII");
            // send fields (android_id and JSON)
            // todo don't send android_id twice
            utility.addFormField("android_id",androidId);
            utility.addFormField("json",json);

            // Stop utility and get responses from the server
            List<String> responses = utility.finish();

            // print responses
            for(int i=0; i < responses.size(); i++) {
                Log.d(TAG, "Server output: " + responses.get(i));
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error occurred during JSON sending.");
        }
        return "";
    }

}