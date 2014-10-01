package org.mozilla.mozstumbler.client.serialize;

import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.ekito.simpleKML.Serializer;
import com.ekito.simpleKML.model.Coordinate;
import com.ekito.simpleKML.model.Data;
import com.ekito.simpleKML.model.Document;
import com.ekito.simpleKML.model.ExtendedData;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.Icon;
import com.ekito.simpleKML.model.IconStyle;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Point;
import com.ekito.simpleKML.model.Kml;
import com.ekito.simpleKML.model.Style;
import com.ekito.simpleKML.model.StyleSelector;
import com.ekito.simpleKML.model.TimeStamp;

import org.joda.time.DateTime;
import org.mozilla.mozstumbler.client.mapview.ObservationPoint;

// add menu item for save/load observations
// activity will have save and load buttons
// save -> save with name obs-date-#obs.kml
// load -> show list to pick from

public class ObservationPointSerializer extends AsyncTask<Void, Void, Boolean> {
    public interface IListener {
        public void onWriteComplete(File file);
        public void onReadComplete(LinkedList<ObservationPoint> points, File file);
        public void onError();
    }

    private static final String LOG_TAG = ObservationPointSerializer.class.getSimpleName();
    private static final String GPS_NAME = "GPS";
    private static final String MLS_NAME = "MLS";
    private static final String ICON_RED_CIRCLE = "http://maps.google.com/mapfiles/kml/shapes/placemark_circle_highlight.png";
    private static final String STYLE_NAME_RED_CIRCLE = "redcircle";
    private static final String ICON_ARROW = "http://maps.google.com/mapfiles/kml/shapes/arrow.png";
    private static final String STYLE_NAME_ARROW = "arrow";

    private static final String COLOR_HAS_WIFI = "ffff0000"; //green in AABBGGRR
    private static final String COLOR_HAS_CELLS = "ff00ffff"; // yellow
    private static final String COLOR_HAS_BOTH = "ff00ff00"; // blue

    public static final String WIFIS = "Wi-Fis";
    public static final String CELLS = "Cells";

    public enum Mode { READ, WRITE }

    private final LinkedList<ObservationPoint> mPointList;
    private File mFile;
    private Mode mMode;

    final WeakReference<IListener> mObservationPointSerializerListener;

    ObservationPointSerializer(IListener listener, Mode mode, File file,
                               LinkedList<ObservationPoint> pointList)
    {
        mObservationPointSerializerListener = new WeakReference<IListener>(listener);
        mFile = file;
        mPointList = pointList;
        mMode = mode;
    }

    void addStyle(Document doc, String iconHref, String styleName, Float scale) {
        Icon icon = new Icon();
        icon.setHref(iconHref);
        IconStyle iconStyle = new IconStyle();
        iconStyle.setIcon(icon);
        if (scale != null) {
            iconStyle.setScale(scale);
        }
        Style style = new Style();
        style.setId(styleName);
        style.setIconStyle(iconStyle);

        List<StyleSelector> styleSelectorList = doc.getStyleSelector();
        if (doc.getStyleSelector() == null) {
            styleSelectorList = new LinkedList<StyleSelector>();
            doc.setStyleSelector(styleSelectorList);
        }

        styleSelectorList.add(style);
    }

    Folder createFolder(String name, List<Feature> features) {
        Folder folder = new Folder();
        folder.setName(name);
        folder.setFeatureList(features);
        return folder;
    }

    void setHeadingAndColor(Placemark placemark, double heading, String color) {
        placemark.setStyleUrl("#" + STYLE_NAME_ARROW);

        IconStyle iconStyle = new IconStyle();
        iconStyle.setHeading((float)heading);
        if (color != null) {
            iconStyle.setColor(color);
        }
        Style style = new Style();
        style.setIconStyle(iconStyle);

        List<StyleSelector> styleSelector = new LinkedList<StyleSelector>();
        styleSelector.add(style);
        placemark.setStyleSelector(styleSelector);
    }

