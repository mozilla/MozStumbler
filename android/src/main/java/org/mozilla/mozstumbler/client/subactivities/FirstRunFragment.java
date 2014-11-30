/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.subactivities;

import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.MainApp;

public class FirstRunFragment extends DialogFragment {
    private static FirstRunFragment mInstance;

    public static void showInstance(FragmentManager fm) {
        if (mInstance == null) {
            mInstance = new FirstRunFragment();
            mInstance.show(fm, null);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(getActivity().getString(R.string.first_run_welcome_to_mozstumbler));
        getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(0xaa000000));
        getDialog().setCanceledOnTouchOutside(false);

        View root = inflater.inflate(R.layout.fragment_first_run, container, false);

        TextView tv = (TextView) root.findViewById(R.id.textview2);
        tv.setMovementMethod(LinkMovementMethod.getInstance());

        Button button = (Button) root.findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainApp) getActivity().getApplication()).startScanning();
                ClientPrefs.getInstance().setFirstRun(false);

                Dialog d = getDialog();
                if (d != null) {
                    dismiss();
                }

            }
        });

        return root;
    }
}
