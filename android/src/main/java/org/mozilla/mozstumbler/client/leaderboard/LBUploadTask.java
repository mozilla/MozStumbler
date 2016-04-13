/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.leaderboard;

import android.text.TextUtils;

import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.http.FxAService;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.http.IResponse;
import org.mozilla.mozstumbler.service.core.http.ISubmitService;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.JSONRowsStorageManager;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploadParam;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploader;
import org.mozilla.mozstumbler.service.utils.Zipper;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.HashMap;
import java.util.Map;

class LBUploadTask extends AsyncUploader {

    private static ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private String LOG_TAG = LoggerUtil.makeLogTag(LBUploadTask.class);

    class LBSubmitter implements ISubmitService {


        private ClientPrefs getPrefs() {
            return ClientPrefs.getInstanceWithoutContext();
        }


        final IHttpUtil httpDelegate = (IHttpUtil) ServiceLocator.getInstance().getService(IHttpUtil.class);

        @Override
        public IResponse submit(byte[] data, Map<String, String> headers, boolean precompressed) {
            final String submit_url = getPrefs().getLbSubmitUrl();

            if (TextUtils.isEmpty(submit_url)) {
                return null;
            }
            Log.d(LOG_TAG, "Sending leaderboard data to: [" + submit_url + "]");

            String sData = "";

            if (precompressed) {
                sData = Zipper.unzipData(data);
            } else {
                sData = new String(data);
            }
            Log.d(LOG_TAG, "Sending leaderboard data: " + sData);

            IResponse resp =  httpDelegate.post(submit_url, data, headers, precompressed);
            Log.d(LOG_TAG, "Got response: " + resp.httpStatusCode());
            return resp;
        }
    }

    public LBUploadTask(JSONRowsStorageManager storageManager) {
        super(storageManager);
    }

    @Override
    protected ISubmitService getSubmitter() {
        return new LBSubmitter();
    }

    @Override
    protected HashMap<String, String> getHeaders(AsyncUploadParam param) {
        HashMap<String,String> headers = new HashMap<String, String>();


        String bearerToken = Prefs.getInstanceWithoutContext().getBearerToken();
        headers.put("Content-Encoding", "gzip");

        if (!TextUtils.isEmpty(bearerToken)) {
            headers.put(FxAService.BEARER_HEADER, "Bearer " + bearerToken);
        }
        return headers;
    }

    @Override
    protected boolean checkCanUpload(AsyncUploadParam param) {
        return true;
    }
}
