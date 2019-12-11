package com.rnglol.projectxapp;

import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class UploadFileAsync extends AsyncTask<String, Void, String> {

    private final static String TAG = "ProjectX/UploadFile";

    @Override
    protected String doInBackground(String... params) {
        try {
            Log.d(TAG, "Sending photo...");
            // input parameters
            final String path = params[0];
            final String fileName = params[1];
            final String fileSendName = params[2];
            final String sendFileUrl = params[3];
            final String androidId = params[4];
            final String timeStamp = params[5];

            // create new utility
            MultipartUtility utility = new MultipartUtility(sendFileUrl,"US-ASCII");

            // load file
            File sourceFile = new File(path,fileName);
            // send fields (android_id and time_stamp)
            utility.addFormField("android_id",androidId);
            utility.addFormField("time_stamp",timeStamp);
            // send file
            utility.addFilePart(fileSendName, sourceFile);

            // Stop utility and get responses from the server
            List<String> responses = utility.finish();

            // print responses
            for(int i=0; i < responses.size(); i++) {
                Log.d(TAG, "Server output: " + responses.get(i));
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error occurred during Photo sending.");
            ex.printStackTrace();
        }
        return "";
    }
}