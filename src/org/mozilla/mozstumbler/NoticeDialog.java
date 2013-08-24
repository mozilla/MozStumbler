package org.mozilla.mozstumbler;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;

public class NoticeDialog
{
    private Activity mActivity;
    private Prefs mPrefs;

    public NoticeDialog(Activity context, Prefs prefs) {
        mActivity = context;
        mPrefs = prefs;
    }

    public void show() {
        if (mPrefs.getHasSeenNotice()) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity)
            .setTitle(mActivity.getString(R.string.app_name))
            .setMessage(mActivity.getString(R.string.notice))
            .setPositiveButton(android.R.string.ok,
                               new Dialog.OnClickListener() {
                                   @Override
                                   public void onClick(DialogInterface di, int which) {
                                       mPrefs.setHasSeenNotice();
                                       di.dismiss();
                                   }
                               })
            .setNegativeButton(android.R.string.cancel,
                               new Dialog.OnClickListener() {
                                 @Override
                                 public void onClick(DialogInterface di, int which) {
                                     mActivity.finish();
                                 }
                             });
        builder.create().show();
    }
}
