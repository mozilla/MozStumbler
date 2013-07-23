package org.mozilla.mozstumbler;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.StrictMode;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {

    private static final String LOGTAG = MainActivity.class.getName();

    private ScannerServiceInterface mConnectionRemote;
    private ServiceConnection mConnection;

    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(LOGTAG, "New intent with flags " + intent.getFlags());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableStrictMode();

        setContentView(R.layout.activity_main);

        Log.d(LOGTAG, "onCreate");
    }

    @Override
    protected void onStart() {
        super.onStart();

        mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder binder) {
                mConnectionRemote = ScannerServiceInterface.Stub.asInterface(binder);
                Log.d(LOGTAG, "Service connected");
                updateUI();
            }

            public void onServiceDisconnected(ComponentName className) {
                mConnectionRemote = null;
                Log.d(LOGTAG, "Service disconnected", new Exception());
            }
        };

        Intent intent = new Intent(this, ScannerService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        Log.d(LOGTAG, "onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();

        try {
            if (mConnectionRemote.isScanning()) {
                mConnectionRemote.showNotification();
            }
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        unbindService(mConnection);
        mConnection = null;
        mConnectionRemote = null;

        Log.d(LOGTAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(LOGTAG, "onDestroy");
    }

    protected void updateUI() {
        Log.d(LOGTAG, "Updating UI");

        boolean scanning = false;
        try {
            scanning = mConnectionRemote.isScanning();
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        int buttonText;
        if (scanning) {
            buttonText = R.string.stop_scanning;
        } else {
            buttonText = R.string.start_scanning;
        }

        Button scanningBtn = (Button) findViewById(R.id.toggle_scanning);
        scanningBtn.setText(buttonText);
    }

    public void onBtnClicked(View v) throws RemoteException {

        if (v.getId() == R.id.toggle_scanning) {

            boolean scanning = mConnectionRemote.isScanning();
            Log.d(LOGTAG, "Connection remote return: isScanning() = " + scanning);

            int buttonText;

            if (scanning) {
                mConnectionRemote.stopScanning();
                buttonText = R.string.start_scanning;
            } else {
                mConnectionRemote.startScanning();
                buttonText = R.string.stop_scanning;
            }

            Button b = (Button) v;
            b.setText(buttonText);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @TargetApi(9)
    private void enableStrictMode() {
        if (Build.VERSION.SDK_INT < 9) {
            return;
        }

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                  .detectAll().penaltyLog().build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll()
                  .penaltyLog().build());
    }
}
