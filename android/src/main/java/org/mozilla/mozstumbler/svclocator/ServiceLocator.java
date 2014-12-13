/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.svclocator;

import java.util.HashMap;

public class ServiceLocator {

    private ServiceConfig svcMap = new ServiceConfig();
    private ServiceLocator parentLocator = null;

    private static ServiceLocator instance = null;


    public static synchronized ServiceLocator newRoot(ServiceConfig newMap) {
        ServiceLocator svcLocator = new ServiceLocator(null);
        svcLocator.svcMap = newMap;
        return svcLocator;
    }

    public static synchronized void setRootInstance(ServiceLocator root) {
        instance = root;
    }

    public static synchronized ServiceLocator getInstance() {
        return instance;
    }

    public ServiceLocator(ServiceLocator parent) {
        parentLocator = parent;
    }

    public Object getService(Class<?> svcInterface) {
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
