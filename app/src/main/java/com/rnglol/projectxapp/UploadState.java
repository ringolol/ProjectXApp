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
            Log.d(TAG, "Sending JSON...");

            // input parameters
            String sendJsonUrl = params[0];
            String json = params[1];

            // create new utility
            MultipartUtility utility = new MultipartUtility(sendJsonUrl,"US-ASCII");

            // send JSON
            utility.addFormField("json", json);

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