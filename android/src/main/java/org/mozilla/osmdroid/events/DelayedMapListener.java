package org.mozilla.osmdroid.events;

import android.os.Handler;

import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

/*
 * A MapListener that aggregates multiple events called in quick succession.
 * After an event arrives, if another event arrives within <code>delay</code> milliseconds,
 * the original event is discarded.  Otherwise, the event is propagated to the wrapped
 * MapListener.  Note: This class is not thread-safe.
 *
 * @author Theodore Hong
 */
public class DelayedMapListener implements MapListener {

    /**
     * Default listening delay
     */
    protected static final int DEFAULT_DELAY = 100;
    private static final String LOG_TAG = LoggerUtil.makeLogTag(DelayedMapListener.class);
    /**
     * Listening delay, in milliseconds
     */
    protected long delay;
    protected Handler handler;
    protected CallbackTask callback;
    /**
     * The wrapped MapListener
     */
    MapListener wrappedListener;

    /*
     * @param wrappedListener The wrapped MapListener
     *
     * @param delay Listening delay, in milliseconds
     */
    public DelayedMapListener(final MapListener wrappedListener, final long delay) {
        this.wrappedListener = wrappedListener;
        this.delay = delay;
        this.handler = new Handler();
        this.callback = null;
    }

    /*
     * Constructor with default delay.
     *
     * @param wrappedListener The wrapped MapListener
     */
    public DelayedMapListener(final MapListener wrappedListener) {
        this(wrappedListener, DEFAULT_DELAY);
    }

    @Override
    public boolean onScroll(final ScrollEvent event) {
        dispatch(event);
        return true;
    }

    @Override
    public boolean onZoom(final ZoomEvent event) {
        dispatch(event);
        return true;
    }

    /*
     * Process an incoming MapEvent.
     */
    protected void dispatch(final MapEvent event) {
        // cancel any pending callback
        if (callback != null) {
            handler.removeCallbacks(callback);
        }
        callback = new CallbackTask(event);

        // set timer
        handler.postDelayed(callback, delay);
    }

    // Callback tasks
    private class CallbackTask implements Runnable {
        private final MapEvent event;

        public CallbackTask(final MapEvent event) {
            this.event = event;
        }

        @Override
        public void run() {
            // do the callback
            if (event instanceof ScrollEvent) {
                wrappedListener.onScroll((ScrollEvent) event);
            } else if (event instanceof ZoomEvent) {
                wrappedListener.onZoom((ZoomEvent) event);
            } else {
                // unknown event; discard
                ClientLog.d(LOG_TAG, "Unknown event received: " + event);
            }
        }
    }
}
