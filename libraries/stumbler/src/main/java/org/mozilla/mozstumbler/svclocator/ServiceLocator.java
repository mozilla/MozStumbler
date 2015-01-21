/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.svclocator;

import java.lang.reflect.Proxy;

public class ServiceLocator {

    private ServiceConfig svcMap = new ServiceConfig();
    private ServiceLocator parentLocator = null;

    private static ServiceLocator instance = null;

    public ServiceLocator(ServiceLocator parent) {
        parentLocator = parent;
    }

    public static synchronized void newRoot(ServiceConfig newMap) {
        if (instance == null) {
            instance = getInstance();
        }
        instance.svcMap = newMap;
    }

    public static synchronized ServiceLocator getInstance() {
        if (instance == null) {
            instance = new ServiceLocator(null);
        }
        return instance;
    }


    /*
     Call this method with an interface class to get a lazily bound proxy to the service.
     */
    public Object getService(Class<?> svcDefinition) {
        return Proxy.newProxyInstance(svcDefinition.getClassLoader(),
                new Class<?>[]{svcDefinition},
                new DynamicProxy(svcDefinition));
    }

    /*
     You almost certainly don't want to be calling this.  Use getService instead to get a lazily
     bound proxy instance.
     */
    public Object getDirectService(Class<?> svcInterface) {
        Object result = null;

        if (svcMap.containsKey(svcInterface)) {
            return svcMap.get(svcInterface);
        }

        if (parentLocator != null) {
            result = parentLocator.getService(svcInterface);
        }
        return result;
    }

    public void putService(Class<?> svcInterface, Object obj) {
        svcMap.put(svcInterface, obj);
    }

}
