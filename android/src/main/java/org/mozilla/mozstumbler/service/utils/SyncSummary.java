/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.utils;


// This seems really silly.  Why do we have a struct at all.
public class SyncSummary {
    public int numIoExceptions;
    public int totalBytesSent;
}