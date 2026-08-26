package com.reelixy.videoplayer.models;

public class Folder {
    private final String name;
    /** Relative MediaStore path on API 29+, absolute folder path on older Android. */
    private final String path;
    private final int videoCount;

    public Folder(String name, String path, int videoCount) {
        this.name = name;
        this.path = path;
        this.videoCount = videoCount;
    }

    public String getName() { return name; }
    public String getPath() { return path; }
    public int getVideoCount() { return videoCount; }
}
