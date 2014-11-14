package org.mozilla.mozstumbler.service.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public class BatteryCheck {
    public static class BatteryInfo {
        public int level;
        public boolean isCharging;
    }

    public static BatteryInfo batteryLevel(Context context) {
        BatteryInfo info = new BatteryInfo();
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) {
            info.level = -1;
            return info;
        }

        int rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        info.isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING);
        info.level = Math.round(rawLevel * scale / 100.0f);

        return info;
    }

    public static boolean isBatteryLessThan(int percent, Context context) {
        BatteryCheck.BatteryInfo info = BatteryCheck.batteryLevel(context);
        if (info.level < 0)
            return false;
        return !info.isCharging && info.level < percent;
    }
}
