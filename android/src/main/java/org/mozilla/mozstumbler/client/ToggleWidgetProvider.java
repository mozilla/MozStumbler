package org.mozilla.mozstumbler.client;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;
import org.mozilla.mozstumbler.R;

public class ToggleWidgetProvider extends AppWidgetProvider{

    private RemoteViews remoteViews;
    private ComponentName watchWidget;
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {
        for (int widgetId : appWidgetIds)   {

            remoteViews = new RemoteViews( context.getPackageName(), R.layout.toggle_widget );
            watchWidget = new ComponentName( context, ToggleWidgetProvider.class );

            Intent intentClick = new Intent(context,ToggleWidgetProvider.class);
            intentClick.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ""+appWidgetIds[0]);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, appWidgetIds[0],intentClick, 0);
            remoteViews.setOnClickPendingIntent(R.id.toggleServiceButton, pendingIntent);
            appWidgetManager.updateAppWidget( watchWidget, remoteViews );
        }
    }
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent.getAction() == null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                remoteViews = new RemoteViews(context.getPackageName(), R.layout.toggle_widget);

                watchWidget = new ComponentName(context, ToggleWidgetProvider.class);
                (AppWidgetManager.getInstance(context)).updateAppWidget(watchWidget, remoteViews);
                AppWidgetProvider provider = new AppWidgetProvider();
                Intent myIntent = new Intent(context, ToggleWidgetHelper.class);
                myIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(myIntent);
            }
        }
    }
}