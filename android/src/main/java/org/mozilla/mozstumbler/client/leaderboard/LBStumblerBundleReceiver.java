/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.leaderboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.service.stumblerthread.Reporter;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.StumblerBundle;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploadParam;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploaderMLS;

public class LBStumblerBundleReceiver extends BroadcastReceiver {
    class UploadingOccurringReceiver extends BroadcastReceiver {
        LBUploadTask mUploadTask;

        @Override
        public void onReceive(Context context, Intent intent) {
            // start this upload
            if (mUploadTask != null &&
                    mUploadTask.isUploading.get()) {
                return;
            }

            mUploadTask = new LBUploadTask(mStorage);
            boolean ignoredValue = false;
            ClientPrefs prefs = ClientPrefs.getInstance(context);
            AsyncUploadParam params = new AsyncUploadParam(ignoredValue, ignoredValue,
                    prefs.getEmail(), prefs.getNickname());
            mUploadTask.execute(params);
        }
    }

    final LBDataStorage mStorage;
    final UploadingOccurringReceiver mUploadTrigger = new UploadingOccurringReceiver();

    public LBStumblerBundleReceiver(Context c) {
        mStorage = new LBDataStorage(c);

        LocalBroadcastManager.getInstance(c).registerReceiver(this,
                new IntentFilter(Reporter.ACTION_NEW_BUNDLE));

        LocalBroadcastManager.getInstance(c).registerReceiver(mUploadTrigger,
                new IntentFilter(AsyncUploaderMLS.ACTION_MLS_UPLOAD_COMPLETED));

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final StumblerBundle bundle = intent.getParcelableExtra(Reporter.NEW_BUNDLE_ARG_BUNDLE);
        mStorage.insert(bundle.getGpsPosition(),
                bundle.getUnmodifiableCellData().size(),
                bundle.getUnmodifiableWifiData().size());
    }
}
