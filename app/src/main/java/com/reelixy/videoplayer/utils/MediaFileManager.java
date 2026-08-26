package com.reelixy.videoplayer.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.annotation.RequiresApi;

import com.reelixy.videoplayer.models.Folder;
import com.reelixy.videoplayer.models.Video;

import java.io.File;

/** Safe MediaStore/file operations used by the library management UI. */
public final class MediaFileManager {
    private MediaFileManager() {}

    public static String sanitizeName(String requested, String originalName) {
        String name = requested == null ? "" : requested.trim();
        name = name.replace('/', '_').replace('\\', '_');
        while (name.endsWith(".")) name = name.substring(0, name.length() - 1);
        if (name.isEmpty()) throw new IllegalArgumentException("Name cannot be empty");
        String extension = "";
        int dot = originalName == null ? -1 : originalName.lastIndexOf('.');
        if (dot > 0) extension = originalName.substring(dot);
        if (!extension.isEmpty() && !name.toLowerCase(java.util.Locale.US).endsWith(extension.toLowerCase(java.util.Locale.US))) {
            name += extension;
        }
        return name;
    }

    public static void renameVideo(Context context, Video video, String requestedName) {
        String newName = sanitizeName(requestedName, video.getTitle());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, newName);
            int updated = context.getContentResolver().update(Uri.parse(video.getContentUri()), values, null, null);
            if (updated <= 0) throw new IllegalStateException("Unable to rename video");
            return;
        }
        File source = new File(video.getFilePath());
        File target = new File(source.getParentFile(), newName);
        if (!source.renameTo(target)) throw new IllegalStateException("Unable to rename video");
    }

    public static void deleteVideo(Context context, Video video) {
        ContentResolver resolver = context.getContentResolver();
        int deleted = resolver.delete(Uri.parse(video.getContentUri()), null, null);
        if (deleted > 0) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            File file = new File(video.getFilePath());
            if (file.exists() && file.delete()) return;
        }
        throw new IllegalStateException("Unable to delete video");
    }

    public static void moveVideo(Context context, Video video, String destinationFolderPath) {
        if (destinationFolderPath == null || destinationFolderPath.trim().isEmpty())
            throw new IllegalArgumentException("Destination folder is invalid");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String relative = normalizeRelativePath(destinationFolderPath);
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.RELATIVE_PATH, relative);
            int updated = context.getContentResolver().update(Uri.parse(video.getContentUri()), values, null, null);
            if (updated <= 0) throw new IllegalStateException("Unable to move video");
            return;
        }
        File source = new File(video.getFilePath());
        File targetDir = new File(destinationFolderPath);
        if (!targetDir.exists() && !targetDir.mkdirs()) throw new IllegalStateException("Unable to create destination folder");
        File target = new File(targetDir, source.getName());
        if (!source.renameTo(target)) throw new IllegalStateException("Unable to move video");
    }

    public static void renameFolder(Context context, Folder folder, String requestedName) {
        String newName = requestedName == null ? "" : requestedName.trim().replace('/', '_').replace('\\', '_');
        while (newName.endsWith(".")) newName = newName.substring(0, newName.length() - 1);
        if (newName.isEmpty()) throw new IllegalArgumentException("Folder name cannot be empty");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String oldRelative = normalizeRelativePath(folder.getPath());
            String parent = parentRelativePath(oldRelative);
            String newRelative = parent + newName + "/";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.RELATIVE_PATH, newRelative);
            int updated = context.getContentResolver().update(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values,
                    MediaStore.Video.Media.RELATIVE_PATH + "=?",
                    new String[]{oldRelative});
            if (updated <= 0) throw new IllegalStateException("No videos were moved");
            return;
        }
        File source = new File(folder.getPath());
        File target = new File(source.getParentFile(), newName);
        if (!source.renameTo(target)) throw new IllegalStateException("Unable to rename folder");
    }

    public static void deleteFolder(Context context, Folder folder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String relative = normalizeRelativePath(folder.getPath());
            int deleted = context.getContentResolver().delete(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media.RELATIVE_PATH + "=?",
                    new String[]{relative});
            if (deleted <= 0) throw new IllegalStateException("No videos were deleted");
            return;
        }
        File dir = new File(folder.getPath());
        if (!deleteRecursively(dir)) throw new IllegalStateException("Unable to delete folder");
    }

    /** Returns the app's stable-ish identity after a rename operation. */
    public static String identityAfterRename(Video video, String requestedName) {
        String name = sanitizeName(requestedName, video.getTitle());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && video.getRelativePath() != null && !video.getRelativePath().isEmpty()) {
            String base = "/storage/emulated/0/" + normalizeRelativePath(video.getRelativePath());
            return base + name;
        }
        File source = new File(video.getFilePath());
        File parent = source.getParentFile();
        return parent == null ? name : new File(parent, name).getPath();
    }

    /** Returns the app's stable-ish identity after a move operation. */
    public static String identityAfterMove(Video video, String destinationFolderPath) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return "/storage/emulated/0/" + normalizeRelativePath(destinationFolderPath) + video.getTitle();
        }
        return new File(destinationFolderPath, video.getTitle()).getPath();
    }

    public static String folderIdentity(String folderPath) {
        if (folderPath == null) return "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return "/storage/emulated/0/" + normalizeRelativePath(folderPath);
        return folderPath.endsWith(File.separator) ? folderPath : folderPath + File.separator;
    }

    private static String normalizeRelativePath(String value) {
        String path = value == null ? "" : value.trim().replace('\\', '/');
        while (path.startsWith("/")) path = path.substring(1);
        return path.endsWith("/") ? path : path + "/";
    }

    private static String parentRelativePath(String value) {
        String normalized = normalizeRelativePath(value);
        int slash = normalized.lastIndexOf('/', normalized.length() - 2);
        return slash < 0 ? "" : normalized.substring(0, slash + 1);
    }

    private static boolean deleteRecursively(File file) {
        if (!file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) if (!deleteRecursively(child)) return false;
        }
        return file.delete();
    }
}
