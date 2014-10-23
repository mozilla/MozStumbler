package org.mozilla.osmdroid.tileprovider.util;

import android.os.Handler;
import android.os.Message;
import android.view.View;

import org.mozilla.osmdroid.tileprovider.MapTile;

public class SimpleInvalidationHandler extends Handler {
    private final View mView;

    public SimpleInvalidationHandler(final View pView) {
        super();
        mView = pView;
    }

    @Override
    public void handleMessage(final Message msg) {
        switch (msg.what) {
            case MapTile.MAPTILE_SUCCESS_ID:
                mView.invalidate();
                break;
        }
    }
}
