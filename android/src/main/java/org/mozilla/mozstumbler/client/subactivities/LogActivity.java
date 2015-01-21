/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.subactivities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LogActivity extends ActionBarActivity {
    private static String LOG_TAG = LoggerUtil.makeLogTag(LogActivity.class);
    private static LinkedList<String> buffer = new LinkedList<String>();
    private static int sLongLinesCounter;
    private static final int MAX_SIZE = 200;
    private static LogMessageReceiver sInstance;

    public static class LogMessageReceiver extends BroadcastReceiver {

        // Ensure that the message buffer used by the GUI is accessed only on the main thread
        static class AddToBufferOnMain extends Handler {
            WeakReference<LogMessageReceiver> mParentClass;

            public AddToBufferOnMain(WeakReference<LogMessageReceiver> parent) {
                mParentClass = parent;
            }

            public void handleMessage(Message m) {
                String msg = null;
                do {
                    msg = AppGlobals.guiLogMessageBuffer.poll();
                    if (mParentClass.get() != null) {
                        mParentClass.get().addMessageToBuffer(msg);
                    }
                } while (msg != null);
            }
        }

        Timer mFlushMessagesTimer = new Timer();
        AddToBufferOnMain mMainThreadHandler;

        public static void createGlobalInstance(Context context) {
            sInstance = new LogMessageReceiver(context);
            sInstance.mMainThreadHandler = new AddToBufferOnMain(new WeakReference<LogMessageReceiver>(sInstance));
            AppGlobals.guiLogMessageBuffer = new ConcurrentLinkedQueue<String>();
        }

        LogMessageReceiver(Context context) {
            LocalBroadcastManager.getInstance(context).registerReceiver(this,
                    new IntentFilter(AppGlobals.ACTION_GUI_LOG_MESSAGE));

            final int kMillis = 1000 * 3;
            mFlushMessagesTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    mMainThreadHandler.obtainMessage().sendToTarget();
                }
            }, kMillis, kMillis);
        }

        void addMessageToBuffer(String s) {
            if (s == null) {
                return;
            }

            if (buffer.size() > MAX_SIZE) {
                buffer.removeFirst();
            }

            // size of log is: 1000 * 30 + 200 * 470 = 30 kb + 94 kb, should be a very safe size

            final int kMaxCharsOfLongerLines = 1000;
            final int kMaxCharsOfTruncatedLine = 200;
            final int kLongLinesAllowedBeforeTruncate = 30;

            if (s.length() > kMaxCharsOfTruncatedLine) {
                sLongLinesCounter++;

                if (sLongLinesCounter == kLongLinesAllowedBeforeTruncate) {
                    String msg = "LOG VIEWER REACHED " + kLongLinesAllowedBeforeTruncate +" LONG MESSAGES. TRUNCATING MESSAGES.";
                    buffer.add(msg);
                    if (sConsoleView != null) {
                        sConsoleView.println(msg);
                    }
                }
            }

            final boolean isTruncating = sLongLinesCounter >= kLongLinesAllowedBeforeTruncate;

            final int maxChars = isTruncating? kMaxCharsOfTruncatedLine : kMaxCharsOfLongerLines;
            if (s.length() > maxChars && !s.startsWith(AppGlobals.NO_TRUNCATE_FLAG)) {
                // 1/3 of max length, ellipse, then last 2/3 of max length
                s = s.substring(0, maxChars / 3) + " ... " + s.substring(s.length() - 1 - maxChars * 2/3);
            }

            String prev = (buffer.size() > 0) ? buffer.getLast() : null;
            if (prev != null && prev.length() > 10 && s.length() > 10) {
                if (prev.substring(10).equals(s.substring(10))) {
                    ClientLog.d(LOG_TAG, "Message is repeated: " + s);
                    return;
                }
            }
            buffer.add(s);
            if (sConsoleView != null) {
                sConsoleView.println(s);
            }
        }

        @Override
        public void onReceive(Context c, Intent intent) {
            String s = intent.getStringExtra(AppGlobals.ACTION_GUI_LOG_MESSAGE_EXTRA);
            addMessageToBuffer(s);
        }
    }

    ConsoleView mConsoleView;
    static ConsoleView sConsoleView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);
        mConsoleView = (ConsoleView) findViewById(R.id.scrollview);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sConsoleView = mConsoleView;
        for (String s: buffer) {
            mConsoleView.println(s);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sConsoleView = null;
    }

    public static class ConsoleView extends ScrollView {
        private static final String LOG_TAG = LoggerUtil.makeLogTag(ConsoleView.class);
        public TextView tv;
        boolean enable_scroll = true;

        void init(Context context) {
            tv = new TextView(context);
            addView(tv);
            tv.setTextSize(13.0f);
            tv.setClickable(false);
            enableScroll(true);
        }

        public ConsoleView(Context context) {
            super(context);
            init(context);
        }

        public ConsoleView(Context context, AttributeSet attrs)
        {
            super(context, attrs);
            init(context);
        }
        public ConsoleView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
            init(context);
        }

        public void enableScroll(boolean v) {
            this.enable_scroll = v;
        }

        public void print(String str){
            tv.append(Html.fromHtml(str + "<br />"));

            if (enable_scroll) {
                scrollTo(0, tv.getBottom());
            }
        }

        public void println(String str){
            print(str + "\n");
        }

        public void clear() {
            tv.setText("");
            this.scrollTo(0, 0);
        }

        @Override
        protected void onScrollChanged(int x, int y, int oldx, int oldy) {
            super.onScrollChanged(x, y, oldx, oldy);
            int diff = tv.getHeight() - (y + getHeight());
            boolean isAtBottom = diff <= 0;
            enableScroll(isAtBottom);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.log_menu, menu);
        MenuItem m = menu.findItem(R.id.scroll_to_end);
        MenuItemCompat.setShowAsAction(m, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        m = menu.findItem(R.id.scroll_to_start);
        MenuItemCompat.setShowAsAction(m, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.scroll_to_start:
                this.mConsoleView.fullScroll(View.FOCUS_UP);
                return true;
            case R.id.scroll_to_end:
                this.mConsoleView.fullScroll(View.FOCUS_DOWN);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
