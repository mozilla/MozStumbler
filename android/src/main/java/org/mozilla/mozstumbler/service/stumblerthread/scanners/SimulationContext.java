package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.content.Context;
import android.content.ContextWrapper;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.telephony.TelephonyManager;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by victorng on 14-11-04.
 */
public class SimulationContext extends ContextWrapper {

    public final static int SIMULATION_PING_INTERVAL = 1000 * 1; // Every second
    private static final String LOG_TAG = AppGlobals.makeLogTag(SimulationContext.class.getSimpleName());
    private double mLon;
    private double mLat;
    Handler handler = new Handler();
    private LocationManager locationManager;
    final boolean REQUIRED_NETWORK = false;
    final boolean REQUIRES_SATELLITE = false;
    final boolean REQUIRES_CELL = false;
    final boolean HAS_MONETARY_COST = false;
    final boolean SUPPORTS_ALTITUDE = false;
    final boolean SUPPORTS_SPEED = false;
    final boolean SUPPORTS_BEARING = false;
    final int POWER_REQUIREMENT = 0;
    final int ACCURACY = 5;
    private final WifiManager wifiManager;
    private boolean keepRunning;
    private Object nextWifiBlock;
    private List<CellInfo> nextCellBlock;

    public SimulationContext(Context context) {
        super(context);

        mLat = Prefs.getInstance().getSimulationLat();
        mLon = Prefs.getInstance().getSimulationLon();

        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        // Register test provider for GPS
        locationManager.addTestProvider(LocationManager.GPS_PROVIDER,
                REQUIRED_NETWORK, REQUIRES_SATELLITE,
                REQUIRES_CELL, HAS_MONETARY_COST, SUPPORTS_ALTITUDE, SUPPORTS_SPEED,
                SUPPORTS_BEARING, POWER_REQUIREMENT, ACCURACY);
        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,
                true);

        startRepeatingTask();
    }

    public synchronized void deactivateSimulation() {
        keepRunning = false;

        try {
            if (locationManager != null) {
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            }
        } catch (IllegalArgumentException ex) {
            // no test provider was registered.  Shouldn't happen but it's totally safe.
        }

    }

    void startRepeatingTask()
    {
        synchronized (this) {
            keepRunning = true;
        }

        handler.postDelayed(new Runnable() {
            public void run() {
                Location mockLocation = getNextGPSLocation();
                if (mockLocation == null) {
                    return;
                }

                synchronized(this) {
                    if (keepRunning) {
                        locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation);
                        // Send another ping
                        handler.postDelayed(this, SIMULATION_PING_INTERVAL);

                    }
                }
            }
        }, SIMULATION_PING_INTERVAL);
    }

    public Location getNextGPSLocation() {
        Location mockLocation = new Location(LocationManager.GPS_PROVIDER); // a string
        mockLocation.setLatitude(mLat);
        mockLocation.setLongitude(mLon);
        mockLocation.setAltitude(42.0);  // meters above sea level
        mockLocation.setAccuracy(5);
        mockLocation.setTime(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }

        // This is rougly ~5m-ish.
        mLat += 0.00003;
        mLon += 0.00003;

        return mockLocation;
    }

    public Object getSystemService(String name) {
        if (name.equals(Context.LOCATION_SERVICE)){
            return locationManager;
        }
        return super.getSystemService(name);
    }

    /*
     This returns the next fake wifi block of data
     */
    public List<ScanResult> getNextMockWifiBlock() {
        LinkedList<ScanResult> resultList = new LinkedList<ScanResult>();

        ScanResult scan = null;
        try {
            scan = makeScanResult();
            scan.BSSID = "E0:3F:49:98:A6:A4";
            resultList.add(scan);

            scan = makeScanResult();
            scan.BSSID = "E0:3F:49:98:A6:A0";
            resultList.add(scan);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error creating a mock ScanResult", e);
        }

        return resultList;
    }

    public List<CellInfo> getNextMockCellBlock() {
        LinkedList<CellInfo> result = new LinkedList<CellInfo>();

        try {
            result.add(makeCellInfo(1, 1, 60330, 1660199, 19));
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error creating a mock CellInfo block", e);
        }
        return result;
    }

    private ScanResult makeScanResult() throws IllegalAccessException,
            InstantiationException,
            InvocationTargetException {

        Constructor<?>[] ctors = ScanResult.class.getDeclaredConstructors();
        for (Constructor<?> ctor : ctors) {
            int len = ctor.getParameterTypes().length;
            if (len == 5) {
                ctor.setAccessible(true);
                return (ScanResult) ctor.newInstance("", "", "", 0, 0);
            }
            if (len == 6) { // Android 4.4.3 has this constructor
                ctor.setAccessible(true);
                return (ScanResult) ctor.newInstance(null, "", "", 0, 0, 0);
            }
        }
        return null;

    }

    private CellInfo makeCellInfo(int mcc, int mnc, int lac, int cid, int asu) throws IllegalAccessException, InvocationTargetException {
        CellInfo cell = new CellInfo(TelephonyManager.PHONE_TYPE_GSM);
        Method method = getMethod(CellInfo.class, "setGsmCellInfo");
        assert (method != null);
        method.invoke(cell, mcc, mnc, lac, cid, asu);
        return cell;
    }

    private Method getMethod(Class<?> c, String name) {
        Method[] methods = c.getDeclaredMethods();
        for (Method m : methods) {
            if (m.getName().contains(name)) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

}

