/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.subactivities;

import android.widget.Button;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.UnittestLogger;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.robolectric.bytecode.ShadowWrangler.shadowOf;
import static org.robolectric.util.FragmentTestUtil.startFragment;

@Config(emulateSdk = 18,
        shadows = {FirstRunDialogFragmentShadow.class})
@RunWith(RobolectricTestRunner.class)
public class FirstRunFragmentTest {

    private static ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(FirstRunFragmentTest.class);

    @Before
    public void setUp() {
        ServiceLocator.getInstance().putService(ILogger.class, new UnittestLogger());
    }

    @Test
    public void testFirstRunFragment() {
        FirstRunFragment frf = new FirstRunFragment();
        startFragment(frf);

        frf = spy(frf);

        doReturn(null).when(frf).getActivity();

        Button button = (Button) frf.root.findViewById(R.id.button);
        FirstRunDialogFragmentShadow frfShadow = (FirstRunDialogFragmentShadow) shadowOf(frf);

        frfShadow.returnNullActivity(true);

        // You unfortunately can't override the getApplication() method on activity as it's been
        // declared final in the real Activity class.

        Log.d(LOG_TAG, "Got shadow: " + frfShadow);
        button.performClick();

    }

}
