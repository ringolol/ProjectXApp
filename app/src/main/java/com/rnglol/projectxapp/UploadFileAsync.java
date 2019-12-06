package com.rnglol.projectxapp;

import android.os.AsyncTask;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class UploadFileAsync extends AsyncTask<String, Void, String> {

    private final static String TAG = "ProjectX/UploadFile";
    private MultipartUtility utility;

    @Override
    protected String doInBackground(String... params) {
        String answer = "";
        try {
            String absolutePath = params[0];
            String sourceFileUri = params[1];
            String serverUrl = params[2];
            String android_id = params[3];

            utility = new MultipartUtility(serverUrl,"US-ASCII");

            File sourceFile = new File(absolutePath,sourceFileUri);
            utility.addFormField("android_id",android_id);

            utility.addFilePart("sent_image", sourceFile);

            // Out put from server
            List<String> strs = utility.finish();
            for(int i=0; i < strs.size(); i++) {
                Log.d(TAG, "Server output: " + strs.get(i));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return answer;

        /*Log.d(TAG,"start_send");
        String absolutePath = params[0];
        String sourceFileUri = params[1];
        HttpURLConnection conn = null;
        DataOutputStream dos = null;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1 * 1024 * 1024;
        File sourceFile = new File(absolutePath,sourceFileUri);
        String answer = "";

        if (sourceFile.isFile()) {
            try {
                String upLoadServerUri = params[2];

                // open a URL connection to the Servlet
                FileInputStream fileInputStream = new FileInputStream(
                        sourceFile);
                URL url = new URL(upLoadServerUri);

                // Open a HTTP connection to the URL
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true); // Allow Inputs
                conn.setDoOutput(true); // Allow Outputs
                conn.setUseCaches(false); // Don't use a Cached Copy
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("ENCTYPE", "multipart/form-data");
                conn.setRequestProperty("Content-Type",
                        "multipart/form-data;boundary=" + boundary);

                dos = new DataOutputStream(conn.getOutputStream());

                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data;name=\"sent_image\";filename=\""
                        + sourceFileUri + "\"" + lineEnd);

                dos.writeBytes(lineEnd);

                // create a buffer of maximum size
                bytesAvailable = fileInputStream.available();

                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                buffer = new byte[bufferSize];

                // read file and write it into form...
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                while (bytesRead > 0) {

                    dos.write(buffer, 0, bufferSize);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math
                            .min(bytesAvailable, maxBufferSize);
                    bytesRead = fileInputStream.read(buffer, 0,
                            bufferSize);

                }

                // send multipart form data necesssary after file
                // data...
                dos.writeBytes(lineEnd);
                dos.writeBytes(twoHyphens + boundary + twoHyphens
                        + lineEnd);

                // close the streams
                fileInputStream.close();
                dos.flush();
                dos.close();

                Log.d(TAG,"successfully sent");

                InputStream in = conn.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(in);

                int inputStreamData = inputStreamReader.read();
                while (inputStreamData != -1) {
                    char current = (char) inputStreamData;
                    inputStreamData = inputStreamReader.read();
                    answer += current;
                }

            } catch (Exception e) {
                Log.d(TAG,"error while sending");
                e.printStackTrace();
            }
        }
        Log.w(TAG,"SERVER ANS: " + answer);
        return answer;*/
    }

    @Override
    protected void onPostExecute(String result) {
    }

    @Override
    protected void onPreExecute() {
    }

    @Override
    protected void onProgressUpdate(Void... values) {
    }
}