/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.svclocator;

import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.svclocator.services.ISystemClock;

import java.lang.reflect.Constructor;
import java.util.HashMap;

public class ServiceConfig extends HashMap<Class<?>, Object> {

    private static final long serialVersionUID = 1111111111L;

    public static ServiceConfig defaultServiceConfig() {

        ServiceConfig result = new ServiceConfig();

        // All classes here must have an argument free constructor.
        result.put(ISystemClock.class, load("org.mozilla.mozstumbler.svclocator.services.SystemClock"));
        result.put(IHttpUtil.class, load("org.mozilla.mozstumbler.service.core.http.HttpUtil"));

        return result;
    }

    public static Object load(String className) {

        Class<?> c = null;
        try {
            c = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error loading ["+className+"] class");
        }
        Constructor[] constructors = c.getConstructors();

        Constructor<?> myConstructor= null;
        for (Constructor<?> construct: constructors) {
            if (construct.getParameterTypes().length == 0) {
                myConstructor = construct;
                break;
            }
        }

        if (myConstructor == null) {
            throw new RuntimeException("No constructor found");
        }

        try {
            return myConstructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e.toString());
        }
    }
}
