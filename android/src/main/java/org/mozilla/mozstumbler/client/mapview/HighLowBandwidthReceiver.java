/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.mapview;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import java.lang.ref.WeakReference;

public class HighLowBandwidthReceiver extends BroadcastReceiver {
    WeakReference<MapFragment> mMapFragment = new WeakReference<MapFragment>(null);

    public HighLowBandwidthReceiver(MapFragment map) {
        mMapFragment = new WeakReference<MapFragment>(map);
        Context c = mMapFragment.get().getActivity();
        assert(c != null && c.getApplicationContext() != null);
        c.getApplicationContext().registerReceiver(this,
                new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        MapFragment map = mMapFragment.get();
        if (map == null || map.getActivity() == null) {
            return;
        }
        map.mapNetworkConnectionChanged();
    }

    public void unregister(Context applicationContext) {
        applicationContext.unregisterReceiver(this);
        mMapFragment = new WeakReference<MapFragment>(null);
    }
}
