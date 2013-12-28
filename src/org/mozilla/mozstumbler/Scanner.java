package org.mozilla.mozstumbler;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.mozilla.mozstumbler.cellscanner.CellScanner;

class Scanner {
  private static final String LOGTAG = Scanner.class.getName();

  private final Context  mContext;
  private boolean        mIsScanning;

  private GPSScanner     mGPSScanner;
  private WifiScanner    mWifiScanner;
  private CellScanner    mCellScanner;

  Scanner(Context context) {
    mContext = context;
    mGPSScanner  = new GPSScanner(context);
    mWifiScanner = new WifiScanner(context);
    mCellScanner = new CellScanner(context);
  }

  void startScanning() {
    if (mIsScanning) {
      return;
    }
    Log.d(LOGTAG, "Scanning started...");

    mGPSScanner.start();
    mWifiScanner.start();
    mCellScanner.start();

    mIsScanning = true;

    // FIXME convey "start" event here?
    // for now all we want is to update the UI anyway
    Intent startIntent = new Intent(ScannerService.MESSAGE_TOPIC);
    startIntent.putExtra(Intent.EXTRA_SUBJECT, "Scanner");
    mContext.sendBroadcast(startIntent);
  }

  void stopScanning() {
    if (!mIsScanning) {
      return;
    }

    Log.d(LOGTAG, "Scanning stopped");

    mGPSScanner.stop();
    mWifiScanner.stop();
    mCellScanner.stop();

    mIsScanning = false;

    // FIXME convey "stop" event here?
    // for now all we want is to update the UI anyway
    Intent stopIntent = new Intent(ScannerService.MESSAGE_TOPIC);
    stopIntent.putExtra(Intent.EXTRA_SUBJECT, "Scanner");
    mContext.sendBroadcast(stopIntent);
  }

  boolean isScanning() {
    return mIsScanning;
  }

  int getAPCount() {
     return mWifiScanner.getAPCount();
  }

  int getVisibleAPCount() {
     return mWifiScanner.getVisibleAPCount();
  }

  int getLocationCount() {
     return mGPSScanner.getLocationCount();
  }

  double getLatitude() {
     return mGPSScanner.getLatitude();
  }

  double getLongitude() {
     return mGPSScanner.getLongitude();
  }
}
