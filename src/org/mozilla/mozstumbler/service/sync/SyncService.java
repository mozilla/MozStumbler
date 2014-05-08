package org.mozilla.mozstumbler.service.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import org.mozilla.mozstumbler.service.sync.SyncAdapter;

public class SyncService extends Service {
    private static SyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public void onCreate() {
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new SyncAdapter(getApplicationContext(), false);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }

}
