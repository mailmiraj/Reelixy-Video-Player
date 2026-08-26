package com.reelixy.videoplayer.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * Thin wrapper around SharedPreferences for all user-configurable settings
 * (Settings screen), plus small pieces of transient state like last theme choice.
 */
public class PreferenceUtils {

    private static final String PREFS_NAME = "video_player_prefs";

    // Keys
    public static final String KEY_RESUME_PLAYBACK = "resume_playback";
    public static final String KEY_AUTO_PLAY_NEXT = "auto_play_next";
    public static final String KEY_DEFAULT_SPEED = "default_speed";
    public static final String KEY_SKIP_DURATION_MS = "skip_duration_ms";
    public static final String KEY_AUTO_HIDE_CONTROLS_MS = "auto_hide_controls_ms";
    public static final String KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake";
    public static final String KEY_THEME_MODE = "theme_mode"; // 0=system,1=light,2=dark
    public static final String KEY_ZOOM_MODE = "zoom_mode";
    public static final String KEY_DEFAULT_ZOOM = "default_zoom";
    public static final String KEY_SUBTITLE_FONT_SIZE = "subtitle_font_size";
    public static final String KEY_PAUSE_DIM = "pause_dim";
    public static final String KEY_AUTO_PIP = "auto_pip";
    public static final String KEY_DEFAULT_AUDIO_BOOST = "default_audio_boost";
    public static final String KEY_DEFAULT_SUBTITLES = "default_subtitles";

    private PreferenceUtils() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean getBoolean(Context context, String key, boolean defaultValue) {
        return prefs(context).getBoolean(key, defaultValue);
    }

    public static void setBoolean(Context context, String key, boolean value) {
        prefs(context).edit().putBoolean(key, value).apply();
    }

    public static int getInt(Context context, String key, int defaultValue) {
        return prefs(context).getInt(key, defaultValue);
    }

    public static void setInt(Context context, String key, int value) {
        prefs(context).edit().putInt(key, value).apply();
    }

    public static float getFloat(Context context, String key, float defaultValue) {
        return prefs(context).getFloat(key, defaultValue);
    }

    public static void setFloat(Context context, String key, float value) {
        prefs(context).edit().putFloat(key, value).apply();
    }

    public static String getString(Context context, String key, String defaultValue) {
        return prefs(context).getString(key, defaultValue);
    }

    public static void setString(Context context, String key, String value) {
        prefs(context).edit().putString(key, value).apply();
    }

    public static Set<String> getStringSet(Context context, String key) {
        Set<String> set = prefs(context).getStringSet(key, null);
        return set == null ? new HashSet<>() : new HashSet<>(set);
    }

    public static void setStringSet(Context context, String key, Set<String> value) {
        prefs(context).edit().putStringSet(key, value == null ? new HashSet<>() : new HashSet<>(value)).apply();
    }
}
