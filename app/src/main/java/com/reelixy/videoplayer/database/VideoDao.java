package com.reelixy.videoplayer.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain SQLite-backed data access, mirroring the shape of a Room DAO
 * (same method names/signatures used throughout the app) but implemented
 * directly against SQLiteOpenHelper — see DatabaseHelper for why.
 */
public class VideoDao {

    private final DatabaseHelper helper;

    VideoDao(DatabaseHelper helper) {
        this.helper = helper;
    }

    // ---- Favorites ----

    public void addFavorite(FavoriteEntity favorite) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("filePath", favorite.filePath);
        values.put("addedAtMillis", favorite.addedAtMillis);
        db.insertWithOnConflict(DatabaseHelper.TABLE_FAVORITES, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void removeFavorite(String filePath) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete(DatabaseHelper.TABLE_FAVORITES, "filePath = ?", new String[]{filePath});
    }

    public boolean isFavorite(String filePath) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_FAVORITES,
                new String[]{"filePath"}, "filePath = ?", new String[]{filePath},
                null, null, null)) {
            return cursor.getCount() > 0;
        }
    }

    public List<FavoriteEntity> getFavoritesSync() {
        List<FavoriteEntity> result = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_FAVORITES, null, null, null,
                null, null, "addedAtMillis DESC")) {
            int pathCol = cursor.getColumnIndexOrThrow("filePath");
            int addedCol = cursor.getColumnIndexOrThrow("addedAtMillis");
            while (cursor.moveToNext()) {
                result.add(new FavoriteEntity(cursor.getString(pathCol), cursor.getLong(addedCol)));
            }
        }
        return result;
    }

    // ---- Playback history ----

    public void upsertHistory(PlaybackHistoryEntity entity) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("filePath", entity.filePath);
        values.put("title", entity.title);
        values.put("lastPositionMs", entity.lastPositionMs);
        values.put("durationMs", entity.durationMs);
        values.put("lastPlayedAtMillis", entity.lastPlayedAtMillis);
        values.put("completed", entity.completed ? 1 : 0);
        db.insertWithOnConflict(DatabaseHelper.TABLE_HISTORY, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<PlaybackHistoryEntity> getContinueWatchingSync() {
        List<PlaybackHistoryEntity> result = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_HISTORY, null, "completed = 0",
                null, null, null, "lastPlayedAtMillis DESC", "20")) {
            while (cursor.moveToNext()) {
                result.add(historyFromCursor(cursor));
            }
        }
        return result;
    }

    public PlaybackHistoryEntity getHistoryForVideo(String filePath) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_HISTORY, null, "filePath = ?",
                new String[]{filePath}, null, null, null, "1")) {
            if (cursor.moveToFirst()) {
                return historyFromCursor(cursor);
            }
            return null;
        }
    }

    public List<PlaybackHistoryEntity> getRecentlyPlayedSync() {
        List<PlaybackHistoryEntity> result = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_HISTORY, null, null, null,
                null, null, "lastPlayedAtMillis DESC", "50")) {
            while (cursor.moveToNext()) {
                result.add(historyFromCursor(cursor));
            }
        }
        return result;
    }

    private PlaybackHistoryEntity historyFromCursor(Cursor cursor) {
        return new PlaybackHistoryEntity(
                cursor.getString(cursor.getColumnIndexOrThrow("filePath")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getLong(cursor.getColumnIndexOrThrow("lastPositionMs")),
                cursor.getLong(cursor.getColumnIndexOrThrow("durationMs")),
                cursor.getLong(cursor.getColumnIndexOrThrow("lastPlayedAtMillis")),
                cursor.getInt(cursor.getColumnIndexOrThrow("completed")) != 0);
    }

    // ---- Playlists ----

    public long createPlaylist(PlaylistEntity playlist) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", playlist.name);
        values.put("createdAtMillis", playlist.createdAtMillis);
        return db.insert(DatabaseHelper.TABLE_PLAYLISTS, null, values);
    }

    public void deletePlaylist(long playlistId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete(DatabaseHelper.TABLE_PLAYLISTS, "id = ?", new String[]{String.valueOf(playlistId)});
        db.delete(DatabaseHelper.TABLE_PLAYLIST_VIDEOS, "playlistId = ?", new String[]{String.valueOf(playlistId)});
    }

    public void renamePlaylist(long playlistId, String newName) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", newName);
        db.update(DatabaseHelper.TABLE_PLAYLISTS, values, "id = ?", new String[]{String.valueOf(playlistId)});
    }

    public List<PlaylistEntity> getPlaylistsSync() {
        List<PlaylistEntity> result = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_PLAYLISTS, null, null, null,
                null, null, "createdAtMillis DESC")) {
            while (cursor.moveToNext()) {
                result.add(playlistFromCursor(cursor));
            }
        }
        return result;
    }

    public PlaylistEntity getPlaylistSync(long playlistId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_PLAYLISTS, null, "id = ?",
                new String[]{String.valueOf(playlistId)}, null, null, null, "1")) {
            if (cursor.moveToFirst()) {
                return playlistFromCursor(cursor);
            }
            return null;
        }
    }

    private PlaylistEntity playlistFromCursor(Cursor cursor) {
        return new PlaylistEntity(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                cursor.getLong(cursor.getColumnIndexOrThrow("createdAtMillis")));
    }

    public void addVideoToPlaylist(PlaylistVideoEntity entity) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("playlistId", entity.playlistId);
        values.put("filePath", entity.filePath);
        values.put("sortOrder", entity.sortOrder);
        db.insertWithOnConflict(DatabaseHelper.TABLE_PLAYLIST_VIDEOS, null, values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void removeVideoFromPlaylist(long playlistId, String filePath) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete(DatabaseHelper.TABLE_PLAYLIST_VIDEOS, "playlistId = ? AND filePath = ?",
                new String[]{String.valueOf(playlistId), filePath});
    }

    public List<PlaylistVideoEntity> getPlaylistVideosSync(long playlistId) {
        List<PlaylistVideoEntity> result = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_PLAYLIST_VIDEOS, null, "playlistId = ?",
                new String[]{String.valueOf(playlistId)}, null, null, "sortOrder ASC")) {
            int idCol = cursor.getColumnIndexOrThrow("id");
            int playlistIdCol = cursor.getColumnIndexOrThrow("playlistId");
            int pathCol = cursor.getColumnIndexOrThrow("filePath");
            int sortCol = cursor.getColumnIndexOrThrow("sortOrder");
            while (cursor.moveToNext()) {
                PlaylistVideoEntity entity = new PlaylistVideoEntity(
                        cursor.getLong(playlistIdCol), cursor.getString(pathCol), cursor.getInt(sortCol));
                entity.id = cursor.getLong(idCol);
                result.add(entity);
            }
        }
        return result;
    }

    public int getPlaylistVideoCount(long playlistId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_PLAYLIST_VIDEOS, new String[]{"COUNT(*)"},
                "playlistId = ?", new String[]{String.valueOf(playlistId)}, null, null, null)) {
            if (cursor.moveToFirst()) return cursor.getInt(0);
            return 0;
        }
    }

    public void updatePlaylistVideoOrder(long playlistId, java.util.List<String> filePaths) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < filePaths.size(); i++) {
                ContentValues values = new ContentValues();
                values.put("sortOrder", i);
                db.update(DatabaseHelper.TABLE_PLAYLIST_VIDEOS, values,
                        "playlistId = ? AND filePath = ?",
                        new String[]{String.valueOf(playlistId), filePaths.get(i)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
    // ---- Bookmarks ----

    public void addBookmark(String filePath, long positionMs, long createdAtMillis) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("filePath", filePath);
        values.put("positionMs", Math.max(0L, positionMs));
        values.put("createdAtMillis", createdAtMillis);
        db.insertWithOnConflict(DatabaseHelper.TABLE_BOOKMARKS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<BookmarkEntity> getBookmarksSync(String filePath) {
        List<BookmarkEntity> result = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_BOOKMARKS, null, "filePath = ?",
                new String[]{filePath}, null, null, "positionMs ASC")) {
            int idCol = cursor.getColumnIndexOrThrow("id");
            int pathCol = cursor.getColumnIndexOrThrow("filePath");
            int posCol = cursor.getColumnIndexOrThrow("positionMs");
            int createdCol = cursor.getColumnIndexOrThrow("createdAtMillis");
            while (cursor.moveToNext()) {
                result.add(new BookmarkEntity(cursor.getLong(idCol), cursor.getString(pathCol),
                        cursor.getLong(posCol), cursor.getLong(createdCol)));
            }
        }
        return result;
    }

    public void removeBookmark(long id) {
        helper.getWritableDatabase().delete(DatabaseHelper.TABLE_BOOKMARKS, "id = ?",
                new String[]{String.valueOf(id)});
    }

    // ---- Media identity reconciliation ----

    /**
     * Migrates all app-owned references from an old media identity to a new one.
     * Kept in one transaction so rename/move operations cannot partially migrate state.
     */
    public void migrateVideoReferences(String oldPath, String newPath) {
        if (oldPath == null || newPath == null || oldPath.equals(newPath)) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            mergeFavorite(db, oldPath, newPath);
            mergeHistory(db, oldPath, newPath);
            mergeBookmarks(db, oldPath, newPath);
            mergePreferences(db, oldPath, newPath);
            mergePlaylistMembership(db, oldPath, newPath);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Removes every database reference owned by the given media identity. */
    public void removeVideoReferences(String filePath) {
        if (filePath == null) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(DatabaseHelper.TABLE_FAVORITES, "filePath = ?", new String[]{filePath});
            db.delete(DatabaseHelper.TABLE_HISTORY, "filePath = ?", new String[]{filePath});
            db.delete(DatabaseHelper.TABLE_BOOKMARKS, "filePath = ?", new String[]{filePath});
            db.delete(DatabaseHelper.TABLE_VIDEO_PREFERENCES, "filePath = ?", new String[]{filePath});
            db.delete(DatabaseHelper.TABLE_PLAYLIST_VIDEOS, "filePath = ?", new String[]{filePath});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Migrates all video references whose identity starts with an old folder prefix. */
    public void migrateFolderReferences(String oldPrefix, String newPrefix) {
        migrateFolderReferencesInternal(oldPrefix, newPrefix, false);
    }

    /** Removes all video references whose identity starts with a folder prefix. */
    public void removeFolderReferences(String oldPrefix) {
        migrateFolderReferencesInternal(oldPrefix, null, true);
    }

    private void migrateFolderReferencesInternal(String oldPrefix, String newPrefix, boolean removeOnly) {
        if (oldPrefix == null || oldPrefix.isEmpty()) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
        String like = oldPrefix.endsWith("/") ? oldPrefix + "%" : oldPrefix + "/%";
        collectPaths(db, DatabaseHelper.TABLE_FAVORITES, like, paths);
        collectPaths(db, DatabaseHelper.TABLE_HISTORY, like, paths);
        collectPaths(db, DatabaseHelper.TABLE_BOOKMARKS, like, paths);
        collectPaths(db, DatabaseHelper.TABLE_VIDEO_PREFERENCES, like, paths);
        collectPaths(db, DatabaseHelper.TABLE_PLAYLIST_VIDEOS, like, paths);
        for (String oldPath : paths) {
            if (removeOnly) removeVideoReferences(oldPath);
            else migrateVideoReferences(oldPath, newPrefix + oldPath.substring(oldPrefix.length()));
        }
    }

    private void collectPaths(SQLiteDatabase db, String table, String like, java.util.Set<String> out) {
        try (Cursor c = db.query(true, table, new String[]{"filePath"}, "filePath LIKE ?",
                new String[]{like}, null, null, null, null)) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
    }

    private void mergeFavorite(SQLiteDatabase db, String oldPath, String newPath) {
        try (Cursor c = db.query(DatabaseHelper.TABLE_FAVORITES, new String[]{"addedAtMillis"}, "filePath = ?",
                new String[]{oldPath}, null, null, null, "1")) {
            if (c.moveToFirst()) {
                ContentValues v = new ContentValues();
                v.put("filePath", newPath);
                v.put("addedAtMillis", c.getLong(0));
                db.insertWithOnConflict(DatabaseHelper.TABLE_FAVORITES, null, v, SQLiteDatabase.CONFLICT_IGNORE);
                db.delete(DatabaseHelper.TABLE_FAVORITES, "filePath = ?", new String[]{oldPath});
            }
        }
    }

    private void mergeHistory(SQLiteDatabase db, String oldPath, String newPath) {
        try (Cursor c = db.query(DatabaseHelper.TABLE_HISTORY, null, "filePath = ?", new String[]{oldPath}, null, null, null, "1")) {
            if (c.moveToFirst()) {
                ContentValues v = new ContentValues();
                v.put("filePath", newPath);
                v.put("title", c.getString(c.getColumnIndexOrThrow("title")));
                v.put("lastPositionMs", c.getLong(c.getColumnIndexOrThrow("lastPositionMs")));
                v.put("durationMs", c.getLong(c.getColumnIndexOrThrow("durationMs")));
                v.put("lastPlayedAtMillis", c.getLong(c.getColumnIndexOrThrow("lastPlayedAtMillis")));
                v.put("completed", c.getInt(c.getColumnIndexOrThrow("completed")));
                db.insertWithOnConflict(DatabaseHelper.TABLE_HISTORY, null, v, SQLiteDatabase.CONFLICT_REPLACE);
                db.delete(DatabaseHelper.TABLE_HISTORY, "filePath = ?", new String[]{oldPath});
            }
        }
    }

    private void mergeBookmarks(SQLiteDatabase db, String oldPath, String newPath) {
        try (Cursor c = db.query(DatabaseHelper.TABLE_BOOKMARKS,
                new String[]{"positionMs", "createdAtMillis"}, "filePath = ?", new String[]{oldPath},
                null, null, "positionMs ASC")) {
            while (c.moveToNext()) {
                ContentValues v = new ContentValues();
                v.put("filePath", newPath);
                v.put("positionMs", c.getLong(0));
                v.put("createdAtMillis", c.getLong(1));
                db.insertWithOnConflict(DatabaseHelper.TABLE_BOOKMARKS, null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
        db.delete(DatabaseHelper.TABLE_BOOKMARKS, "filePath = ?", new String[]{oldPath});
    }

    private void mergePreferences(SQLiteDatabase db, String oldPath, String newPath) {
        try (Cursor c = db.query(DatabaseHelper.TABLE_VIDEO_PREFERENCES, null, "filePath = ?", new String[]{oldPath}, null, null, null, "1")) {
            if (c.moveToFirst()) {
                ContentValues v = new ContentValues();
                v.put("filePath", newPath);
                v.put("speed", c.getFloat(c.getColumnIndexOrThrow("speed")));
                v.put("zoom", c.getFloat(c.getColumnIndexOrThrow("zoom")));
                v.put("updatedAtMillis", c.getLong(c.getColumnIndexOrThrow("updatedAtMillis")));
                db.insertWithOnConflict(DatabaseHelper.TABLE_VIDEO_PREFERENCES, null, v, SQLiteDatabase.CONFLICT_REPLACE);
                db.delete(DatabaseHelper.TABLE_VIDEO_PREFERENCES, "filePath = ?", new String[]{oldPath});
            }
        }
    }

    private void mergePlaylistMembership(SQLiteDatabase db, String oldPath, String newPath) {
        try (Cursor c = db.query(DatabaseHelper.TABLE_PLAYLIST_VIDEOS,
                new String[]{"playlistId", "sortOrder"}, "filePath = ?", new String[]{oldPath}, null, null, null)) {
            java.util.List<long[]> memberships = new java.util.ArrayList<>();
            while (c.moveToNext()) memberships.add(new long[]{c.getLong(0), c.getLong(1)});
            for (long[] row : memberships) {
                ContentValues v = new ContentValues();
                v.put("playlistId", row[0]);
                v.put("filePath", newPath);
                v.put("sortOrder", row[1]);
                db.insertWithOnConflict(DatabaseHelper.TABLE_PLAYLIST_VIDEOS, null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.delete(DatabaseHelper.TABLE_PLAYLIST_VIDEOS, "filePath = ?", new String[]{oldPath});
        }
    }

    // ---- Per-video preferences ----

    public VideoPreferenceEntity getVideoPreferenceSync(String filePath) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.query(DatabaseHelper.TABLE_VIDEO_PREFERENCES, null, "filePath = ?",
                new String[]{filePath}, null, null, null, "1")) {
            if (cursor.moveToFirst()) {
                return new VideoPreferenceEntity(
                        cursor.getString(cursor.getColumnIndexOrThrow("filePath")),
                        cursor.getFloat(cursor.getColumnIndexOrThrow("speed")),
                        cursor.getFloat(cursor.getColumnIndexOrThrow("zoom")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("updatedAtMillis")));
            }
        }
        return null;
    }

    public void upsertVideoPreference(String filePath, float speed, float zoom, long updatedAtMillis) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("filePath", filePath);
        values.put("speed", speed);
        values.put("zoom", zoom);
        values.put("updatedAtMillis", updatedAtMillis);
        db.insertWithOnConflict(DatabaseHelper.TABLE_VIDEO_PREFERENCES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

}
