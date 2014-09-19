/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.http;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;

/**
 * Created by victorng on 2014-09-19.
 */
public class MockHttpUtil implements IHttpUtil {
    @Override
    public String getUrlAsString(URL url) throws IOException {
        return "abc";
    }

    @Override
    public File getUrlAsFile(URL url, File file) throws IOException {
        Writer writer = new FileWriter(file);
        writer.write(getUrlAsString(url));
        return file;
    }
}
