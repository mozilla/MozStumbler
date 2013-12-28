package org.mozilla.mozstumbler;

import android.app.IntentService;
import android.content.Intent;
import android.text.format.DateFormat;
import android.util.Log;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

public class ActivityRecognitionIntentService extends IntentService {

    private static final String LOGTAG = ActivityRecognitionIntentService.class.getName();

    public ActivityRecognitionIntentService() {
        super(LOGTAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(LOGTAG, "onHandleIntent: recognition.");

        if (!ActivityRecognitionResult.hasResult(intent)) {
            return;
        }

        ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
        DetectedActivity mostProbableActivity = result.getMostProbableActivity();
        int activityType = mostProbableActivity.getType();
        int confidence = mostProbableActivity.getConfidence();

        Log.d(LOGTAG, "Receive recognition.");
        Log.d(LOGTAG, " activityType: " + activityType);
        Log.d(LOGTAG, " confidence: " + confidence);
        Log.d(LOGTAG, " time: " + DateFormat.format("hh:mm:ss.sss", result.getTime()));
        Log.d(LOGTAG, " elapsedTime: " + DateFormat.format("hh:mm:ss.sss", result.getElapsedRealtimeMillis()));


        Intent notify = new Intent("receive_recognition");
        notify.setPackage(getPackageName());
        notify.putExtra("activity_type", activityType);
        notify.putExtra("confidence", confidence);
        notify.putExtra("time", result.getTime());
        sendBroadcast(notify);
    }
}
