package org.mozilla.mozstumbler.client.fragments.map;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.overlay.GpsLocationProvider;
import com.mapbox.mapboxsdk.overlay.Overlay;
import com.mapbox.mapboxsdk.overlay.OverlayManager;
import com.mapbox.mapboxsdk.overlay.UserLocationOverlay;
import com.mapbox.mapboxsdk.views.MapView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.MainActivity;
import org.mozilla.mozstumbler.client.developers.DeveloperOverlayFragment;
import org.mozilla.mozstumbler.client.mapview.RainbowOverlay;
import org.mozilla.mozstumbler.client.mapview.StarOverlay;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class MapFragment extends Fragment implements StarOverlay.StarOverlaySelectedListener,
        RainbowOverlay.RainbowOverlaySelectedListener,
        DeveloperOverlayFragment.DeveloperActionListener,
        StumblerOffFragment.DismissStumblerOffListener {

    private MapView mapView;
    private UserLocationOverlay userLocationOverlay;

    private OverlayManager overlayManager;
    private ArrayList<Overlay> starOverlays;
    private ArrayList<Overlay> rainbowOverlays;

    private TodayOverlayFragment todayOverlayFragment;

    private Fragment currentNotificationFragment;
    private boolean allowShowingNotificationFragment;

    private Button stumblingPowerButton;
    private Button showStumblingDataButton;
    private Button zoomToSelfButton;

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

        overlayManager = mapView.getOverlayManager();

        starOverlays = new ArrayList<Overlay>();
        rainbowOverlays = new ArrayList<Overlay>();

        generateRandomStars();
        generateRandomRainbows();

        todayOverlayFragment = (TodayOverlayFragment)getFragmentManager().findFragmentById(R.id.today_overlay_fragment);
        todayOverlayFragment.setStarScore(125);
        todayOverlayFragment.setRainbowScore(150);
        todayOverlayFragment.setCoinScore(100);

        allowShowingNotificationFragment = true;

        stumblingPowerButton = (Button)rootView.findViewById(R.id.stumbling_power_button);
        stumblingPowerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((MainActivity)getActivity()).toggleStumblerServices();
            }
        });

        showStumblingDataButton = (Button)rootView.findViewById(R.id.show_stumbling_data_button);
        showStumblingDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((MainActivity)getActivity()).showStumblingDataFragment(R.id.map_window);
            }
        });

        zoomToSelfButton = (Button)rootView.findViewById(R.id.zoom_to_self_button);
        zoomToSelfButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                userLocationOverlay.goToMyPosition(true);
            }
        });

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
        for (int i = 0; i < 30; i++) {
            LatLng userLocationLatLng = userLocationOverlay.getMyLocation();

            StarOverlay starOverlay = new StarOverlay(getActivity(), mapView, userLocationLatLng);
            starOverlay.setStarOverlaySelectedListener(this);

            starOverlays.add(starOverlay);
        }

        overlayManager.addAll(starOverlays);
    }

    private void generateRandomRainbows() {
        for (int i = 0; i < 3; i++) {
            LatLng userLocationLatLng = userLocationOverlay.getMyLocation();

            RainbowOverlay rainbowOverlay = new RainbowOverlay(getActivity(), mapView, userLocationLatLng);
            rainbowOverlay.setRainbowOverlaySelectedListener(this);

            rainbowOverlays.add(rainbowOverlay);
        }

        overlayManager.addAll(rainbowOverlays);
    }

    @Override
    public void starOverlaySelected(StarOverlay selectedStarOverlay) {
        if (starOverlays.contains(selectedStarOverlay)) {
            overlayManager.remove(selectedStarOverlay);
            mapView.invalidate();
        }
    }

    @Override
    public void rainbowOverlaySelected(RainbowOverlay selectedRainbowOverlay) {
        if (rainbowOverlays.contains(selectedRainbowOverlay)) {
            overlayManager.remove(selectedRainbowOverlay);
            mapView.invalidate();
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
