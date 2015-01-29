package org.mozilla.osmdroid.http;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * Factory class for creating an instance of {@link HttpClient}.
 * The default implementation returns an instance of {@link DefaultHttpClient}.
 * In order to use a different implementation call {@link #setFactoryInstance(IHttpClientFactory)}
 * early in your code, for example in <code>onCreate</code> in your main activity.
 * For example to use
 * <a href="http://square.github.io/okhttp/">OkHttp/</a>
 * use the following code
 * <code>
 * HttpClientFactory.setFactoryInstance(new IHttpClientFactory() {
 * public HttpClient createHttpClient() {
 * return new OkApacheClient();
 * }
 * });
 * </code>
 */
public class HttpClientFactory {

    private static final int CONNECTION_TIMEOUT_MS = 2000;
    private static final int SOCKET_TIMEOUT_MS = 2000;

    private static IHttpClientFactory mFactoryInstance = new IHttpClientFactory() {
        @Override
        public HttpClient createHttpClient() {
            HttpParams my_httpParams = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(my_httpParams, CONNECTION_TIMEOUT_MS);
            HttpConnectionParams.setSoTimeout(my_httpParams, SOCKET_TIMEOUT_MS);
            return new DefaultHttpClient(my_httpParams);
        }
    };

    public static void setFactoryInstance(final IHttpClientFactory aHttpClientFactory) {
        mFactoryInstance = aHttpClientFactory;
    }

    public static HttpClient createHttpClient() {
        return mFactoryInstance.createHttpClient();
    }
}
