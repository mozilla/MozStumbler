/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.svclocator.services;

import org.mozilla.mozstumbler.svclocator.ServiceLocator;

public class SampleImplementation implements ISampleInterface {
    public String echo(String name) {
        return name;
    }

    @Override
    public boolean getBool() {
        return true;
    }

    @Override
    public int getInt() {
        return 1;
    }

    @Override
    public long getLong() {
        return 1;
    }

    @Override
    public float getFloat() {
        return 1;
    }

    @Override
    public char getChar() {
        return 'a';
    }

    @Override
    public byte getByte() {
        return 1;
    }

    @Override
    public short getShort() {
        return 1;
    }

    @Override
    public double getDouble() {
        return 1;
    }

    @Override
    public Integer getBoxedInteger() {
        return new Integer(1);
    }

    @Override
    public ServiceLocator getServiceLocator() {
        return ServiceLocator.getInstance();
    }
}