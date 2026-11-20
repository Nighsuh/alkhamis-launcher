package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

/** Full-screen look at the shrine dream. Any key closes it. */
public final class DreamPreviewActivity extends Activity {
    private DreamBoard board;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.dream_shrine);
        board = new DreamBoard(this, getWindow().getDecorView());
        board.start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Finish after this event is delivered. Finishing inside the dispatch blocks the focus-loss
        // event that follows and the system reports the dream as not responding.
        if (event.getAction() == KeyEvent.ACTION_UP && !isFinishing()) {
            getWindow().getDecorView().post(this::finish);
        }
        return true;
    }

    @Override
    protected void onStop() {
        if (board != null) board.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (board != null) board.stop();
        board = null;
        super.onDestroy();
    }
}
