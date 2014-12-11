/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.svclocator.services;

/*
 This interface provides interfaces to time related methods.

 */
public interface ISystemClock {

    // Implementations should provide an implementation of System.currentTimeMillis
    public long currentTimeMillis();


}
