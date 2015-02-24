/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.svclocator;

import android.util.Log;

import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class DynamicProxy implements InvocationHandler {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(DynamicProxy.class);
    private Object impl;
    private Class<?> svcDefinition;

    public DynamicProxy(Class<?> svcDef) {
        this.svcDefinition = svcDef;
    }

    /*
    Gets an instance of the delegate
     */
    private synchronized Object getInstance() {
        if (this.impl == null) {
            this.impl = ServiceLocator.getInstance().getDirectService(this.svcDefinition);
        }
        return this.impl;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {

        Object obj = getInstance();

        if (obj == null) {
            // return a reasonable default for primitive types
            // and a NULL for anything that's not primitive.

            String errMsg = svcDefinition.getCanonicalName() + " service was called prior to initialization.";
            Log.w(LOG_TAG, errMsg);
            System.err.println(errMsg);

            Class<?> returnType = method.getReturnType();
            String canonName = returnType.getCanonicalName();
            if (returnType.isPrimitive()) {
                if (canonName.equals("boolean")) {
                    return false;
                } else if (canonName.equals("char")) {
                    return '\u0000';
                } else if (canonName.equals("byte")) {
                    return (byte) 0;
                } else if (canonName.equals("short")) {
                    return (short) 0;
                } else if (canonName.equals("int")) {
                    return 0;
                } else if (canonName.equals("long")) {
                    return (long) 0;
                } else if (canonName.equals("float")) {
                    return (float) 0;
                } else if (canonName.equals("double")) {
                    return (double) 0;
                } else if (canonName.equals("void")) {
                    return null;
                }
                throw new RuntimeException("Unknown type: [" + canonName + "]");
            } else {
                return null;
            }
        }

        if (Object.class == method.getDeclaringClass()) {
            String name = method.getName();
            if ("equals".equals(name)) {
                return proxy == args[0];
            } else if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            } else if ("toString".equals(name)) {
                return proxy.getClass().getName() + "@" +
                        Integer.toHexString(System.identityHashCode(proxy)) +
                        ", with InvocationHandler " + this;
            } else {
                throw new IllegalStateException(String.valueOf(method));
            }
        }
        return method.invoke(obj, args);
    }
}