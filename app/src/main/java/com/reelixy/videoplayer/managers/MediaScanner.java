package com.reelixy.videoplayer.managers;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import com.reelixy.videoplayer.models.Video;
import com.reelixy.videoplayer.utils.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scans the device's video library via MediaStore on a background thread.
 * Never touch this from the main thread — call {@link #scanAsync}, results
 * are delivered back on the main thread via the callback.
 *
 * IMPORTANT (scoped storage): on API 29+, MediaStore.Video.Media.DATA (the
 * raw absolute file path) very often comes back null even with full media
 * permission granted — this is expected scoped-storage behavior, not a
 * permission bug. This scanner never discards a row just because DATA is
 * null: it falls back to RELATIVE_PATH + DISPLAY_NAME (available API 29+),
 * and as a last resort to the content:// URI itself, which is always
 * present. Every readable row is always kept.
 */
public class MediaScanner {

    private static final String TAG = "MediaScanner";

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private volatile boolean scanning;

    public interface ScanCallback {
        void onScanComplete(List<Video> videos);
        void onScanFailed(Exception e);
        default void onScanProgress(int scanned, int total) {}
    }

    public MediaScanner(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void scanAsync(ScanCallback callback) {
        if (scanning) return;
        cancelRequested.set(false);
        scanning = true;
        executor.execute(() -> {
            try {
                List<Video> videos = scanSync(callback);
                if (cancelRequested.get()) {
                    mainHandler.post(() -> callback.onScanFailed(new java.util.concurrent.CancellationException("Scan cancelled")));
                } else {
                    Log.d(TAG, "Scan found " + videos.size() + " video(s)");
                    mainHandler.post(() -> callback.onScanComplete(videos));
                }
            } catch (Exception e) {
                Log.e(TAG, "Media scan failed", e);
                mainHandler.post(() -> callback.onScanFailed(e));
            } finally {
                scanning = false;
            }
        });
    }

    public void cancelScan() {
        cancelRequested.set(true);
    }

    public boolean isScanning() {
        return scanning;
    }

    public void shutdown() {
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private List<Video> scanSync(ScanCallback callback) {
        List<Video> results = new ArrayList<>();

        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        boolean hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;

        List<String> projectionList = new ArrayList<>(java.util.Arrays.asList(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.MIME_TYPE
        ));
        if (hasRelativePath) {
            projectionList.add(MediaStore.Video.Media.RELATIVE_PATH);
        }
        String[] projection = projectionList.toArray(new String[0]);

        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = appContext.getContentResolver().query(
                collection, projection, null, null, sortOrder)) {

            if (cursor == null) {
                Log.w(TAG, "MediaStore query returned a null cursor");
                return results;
            }

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            int durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
            int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
            int widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH);
            int heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT);
            int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
            int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE);
            int relPathCol = hasRelativePath
                    ? cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                    : -1;

            Log.d(TAG, "MediaStore cursor row count: " + cursor.getCount());

            int total = cursor.getCount();
            int scanned = 0;
            while (cursor.moveToNext()) {
                if (cancelRequested.get()) break;
                long id = cursor.getLong(idCol);
                String name = cursor.getString(nameCol);
                if (name == null) name = "Untitled";

                String rawData = cursor.getString(dataCol);
                String relativePath = relPathCol >= 0 ? cursor.getString(relPathCol) : null;

                Uri contentUri = ContentUris.withAppendedId(collection, id);

                // Resolve a display/identifier "path" with graceful fallbacks —
                // never skip a row just because DATA is null (scoped storage).
                String resolvedPath;
                String folderName;
                if (rawData != null) {
                    resolvedPath = rawData;
                    folderName = FileUtils.getFolderName(rawData);
                } else if (relativePath != null) {
                    // e.g. "Movies/Anime/" + "Episode 01.mp4" -> a synthetic
                    // but stable, human-readable path for display/grouping.
                    resolvedPath = "/storage/emulated/0/" + relativePath + name;
                    String trimmed = relativePath.endsWith("/")
                            ? relativePath.substring(0, relativePath.length() - 1)
                            : relativePath;
                    int lastSlash = trimmed.lastIndexOf('/');
                    folderName = lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
                    if (folderName.isEmpty()) folderName = "Videos";
                } else {
                    // Last resort: always non-null, guarantees the row is kept.
                    resolvedPath = contentUri.toString();
                    folderName = "Videos";
                }

                Video video = new Video(
                        id,
                        name,
                        resolvedPath,
                        contentUri.toString(),
                        durationCol >= 0 ? cursor.getLong(durationCol) : 0L,
                        cursor.getLong(sizeCol),
                        cursor.getInt(widthCol),
                        cursor.getInt(heightCol),
                        cursor.getLong(dateCol),
                        folderName,
                        relativePath,
                        cursor.getString(mimeCol)
                );
                results.add(video);
                scanned++;
                if ((scanned % 32) == 0 || scanned == total) {
                    final int progress = scanned;
                    mainHandler.post(() -> callback.onScanProgress(progress, total));
                }
            }
        }

        return results;
    }
}
