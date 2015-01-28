package org.mozilla.osmdroid.contributor;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.mozilla.osmdroid.contributor.util.RecordedGeoPoint;
import org.mozilla.osmdroid.contributor.util.RecordedRouteGPXFormatter;
import org.mozilla.osmdroid.contributor.util.Util;
import org.mozilla.osmdroid.http.HttpClientFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;

public class GpxToPHPUploader {

    protected static final String UPLOADSCRIPT_URL = "http://www.PLACEYOURDOMAINHERE.com/anyfolder/gpxuploader/upload.php";
    private static final String LOG_TAG = LoggerUtil.makeLogTag(GpxToPHPUploader.class);

    /**
     * This is a utility class with only static members.
     */
    private GpxToPHPUploader() {
    }

    public static void uploadAsync(final ArrayList<RecordedGeoPoint> recordedGeoPoints) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!Util.isSufficienDataForUpload(recordedGeoPoints))
                        return;

                    final InputStream gpxInputStream = new ByteArrayInputStream(
                            RecordedRouteGPXFormatter.create(recordedGeoPoints).getBytes());
                    final HttpClient httpClient = HttpClientFactory.createHttpClient();

                    final HttpPost request = new HttpPost(UPLOADSCRIPT_URL);

                    // create the multipart request and add the parts to it
                    final MultipartEntity requestEntity = new MultipartEntity();
                    requestEntity.addPart("gpxfile", new InputStreamBody(gpxInputStream, ""
                            + System.currentTimeMillis() + ".gpx"));

                    httpClient.getParams().setBooleanParameter("http.protocol.expect-continue",
                            false);

                    request.setEntity(requestEntity);

                    final HttpResponse response = httpClient.execute(request);
                    final int status = response.getStatusLine().getStatusCode();

                    if (status != HttpStatus.SC_OK) {
                        ClientLog.w(LOG_TAG, "GPXUploader status != HttpStatus.SC_OK");
                    } else {
                        final Reader r = new InputStreamReader(new BufferedInputStream(response
                                .getEntity().getContent()));
                        // see above
                        final char[] buf = new char[8 * 1024];
                        int read;
                        final StringBuilder sb = new StringBuilder();
                        while ((read = r.read(buf)) != -1)
                            sb.append(buf, 0, read);

                        ClientLog.d(LOG_TAG, "GPXUploader Response: " + sb.toString());
                    }
                } catch (final Exception e) {
                    ClientLog.e(LOG_TAG, "OSMUpload Error", e);
                }
            }
        }).start();
    }
}
