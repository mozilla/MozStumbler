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
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.net.URI;
import java.net.URISyntaxException;

public class LeaderboardActivity extends ActionBarActivity {


    ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(LeaderboardActivity.class);

    private WebView mWebView;
    private boolean mHasError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.activity_leaderboard);
        mWebView = (WebView) findViewById(R.id.webview);
        mWebView.setVisibility(View.INVISIBLE);
        mWebView.getSettings().setJavaScriptEnabled(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        setSupportProgressBarVisibility(true);
        final Activity activity = this;
        mWebView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
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
        ClientPrefs prefs = ClientPrefs.getInstance(this);
        URI tmpURI = null;
        String url = null;

        try {
            tmpURI = new URI(prefs.getLbBaseURI() +  "/?profile=" + prefs.getLeaderboardUID());
            url = tmpURI.normalize().toString();
        } catch (URISyntaxException e) {
            Log.e(LOG_TAG, "Error normalizing URL", e);
            url = prefs.getLbBaseURI();
        }

        mWebView.loadUrl(url);
    }

}
