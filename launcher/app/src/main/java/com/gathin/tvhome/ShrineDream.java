package com.gathin.tvhome;

import android.service.dreams.DreamService;

/** System screensaver: shrine clock, slideshow, or the current wallpaper. Any key wakes. */
public class ShrineDream extends DreamService {
    private DreamBoard board;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_shrine);
        board = new DreamBoard(this, getWindow().getDecorView());
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (board != null) board.start();
    }

    @Override
    public void onDreamingStopped() {
        if (board != null) board.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (board != null) board.stop();
        super.onDetachedFromWindow();
    }
}
