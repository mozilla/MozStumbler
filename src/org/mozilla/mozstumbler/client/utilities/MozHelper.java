package org.mozilla.mozstumbler.client.utilities;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class MozHelper {
    public static String localizeNumber(int numberToFormat) {
        return NumberFormat.getInstance(Locale.getDefault()).format(numberToFormat);
    }
}
