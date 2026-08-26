package com.reelixy.videoplayer.player;

import com.reelixy.videoplayer.views.GestureOverlayView;

/**
 * Owns gesture wiring for the player surface. Keeps gesture lifecycle out of
 * PlayerActivity while the activity remains the domain callback target.
 */
public final class PlayerGestureController {
    private final GestureOverlayView overlay;
    private final GestureOverlayView.Listener listener;
    private boolean enabled = true;

    public PlayerGestureController(GestureOverlayView overlay, GestureOverlayView.Listener listener) {
        this.overlay = overlay;
        this.listener = listener;
        attach();
    }

    private void attach() {
        if (overlay == null) return;
        overlay.setListener(listener);
        overlay.setEnabled(enabled);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (overlay != null) {
            overlay.setEnabled(enabled);
            overlay.setVisibility(enabled ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void release() {
        if (overlay != null) {
            overlay.setListener(null);
            overlay.setEnabled(false);
        }
    }
}
