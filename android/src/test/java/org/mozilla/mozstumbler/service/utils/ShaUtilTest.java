package org.mozilla.mozstumbler.service.utils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class ShaUtilTest {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testHexDigest() {
        String digest = ShaUtil.sha1_hex_digest("victor@crankycoder.com");
        String expected = "a0719cf708b680528765e487c027f5b462a319c5";

        // Expected was generated from python's hashlib
        // hashlib.sha1('victor@crankycoder.com').hexdigest();
        assertEquals(expected, digest);
        assertEquals(40, digest.length());
    }
}
