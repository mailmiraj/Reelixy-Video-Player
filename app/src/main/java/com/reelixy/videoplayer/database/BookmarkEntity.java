package com.reelixy.videoplayer.database;

public final class BookmarkEntity {
    public final long id;
    public final String filePath;
    public final long positionMs;
    public final long createdAtMillis;

    public BookmarkEntity(long id, String filePath, long positionMs, long createdAtMillis) {
        this.id = id;
        this.filePath = filePath;
        this.positionMs = positionMs;
        this.createdAtMillis = createdAtMillis;
    }
}
