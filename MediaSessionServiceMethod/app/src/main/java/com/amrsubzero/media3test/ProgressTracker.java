package com.amrsubzero.media3test;

import android.os.Handler;

import androidx.media3.common.Player;

public class ProgressTracker implements Runnable {

    public interface PositionListener{
        void progress(long position);
    }

    private final Player player;
    private final int interval;
    private final Handler handler;
    private final PositionListener positionListener;

    public ProgressTracker(Player player, int interval, PositionListener positionListener) {
        this.player = player;
        this.interval = interval;
        this.positionListener = positionListener;
        handler = new Handler();
        handler.post(this);
    }

    public void run() {
        long position = player.getCurrentPosition();
        positionListener.progress(position);
        handler.postDelayed(this, interval);
    }

    public void purgeHandler() {
        handler.removeCallbacks(this);
    }
}