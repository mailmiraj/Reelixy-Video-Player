package com.reelixy.videoplayer.database;

/** Plain POJO — no Room. id is 0 until the row is inserted. */
public class PlaylistEntity {
    public long id;
    public String name;
    public long createdAtMillis;

    public PlaylistEntity(String name, long createdAtMillis) {
        this.name = name;
        this.createdAtMillis = createdAtMillis;
    }

    public PlaylistEntity(long id, String name, long createdAtMillis) {
        this.id = id;
        this.name = name;
        this.createdAtMillis = createdAtMillis;
    }
}
