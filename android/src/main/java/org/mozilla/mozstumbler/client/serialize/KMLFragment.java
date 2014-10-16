/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.serialize;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ObservedLocationsReceiver;
import org.mozilla.mozstumbler.client.mapview.ObservationPoint;
import org.mozilla.mozstumbler.service.AppGlobals;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.ref.WeakReference;
import java.util.LinkedList;

public class KMLFragment extends Fragment
    implements ObservationPointSerializer.IListener {

    private final String LOG_TAG = AppGlobals.LOG_PREFIX + KMLFragment.class.getSimpleName();

    private LinkedList<ObservationPoint> mPointsToWrite;
    private WeakReference<ProgressDialog> mProgressDialog;
    private TextView mSavedFileLocation;

    private View mRootView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_kml, container, false);
        mPointsToWrite = ObservedLocationsReceiver.getInstance().getObservationPoints();
        mProgressDialog = new WeakReference<ProgressDialog>(null);
        mSavedFileLocation = (TextView)mRootView.findViewById(R.id.textViewSavedFile);

        View buttonLoad = mRootView.findViewById(R.id.buttonLoad);
        View buttonSave = mRootView.findViewById(R.id.buttonSave);

        buttonLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickLoad(v);
            }
        });
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickSave(v);
            }
        });

        return mRootView;
    }

    private boolean mIsRunning;
    private void showProgress(boolean isStarted, String msg) {
        if (isStarted) {
            mProgressDialog = new WeakReference<ProgressDialog>(new ProgressDialog(getActivity()));
            mProgressDialog.get().setCancelable(false);
            mProgressDialog.get().setCanceledOnTouchOutside(false);
            mProgressDialog.get().setMessage(msg);
            mProgressDialog.get().show();
        } else {
            mIsRunning = false;
            if (mProgressDialog.get() != null) {
                mProgressDialog.get().dismiss();
            }
        }
        setButtonsEnabledState();
    }

    @Override
    public void onResume() {
        super.onResume();

        setButtonsEnabledState();
    }

    private void setButtonsEnabledState() {
        View buttonLoad = mRootView.findViewById(R.id.buttonLoad);
        View buttonSave = mRootView.findViewById(R.id.buttonSave);

        buttonLoad.setEnabled(!mIsRunning);
        buttonSave.setEnabled(!mIsRunning);

        if (!mIsRunning) {
            String[] files = getFileList();
            buttonLoad.setEnabled(files != null && files.length > 0);
        }
    }

    public void onReadComplete(LinkedList<ObservationPoint> points, File file) {
        showProgress(false, null);
    }

    public void onError() {
        showProgress(false, null);
    }

    public void onWriteComplete(final File file) {
        showProgress(false, null);

        mSavedFileLocation.setText(file.getAbsolutePath());

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.kml_file_saved)
                .setMessage(R.string.share_after_kml_saved)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        shareFile(file);
                    }
                })
                .setNegativeButton(android.R.string.no, null).show();
    }

    private void onClickSave(View v) {
        if (mPointsToWrite == null) {
            return;
        }

        if (mIsRunning) {
            return;
        }

        mIsRunning = true;
        setButtonsEnabledState();

        final DateTime date = DateTime.now();
        final DateTimeFormatter dtf = DateTimeFormat.forPattern("dd-MM-yyyy-HH:mm");
        final String name = "obs-" +  mPointsToWrite.size() + "-date-" + dtf.print(date) + ".kml";
        final File dir = getActivity().getExternalFilesDir(null);
        final File file = new File(dir, name);

        showProgress(true, getString(R.string.saving_kml) + " to " + (dir != null ? dir.toString() : "null"));
        ObservationPointSerializer obs = new ObservationPointSerializer(this,
                ObservationPointSerializer.Mode.WRITE, file, mPointsToWrite);
        obs.execute();
    }

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        final ListView list = (ListView)v;
        final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
        final int position = info.position;
        final Object object = list.getAdapter().getItem(position);

        menu.add(R.string.load_file);
        menu.add(R.string.share_file);
        menu.add(R.string.delete_file);
        menu.add(R.string.delete_all);

        MenuItem.OnMenuItemClickListener listener = new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                contextItemSelected(item, (object == null)? null : object.toString());
                if (mLoadFileDialog != null && mLoadFileDialog.isShowing()) {
                    mLoadFileDialog.dismiss();
                }
                return false;
            }
        };

        for (int i = 0, n = menu.size(); i < n; i++)
            menu.getItem(i).setOnMenuItemClickListener(listener);
    }

    private void shareFile(File file) {
        final Intent intent = new Intent(android.content.Intent.ACTION_SEND);
        intent.setType("*/*");
        intent.putExtra(android.content.Intent.EXTRA_SUBJECT, R.string.mozstumbler_kml_file);
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
        startActivity(Intent.createChooser(intent, getString(R.string.share_file)));
    }

    private void contextItemSelected(MenuItem item, final String filename) {
        final boolean isDeleteFile = item.getTitle().equals(getString(R.string.delete_file));
        boolean isLoadFile = item.getTitle().equals(getString(R.string.load_file));
        boolean isDeleteAll = item.getTitle().equals(getString(R.string.delete_all));
        boolean isShareFile = item.getTitle().equals(getString(R.string.share_file));

        if (filename == null && (isDeleteFile || isLoadFile || isShareFile)) {
            return;
        }

        if (isLoadFile) {
            showProgress(true, getString(R.string.loading_kml));
            ObservationPointSerializer obs = new ObservationPointSerializer(this,
                    ObservationPointSerializer.Mode.READ,
                    new File(getActivity().getExternalFilesDir(null), filename), mPointsToWrite);
            obs.execute();
            return;
        }

        if (isShareFile) {
            shareFile(new File(getActivity().getExternalFilesDir(null), filename));
            return;
        }

        if (!isDeleteFile && !isDeleteAll) {
            return;
        }

        new AlertDialog.Builder(getActivity())
                .setTitle(item.getTitle())
                .setMessage(R.string.are_you_sure)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        if (isDeleteFile) {
                            File f = new File(getActivity().getExternalFilesDir(null), filename);
                            f.delete();
                        } else {
                            String[] files = getFileList();
                            if (files != null) {
                                for (String file : files) {
                                    new File(getActivity().getExternalFilesDir(null), file).delete();
                                }
                            }
                        }
                    }
                })
                .setNegativeButton(android.R.string.no, null).show();
    }

    private String[] getFileList() {
        final File dir = getActivity().getExternalFilesDir(null);
        if (dir == null) {
            return null;
        }
        String[] list = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".kml");
            }
        });
        return (list != null)? list : new String[0];
    }

    private Dialog mLoadFileDialog;

    private void onClickLoad(View v) {
        final String[] files = getFileList();
        if (files == null || files.length < 1) {
            return;
        }

        if (mIsRunning) {
            return;
        }
        mIsRunning = true;
        setButtonsEnabledState();

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Select File");
        final ListView listView = new ListView(getActivity());
        registerForContextMenu(listView);
        final ArrayAdapter<String> modeAdapter = new ArrayAdapter<String>(getActivity(),
                                                                    android.R.layout.simple_list_item_1,
                                                                    android.R.id.text1,
                                                                    files);
        listView.setAdapter(modeAdapter);
        builder.setView(listView);
        mLoadFileDialog = builder.create();
        mLoadFileDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mIsRunning = false;
                setButtonsEnabledState();
            }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showProgress(true, getString(R.string.loading_kml));
                File file = new File(getActivity().getExternalFilesDir(null), files[position]);
                ObservationPointSerializer obs = new ObservationPointSerializer(KMLFragment.this,
                        ObservationPointSerializer.Mode.READ,
                        file, mPointsToWrite);
                obs.execute();
                mLoadFileDialog.setOnDismissListener(null);
                mLoadFileDialog.dismiss();
            }
        });

        mLoadFileDialog.show();
    }
}
