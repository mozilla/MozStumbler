package org.mozilla.mozstumbler.service.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;

import org.mozilla.mozstumbler.service.datahandling.DatabaseContract;
import org.mozilla.mozstumbler.service.SharedConstants;

public final class SyncUtils {
    // private static final long SYNC_FREQUENCY = 3 * 60 * 60;  // 3 hours (in seconds)
    private static final String CONTENT_AUTHORITY = DatabaseContract.CONTENT_AUTHORITY;

    private SyncUtils() {
    }

    public static void CreateSyncAccount(Context context) {
        // Create account, if it's missing. (Either first run, or user has deleted account.)
        Account account = AuthenticatorService.GetAccount();
        AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        if (accountManager.addAccountExplicitly(account, null, null)) {
            ContentResolver.setIsSyncable(account, CONTENT_AUTHORITY, 1);
            ContentResolver.setSyncAutomatically(account, CONTENT_AUTHORITY, true);
            // Recommend a schedule for automatic synchronization. The system may modify this based
            // on other scheduled syncs and network utilization.
            // ContentResolver.addPeriodicSync(
            //        account, CONTENT_AUTHORITY, new Bundle(),SYNC_FREQUENCY);
        }
    }

    public static void TriggerRefresh(boolean ignoreWifiStatus) {
        Bundle b = new Bundle();
        b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        b.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        b.putBoolean(SharedConstants.SYNC_EXTRAS_IGNORE_WIFI_STATUS, ignoreWifiStatus);
        ContentResolver.requestSync(
                AuthenticatorService.GetAccount(),
                CONTENT_AUTHORITY,
                b);
    }
}
