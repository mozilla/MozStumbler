package org.mozilla.mozstumbler.client.fonts;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Created by JeremyChiang on 2014-08-11.
 */
public class FiraSansBoldTextView extends TextView {

    public FiraSansBoldTextView(Context context) {
        super(context);
        init();
    }

    public FiraSansBoldTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FiraSansBoldTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        Typeface fira = Typefaces.get(getContext(), "FiraSans-Bold.ttf");
        setTypeface(fira);
    }
}
