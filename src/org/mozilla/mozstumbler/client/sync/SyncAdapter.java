package org.mozilla.mozstumbler.client.sync;

import android.accounts.Account;
import android.annotation.TargetApi;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import org.mozilla.mozstumbler.service.SharedConstants;
import org.mozilla.mozstumbler.service.sync.UploadReports;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String LOGTAG = SyncAdapter.class.getName();

    private UploadReports mUploadReports;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mUploadReports = new UploadReports();
    }

    /**
     * Set up the sync adapter. This form of the
     * constructor maintains compatibility with Android 3.0
     * and later platform versions
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public SyncAdapter(
            Context context,
            boolean autoInitialize,
            boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);

        mUploadReports = new UploadReports();
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        final boolean ignoreNetworkStatus = extras.getBoolean(SharedConstants.SYNC_EXTRAS_IGNORE_WIFI_STATUS, false);
        mUploadReports.uploadReports(ignoreNetworkStatus, syncResult);
        Log.i(LOGTAG, "Network synchronization complete");
    }

}
