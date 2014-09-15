package org.mozilla.mozstumbler.service.mainthread;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.mozilla.mozstumbler.service.stumblerthread.StumblerService;

// See PassiveServiceReceiver. Used to delay starting the service in passive mode.
// The received intent is reused/resent to its final destination.
public class DelayedStartAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Intent startServiceIntent = new Intent(intent);
        startServiceIntent.setClass(context, StumblerService.class);
        context.startService(startServiceIntent);
    }
}
