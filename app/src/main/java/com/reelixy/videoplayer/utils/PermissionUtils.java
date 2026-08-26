package com.reelixy.videoplayer.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

public class PermissionUtils {

    private PermissionUtils() {}

    /** Returns the correct runtime permission string for reading video media on this API level. */
    public static String videoPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_VIDEO;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public static boolean hasVideoPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, videoPermission())
                == PackageManager.PERMISSION_GRANTED;
    }
}
