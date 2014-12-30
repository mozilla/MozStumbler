/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.svclocator;

import org.mozilla.mozstumbler.svclocator.services.ISystemClock;
import org.mozilla.mozstumbler.svclocator.services.SystemClock;

import java.util.HashMap;

public class ServiceConfig extends HashMap<Class<?>, Object> {

    private static final long serialVersionUID = 1111111111L;

    public static ServiceConfig defaultServiceConfig() {

        ServiceConfig result = new ServiceConfig();

        result.put(ISystemClock.class, new SystemClock());

        return result;
    }

}
