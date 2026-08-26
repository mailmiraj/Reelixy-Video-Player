package com.reelixy.videoplayer.models;

/**
 * Represents a single video file discovered on the device via MediaStore.
 * This is a plain in-memory model — it is not persisted directly; Room only
 * stores references (favorites, history, playlist entries) keyed by {@link #id}.
 */
public class Video {

    private final long id;          // MediaStore _id
    private final String title;
    private final String filePath;  // absolute file system path
    private final String contentUri; // content:// URI string, used for playback
    private final long durationMs;
    private final long sizeBytes;
    private final int width;
    private final int height;
    private final long dateAddedSeconds;
    private final String folderName;
    private final String relativePath;
    private final String mimeType;

    public Video(long id, String title, String filePath, String contentUri, long durationMs,
                 long sizeBytes, int width, int height, long dateAddedSeconds,
                 String folderName, String relativePath, String mimeType) {
        this.id = id;
        this.title = title;
        this.filePath = filePath;
        this.contentUri = contentUri;
        this.durationMs = durationMs;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.dateAddedSeconds = dateAddedSeconds;
        this.folderName = folderName;
        this.relativePath = relativePath;
        this.mimeType = mimeType;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getFilePath() { return filePath; }
    public String getContentUri() { return contentUri; }
    public long getDurationMs() { return durationMs; }
    public long getSizeBytes() { return sizeBytes; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getDateAddedSeconds() { return dateAddedSeconds; }
    public String getFolderName() { return folderName; }
    public String getRelativePath() { return relativePath; }
    public String getMimeType() { return mimeType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Video)) return false;
        return id == ((Video) o).id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
