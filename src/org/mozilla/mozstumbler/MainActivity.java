package org.mozilla.mozstumbler;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver; 
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.Toast; 

public class MainActivity extends Activity {

    private static final String LOGTAG = MainActivity.class.getName();

    private ScannerServiceInterface mConnectionRemote;
    private ServiceConnection mConnection;

    private ServiceBroadcastReceiver mReceiver;

    private class ServiceBroadcastReceiver extends BroadcastReceiver {
        private boolean mReceiverIsRegistered;

        public void register() {
          if (!mReceiverIsRegistered) {
            registerReceiver(this, new IntentFilter(ScannerService.MESSAGE_TOPIC));
            mReceiverIsRegistered = true;
          }
        }
    
        public void unregister() {
          if (mReceiverIsRegistered) {
            unregisterReceiver(this);
            mReceiverIsRegistered = false;
          }
        }
    
        @Override
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          if (!action.equals(ScannerService.MESSAGE_TOPIC)) {
            Log.e(LOGTAG, "Received an unknown intent");
            return;
          }
    
          String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
          if (subject.equals("Notification")) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            Toast.makeText(getApplicationContext(), (CharSequence) text,
                           Toast.LENGTH_SHORT).show();
    
            Log.d(LOGTAG, "Received a notification intent and showing: "
                  + text);
            return;
          } else {
            Log.e(LOGTAG, "Received an unknown notification intent");
            return;
          }
        }
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

        mReceiver = new ServiceBroadcastReceiver();
        mReceiver.register(); 

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
        mReceiver.unregister();
        mReceiver = null; 
        Log.d(LOGTAG, "onStop");
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

        Button scanningBtn = (Button) findViewById(R.id.toggle_scanning);
        if (scanning) {
          scanningBtn.setText(R.string.stop_scanning);
        } else {
          scanningBtn.setText(R.string.start_scanning);
        }
    }

    public void onBtnClicked(View v) throws RemoteException {
        if (v.getId() == R.id.toggle_scanning) {
            boolean scanning = mConnectionRemote.isScanning();
            Log.d(LOGTAG, "Connection remote return: isScanning() = " + scanning);

            Button b = (Button) v;
            if (scanning) {
                mConnectionRemote.stopScanning();
                b.setText(R.string.start_scanning);
            } else {
                mConnectionRemote.startScanning();
                b.setText(R.string.stop_scanning);
            }
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
