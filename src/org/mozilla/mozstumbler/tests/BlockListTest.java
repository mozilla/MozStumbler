package org.mozilla.mozstumbler.tests;

import android.test.InstrumentationTestCase;

import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.blocklist.BlockListDownloader;

/**
 * Created by HYPER on 21-06-2014.
 */
public class BlockListTest extends InstrumentationTestCase {
    public BlockListTest() {
    }

    public void testDownloadBlocklist() {
        Prefs.createGlobalInstance(getInstrumentation().getContext());
        BlockListDownloader bld = new BlockListDownloader(getInstrumentation().getContext());
        bld.mIsTestMode = true;
        bld.checkForNewList();

    }
}
