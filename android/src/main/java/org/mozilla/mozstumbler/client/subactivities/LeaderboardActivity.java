/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.subactivities;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;

public class LeaderboardActivity extends ActionBarActivity {
    private static final String LEADERBOARD_URL = "https://location.services.mozilla.com/leaders";
    private WebView mWebView;
    private boolean mHasError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.activity_leaderboard);
        mWebView = (WebView) findViewById(R.id.webview);
        mWebView.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        setSupportProgressBarVisibility(true);
        final Activity activity = this;
        mWebView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                //activity.setProgress(progress * 100);
                setSupportProgress(progress * 100);
                if (progress > 45 && !mHasError) {
                    mWebView.setVisibility(View.VISIBLE);
                }

                if (progress > 90) {
                    setSupportProgressBarVisibility(false);
                }
            }
        });
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                mHasError = true;
                Toast.makeText(activity, "The Leaderboard requires an Internet connection.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onPageFinished(WebView webview, String url) {
                if (!mHasError) {
                    mWebView.setVisibility(View.VISIBLE);
                }
                setSupportProgressBarVisibility(false);
            }
        });

        setProgress(0);
        ClientPrefs prefs = ClientPrefs.getInstance();
        if (prefs == null) {
            prefs = ClientPrefs.createGlobalInstance(this.getApplicationContext());
        }
        String nick = prefs.getNickname();
        String url = LEADERBOARD_URL;
        if (nick != null) {
            url += "#" + nick;
        } else {
            // TODO Get server side to add this anchor
            // https://github.com/mozilla/ichnaea/issues/327
            url += "#leaderboard_start";
        }
        mWebView.loadUrl(url);
    }
}
