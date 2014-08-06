package org.mozilla.mozstumbler.client.fragments.monitor;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.service.StumblerService;
import org.mozilla.mozstumbler.service.scanners.WifiScanner;

/**
 * Created by JeremyChiang on 2014-08-06.
 */
public class StumblingDataFragment extends Fragment {

    public interface DismissStumblingDataFragmentListener {
        public void dismissStumblingDataFragment();
    }

    private DismissStumblingDataFragmentListener dismissStumblingDataFragmentListener;

    private int wifiStatus;
    private double latitude;
    private double longitude;

    private int locationsScanned;
    private int accessPointsScanned;
    private int accessPointsVisible;

    private int cellTowersScanned;
    private int cellTowersVisible;

    private TextView wifiStatusTextView;
    private TextView latitudeTextView;
    private TextView longitudeTextView;

    private TextView locationsScannedTextView;
    private TextView accessPointsScannedTextView;
    private TextView accessPointsVisibleTextView;

    private TextView cellTowersScannedTextView;
    private TextView cellTowersVisibleTextView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_stumbling_data, container, false);

        wifiStatusTextView = (TextView)rootView.findViewById(R.id.wifi_status_text_view);
        latitudeTextView = (TextView)rootView.findViewById(R.id.latitude_text_view);
        longitudeTextView = (TextView)rootView.findViewById(R.id.longitude_text_view);

        locationsScannedTextView = (TextView)rootView.findViewById(R.id.location_scanned_text_view);
        accessPointsScannedTextView = (TextView)rootView.findViewById(R.id.access_points_scanned_text_view);
        accessPointsVisibleTextView = (TextView)rootView.findViewById(R.id.access_points_visible_text_view);

        cellTowersScannedTextView = (TextView)rootView.findViewById(R.id.cell_towers_scanned_text_view);
        cellTowersVisibleTextView = (TextView)rootView.findViewById(R.id.cell_towers_visible_text_view);

        rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissStumblingDataFragmentListener.dismissStumblingDataFragment();
            }
        });

        setWifiStatus(wifiStatus);
        setLatitude(latitude);
        setLongitude(longitude);

        setLocationsScanned(locationsScanned);
        setAccessPointsScanned(accessPointsScanned);
        setAccessPointsVisible(accessPointsVisible);

        setCellTowersScanned(cellTowersScanned);
        setCellTowersVisible(cellTowersVisible);

        return rootView;
    }

    public void setDismissStumblingDataFragmentListener(DismissStumblingDataFragmentListener dismissStumblingDataFragmentListener) {
        this.dismissStumblingDataFragmentListener = dismissStumblingDataFragmentListener;
    }

    public void updateDataWithBundle(Bundle bundle) {
        if (this.isVisible()) {
            setWifiStatus(bundle.getInt(StumblerService.KEY_WIFI_STATUS));
            setLatitude(bundle.getDouble(StumblerService.KEY_LATITUDE));
            setLongitude(bundle.getDouble(StumblerService.KEY_LONGITUDE));

            setLocationsScanned(bundle.getInt(StumblerService.KEY_LOCATIONS_SCANNED));
            setAccessPointsScanned(bundle.getInt(StumblerService.KEY_ACCESS_POINTS_SCANNED));
            setAccessPointsVisible(bundle.getInt(StumblerService.KEY_ACCESS_POINTS_VISIBLE));

            setCellTowersScanned(bundle.getInt(StumblerService.KEY_CELL_TOWERS_SCANNED));
            setCellTowersVisible(bundle.getInt(StumblerService.KEY_CELL_TOWERS_VISIBLE));
        }
    }

    private void setWifiStatus(int newWifiStatus) {
        wifiStatus = newWifiStatus;

        String wifiStatusString = null;

        switch (wifiStatus) {
            case WifiScanner.STATUS_IDLE:
                wifiStatusString = getString(R.string.wifi_status_idle);
                break;
            case WifiScanner.STATUS_ACTIVE:
                wifiStatusString = getString(R.string.wifi_status_active);
                break;
            case WifiScanner.STATUS_WIFI_DISABLED:
                wifiStatusString = getString(R.string.wifi_status_disabled);
                break;
            default:
                break;
        }

        wifiStatusTextView.setText(wifiStatusString);
    }

    private void setLatitude(double newLatitude) {
        latitude = newLatitude;
        latitudeTextView.setText(Double.toString(latitude));
    }

    private void setLongitude(double newLongitude) {
        longitude = newLongitude;
        longitudeTextView.setText(Double.toString(longitude));
    }

    private void setLocationsScanned(int newLocationsScanned) {
        locationsScanned = newLocationsScanned;
        locationsScannedTextView.setText(Integer.toString(locationsScanned));
    }

    private void setAccessPointsScanned(int newAccessPointsScanned) {
        accessPointsScanned = newAccessPointsScanned;
        accessPointsScannedTextView.setText(Integer.toString(accessPointsScanned));
    }

    private void setAccessPointsVisible(int newAccessPointsVisible) {
        accessPointsVisible = newAccessPointsVisible;
        accessPointsVisibleTextView.setText(Integer.toString(accessPointsVisible));
    }

    private void setCellTowersScanned(int newCellTowersScanned) {
        cellTowersScanned = newCellTowersScanned;
        cellTowersScannedTextView.setText(Integer.toString(cellTowersScanned));
    }

    private void setCellTowersVisible(int newCellTowersVisible) {
        cellTowersVisible = newCellTowersVisible;
        cellTowersVisibleTextView.setText(Integer.toString(cellTowersVisible));
    }
}
