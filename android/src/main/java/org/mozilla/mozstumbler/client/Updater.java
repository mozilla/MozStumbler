/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.http.IResponse;
import org.mozilla.mozstumbler.service.utils.NetworkInfo;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class Updater {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(Updater.class);
    private static final String LATEST_URL = "https://github.com/mozilla/MozStumbler/releases/latest";
    private static final String APK_URL_FORMAT = "https://github.com/mozilla/MozStumbler/releases/download/v%s/MozStumbler-v%s.apk";
     private static final long UPDATE_CHECK_FREQ = 6 * 60 * 60 * 1000; // 6 hours
     private static long sLastUpdateCheck = 0;

    public Updater() {
    }

    public boolean wifiExclusiveAndUnavailable(Context c) {
        return !NetworkInfo.getInstance().isWifiAvailable() && ClientPrefs.getInstance(c).getUseWifiOnly();
    }


    public boolean checkForUpdatesRateLimited(final Activity activity, String api_key) {
        if (System.currentTimeMillis() - sLastUpdateCheck < UPDATE_CHECK_FREQ) {
            return false;
        }
        return this.checkForUpdates(activity, api_key);
    }


    public boolean checkForUpdates(final Activity activity, String api_key) {

        // No API Key means skip the update
        if (api_key == null || api_key.equals("")) {
            return false;
        }

        if (!NetworkInfo.getInstance().isConnected()) {
            return false;
        }

        // No wifi available and require the use of wifi only means skip
        if (wifiExclusiveAndUnavailable(activity.getApplicationContext())) {
            return false;
        }

        new AsyncTask<Void, Void, IResponse>() {
            @Override
            public IResponse doInBackground(Void... params) {
                IHttpUtil httpClient = (IHttpUtil) ServiceLocator.getInstance().getService(IHttpUtil.class);
                return httpClient.head(LATEST_URL, null);
            }

            @Override
            public void onPostExecute(IResponse response) {
                if (response == null) {
                    return;
                }
                Map<String, List<String>> headers = response.getHeaders();
                if (headers == null) {
                    return;
                }
                Log.i(LOG_TAG, "Got headers: " + headers.toString());
                if (headers.get("Location") == null) {
                    return;
                }
                String locationUrl = headers.get("Location").get(0);
                if (locationUrl == null || locationUrl.length() < 1) {
                    return;
                }
                String[] parts = locationUrl.split("/");
                if (parts.length < 2) {
                    return;
                }
                String tag = parts[parts.length - 1];
                if (tag.length() < 2) {
                    return;
                }
                String latestVersion = tag.substring(1); // strip the 'v' from the beginning

                String installedVersion = PackageUtils.getAppVersion(activity);
                installedVersion = stripBuildHostName(installedVersion);

                Log.d(LOG_TAG, "Installed version: " + installedVersion);
                Log.d(LOG_TAG, "Latest version: " + latestVersion);

                if (isVersionGreaterThan(latestVersion, installedVersion) && !activity.isFinishing()) {
                    showUpdateDialog(activity, installedVersion, latestVersion);
                }
            }
        }.execute();

        sLastUpdateCheck = System.currentTimeMillis();
        return true;
    }

    String stripBuildHostName(String installedVersion) {
        // Some versions had the old buildhost stuff in there, we need
        // to strip out anything pase the 3rd integer part.
        String[] parts = installedVersion.split("\\.");
        if (parts.length < 3) {
            throw new RuntimeException("Unexpected version string: [" + installedVersion + "] parts:" + parts.length);
        }
        return parts[0] + "." + parts[1] + "." + parts[2];
    }

    private boolean isVersionGreaterThan(String a, String b) {
        if (a == null) {
            return false;
        }

        if (b == null) {
            return true;
        }

        if (a.equals(b)) {
            return false; // fast path
        }

        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        int len = Math.min(as.length, bs.length);

        try {
            for (int i = 0; i < len; i++) {
                int an = Integer.parseInt(as[i]);
                int bn = Integer.parseInt(bs[i]);
                if (an == bn) {
                    continue;
                }
                return (an > bn);
            }
        } catch (NumberFormatException e) {
            Log.w(LOG_TAG, "a='" + a + "', b='" + b + "'", e);
            return false;
        }

        // Strings have identical prefixes, so longest version string wins.
        return as.length > bs.length;
    }

    private void showUpdateDialog(final Context context,
                                  String installedVersion,
                                  final String latestVersion) {
        String msg = context.getString(R.string.update_message);
        msg = String.format(msg, installedVersion, latestVersion);

        if (installedVersion.startsWith("0.") &&
                latestVersion.startsWith("1.")) {
            // From 0.x to 1.0 and higher, the keystore changed
            msg += " " + context.getString(R.string.must_uninstall_to_update);
        }

        final Dialog.OnCancelListener onCancel = new Dialog.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface di) {
                di.dismiss();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.update_title))
                .setMessage(msg)
                .setPositiveButton(context.getString(R.string.update_now),
                        new Dialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface di, int which) {
                                Log.d(LOG_TAG, "Update Now");
                                di.dismiss();
                                downloadAndInstallUpdate(context, latestVersion);
                            }
                        })
                .setNegativeButton(context.getString(R.string.update_later),
                        new Dialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface di, int which) {
                                onCancel.onCancel(di);
                            }
                        })
                .setOnCancelListener(onCancel);
        builder.create().show();
    }

    private void downloadAndInstallUpdate(final Context context, final String version) {
        new AsyncTask<Void, Void, File>() {
            @Override
            public File doInBackground(Void... params) {
                URL apkURL = getUpdateURL(version);
                File apk = downloadFile(context, apkURL);
                if (apk == null || !apk.exists()) {
                    Log.e(LOG_TAG, "Update file not found!");
                    return null;
                }

                return apk;
            }

            @Override
            public void onPostExecute(File result) {
                if (result != null) {
                    installPackage(context, result);
                }
            }
        }.execute();
    }

    private URL getUpdateURL(String version) {
        String url = String.format(APK_URL_FORMAT, version, version);
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private File downloadFile(Context context, URL url) {
        Log.d(LOG_TAG, "Downloading: " + url);

        File file;
        File dir = context.getExternalFilesDir(null);
        try {
            file = File.createTempFile("update", ".apk", dir);
        } catch (IOException e1) {
            Log.e(LOG_TAG, "", e1);
            return null;
        }

        try {
            IHttpUtil httpClient = (IHttpUtil) ServiceLocator.getInstance().getService(IHttpUtil.class);
            return httpClient.getUrlAsFile(url, file);
        } catch (IOException e) {
            Log.e(LOG_TAG, "", e);
            file.delete();
            return null;
        }
    }


    private void installPackage(Context context, File apkFile) {
        Uri apkURI = Uri.fromFile(apkFile);
        Log.d(LOG_TAG, "Installing: " + apkURI);

        //First stop the service so it is not running more
        Intent service = new Intent();
        service.setClass(context, ClientStumblerService.class);
        context.stopService(service);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkURI, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        //And then kill the app to avoid any error
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
