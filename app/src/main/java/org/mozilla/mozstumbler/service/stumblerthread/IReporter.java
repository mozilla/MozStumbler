/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread;

import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

/**
 * This is the public interface for the Reporter class.  
 * All methods here are properly synchronized as the reporter
 * must manage a consistent StumblerBundle instance.
 */
public interface IReporter {
    /*
     * There are 2 threads of control that can access the
     * reporter.  
     *
     * The StumberService (or ClientStumblerService)
     * has a reference called mReporter which is an instance of
     * the Reporter object.  It invokes the flush() method when
     * StumblerService::stopScanning() is called - usually from UI
     * events from an Activity.
     *
     * The other thread of control is the Android intents pump,
     * which calls into the Reporter class through onReceive via
     * an Intent.
     * 
     */

    public void startup(Context context);

    public void shutdown();

    public void flush();

    public JSONObject getPreviousBundleJSON();
}
