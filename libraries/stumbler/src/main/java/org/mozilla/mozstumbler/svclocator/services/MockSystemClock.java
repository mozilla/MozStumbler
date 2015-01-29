/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.svclocator.services;

public class MockSystemClock implements ISystemClock {

    private long currentTime;

    public void setCurrentTime(long currentTime) {
        this.currentTime = currentTime;
    }

    @Override
    public long currentTimeMillis() {
        return currentTime;
    }
}
