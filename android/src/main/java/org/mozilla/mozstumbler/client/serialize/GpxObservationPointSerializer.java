/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.serialize;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.NamespaceList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;

import org.mozilla.mozstumbler.client.mapview.ObservationPoint;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

public class GpxObservationPointSerializer extends AsyncTask<Void, Void, Boolean> {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(GpxObservationPointSerializer.class);
    final WeakReference<IListener> mObservationPointSerializerListener;
    private final LinkedList<ObservationPoint> mPointList;
    private File mFile;

    GpxObservationPointSerializer(IListener listener, File file,
                                  LinkedList<ObservationPoint> pointList) {
        mObservationPointSerializerListener = new WeakReference<IListener>(listener);
        mFile = file;
        mPointList = pointList;
    }

    synchronized boolean writeOut(File outFile) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));

        Gpx gpx = new Gpx();

        Trk trk = new Trk();
        gpx.trk.add(trk);

        Trkseg seg;
        for (ObservationPoint observationPoint : mPointList) {
            if ((observationPoint.mWifiCount < 1 && observationPoint.mCellCount < 1)
                    || observationPoint.mTrackSegment < 0) {
                // This point is in-progress in terms of scanning, don't write it out
                continue;
            }

            while (trk.trkseg.size() <= observationPoint.mTrackSegment) {
                trk.trkseg.add(new Trkseg());
            }
            seg = trk.trkseg.get(observationPoint.mTrackSegment);

            Location loc = observationPoint.pointGPS;
            Trkpt pt = new Trkpt();
            pt.lat = loc.getLatitude();
            pt.lon = loc.getLongitude();
            pt.time = df.format(new Date(loc.getTime()));
            if (loc.hasAltitude()) {
                pt.geoidheight = loc.getAltitude();
            }
            if (loc.hasAccuracy()) {
                pt.pdop = loc.getAccuracy();
            }
            seg.trkpt.add(pt);
        }

        Serializer gpxSerializer = new Persister();
        try {
            gpxSerializer.write(gpx, outFile);
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            return false;
        }
        Log.d(LOG_TAG, "write done");
        return true;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        return writeOut(mFile);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        IListener listener = mObservationPointSerializerListener.get();
        if (listener == null) {
            return;
        }

        if (!result) {
            listener.onError();
            return;
        }

        listener.onWriteComplete(mFile);
    }

    public interface IListener {
        void onWriteComplete(File file);

        void onError();
    }

    @Root
    @NamespaceList({
            @Namespace(reference="http://www.topografix.com/GPX/1/1"),
            @Namespace(reference="http://www.w3.org/2001/XMLSchema-instance", prefix="xsi")
    })
    static class Gpx {
        @Attribute
        static final String version = "1.1";
        @Attribute
        static final String creator = "Mozilla Stumbler";
        @Attribute
        static final String schemaLocation = "http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd";

        @ElementList(inline=true)
        List<Trk> trk;

        Gpx() {
            trk = new LinkedList<Trk>();
        }
    }

    @Root
    static class Trk {
        @ElementList(inline=true)
        List<Trkseg> trkseg;

        Trk() {
            trkseg = new LinkedList<Trkseg>();
        }
    }

    @Root
    static class Trkseg {
        @ElementList(inline=true)
        List<Trkpt> trkpt;

        Trkseg() {
            trkpt = new LinkedList<Trkpt>();
        }
    }

    @Root
    static class Trkpt {
        @Attribute
        double lat;
        @Attribute
        double lon;

        @Element
        String time;
        @Element(required=false)
        Double geoidheight;
        @Element(required=false)
        Float pdop;
    }
}
