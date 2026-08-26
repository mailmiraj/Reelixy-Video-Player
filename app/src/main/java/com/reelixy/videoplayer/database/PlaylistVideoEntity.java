package com.reelixy.videoplayer.database;

/** One (playlist, video) membership row, ordered by sortOrder. Plain POJO — no Room. */
public class PlaylistVideoEntity {
    public long id;
    public long playlistId;
    public String filePath;
    public int sortOrder;

    public PlaylistVideoEntity(long playlistId, String filePath, int sortOrder) {
        this.playlistId = playlistId;
        this.filePath = filePath;
        this.sortOrder = sortOrder;
    }
}
