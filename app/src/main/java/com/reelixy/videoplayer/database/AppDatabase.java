package com.reelixy.videoplayer.database;

import android.content.Context;

/**
 * Thin facade kept so call sites everywhere else in the app (which do
 * AppDatabase.getInstance(context).videoDao()...) don't need to change.
 * Backed by a plain SQLiteOpenHelper — see DatabaseHelper for why this
 * is not Room.
 */
public class AppDatabase {

    private static volatile AppDatabase INSTANCE;

    private final VideoDao videoDao;

    private AppDatabase(Context context) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        this.videoDao = new VideoDao(helper);
    }

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AppDatabase(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public VideoDao videoDao() {
        return videoDao;
    }
}
