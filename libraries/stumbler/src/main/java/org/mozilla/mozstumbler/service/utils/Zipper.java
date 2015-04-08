/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.utils;

import android.util.Log;

import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Zipper {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(Zipper.class);

    /*
    Compress data using gzip, return null if compression fails.
     */
    public static byte[] zipData(byte[] data) {
        byte[] output = null;
        GZIPOutputStream gz_outputstream = null;
        ByteArrayOutputStream os = null;
        try {
            os = new ByteArrayOutputStream();
            gz_outputstream = new GZIPOutputStream(os);
            gz_outputstream.write(data);
            gz_outputstream.finish();
            output = os.toByteArray();
        } catch (IOException e) {
            return null;
        } finally {
            try {
                if (gz_outputstream != null) {
                    gz_outputstream.close();
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "", e);
                // there's nothing you could do to fix this
            }
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "", e);
                // there's nothing you could do to fix this
            }
        }
        return output;
    }


    /*
     Decompress gzip data or return NULL if the stream cannot be decompressed properly.
     */
    public static String unzipData(byte[] data) {
        StringBuilder result = new StringBuilder();

        ByteArrayInputStream bs = null;
        GZIPInputStream gzipInputStream = null;
        try {
            bs = new ByteArrayInputStream(data);
            gzipInputStream = new GZIPInputStream(bs);
            InputStreamReader reader = new InputStreamReader(gzipInputStream);
            BufferedReader in = new BufferedReader(reader);
            String read;
            while ((read = in.readLine()) != null) {
                result.append(read);
            }
        } catch (IOException e) {
            Log.w(LOG_TAG, "Can't decompress report data: " + e.toString());
            return null;
        } finally {
            if (gzipInputStream != null) {
                try {
                    gzipInputStream.close();
                } catch (IOException e) {
                    // eat it - nothing we can do
                }
            }

            if (bs!= null) {
                try {
                    bs.close();
                } catch (IOException e) {
                    // eat it - nothing we can do.
                }
            }
        }
        return result.toString();
    }

}
