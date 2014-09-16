package org.mozilla.mozstumbler.client.tests;

import org.mozilla.mozstumbler.client.IHttpUtil;

import java.io.Reader;
import java.io.StringReader;
import java.net.URL;

/**
 * Created by victorng on 2014-09-16.
 */
public class MockHttpUtil implements IHttpUtil {

    private final String mockContent;

    public MockHttpUtil(String content) {
        mockContent = content;
    }

    @Override
    public String getUrlAsString(String url) {
        return mockContent;
    }

    @Override
    public Reader getUrlAsReader(String url) {
        return new StringReader(mockContent);
    }

    @Override
    public Reader getUrlAsReader(URL url) {
        return new StringReader(mockContent);
    }
}
