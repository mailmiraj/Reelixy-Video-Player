package com.reelixy.videoplayer.utils;

import java.util.Locale;

public class FileUtils {

    private FileUtils() {}

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024, exp), unit);
    }

    public static String getFolderName(String filePath) {
        if (filePath == null) return "";
        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash <= 0) return "";
        String withoutFile = filePath.substring(0, lastSlash);
        int prevSlash = withoutFile.lastIndexOf('/');
        return prevSlash >= 0 ? withoutFile.substring(prevSlash + 1) : withoutFile;
    }

    public static String getParentPath(String filePath) {
        if (filePath == null) return "";
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash > 0 ? filePath.substring(0, lastSlash) : "/";
    }

    public static String getExtension(String filePath) {
        if (filePath == null) return "";
        int dot = filePath.lastIndexOf('.');
        return dot >= 0 ? filePath.substring(dot + 1).toUpperCase(Locale.US) : "";
    }
}
