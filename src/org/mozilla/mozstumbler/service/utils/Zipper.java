package org.mozilla.mozstumbler.service.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Zipper {
    public static byte[] zipData(byte[] data) throws IOException {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        GZIPOutputStream gstream = null;
        byte[] output;
        try {
            gstream = new GZIPOutputStream(os);
            gstream.write(data);
            gstream.finish();
            output = os.toByteArray();
        } finally {
            os.close();
            if (gstream != null) {
                gstream.close();
            }
        }
        return output;
    }

    public static String unzipData(byte[] data) throws IOException {
        final ByteArrayInputStream bs = new ByteArrayInputStream(data);
        GZIPInputStream gstream = null;
        String result = "";

        try {
            gstream = new GZIPInputStream(bs);

            InputStreamReader reader = new InputStreamReader(gstream);
            BufferedReader in = new BufferedReader(reader);

            String read;
            while ((read = in.readLine()) != null) {
                result += read;
            }

        } finally {
            bs.close();
            if (gstream != null) {
                gstream.close();
            }
        }
        return result;
    }
}
