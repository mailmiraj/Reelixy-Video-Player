package com.reelixy.videoplayer.utils;

public class MediaUtils {

    private MediaUtils() {}

    /** Best-effort common-format check, used only for lightweight UI hints. */
    public static boolean isLikelyVideoMime(String mimeType) {
        return mimeType != null && mimeType.startsWith("video/");
    }
}
