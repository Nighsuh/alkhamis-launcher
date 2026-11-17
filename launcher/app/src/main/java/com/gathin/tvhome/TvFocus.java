package com.gathin.tvhome;

import android.view.KeyEvent;
import android.view.View;

/** D-pad Up and Down leave a text field or a slider. Left and Right stay with it. */
final class TvFocus {
    private TvFocus() {}

    static void vertical(View view) {
        view.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int dir = key == KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN
                    : key == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : 0;
            if (dir == 0) return false;
            View next = v.focusSearch(dir);
            if (next == null || next == v) return false;
            next.requestFocus();
            return true;
        });
    }
}
