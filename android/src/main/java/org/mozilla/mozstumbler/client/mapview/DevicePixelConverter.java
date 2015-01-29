/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;

public class DevicePixelConverter {
    final float mDisplayDensity;

    DevicePixelConverter(Context ctx) {
        mDisplayDensity = ctx.getResources().getDisplayMetrics().density;
    }

    public int pxToDp(float pixels) {
        return (int) (pixels * mDisplayDensity + 0.5f);
    }
}
