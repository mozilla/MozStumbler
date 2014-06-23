package org.mozilla.mozstumbler.client;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.support.v4.content.LocalBroadcastManager;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.ScrollView;
import android.widget.TextView;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.service.SharedConstants;
import java.util.LinkedList;

public class LogActivity extends Activity {
    static LinkedList<String> buffer = new LinkedList<String>();
    static final int MAX_SIZE = 1000;

    public static class LogMessageReceiver extends BroadcastReceiver {
        boolean mIsRegistered;

        public void register(Context context) {
            if (mIsRegistered)
                return;

            LocalBroadcastManager.getInstance(context).registerReceiver(this,
                    new IntentFilter(SharedConstants.ACTION_GUI_LOG_MESSAGE));
            mIsRegistered = true;
        }
        @Override
        public void onReceive(Context c, Intent intent) {
            String s = intent.getStringExtra(SharedConstants.ACTION_GUI_LOG_MESSAGE_EXTRA);
            if (s == null)
                return;

            if (buffer.size() > MAX_SIZE) {
                buffer.removeFirst();
            }
            buffer.add(s);
            if (sConsoleView != null) {
                sConsoleView.println(s);
            }
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
            tv.append(str);

            if (enable_scroll) {
                scrollTo(0,tv.getBottom());
            }
        }

        public void println(String str){
            print(str + "\n");
        }

        public void clear() {
            tv.setText("");
            this.scrollTo(0, 0);
        }
    }
}
