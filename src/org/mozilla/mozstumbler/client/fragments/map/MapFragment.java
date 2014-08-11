package org.mozilla.mozstumbler.client.fragments.map;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

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
import org.mozilla.mozstumbler.client.models.User;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class MapFragment extends Fragment implements StarOverlay.StarOverlaySelectedListener,
        RainbowOverlay.RainbowOverlaySelectedListener,
        DeveloperOverlayFragment.DeveloperActionListener,
        User.CoinRewardedListener,
        StumblerOffFragment.DismissStumblerOffListener {

    private final boolean mustBeNearbyToCollect = true;
    private final double nearbyThresholdRadius = 0.0015;

    private User user;

    private MapView mapView;
    private UserLocationOverlay userLocationOverlay;

    private OverlayManager overlayManager;
    private ArrayList<Overlay> starOverlays;
    private ArrayList<Overlay> rainbowOverlays;

    private TodayOverlayFragment todayOverlayFragment;

    private Fragment currentNotificationFragment;
    private boolean allowShowingNotificationFragment;

    private Button zoomToSelfButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_map, container, false);

        mapView = (MapView)rootView.findViewById(R.id.map_view);
        user = ((MainActivity)getActivity()).getUser();

        setupUserLocationOverlay();
        setupStarsAndRainbows();
        setupTodayOverlayFragment();

        allowShowingNotificationFragment = true;

        setupZoomToSelfButton(rootView);

        return rootView;
    }

    private void setupUserLocationOverlay() {
        userLocationOverlay = new UserLocationOverlay(new GpsLocationProvider(getActivity()), mapView);
        userLocationOverlay.enableMyLocation();
        userLocationOverlay.setDrawAccuracyEnabled(true);
        userLocationOverlay.setTrackingMode(UserLocationOverlay.TrackingMode.FOLLOW);
        userLocationOverlay.setRequiredZoom(17.0f);

        Bitmap stumblerMarkerBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.stumbler_marker_map);
        userLocationOverlay.setPersonBitmap(stumblerMarkerBitmap);

        mapView.setCenter(userLocationOverlay.getMyLocation());
        mapView.getOverlays().add(userLocationOverlay);
    }

    private void setupStarsAndRainbows() {
        overlayManager = mapView.getOverlayManager();
        starOverlays = new ArrayList<Overlay>();
        rainbowOverlays = new ArrayList<Overlay>();

        generateRandomStars();
        generateRandomRainbows();
    }

    private void setupTodayOverlayFragment() {
        todayOverlayFragment = (TodayOverlayFragment)getFragmentManager().findFragmentById(R.id.today_overlay_fragment);
        todayOverlayFragment.setStarScore(user.getStarScoreToday());
        todayOverlayFragment.setRainbowScore(user.getRainbowScoreToday());
        todayOverlayFragment.setCoinScore(user.getCoinScoreToday());
        user.setUserScoreUpdatedListener(todayOverlayFragment);
    }

    private void setupZoomToSelfButton(View rootView) {
        zoomToSelfButton = (Button)rootView.findViewById(R.id.zoom_to_self_button);
        zoomToSelfButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                userLocationOverlay.goToMyPosition(true);
            }
        });
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
        for (int i = 0; i < 50; i++) {
            LatLng userLocationLatLng = userLocationOverlay.getMyLocation();

            StarOverlay starOverlay = new StarOverlay(getActivity(), mapView, userLocationLatLng);
            starOverlay.setStarOverlaySelectedListener(this);

            starOverlays.add(starOverlay);
        }

        overlayManager.addAll(starOverlays);
    }

    private void generateRandomRainbows() {
        for (int i = 0; i < 50; i++) {
            LatLng userLocationLatLng = userLocationOverlay.getMyLocation();

            RainbowOverlay rainbowOverlay = new RainbowOverlay(getActivity(), mapView, userLocationLatLng);
            rainbowOverlay.setRainbowOverlaySelectedListener(this);

            rainbowOverlays.add(rainbowOverlay);
        }

        overlayManager.addAll(rainbowOverlays);
    }

    public void updateScoreForToday() {
        if (todayOverlayFragment != null) {
            todayOverlayFragment.userScoreUpdated(user);
        }
    }

    public void resetScoreForToday() {
        user.resetScoreForToday();
        todayOverlayFragment.userScoreUpdated(user);
    }

    @Override
    public void starOverlaySelected(StarOverlay selectedStarOverlay) {
        if (starOverlays.contains(selectedStarOverlay)) {

            boolean canIncrement = false;

            if (mustBeNearbyToCollect) {
                LatLng selectedOverlayLatLng = selectedStarOverlay.getLatLng();
                LatLng userLocationLatLng = userLocationOverlay.getMyLocation();

                double xDiff = Math.abs(selectedOverlayLatLng.getLatitude() - userLocationLatLng.getLatitude());
                double yDiff = Math.abs(selectedOverlayLatLng.getLongitude() - userLocationLatLng.getLongitude());

                if (xDiff <= nearbyThresholdRadius && yDiff <= nearbyThresholdRadius) {
                    canIncrement = true;
                } else {
                    Toast.makeText(getActivity(), getString(R.string.too_far_away_to_collect), Toast.LENGTH_SHORT).show();
                }

            } else {
                canIncrement = true;
            }

            if (canIncrement) {
                overlayManager.remove(selectedStarOverlay);
                mapView.invalidate();

                user.incrementStarScore();
                //Toast.makeText(getActivity(), getString(R.string.star_collected), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void rainbowOverlaySelected(RainbowOverlay selectedRainbowOverlay) {
        if (rainbowOverlays.contains(selectedRainbowOverlay)) {

            boolean canIncrement = false;

            if (mustBeNearbyToCollect) {
                LatLng selectedOverlayLatLng = selectedRainbowOverlay.getLatLng();
                LatLng userLocationLatLng = userLocationOverlay.getMyLocation();

                double xDiff = Math.abs(selectedOverlayLatLng.getLatitude() - userLocationLatLng.getLatitude());
                double yDiff = Math.abs(selectedOverlayLatLng.getLongitude() - userLocationLatLng.getLongitude());

                if (xDiff <= nearbyThresholdRadius && yDiff <= nearbyThresholdRadius) {
                    canIncrement = true;
                } else {
                    Toast.makeText(getActivity(), getString(R.string.too_far_away_to_collect), Toast.LENGTH_SHORT).show();
                }

            } else {
                canIncrement = true;
            }

            if (canIncrement) {
                overlayManager.remove(selectedRainbowOverlay);
                mapView.invalidate();

                user.incrementRainbowScore();
                //Toast.makeText(getActivity(), getString(R.string.rainbow_collected), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void coinRewarded() {
        if (allowShowingNotificationFragment) {
            allowShowingNotificationFragment = false;
            showCoinRewardFragment();
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
