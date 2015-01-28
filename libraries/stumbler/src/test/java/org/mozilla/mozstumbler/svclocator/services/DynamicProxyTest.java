/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.svclocator.services;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.svclocator.ServiceConfig;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class DynamicProxyTest {

    @Test
    public void testLoadLazyProxy() {
        // This should test that we can obtain a lazily bound proxy
        // through the ServiceLocator
        ServiceLocator locator = ServiceLocator.getInstance();
        ISampleInterface t = (ISampleInterface) locator.getService(ISampleInterface.class);

        assertEquals(null, t.echo("bob"));
        assertEquals(0, t.getByte());
        assertEquals(0, t.getShort());
        assertEquals(0, t.getInt());
        assertEquals(0, t.getLong());
        assertTrue(0 == Float.compare((float) 0, t.getFloat()));
        assertTrue(0 == Double.compare(t.getDouble(), (double) 0));
        assertEquals('\u0000', t.getChar());

        assertFalse(t.getBool());

        //assertNull(t.getServiceLocator());

        SampleImplementation obj = new SampleImplementation();
        ServiceLocator.newRoot(new ServiceConfig());
        ServiceLocator.getInstance().putService(ISampleInterface.class, new SampleImplementation());

        assertEquals("bob", t.echo("bob"));
        assertEquals((byte) 1, t.getByte());
        assertEquals((short) 1, t.getShort());
        assertEquals(1, t.getInt());
        assertEquals(1, t.getLong());
        assertTrue(0 == Float.compare((float) 1, t.getFloat()));
        assertTrue(0 == Double.compare(t.getDouble(), (double) 1));
        assertEquals('a', t.getChar());        //assertNull(t.getServiceLocator());
    }
}