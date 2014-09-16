package org.mozilla.mozstumbler.client;

import java.io.Reader;
import java.net.URL;

/**
 * Created by victorng on 2014-09-15.
 */
public interface IHttpUtil {

    public String getUrlAsString(String url);
    public Reader getUrlAsReader(String url);
    public Reader getUrlAsReader(URL url) ;


}
