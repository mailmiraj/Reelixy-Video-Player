package com.reelixy.videoplayer.database;

/** A favorited video, keyed by its file path. Plain POJO — no Room. */
public class FavoriteEntity {
    public String filePath;
    public long addedAtMillis;

    public FavoriteEntity(String filePath, long addedAtMillis) {
        this.filePath = filePath;
        this.addedAtMillis = addedAtMillis;
    }
}
