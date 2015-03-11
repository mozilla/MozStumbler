package org.mozilla.mozstumbler.client.util;

import android.content.Context;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.http.IResponse;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.mozilla.osmdroid.views.MapView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

import org.mozilla.mozstumbler.client.util.MapTileUtil.TileId;

public class CellDataRequester {

    public interface Callback {
        void gotData();
    }
    private final Callback mCallback;

    private static final long DELAY_MS = 50;
    private static final int MAX_REQUESTS = 200;

    private static final String LOG_TAG = LoggerUtil.makeLogTag(CellDataRequester.class);
    private static final String BASE_URL ="http://ec2-52-1-93-147.compute-1.amazonaws.com/cells?";
    private final String mStorageDir;
    private final int mZoomLevel = 14;
    private static RequestTask mAsyncTask;
    private final Context mContext;

    private static class PendingRequests {
        private static List<TileJson> mRequests = new ArrayList<TileJson>();
        private static final ReentrantLock _lock = new ReentrantLock();

        public PendingRequests() {}

        public List<TileJson> lock() {
            _lock.lock();
            return mRequests;
        }

        public void unlock() {
            _lock.unlock();
        }
    }
    private static PendingRequests mPendingRequests = new PendingRequests();

    static class TileJson extends TileId implements Comparable<TileJson> {
        private static final HashMap<String, TileJson> gridPool = new HashMap<String, TileJson>();
        private JSONArray json;

        @Override
        public int compareTo(TileJson another) {
            if (this == another) {
                return 0;
            }
            return -1;
        }

        static synchronized TileJson create(int x, int y) {
            TileJson grid = new TileJson(x, y);
            if (gridPool.containsKey(grid.toString())) {
                return gridPool.get(grid.toString());
            }
            gridPool.put(grid.toString(), grid);
            return grid;
        }

        static synchronized TileJson create(TileId orig) {
            return create(orig.x, orig.y);
        }

        static synchronized ArrayList<TileJson> create(ArrayList<TileId> list) {
            ArrayList<TileJson> result = new ArrayList<TileJson>(list.size());
            for (TileId t : list) {
                result.add(TileJson.create(t));
            }
            return result;
        }

        private TileJson(int x, int y) {
            super(x, y);
        }

        synchronized public String toString() {
            return x + "/" + y;
        }

        synchronized public JSONArray getJson() {
            return json;
        }

        synchronized public void setJson(JSONArray json) {
            this.json = json;
        }

        synchronized public String getJsonAsString() {
            return (json != null)? json.toString() : "";
        }
    }

    public Map<String, JSONArray> getData(MapView mapView) {
        Location min = new Location(AppGlobals.LOCATION_ORIGIN_INTERNAL);
        Location max = new Location(AppGlobals.LOCATION_ORIGIN_INTERNAL);

        if (!MapTileUtil.boundingBox(mapView, min, max)) {
            return null;
        }

        TileJson low = TileJson.create(MapTileUtil.degreesToGridNumber(min, mZoomLevel));
        TileJson high = TileJson.create(MapTileUtil.degreesToGridNumber(max, mZoomLevel));
        ArrayList<TileJson> list =
                TileJson.create(MapTileUtil.generateGrids(low, high));
        Map<String,  JSONArray> haveDataFor = new HashMap<String, JSONArray>();
        ArrayList<TileJson> missingDataFor = new ArrayList<TileJson>();
        for (TileJson t : list) {
            if (t.getJson() != null) {
                haveDataFor.put(t.toString(), t.getJson());
            } else {
                missingDataFor.add(t);
            }
        }

        addToPendingRequests(missingDataFor, true);
        if (mAsyncTask == null || mAsyncTask.isCancelled()) {
            callAsynchronousTask(null);
        }

        return haveDataFor;
    }

    public void get(MapView mapView) {
        callAsynchronousTask(mapView);
    }

