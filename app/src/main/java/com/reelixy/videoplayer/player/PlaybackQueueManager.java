package com.reelixy.videoplayer.player;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Owns queue discovery and navigation state. Activity remains responsible for UI. */
public final class PlaybackQueueManager {
    public static final int MAX_UP_NEXT_ITEMS = 8;
    private final List<Item> items = new ArrayList<>();
    private int currentIndex = -1;

    public synchronized void replace(List<Item> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        currentIndex = -1;
    }
    public synchronized List<Item> snapshot() { return new ArrayList<>(items); }
    public synchronized boolean isEmpty() { return items.isEmpty(); }
    public synchronized int size() { return items.size(); }
    public synchronized int getCurrentIndex() { return currentIndex; }
    public synchronized Item get(int index) { return index >= 0 && index < items.size() ? items.get(index) : null; }
    public synchronized Item peekNext() { return get(currentIndex + 1); }
    public synchronized Item advance() {
        int next = currentIndex + 1;
        if (next >= items.size()) return null;
        currentIndex = next;
        return items.get(currentIndex);
    }
    public synchronized void select(int index) { currentIndex = Math.max(-1, Math.min(index, items.size() - 1)); }

    /** Move a later item to the next playback slot without disturbing the current item. */
    public synchronized void moveToNext(int index) {
        if (index <= currentIndex || index >= items.size()) return;
        Item item = items.remove(index);
        items.add(Math.min(currentIndex + 1, items.size()), item);
    }

    /** Shuffle only the upcoming portion of the queue; the current item remains stable. */
    public synchronized void shuffleUpcoming() {
        int from = Math.max(0, currentIndex + 1);
        if (from >= items.size() - 1) return;
        List<Item> upcoming = new ArrayList<>(items.subList(from, items.size()));
        Collections.shuffle(upcoming);
        for (int i = 0; i < upcoming.size(); i++) items.set(from + i, upcoming.get(i));
    }

    public static List<Item> discoverUpNext(ContentResolver resolver, String currentUri) {
        List<Item> result = new ArrayList<>();
        if (resolver == null || currentUri == null) return result;
        String[] projection = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA, MediaStore.Video.Media.RELATIVE_PATH,
                MediaStore.Video.Media.DURATION};
        try (Cursor cursor = resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
                null, null, MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (cursor == null) return result;
            int idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID);
            int nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME);
            int dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
            int relCol = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH);
            int durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
            boolean currentFound = false;
            while (cursor.moveToNext()) {
                Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idCol));
                String uriString = uri.toString();
                if (!currentFound && uriString.equals(currentUri)) { currentFound = true; continue; }
                if (!currentFound) continue;
                String name = nameCol >= 0 ? cursor.getString(nameCol) : "Untitled";
                if (name == null) name = "Untitled";
                String path = dataCol >= 0 ? cursor.getString(dataCol) : null;
                if (path == null && relCol >= 0) {
                    String rel = cursor.getString(relCol);
                    path = rel == null ? uriString : "/storage/emulated/0/" + rel + name;
                }
                if (path == null) path = uriString;
                long duration = durationCol >= 0 ? cursor.getLong(durationCol) : 0L;
                result.add(new Item(uriString, path, name, duration));
                if (result.size() >= MAX_UP_NEXT_ITEMS) break;
            }
        } catch (Exception ignored) { }
        return result;
    }

    public static final class Item {
        public final String uri, path, title;
        public final long durationMs;
        public Item(String uri, String path, String title, long durationMs) {
            this.uri = uri; this.path = path; this.title = title; this.durationMs = durationMs;
        }
    }
}
