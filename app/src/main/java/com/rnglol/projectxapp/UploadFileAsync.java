package com.rnglol.projectxapp;

import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class UploadFileAsync extends AsyncTask<String, Void, String> {

    private final static String TAG = "ProjectX/UploadFile";
    private MultipartUtility utility;

    @Override
    protected String doInBackground(String... params) {
        try {
            String path = params[0];
            String fileName = params[1];
            String fileSendName = params[2];
            String sendFileUrl = params[3];
            String androidId = params[4];
            String timeStamp = params[5];

            utility = new MultipartUtility(sendFileUrl,"US-ASCII");

            File sourceFile = new File(path,fileName);
            utility.addFormField("android_id",androidId);
            utility.addFormField("time_stamp",timeStamp);

            utility.addFilePart(fileSendName, sourceFile);

            // Stop utility and get responses from the server
            List<String> responses = utility.finish();

            for(int i=0; i < responses.size(); i++) {
                Log.d(TAG, "Server output: " + responses.get(i));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
}