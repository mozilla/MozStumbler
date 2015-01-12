/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.svclocator.services;

import android.appwidget.AppWidgetManager;

/*
 This class just provides a proxy around the AppWidgetManager so that we can safely mock it out
 when we need to run tests.
 */
public class AppWidgetManagerProxy {
    public void updateAppWidget(android.content.Context context,
                                android.content.ComponentName provider,
                                android.widget.RemoteViews views) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        manager.updateAppWidget(provider, views);
    }

}