    static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }

    public CellDataRequester(Context context, Callback callback) {
        mStorageDir = context.getFilesDir().getPath() + "/cellgrid";
        mCallback = callback;
        mContext = context;

        //TODO manage the disk storage!!
        deleteRecursive(new File(mStorageDir));
    }

    // The mapview can be used to specify the bounds to get results for
    // or left null, the existing pending results will be queried.
    // By delaying the mapview bounds query, we can increase the likelihood that
    // the viewport has stabilized (that is, isn't in the mist of a scroll).
    public void callAsynchronousTask(final MapView mapView) {
        final Handler handler = new Handler();
        TimerTask doAsynchronousTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        if (mapView != null) {
                            Location min = new Location(AppGlobals.LOCATION_ORIGIN_INTERNAL);
                            Location max = new Location(AppGlobals.LOCATION_ORIGIN_INTERNAL);
                            if (!MapTileUtil.boundingBox(mapView, min, max)) {
                                return;
                            }
                            TileJson low = TileJson.create(MapTileUtil.degreesToGridNumber(min, mZoomLevel));
                            TileJson high = TileJson.create(MapTileUtil.degreesToGridNumber(max, mZoomLevel));
                            ArrayList<TileJson> list = TileJson.create(MapTileUtil.generateGrids(low, high));
                            addToPendingRequests(list, true);
                        }

                        mAsyncTask = new RequestTask();
                        mAsyncTask.execute();
                    }
                });
            }
        };

        if (mAsyncTask != null) {
            mAsyncTask.cancel(true);
        }

        handler.removeCallbacks(doAsynchronousTask);
        handler.postDelayed(doAsynchronousTask, DELAY_MS);
    }

    private void addToPendingRequests(ArrayList<TileJson> list, boolean clear) {
        List<TileJson> requests = mPendingRequests.lock();
        if (clear) {
            requests.clear();
        }
        for (int i = 0; i < list.size() && i < MAX_REQUESTS; i++) {
            requests.add(list.get(i));
        }
        mPendingRequests.unlock();
    }

    class RequestTask extends AsyncTask<Void, Void, Void> {
        void tryReadFromDisk() {
            List<TileJson> requests = mPendingRequests.lock();
            TileJson grid;
            Iterator<TileJson> iterator = requests.iterator();
            while(iterator.hasNext()) {
                grid = iterator.next();
                if (grid.getJson() == null) {
                    JSONArray existing = readGridFromDisk(grid.toString());
                    if (existing != null && existing.length() > 0) {
                        grid.setJson(existing);
                        iterator.remove();
                    }
                } else {
                    iterator.remove();
                }
            }
            mPendingRequests.unlock();
        }

        void readOneFromNetwork() {
            List<TileJson> requests = mPendingRequests.lock();
            TileJson grid = requests.remove(requests.size() - 1);
            mPendingRequests.unlock();

            // Grid id: longitude is floored to the west, latitude is floored to the north
            // turn the grid to coords and ask for that box
            Location loc1 = MapTileUtil.gridNumberToDegrees(grid, mZoomLevel);
            // grid x increased with longitude, grid y increases as lat decreases
            Location loc2 = MapTileUtil.gridNumberToDegrees(
                    TileJson.create(grid.x + 1, grid.y + 1), mZoomLevel);

            String args = String.format("lat1=%f&lon1=%f&lat2=%f&lon2=%f",
                    loc1.getLatitude(), loc1.getLongitude(),
                    loc2.getLatitude(), loc2.getLongitude());

            final IHttpUtil httpDelegate = (IHttpUtil) ServiceLocator.getInstance().getService(IHttpUtil.class);
            IResponse response = httpDelegate.get(BASE_URL + args, null);
            if (response == null || response.isErrorCode400BadRequest()) {
                return;
            }

            JSONArray json;
            try {
                json = new JSONArray(response.body());
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Error deserializing JSON. " + e.toString(), e);
                return;
            }

            grid.setJson(json);
            saveGridToDisk(grid);
        }

        public Void doInBackground(Void... params) {
            while (!isCancelled()) {
                tryReadFromDisk();

                List<TileJson> requests = mPendingRequests.lock();
                boolean hasMore = requests.size() > 0;
                mPendingRequests.unlock();

                if (!hasMore) {
                    break;
                }

                readOneFromNetwork();

                Handler main = new Handler(mContext.getMainLooper());
                main.post(new Runnable() {
                    public void run() {
                        mCallback.gotData();
                    }
                });

                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException ex) {}
            }
            return null;
        }

        private void saveGridToDisk(TileJson grid) {
            String id = grid.toString();
            File file = new File(mStorageDir + "/" + id);
            File dir = new File(file.getParent());
            if (!dir.exists()) {
                dir.mkdirs();
            }
            try {
                final FileOutputStream fos = new FileOutputStream(file);
                final byte[] bytes = grid.getJsonAsString().getBytes();
                try {
                    fos.write(bytes);
                } finally {
                    fos.close();
                }
            } catch (IOException ex) {}
        }

        private byte[] readFile(File file) throws IOException {
            if (!file.exists()) {
                return null;
            }

            final RandomAccessFile f = new RandomAccessFile(file, "r");
            try {
                final byte[] data = new byte[(int) f.length()];
                f.readFully(data);
                return data;
            } finally {
                f.close();
            }
        }

        private JSONArray readGridFromDisk(String id) {
            File file = new File(mStorageDir + "/" + id);
            try {
                byte[] bytes = readFile(file);
                if (bytes != null) {
                    JSONArray json = new JSONArray(new String(bytes));
                    return json;
                }
            } catch (Exception ex) {}

            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            super.onPostExecute(v);
            List<TileJson> requests = mPendingRequests.lock();
            boolean hasMore = requests.size() > 0;
            mPendingRequests.unlock();

            if (hasMore) {
                callAsynchronousTask(null);
            }
        }
    }
}
