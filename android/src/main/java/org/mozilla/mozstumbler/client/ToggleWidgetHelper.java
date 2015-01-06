package org.mozilla.mozstumbler.client;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import org.mozilla.mozstumbler.R;
public class ToggleWidgetHelper extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Bundle remoteViewsBundle = getIntent().getExtras();
        RemoteViews remoteViews = new RemoteViews(getApplicationContext().getPackageName(), R.layout.toggle_widget);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_helper);
        MainApp app = (MainApp) getApplication();
        if (app.getService() == null) {
            return;
        }
        boolean isScanning = app.getService().isScanning();
        if (isScanning) {
            remoteViews.setTextViewText(R.id.stumbler_info1, "0");
            remoteViews.setTextViewText(R.id.stumbler_info2, "0");
            remoteViews.setImageViewResource(R.id.toggleServiceButton, R.drawable.ic_status_scanning);
            remoteViews.setViewVisibility(R.id.stumbler_info_bar, View.INVISIBLE);
            app.stopScanning();
        } else {
            remoteViews.setTextViewText(R.id.stumbler_info1, "0");
            remoteViews.setTextViewText(R.id.stumbler_info2, "0");
            remoteViews.setImageViewResource(R.id.toggleServiceButton, R.drawable.ic_launcher);
            remoteViews.setViewVisibility(R.id.stumbler_info_bar, View.VISIBLE);
            app.startScanning();
        }
        (AppWidgetManager.getInstance(getApplicationContext())).updateAppWidget(new ComponentName(getApplicationContext(), ToggleWidgetProvider.class), remoteViews);
        this.finish();
    }
}