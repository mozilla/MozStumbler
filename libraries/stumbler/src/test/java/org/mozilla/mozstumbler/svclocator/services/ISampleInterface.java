/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.svclocator.services;

import org.mozilla.mozstumbler.svclocator.ServiceLocator;

public interface ISampleInterface {
    String echo(String name);

    boolean getBool();

    int getInt();

    long getLong();

    float getFloat();

    char getChar();

    byte getByte();

    short getShort();

    double getDouble();

    Integer getBoxedInteger();

    ServiceLocator getServiceLocator();
}