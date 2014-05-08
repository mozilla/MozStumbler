package org.mozilla.mozstumbler.client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import org.mozilla.mozstumbler.service.StumblerService;

public final class TurnOffReceiver extends BroadcastReceiver {
    private static final String LOGTAG = TurnOffReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(LOGTAG, "onReceive!");

        Intent serviceIntent = new Intent(context, StumblerService.class);
        IBinder binder = peekService(context, serviceIntent);
        if (binder != null) {
            // service is running, tell it to stop
            StumblerService.StumblerBinder serviceBinder = (StumblerService.StumblerBinder) binder;
            StumblerService service = serviceBinder.getService();
            service.stopScanning();
        }

        // In the case where the MainActivity is in the foreground, we need to tell it to update
        context.sendBroadcast(new Intent(MainActivity.ACTION_UPDATE_UI));
    }
}
