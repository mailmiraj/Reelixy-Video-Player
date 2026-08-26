package com.reelixy.videoplayer.database;

/**
 * Tracks last playback position + last-played time for a video, used to
 * power "Continue Watching" and "Recently Played". Plain POJO — no Room.
 */
public class PlaybackHistoryEntity {
    public String filePath;
    public String title;
    public long lastPositionMs;
    public long durationMs;
    public long lastPlayedAtMillis;
    public boolean completed;

    public PlaybackHistoryEntity(String filePath, String title, long lastPositionMs,
                                  long durationMs, long lastPlayedAtMillis, boolean completed) {
        this.filePath = filePath;
        this.title = title;
        this.lastPositionMs = lastPositionMs;
        this.durationMs = durationMs;
        this.lastPlayedAtMillis = lastPlayedAtMillis;
        this.completed = completed;
    }
}
