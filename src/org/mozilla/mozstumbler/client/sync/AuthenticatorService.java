package org.mozilla.mozstumbler.client.sync;

import android.accounts.Account;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AuthenticatorService extends Service {
    private static final String ACCOUNT_NAME = "sync";
    private static final String ACCOUNT_TYPE = "org.mozilla.mozstumbler";

    private Authenticator mAuthenticator;

    public static Account GetAccount() {
        final String accountName = ACCOUNT_NAME;
        return new Account(accountName, ACCOUNT_TYPE);
    }

    @Override
    public void onCreate() {
        // Create a new authenticator object
        mAuthenticator = new Authenticator(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mAuthenticator.getIBinder();
    }
}
