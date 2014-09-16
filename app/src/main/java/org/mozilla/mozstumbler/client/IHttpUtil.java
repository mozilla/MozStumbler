package org.mozilla.mozstumbler.client;

import java.io.File;

/**
 * Created by victorng on 2014-09-15.
 */
public interface IHttpUtil {

    public String getUrlAsString(String url);
    public File getUrlAsFile(String url) ;

}
