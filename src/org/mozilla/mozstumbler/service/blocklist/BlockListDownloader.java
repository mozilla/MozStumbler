package org.mozilla.mozstumbler.service.blocklist;

import android.content.Context;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.Prefs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class BlockListDownloader {
    public boolean mIsTestMode;
    Context c;

    public BlockListDownloader(Context co) {
        c = co;
    }

    String getSha() {
        if (!mIsTestMode) {
            return "bb215d4a5e8a7fbe41cd7d61b1bb20e5745e068b";
        }
        String sha;
        try {
            //connection code
            HttpClient client = new DefaultHttpClient();
            HttpConnectionParams.setConnectionTimeout(client.getParams(), 10000); //Timeout Limit
            HttpGet get = new HttpGet("https://api.github.com/repos/mozilla/MozStumbler/contents/res/blocklists/BlockList.txt");
            HttpResponse response = client.execute(get);
            InputStream is = new BufferedHttpEntity(response.getEntity()).getContent();
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            String line;
            StringBuilder total = new StringBuilder();

            //read the json data
            while ((line = r.readLine()) != null)
                total.append(line);
            JSONObject jsonObj = new JSONObject(total + "");
            sha = jsonObj.getString("sha");
        } catch (Exception e) {
            sha = "error";
        }
        return sha;
    }

    boolean isNewSha(String a, String b) {
        return !a.equals(b);
    }

    void updatePrefs(String sha) {
        //update the shared preference with the value of sha
        Prefs.getInstance().setSha("SHA", sha);
    }

    boolean getFile() {
        Log.d("******************************************************", "@Entering getFile()");


        boolean flag = true;
        try {
            //download the new file

            URL url = new URL("https://raw.githubusercontent.com/mozilla/MozStumbler/master/res/blocklists/BlockList.txt");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            Log.d("******************************************************", "@HttpURLConnection opened");
            //set up some things on the connection
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoOutput(true);

            //connect
            urlConnection.connect();

            Log.d("******************************************************", "@connection made");

            File file = new File("/sdcard/MozStumblerData/BlockList.txt");
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            Log.d("******************************************************", "@File object created");
            FileOutputStream fileOutput = new FileOutputStream(file);

            Log.d("******************************************************", "@FileOutputStream made");
            //this will be used in reading the data from the internet
            InputStream inputStream = urlConnection.getInputStream();

            Log.d("******************************************************", "@Connection InputStream obtained");

            //create a buffer...
            byte[] buffer = new byte[1024];

            int bufferLength; //used to store a temporary size of the buffer
            //now, read through the input buffer and write the contents to the file
            while ((bufferLength = inputStream.read(buffer)) > 0) {
                //add the data in the buffer to the file in the file output stream (the file on the sd card
                fileOutput.write(buffer, 0, bufferLength);
                //add up the size so we know how much is downloaded

            }
            //close the output stream when done
            fileOutput.close();
            Log.d("******************************************************", "@FileOutputStream closed");

            inputStream.close();

            Log.d("******************************************************", "@InputStream closed");
        } catch (Exception e) {
            flag = false;

            Log.d("******************************************************", "@Exception is " + e.getMessage());
        }


        Log.d("******************************************************", "@Leaving getFile()");

        return flag;
    }

    public void checkForNewList() {

        Log.d("******************************************************", "@Check for new list entered");


        String oldSha = Prefs.getInstance().getSha("sha");
        Log.d("******************************************************", "@The old sha obtained is " + oldSha + "!");

        String newSha = getSha();
        Log.d("******************************************************", "@The new sha obtained is " + newSha + "!");

        boolean isNew = isNewSha(oldSha, newSha);
        Log.d("******************************************************", "@The old and new shas checked:" + isNew);

        if (!isNew)
            return;
        boolean isOk = getFile();
        Log.d("******************************************************", "@getFile() Result is: " + isOk);

        if (isOk) {
            Log.d("******************************************************", "@File got");
            updatePrefs(newSha);
            Log.d("******************************************************", "@Prefs updated");
        }

        Log.d("******************************************************", "@Leaving method");
    }
}