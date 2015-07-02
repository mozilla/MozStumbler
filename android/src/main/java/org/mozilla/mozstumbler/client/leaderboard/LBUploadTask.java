/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.leaderboard;

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.http.IResponse;
import org.mozilla.mozstumbler.service.core.http.FxAService;
import org.mozilla.mozstumbler.service.core.http.ISubmitService;
import org.mozilla.mozstumbler.service.core.http.MLSLocationService;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.JSONRowsStorageManager;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploadParam;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploader;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;

import java.util.HashMap;
import java.util.Map;

class LBUploadTask extends AsyncUploader {
    class LBSubmitter implements ISubmitService {
        private static final String SUBMIT_URL = BuildConfig.LB_SUBMIT_URL;
        final IHttpUtil httpDelegate = (IHttpUtil) ServiceLocator.getInstance().getService(IHttpUtil.class);

        @Override
        public IResponse submit(byte[] data, Map<String, String> headers, boolean precompressed) {
            return httpDelegate.post(SUBMIT_URL, data, headers, precompressed);
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
        // TODO: do we want to handle a case where the bearer token is not set
        // and FxA is not logged in?  LBUploadTask should probably be created
        // with the bearer token passed in explicitly instead of loading the
        // OAuth state at runtime.
        if (bearerToken != null) {
            headers.put(FxAService.BEARER_HEADER, "Bearer " + bearerToken);
        }
        return headers;
    }

    @Override
    protected boolean checkCanUpload(AsyncUploadParam param) {
        // This task is triggered only on a successful upload of MLS data, so we assume
        // this can also upload without further checks

        // TODO: do we want to handle a case where the bearer token is not set
        // and FxA is not logged in?  LBUploadTask should probably be created
        // with the bearer token passed in explicitly instead of loading the
        // OAuth state at runtime.

        return true;
    }
}
