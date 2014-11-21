/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.subactivities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.serialize.KMLFragment;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.Log;

import java.io.File;

import org.mozilla.mozstumbler.R;
import static org.mozilla.mozstumbler.service.stumblerthread.datahandling.ClientDataStorageManager.sdcardArchivePath;

public class DeveloperActivity extends ActionBarActivity {

    private final String LOG_TAG = AppGlobals.makeLogTag(DeveloperActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer);
        if (savedInstanceState == null) {
            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            ft.add(R.id.frame1, new KMLFragment());
            ft.add(R.id.frame2, new DeveloperOptions());
            ft.commit();
        }

        TextView tv = (TextView) findViewById(R.id.textViewDeveloperTitle);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                new AlertDialog.Builder(DeveloperActivity.this)
                        .setTitle("ACRA test")
                        .setMessage("Force a crash?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Object empty = null;
                                empty.hashCode();
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
                return true;
            }
        });
    }

    // For misc developer options
    public static class DeveloperOptions extends Fragment {
        private final String LOG_TAG = AppGlobals.makeLogTag(DeveloperOptions.class.getSimpleName());

        private View mRootView;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            mRootView = inflater.inflate(R.layout.fragment_developer_options, container, false);
            setupSaveJSONLogs();
            return mRootView;
        }

        private void setupSaveJSONLogs() {
            boolean saveStumbleLogs = Prefs.getInstance().isSaveStumbleLogs();
            CheckBox button = (CheckBox) mRootView.findViewById(R.id.toggleSaveStumbleLogs);
            button.setChecked(saveStumbleLogs);
            button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    onToggleSaveStumbleLogs(isChecked);
                }
            });
        }

        private void onToggleSaveStumbleLogs(boolean isChecked) {
            if (isChecked) {
                Context viewCtx = mRootView.getContext();
                if (!archiveDirCreatedAndMounted(this.getActivity())) {

                    Toast.makeText(viewCtx,
                            viewCtx.getString(R.string.create_log_archive_failure),
                            Toast.LENGTH_SHORT).show();
                    isChecked = false;
                    CheckBox button = (CheckBox) mRootView.findViewById(R.id.toggleSaveStumbleLogs);
                    button.setChecked(isChecked);
                } else {
                    Toast.makeText(viewCtx,
                            viewCtx.getString(R.string.create_log_archive_success) +
                                    sdcardArchivePath(),
                            Toast.LENGTH_LONG).show();
                }
            }
            Prefs.getInstance().setSaveStumbleLogs(isChecked);
        }

        public boolean archiveDirCreatedAndMounted(Context ctx) {
            File saveDir = new File(sdcardArchivePath());
            String storageState = Environment.getExternalStorageState();

            // You have to check the mount state of the external storage.
            // Using the mkdirs() result isn't good enough.
            if(!storageState.equals(Environment.MEDIA_MOUNTED)) {
                return false;
            }

            saveDir.mkdirs();
            if (!saveDir.exists()) {
                return false;
            }
            Log.d(LOG_TAG, "Created: [" + saveDir.getAbsolutePath() + "]");
            return true;
        }


    }


}
