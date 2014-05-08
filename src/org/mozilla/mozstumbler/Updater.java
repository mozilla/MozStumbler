package org.mozilla.mozstumbler;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

final class Updater {
    private static final String LOGTAG = Updater.class.getName();
    private static final String VERSION_URL = "https://raw.github.com/mozilla/MozStumbler/master/VERSION";
    private static final String APK_URL_FORMAT = "https://github.com/mozilla/MozStumbler/releases/download/v%s/MozStumbler-v%s.apk";

    private Updater() {
    }

    static void checkForUpdates(final Context context) {
        new AsyncTask<Void, Void, String>() {
            @Override
            public String doInBackground(Void... params) {
                try {
                    URL url = new URL(VERSION_URL);
                    InputStream stream = null;
                    try {
                        URLConnection connection = openConnectionWithProxy(url);
                        stream = connection.getInputStream();
                        BufferedReader reader = null;
                        try {
                            reader = new BufferedReader(new InputStreamReader(stream));
                            return reader.readLine();
                        } finally {
                            if (reader != null) {
                                reader.close();
                            }
                        }
                    } finally {
                        if (stream != null) {
                            stream.close();
                        }
                    }
                } catch (FileNotFoundException fe) {
                    Log.w(LOGTAG, "VERSION not found: " + VERSION_URL);
                } catch (IOException e) {
                    Log.e(LOGTAG, "", e);
                }

                return null;
            }

            @Override
            public void onPostExecute(String latestVersion) {
                String installedVersion = PackageUtils.getAppVersion(context);

                Log.d(LOGTAG, "Installed version: " + installedVersion);
                Log.d(LOGTAG, "Latest version: " + latestVersion);

                if (isVersionGreaterThan(latestVersion, installedVersion)) {
                    showUpdateDialog(context, installedVersion, latestVersion);
                }
            }
        }.execute();
    }

    private static boolean isVersionGreaterThan(String a, String b) {
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
            Log.w(LOGTAG, "a='" + a + "', b='" + b + "'", e);
            return false;
        }

        // Strings have identical prefixes, so longest version string wins.
        return as.length > bs.length;
    }

    private static void showUpdateDialog(final Context context,
                                         String installedVersion,
                                         final String latestVersion) {
        String msg = context.getString(R.string.update_message);
        msg = String.format(msg, installedVersion, latestVersion);

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
                                       Log.d(LOGTAG, "Update Now");
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

    private static void downloadAndInstallUpdate(final Context context, final String version) {
        new AsyncTask<Void, Void, File>() {
            @Override
            public File doInBackground(Void... params) {
                URL apkURL = getUpdateURL(version);
                File apk = downloadFile(context, apkURL);
                if (apk == null || !apk.exists()) {
                    Log.e(LOGTAG, "Update file not found!");
                    return null;
                }

                return apk;
            }

            @Override
            public void onPostExecute(File result){
                installPackage(context, result);
            }
        }.execute();
    }

    private static URL getUpdateURL(String version) {
        String url = String.format(APK_URL_FORMAT, version, version);
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static File downloadFile(Context context, URL url) {
        Log.d(LOGTAG, "Downloading: " + url);

        File file = createTempFile(context);
        if (file == null) {
            return null;
        }

        final int bufferLength = 8192;
        final byte[] buffer = new byte[bufferLength];

        try {
            InputStream inputStream = null;
            OutputStream outputStream = null;
            try {
                URLConnection connection = openConnectionWithProxy(url);
                inputStream = connection.getInputStream();
                outputStream = new FileOutputStream(file);
                for (;;) {
                    int readLength = inputStream.read(buffer, 0, bufferLength);
                    if (readLength == -1) {
                        return file;
                    }
                    outputStream.write(buffer, 0, readLength);
                }
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        } catch (IOException e) {
            Log.e(LOGTAG, "", e);
            file.delete();
            return null;
        }
    }

    private static URLConnection openConnectionWithProxy(URL url) throws IOException {
        Proxy proxy = Proxy.NO_PROXY;

        ProxySelector proxySelector = ProxySelector.getDefault();
        if (proxySelector != null) {
            URI uri;
            try {
                uri = url.toURI();
            } catch (URISyntaxException e) {
                IOException ioe = new IOException(url.toString());
                ioe.initCause(e);
                throw ioe;
            }

            List<Proxy> proxies = proxySelector.select(uri);
            if (proxies != null && !proxies.isEmpty()) {
                proxy = proxies.get(0);
            }
        }

        return url.openConnection(proxy);
    }

    private static File createTempFile(Context context) {
        File dir = context.getExternalFilesDir(null);
        try {
            return File.createTempFile("update", ".apk", dir);
        } catch (IOException e) {
            Log.e(LOGTAG, "", e);
            return null;
        }
    }

    private static void installPackage(Context context, File apkFile) {
        Uri apkURI = Uri.fromFile(apkFile);
        Log.d(LOGTAG, "Installing: " + apkURI);

        //First stop the service so it is not running more
        Intent service = new Intent();
        service.setClass(context, StumblerService.class);
        context.stopService(service);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkURI, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        //And then kill the app to avoid any error
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
