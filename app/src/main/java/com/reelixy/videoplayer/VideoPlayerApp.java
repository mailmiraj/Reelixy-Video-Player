package com.reelixy.videoplayer;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.reelixy.videoplayer.utils.PreferenceUtils;

public class VideoPlayerApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        applySavedTheme();
    }

    private void applySavedTheme() {
        // 0 = system default, 1 = light, 2 = dark
        int mode = PreferenceUtils.getInt(this, PreferenceUtils.KEY_THEME_MODE, 0);
        switch (mode) {
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
