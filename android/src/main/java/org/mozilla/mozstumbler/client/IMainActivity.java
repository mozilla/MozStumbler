/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

/**
 * This is the public threadsafe interface for the MainActivity.
 *
 * This is useful as the MainActivity needs to operate on the UI
 * thread, but events may come from bound services, or by Intent
 * listeners.
 */

public interface IMainActivity {
    public void updateUiOnMainThread();

    public void displayObservationCount(int count);
}

