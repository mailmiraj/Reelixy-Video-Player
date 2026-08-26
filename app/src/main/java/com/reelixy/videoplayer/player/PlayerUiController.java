package com.reelixy.videoplayer.player;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

/** Controls visibility/focus presentation for the player overlay. */
public final class PlayerUiController {
    public interface Host {
        View controlsContainer();
        View gestureOverlay();
        View focusBadge();
        void onVisibilityChanged(boolean visible);
    }

    private final Host host;
    private final PlayerState state;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = this::hideNow;
    private long autoHideMs = 3000L;
    private boolean released;

    public PlayerUiController(Host host, PlayerState state) {
        this.host = host;
        this.state = state;
    }

    public void setAutoHideMs(long autoHideMs) {
        this.autoHideMs = Math.max(1000L, autoHideMs);
    }

    public void show(boolean scheduleHide) {
        if (released || state.destroyed || state.locked || state.focusMode) return;
        state.controlsVisible = true;
        host.controlsContainer().setVisibility(View.VISIBLE);
        if (host.gestureOverlay() != null) host.gestureOverlay().setVisibility(View.VISIBLE);
        host.onVisibilityChanged(true);
        cancelAutoHide();
        if (scheduleHide) handler.postDelayed(hideRunnable, autoHideMs);
    }

    public void hide() {
        cancelAutoHide();
        hideNow();
    }

    private void hideNow() {
        if (released || state.destroyed || state.locked || state.focusMode) return;
        state.controlsVisible = false;
        host.controlsContainer().setVisibility(View.GONE);
        host.onVisibilityChanged(false);
    }

    public void enterFocus() {
        if (released || state.destroyed) return;
        cancelAutoHide();
        state.focusMode = true;
        state.controlsVisible = false;
        host.controlsContainer().setVisibility(View.GONE);
        if (host.gestureOverlay() != null) host.gestureOverlay().setVisibility(View.VISIBLE);
        if (host.focusBadge() != null) host.focusBadge().setVisibility(View.VISIBLE);
        host.onVisibilityChanged(false);
    }

    public void exitFocus() {
        if (released || state.destroyed) return;
        state.focusMode = false;
        if (host.focusBadge() != null) host.focusBadge().setVisibility(View.GONE);
        show(true);
    }

    public void setLocked(boolean locked) {
        if (released || state.destroyed) return;
        state.locked = locked;
        cancelAutoHide();
        host.controlsContainer().setVisibility(locked ? View.GONE : View.VISIBLE);
        if (host.gestureOverlay() != null) host.gestureOverlay().setVisibility(locked ? View.GONE : View.VISIBLE);
        if (host.focusBadge() != null && locked) host.focusBadge().setVisibility(View.GONE);
        if (!locked && !state.focusMode) handler.postDelayed(hideRunnable, autoHideMs);
        host.onVisibilityChanged(!locked && !state.focusMode);
    }

    public void cancelAutoHide() {
        handler.removeCallbacks(hideRunnable);
    }

    public void release() {
        released = true;
        cancelAutoHide();
    }
}
