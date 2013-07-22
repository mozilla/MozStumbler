package org.mozilla.mozstumbler;

import java.util.Calendar;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

public class ScannerService extends Service {

	private static final String LOGTAG = ScannerService.class.getName();
	private static final int NOTIFICATION = 0;
	private static final int WAKE_TIMEOUT = 5 * 1000;
	private Scanner mScanner = null;
	private LooperThread mLooper = null;
	private PendingIntent mWakeIntent = null;

	class LooperThread extends Thread {
		public Handler mHandler;

		public void run() {
			Looper.prepare();
			mHandler = new Handler();
			Looper.loop();
		}
	}

	public class ScannerServiceBinder extends Binder {
		ScannerService getService() {
			return ScannerService.this;
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.d(LOGTAG, "onCreate");

		mScanner = new Scanner(this);
		mLooper = new LooperThread();
		mLooper.start();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(LOGTAG, "onDestroy");

		mLooper.interrupt();
		mLooper = null;
		mScanner = null;

		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.cancel(NOTIFICATION);

		// TODO Toast.makeText(this, R.string.local_service_stopped,
		// Toast.LENGTH_SHORT).show();
	}

	public void postNotification() {

		if (mLooper.mHandler == null)
			return;

		mLooper.mHandler.post(new Runnable() {
			@Override
			public void run() {

				NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
				Context ctx = getApplicationContext();
				Intent notificationIntent = new Intent(ctx, MainActivity.class);
				PendingIntent contentIntent = PendingIntent.getActivity(ctx,
						NOTIFICATION, notificationIntent,
						PendingIntent.FLAG_CANCEL_CURRENT);

				Resources res = ctx.getResources();
				Notification.Builder builder = new Notification.Builder(ctx);

				builder.setContentIntent(contentIntent)
						.setSmallIcon(R.drawable.ic_launcher)
						.setOngoing(true)
						.setAutoCancel(false)
						.setContentTitle(res.getString(R.string.service_name))
						.setContentText(
								res.getString(R.string.service_scanning));
				Notification n = builder.build();

				nm.notify(NOTIFICATION, n);
			}
		});
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// keep running!
		return Service.START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.d(LOGTAG, "onBind");

		return new ScannerServiceInterface.Stub() {

			@Override
			public boolean isScanning() throws RemoteException {
				return mScanner.isScanning();
			};

			@Override
			public void startScanning() throws RemoteException {
				if (mScanner.isScanning() == true) {
					return;
				}

				mLooper.mHandler.post(new Runnable() {
					@Override
					public void run() {
						mScanner.startScanning();

						// keep us awake.
						Context cxt = getApplicationContext();
						Calendar cal = Calendar.getInstance();
						Intent intent = new Intent(cxt, ScannerService.class);
						mWakeIntent = PendingIntent.getService(cxt, 0, intent,
								0);
						AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
						alarm.setRepeating(AlarmManager.RTC_WAKEUP,
								cal.getTimeInMillis(), WAKE_TIMEOUT,
								mWakeIntent);

					}
				});
			};

			@Override
			public void stopScanning() throws RemoteException {

				if (mScanner.isScanning() == false) {
					return;
				}

				mLooper.mHandler.post(new Runnable() {
					@Override
					public void run() {
						AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
						alarm.cancel(mWakeIntent);

						NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
						nm.cancel(NOTIFICATION);

						mScanner.stopScanning();
					}
				});
			}

			@Override
			public void showNotification() throws RemoteException {
				postNotification();
			};
		};
	}
}
