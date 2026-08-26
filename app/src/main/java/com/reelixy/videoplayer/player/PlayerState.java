package com.reelixy.videoplayer.player;

/** Immutable-ish UI state holder for the player screen. Keep transitions in PlayerActivity. */
public final class PlayerState {
    public boolean controlsVisible = true;
    public boolean locked = false;
    public boolean fullscreen = false;
    public boolean seeking = false;
    public boolean focusMode = false;
    public boolean statsHudEnabled = false;
    public boolean autoNextHandled = false;
    public boolean upNextShownForVideo = false;
    public boolean destroyed = false;
    public boolean pauseDimEnabled = true;
    public float zoomScale = 1.0f;
    public float playbackSpeed = 1.0f;
    public float audioBoost = 1.0f;
    public long loopStartMs = -1L;
    public long loopEndMs = -1L;
    public int queueIndex = -1;
    public boolean shuffleEnabled = false;
    /** 0=off, 1=repeat one, 2=repeat all. */
    public int repeatMode = 0;

    public void resetForNewVideo() {
        controlsVisible = true;
        locked = false;
        seeking = false;
        focusMode = false;
        statsHudEnabled = false;
        autoNextHandled = false;
        upNextShownForVideo = false;
        loopStartMs = -1L;
        loopEndMs = -1L;
        queueIndex = -1;
        shuffleEnabled = false;
        repeatMode = 0;
        audioBoost = 1.0f;
    }
}
