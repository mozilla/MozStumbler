package org.mozilla.mozstumbler.service.core.http;

import java.util.Map;

public interface ISubmitService {
    IResponse submit(byte[] data, Map<String, String> headers, boolean precompressed);
}
