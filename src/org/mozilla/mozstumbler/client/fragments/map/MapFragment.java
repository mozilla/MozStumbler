package org.mozilla.mozstumbler.client.fragments.map;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.overlay.GpsLocationProvider;
import com.mapbox.mapboxsdk.overlay.Marker;
import com.mapbox.mapboxsdk.overlay.UserLocationOverlay;
import com.mapbox.mapboxsdk.views.MapView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.developers.DeveloperOverlayFragment;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class MapFragment extends Fragment implements DeveloperOverlayFragment.DeveloperActionListener,
        StumblerOffFragment.DismissStumblerOffListener {

    private MapView mapView;
    private UserLocationOverlay userLocationOverlay;

    private TodayOverlayFragment todayOverlayFragment;
    private DeveloperOverlayFragment developerOverlayFragment;

    private Fragment currentNotificationFragment;
    private boolean allowShowingNotificationFragment;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_map, container, false);

        mapView = (MapView)rootView.findViewById(R.id.map_view);

        userLocationOverlay = new UserLocationOverlay(new GpsLocationProvider(getActivity()), mapView);
        userLocationOverlay.enableMyLocation();
        userLocationOverlay.setDrawAccuracyEnabled(true);
        userLocationOverlay.setTrackingMode(UserLocationOverlay.TrackingMode.FOLLOW);
        userLocationOverlay.setRequiredZoom(16.5f);

        Bitmap stumblerMarkerBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.stumbler_marker_map);
        userLocationOverlay.setPersonBitmap(stumblerMarkerBitmap);

        mapView.setCenter(userLocationOverlay.getMyLocation());
        mapView.getOverlays().add(userLocationOverlay);

        generateRandomStars();
        generateRandomRainbows();

        todayOverlayFragment = (TodayOverlayFragment)getFragmentManager().findFragmentById(R.id.today_overlay_fragment);
        todayOverlayFragment.setStarScore(125);
        todayOverlayFragment.setRainbowScore(150);
        todayOverlayFragment.setCoinScore(100);

        developerOverlayFragment = (DeveloperOverlayFragment)getFragmentManager().findFragmentById(R.id.developer_overlay_fragment);
        developerOverlayFragment.setDeveloperActionListener(this);

        allowShowingNotificationFragment = true;

        return rootView;
    }

    private void showCoinRewardFragment() {
        final CoinRewardFragment coinRewardFragment = new CoinRewardFragment();

        getFragmentManager().beginTransaction()
                .add(R.id.map_window, coinRewardFragment)
                .commit();

        currentNotificationFragment = coinRewardFragment;

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                getFragmentManager().beginTransaction()
                        .remove(coinRewardFragment)
                        .commit();

                allowShowingNotificationFragment = true;
                currentNotificationFragment = null;
            }
        }, 3000);
    }

    private void showStumblerOffFragment() {
        final StumblerOffFragment stumblerOffFragment = new StumblerOffFragment();
        stumblerOffFragment.setDismissStumblerOffListener(this);

        getFragmentManager().beginTransaction()
                .add(R.id.map_window, stumblerOffFragment)
                .commit();

        currentNotificationFragment = stumblerOffFragment;
    }

    private void generateRandomStars() {
        for (int i = 0; i < 10; i++) {
            LatLng userLocationLatLng = userLocationOverlay.getMyLocation();

            Random rand = new Random();
            int randomLatCoefficient = rand.nextInt((10 - -10) + 1) + -10;
            int randomLonCoefficient = rand.nextInt((10 - -10) + 1) + -10;

            LatLng randomLatLng =
                    new LatLng(
                            userLocationLatLng.getLatitude() + randomLatCoefficient * 0.0005,
                            userLocationLatLng.getLongitude() + randomLonCoefficient * 0.0005);

            Marker starMarker = new Marker(null, null, randomLatLng);
            starMarker.setMarker(getResources().getDrawable(R.drawable.star_marker_map));

            mapView.addMarker(starMarker);
        }
    }

    private void generateRandomRainbows() {
        for (int i = 0; i < 3; i++) {
            LatLng userLocationLatLng = userLocationOverlay.getMyLocation();

            Random rand = new Random();
            int randomLatCoefficient = rand.nextInt((10 - -10) + 1) + -10;
            int randomLonCoefficient = rand.nextInt((10 - -10) + 1) + -10;

            LatLng randomLatLng =
                    new LatLng(
                            userLocationLatLng.getLatitude() + randomLatCoefficient * 0.0005,
                            userLocationLatLng.getLongitude() + randomLonCoefficient * 0.0005);

            Marker rainbowMarker = new Marker(null, null, randomLatLng);
            rainbowMarker.setMarker(getResources().getDrawable(R.drawable.rainbow_marker_map));

            mapView.addMarker(rainbowMarker);
        }
    }

    @Override
    public void simulateCoinReward() {
        if (allowShowingNotificationFragment) {
            allowShowingNotificationFragment = false;
            showCoinRewardFragment();
        }
    }

    @Override
    public void simulateStumblerOff() {
        if (allowShowingNotificationFragment) {
            allowShowingNotificationFragment = false;
            showStumblerOffFragment();
        }
    }

    @Override
    public void dismissStumblerOffFragment() {
        if (currentNotificationFragment != null && currentNotificationFragment instanceof StumblerOffFragment) {
            getFragmentManager().beginTransaction()
                    .remove(currentNotificationFragment)
                    .commit();

            allowShowingNotificationFragment = true;
        }
    }
}
