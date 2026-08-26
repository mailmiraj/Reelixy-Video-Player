package com.reelixy.videoplayer.database;

public final class VideoPreferenceEntity {
    public final String filePath;
    public final float speed;
    public final float zoom;
    public final long updatedAtMillis;

    public VideoPreferenceEntity(String filePath, float speed, float zoom, long updatedAtMillis) {
        this.filePath = filePath;
        this.speed = speed;
        this.zoom = zoom;
        this.updatedAtMillis = updatedAtMillis;
    }
}
