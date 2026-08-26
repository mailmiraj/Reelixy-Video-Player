package com.reelixy.videoplayer.managers;

import android.app.Activity;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.reelixy.videoplayer.utils.PermissionUtils;

/**
 * Wraps the modern ActivityResultLauncher permission flow so callers just
 * ask "do I have access" / "please request it" without touching the launcher API.
 * Must be constructed and registered in onCreate (before STARTED), per AndroidX rules.
 */
public class PermissionManager {

    public interface Callback {
        void onPermissionResult(boolean granted);
    }

    private final ActivityResultLauncher<String> launcher;
    private Callback pendingCallback;

    public PermissionManager(AppCompatActivity activity) {
        launcher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                (ActivityResultCallback<Boolean>) granted -> {
                    if (pendingCallback != null) {
                        pendingCallback.onPermissionResult(granted);
                        pendingCallback = null;
                    }
                });
    }

    public boolean hasPermission(Activity activity) {
        return PermissionUtils.hasVideoPermission(activity);
    }

    public void requestPermission(Callback callback) {
        pendingCallback = callback;
        launcher.launch(PermissionUtils.videoPermission());
    }
}