    boolean writeOut(LinkedList<ObservationPoint> points, File outFile) {
        List<Feature> gpsFeatures = new LinkedList<Feature>();
        List<Feature> mlsFeatures = new LinkedList<Feature>();
        int idCounter = 0;
        for (ObservationPoint observationPoint : points) {
            if (observationPoint.mWifiCount < 1 && observationPoint.mCellCount < 1) {
                // This point is in-progress in terms of scanning, don't write it out
                continue;
            }
            if (observationPoint.mWasReadFromFile) {
                // This was previously read in, don't write out again
                continue;
            }
            idCounter++;

            Point point = new Point();
            point.setId("p" + idCounter); // used to match with MLS point
            point.setCoordinates(observationPoint.getGPSCoordinate());
            Placemark placemark = new Placemark();
            placemark.setName(GPS_NAME);
            DateTime dateTime = new DateTime(observationPoint.mTimestamp);
            TimeStamp time = new TimeStamp();
            time.setWhen(dateTime.toString() /* Date auto formats to RFC 3339 */);
            placemark.setTimePrimitive(time);

            String color = (observationPoint.mWifiCount > 0)? COLOR_HAS_WIFI : COLOR_HAS_CELLS;
            if (observationPoint.mWifiCount > 0 && observationPoint.mCellCount > 0 ) {
                color = COLOR_HAS_BOTH;
            }

            setHeadingAndColor(placemark, observationPoint.mHeading, color);

            List<Data> dataList = new LinkedList<Data>();
            Data data = new Data();
            data.setName(WIFIS);
            data.setValue(String.valueOf(observationPoint.mWifiCount));
            dataList.add(data);
            data = new Data();
            data.setName(CELLS);
            data.setValue(String.valueOf(observationPoint.mCellCount));
            dataList.add(data);

            ExtendedData extendedData = new ExtendedData();
            extendedData.setDataList(dataList);
            placemark.setExtendedData(extendedData);
            List<Geometry> geometryList = new LinkedList<Geometry>();
            geometryList.add(point);
            placemark.setGeometryList(geometryList);
            gpsFeatures.add(placemark);

            if (observationPoint.pointMLS != null) {
                placemark = new Placemark();
                placemark.setStyleUrl("#" + STYLE_NAME_RED_CIRCLE);
                placemark.setName(MLS_NAME);
                placemark.setTimePrimitive(time);
                point = new Point();
                point.setId("p" + idCounter); // used to match with gps point
                point.setCoordinates(observationPoint.getMLSCoordinate());
                geometryList = new LinkedList<Geometry>();
                geometryList.add(point);
                placemark.setGeometryList(geometryList);
                mlsFeatures.add(placemark);
            }
        }

        Document doc = new Document();
        addStyle(doc, ICON_RED_CIRCLE, STYLE_NAME_RED_CIRCLE, null);
        addStyle(doc, ICON_ARROW, STYLE_NAME_ARROW, 0.7f);

        List<Feature> docFeatures = new LinkedList<Feature>();
        docFeatures.add(createFolder(GPS_NAME, gpsFeatures));
        docFeatures.add(createFolder(MLS_NAME, mlsFeatures));

        doc.setFeatureList(docFeatures);
        Kml kml = new Kml();
        kml.setFeature(doc);

        Serializer kmlSerializer = new Serializer();
        try {
            kmlSerializer.write(kml, outFile);
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            return false;
        }
        Log.d(LOG_TAG, "write done");
        return true;
    }

    private boolean isGpsPointRead(String name) {
        return name.equals(GPS_NAME);
    }

    boolean readIn(LinkedList<ObservationPoint> points, File file) {
        Serializer kmlSerializer = new Serializer();
        Kml kml;
        try {
            kml = kmlSerializer.read(file);
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            return false;
        }

        Feature feature = kml.getFeature();
        if (!(feature instanceof Document)) {
            Log.e(LOG_TAG, "expected document");
            return false;
        }
        Document doc = (Document) feature;
        List<Feature> featureList = doc.getFeatureList();
        if (featureList == null) {
            Log.e(LOG_TAG, "expected doc features");
            return false;
        }

        HashMap<String, ObservationPoint> gpsList = new HashMap<String, ObservationPoint>();
        HashMap<String, Coordinate> mlsList = new HashMap<String, Coordinate>();

        for (Feature topFeatures : featureList) {
            if (!(topFeatures instanceof Folder)) {
                continue;
            }

            List<Feature> subFeatures = ((Folder) topFeatures).getFeatureList();
            if (subFeatures == null) {
               continue;
            }

            for (Feature f : subFeatures) {
                if (!(f instanceof Placemark)) {
                    continue;
                }
                Placemark placemark = (Placemark) f;
                List<Geometry> geometryList = placemark.getGeometryList();
                if (geometryList == null || geometryList.size() != 1) {
                    continue;
                }
                Geometry geometry = geometryList.get(0);
                if (!(geometry instanceof Point)) {
                    continue;
                }
                Point p = (Point) geometry;
                Coordinate coordinate = p.getCoordinates();
                boolean isGps = isGpsPointRead(placemark.getName());

                int wifis = 0;
                int cells = 0;
                ExtendedData extendedData = placemark.getExtendedData();
                if (extendedData != null) {
                    List<Data> data = extendedData.getDataList();
                    for (Data d : data) {
                        if (d.getName().equals(WIFIS)) {
                            wifis = Integer.parseInt(d.getValue());
                        } else if (d.getName().equals(CELLS)) {
                            cells = Integer.parseInt(d.getValue());
                        }
                    }
                }

                ObservationPoint observationPoint = new ObservationPoint(coordinate, wifis, cells);
                observationPoint.mWasReadFromFile = true;

                if (isGps) {
                    points.add(observationPoint);
                    gpsList.put(placemark.getId(), observationPoint);
                } else {
                    mlsList.put(placemark.getId(), coordinate);
                }
            }
        }

        Iterator it = mlsList.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry)it.next();
            ObservationPoint obs = gpsList.get(pairs.getKey());
            if (obs != null) {
                obs.setMLSCoordinate((Coordinate)pairs.getValue());
            }
        }

        return true;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        if (mMode == Mode.WRITE) {
            return writeOut(mPointList, mFile);
        } else {
            return readIn(mPointList, mFile);
        }
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

        if (mMode == Mode.WRITE) {
            listener.onWriteComplete(mFile);
        } else {
            listener.onReadComplete(mPointList, mFile);
        }
    }
}
