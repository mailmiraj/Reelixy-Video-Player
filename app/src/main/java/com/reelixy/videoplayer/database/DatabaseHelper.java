package com.reelixy.videoplayer.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Plain SQLiteOpenHelper backing store — deliberately NOT Room.
 *
 * Room's annotation processor tries to open a real (JDBC-backed) SQLite
 * connection at *compile time* to verify @Query strings, via
 * org.xerial:sqlite-jdbc. That library's native binary does not load in
 * on-device build environments like AndroidIDE/Termux (missing glibc),
 * which crashes javac no matter which Room verification flags are set.
 * A hand-written SQLiteOpenHelper has zero annotation processing, so this
 * whole class of build failure is structurally impossible here.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "videoplayer.db";
    private static final int DB_VERSION = 3;

    public static final String TABLE_FAVORITES = "favorites";
    public static final String TABLE_HISTORY = "playback_history";
    public static final String TABLE_PLAYLISTS = "playlists";
    public static final String TABLE_PLAYLIST_VIDEOS = "playlist_videos";
    public static final String TABLE_BOOKMARKS = "bookmarks";
    public static final String TABLE_VIDEO_PREFERENCES = "video_preferences";

    private static volatile DatabaseHelper INSTANCE;

    public static DatabaseHelper getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (DatabaseHelper.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DatabaseHelper(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_FAVORITES + " (" +
                "filePath TEXT PRIMARY KEY NOT NULL, " +
                "addedAtMillis INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_HISTORY + " (" +
                "filePath TEXT PRIMARY KEY NOT NULL, " +
                "title TEXT, " +
                "lastPositionMs INTEGER NOT NULL, " +
                "durationMs INTEGER NOT NULL, " +
                "lastPlayedAtMillis INTEGER NOT NULL, " +
                "completed INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_PLAYLISTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "createdAtMillis INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_PLAYLIST_VIDEOS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "playlistId INTEGER NOT NULL, " +
                "filePath TEXT NOT NULL, " +
                "sortOrder INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_BOOKMARKS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "filePath TEXT NOT NULL, " +
                "positionMs INTEGER NOT NULL, " +
                "createdAtMillis INTEGER NOT NULL, " +
                "UNIQUE(filePath, positionMs))");
        db.execSQL("CREATE INDEX idx_bookmarks_filePath ON " + TABLE_BOOKMARKS + "(filePath)");

        db.execSQL("CREATE INDEX idx_history_recent ON " + TABLE_HISTORY + "(lastPlayedAtMillis DESC)");
        db.execSQL("CREATE INDEX idx_history_completed_recent ON " + TABLE_HISTORY + "(completed, lastPlayedAtMillis DESC)");
        db.execSQL("CREATE INDEX idx_playlist_videos_playlist_order ON " + TABLE_PLAYLIST_VIDEOS + "(playlistId, sortOrder)");
        db.execSQL("CREATE UNIQUE INDEX idx_playlist_videos_unique ON " + TABLE_PLAYLIST_VIDEOS + "(playlistId, filePath)");

        db.execSQL("CREATE TABLE " + TABLE_VIDEO_PREFERENCES + " (" +
                "filePath TEXT PRIMARY KEY NOT NULL, " +
                "speed REAL NOT NULL DEFAULT 1.0, " +
                "zoom REAL NOT NULL DEFAULT 1.0, " +
                "updatedAtMillis INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_BOOKMARKS + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "filePath TEXT NOT NULL, " +
                    "positionMs INTEGER NOT NULL, " +
                    "createdAtMillis INTEGER NOT NULL, " +
                    "UNIQUE(filePath, positionMs))");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookmarks_filePath ON " + TABLE_BOOKMARKS + "(filePath)");

            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_VIDEO_PREFERENCES + " (" +
                    "filePath TEXT PRIMARY KEY NOT NULL, " +
                    "speed REAL NOT NULL DEFAULT 1.0, " +
                    "zoom REAL NOT NULL DEFAULT 1.0, " +
                    "updatedAtMillis INTEGER NOT NULL)");
        }
        if (oldVersion < 3) {
            // Remove duplicate playlist entries before adding the unique membership index.
            db.execSQL("DELETE FROM " + TABLE_PLAYLIST_VIDEOS +
                    " WHERE id NOT IN (SELECT MIN(id) FROM " + TABLE_PLAYLIST_VIDEOS +
                    " GROUP BY playlistId, filePath)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_recent ON " + TABLE_HISTORY + "(lastPlayedAtMillis DESC)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_completed_recent ON " + TABLE_HISTORY + "(completed, lastPlayedAtMillis DESC)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_playlist_videos_playlist_order ON " + TABLE_PLAYLIST_VIDEOS + "(playlistId, sortOrder)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_playlist_videos_unique ON " + TABLE_PLAYLIST_VIDEOS + "(playlistId, filePath)");
        }
    }
}
