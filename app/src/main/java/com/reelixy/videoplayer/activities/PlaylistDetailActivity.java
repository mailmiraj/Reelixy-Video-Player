package com.reelixy.videoplayer.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.reelixy.videoplayer.R;
import com.reelixy.videoplayer.adapters.PlaylistAdapter;
import com.reelixy.videoplayer.database.AppDatabase;
import com.reelixy.videoplayer.database.PlaylistEntity;
import com.reelixy.videoplayer.database.PlaylistVideoEntity;
import com.reelixy.videoplayer.managers.MediaScanner;
import com.reelixy.videoplayer.models.Video;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dual-purpose screen: with no EXTRA_PLAYLIST_ID it lists all playlists
 * (with create/rename/delete); with an id it shows that playlist's videos
 * in "Play all" order. Kept in one activity per section 20's small feature
 * set rather than splitting into two near-empty screens.
 */
public class PlaylistDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PLAYLIST_ID = "extra_playlist_id";

    private AppDatabase database;
    private long playlistId = -1;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private MediaScanner mediaScanner;
    private boolean destroyed;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_detail);
        database = AppDatabase.getInstance(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());
        playlistId = getIntent().getLongExtra(EXTRA_PLAYLIST_ID, -1);

        if (playlistId == -1) {
            showPlaylistsList();
        } else {
            showPlaylistVideos();
        }
    }

    private PlaylistAdapter playlistAdapter;

    private void showPlaylistsList() {
        TextView title = findViewById(R.id.tvPlaylistName);
        title.setText(R.string.my_playlists);
        findViewById(R.id.btnCreatePlaylist).setVisibility(View.VISIBLE);
        findViewById(R.id.btnPlayAll).setVisibility(View.GONE);
        findViewById(R.id.btnCreatePlaylist).setOnClickListener(v -> showCreatePlaylistDialog());
        findViewById(R.id.btnEmptyCreatePlaylist).setOnClickListener(v -> showCreatePlaylistDialog());
        View empty = findViewById(R.id.playlistEmptyState);
        empty.setVisibility(View.GONE);
        RecyclerView rv = findViewById(R.id.rvPlaylistVideos);
        rv.setVisibility(View.VISIBLE);
        rv.setLayoutManager(new LinearLayoutManager(this));
        playlistAdapter = new PlaylistAdapter(new PlaylistAdapter.OnPlaylistClickListener() {
            @Override public void onPlaylistClick(PlaylistEntity playlist) {
                Intent intent = new Intent(PlaylistDetailActivity.this, PlaylistDetailActivity.class);
                intent.putExtra(EXTRA_PLAYLIST_ID, playlist.id);
                startActivity(intent);
            }
            @Override public void onPlaylistLongClick(PlaylistEntity playlist) { showPlaylistActions(playlist); }
        });
        rv.setAdapter(playlistAdapter);

        refreshPlaylists();
    }

    private void refreshPlaylists() {
        ioExecutor.execute(() -> {
            List<PlaylistEntity> playlists = database.videoDao().getPlaylistsSync();
            Map<Long, Integer> counts = new HashMap<>();
            for (PlaylistEntity p : playlists) {
                counts.put(p.id, database.videoDao().getPlaylistVideoCount(p.id));
            }
            runOnUiThread(() -> {
                playlistAdapter.submitList(playlists, counts);
                View empty = findViewById(R.id.playlistEmptyState);
                RecyclerView rv = findViewById(R.id.rvPlaylistVideos);
                boolean isEmpty = playlists.isEmpty();
                empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                rv.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void showPlaylistActions(PlaylistEntity playlist) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, findViewById(R.id.rvPlaylistVideos));
        menu.getMenu().add(0, 1, 0, getString(R.string.action_open_playlist));
        menu.getMenu().add(0, 2, 1, getString(R.string.action_rename_playlist));
        menu.getMenu().add(0, 3, 2, getString(R.string.action_delete_playlist));
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                Intent intent = new Intent(this, PlaylistDetailActivity.class);
                intent.putExtra(EXTRA_PLAYLIST_ID, playlist.id);
                startActivity(intent);
                return true;
            }
            if (item.getItemId() == 2) { showRenamePlaylistDialog(playlist); return true; }
            if (item.getItemId() == 3) {
                new AlertDialog.Builder(this).setTitle(R.string.action_delete_playlist)
                        .setMessage(getString(R.string.delete_playlist_confirmation, playlist.name))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.delete, (d,w) -> ioExecutor.execute(() -> {
                            database.videoDao().deletePlaylist(playlist.id);
                            runOnUiThread(this::refreshPlaylists);
                        })).show();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showRenamePlaylistDialog(PlaylistEntity playlist) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(playlist.name);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle(R.string.action_rename_playlist).setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, (d,w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    ioExecutor.execute(() -> { database.videoDao().renamePlaylist(playlist.id, name); runOnUiThread(this::refreshPlaylists); });
                }).show();
    }

    private void showCreatePlaylistDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.create_playlist);
        new AlertDialog.Builder(this)
                .setTitle(R.string.create_playlist)
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    ioExecutor.execute(() -> {
                        database.videoDao().createPlaylist(new PlaylistEntity(name, System.currentTimeMillis()));
                        runOnUiThread(() -> { if (!destroyed) refreshPlaylists(); });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPlaylistVideos() {
        findViewById(R.id.btnCreatePlaylist).setVisibility(View.GONE);
        findViewById(R.id.btnPlayAll).setVisibility(View.VISIBLE);
        findViewById(R.id.playlistEmptyState).setVisibility(View.GONE);
        RecyclerView rv = findViewById(R.id.rvPlaylistVideos);
        rv.setLayoutManager(new LinearLayoutManager(this));
        com.reelixy.videoplayer.adapters.VideoAdapter adapter =
                new com.reelixy.videoplayer.adapters.VideoAdapter(
                        com.reelixy.videoplayer.adapters.VideoAdapter.VIEW_GRID,
                        new com.reelixy.videoplayer.adapters.VideoAdapter.OnVideoClickListener() {
                            @Override public void onVideoClick(Video video) { openPlayer(video); }
                            @Override public void onVideoLongClick(Video video) {
                                showPlaylistVideoActions(video);
                            }
                        });
        rv.setAdapter(adapter);

        ioExecutor.execute(() -> {
            PlaylistEntity playlist = database.videoDao().getPlaylistSync(playlistId);
            List<PlaylistVideoEntity> entries = database.videoDao().getPlaylistVideosSync(playlistId);

            mediaScanner = new MediaScanner(this);
            mediaScanner.scanAsync(new MediaScanner.ScanCallback() {
                @Override
                public void onScanComplete(List<Video> allVideos) {
                    List<Video> playlistVideos = new ArrayList<>();
                    for (PlaylistVideoEntity entry : entries) {
                        for (Video v : allVideos) {
                            if (v.getFilePath().equals(entry.filePath)) {
                                playlistVideos.add(v);
                                break;
                            }
                        }
                    }
                    runOnUiThread(() -> {
                        if (destroyed) return;
                        if (playlist != null) {
                            ((TextView) findViewById(R.id.tvPlaylistName)).setText(playlist.name);
                        }
                        adapter.submitList(playlistVideos);
                        findViewById(R.id.btnPlayAll).setOnClickListener(v -> {
                            if (!playlistVideos.isEmpty()) openPlayer(playlistVideos.get(0));
                        });
                    });
                }

                @Override
                public void onScanFailed(Exception e) {}
            });
        });
    }

    private void showPlaylistVideoActions(Video video) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, findViewById(R.id.rvPlaylistVideos));
        menu.getMenu().add(0, 1, 0, getString(R.string.action_play));
        menu.getMenu().add(0, 2, 1, getString(R.string.action_remove_from_playlist));
        menu.getMenu().add(0, 3, 2, getString(R.string.action_move_up));
        menu.getMenu().add(0, 4, 3, getString(R.string.action_move_down));
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) { openPlayer(video); return true; }
            if (item.getItemId() == 2) {
                ioExecutor.execute(() -> { database.videoDao().removeVideoFromPlaylist(playlistId, video.getFilePath()); runOnUiThread(this::showPlaylistVideos); });
                return true;
            }
            if (item.getItemId() == 3 || item.getItemId() == 4) {
                ioExecutor.execute(() -> movePlaylistVideo(video, item.getItemId() == 3));
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void movePlaylistVideo(Video video, boolean up) {
        List<PlaylistVideoEntity> entries = database.videoDao().getPlaylistVideosSync(playlistId);
        int index = -1;
        for (int i=0;i<entries.size();i++) if (entries.get(i).filePath.equals(video.getFilePath())) { index=i; break; }
        if (index < 0) return;
        int target = up ? index - 1 : index + 1;
        if (target < 0 || target >= entries.size()) return;
        PlaylistVideoEntity temp = entries.get(index); entries.set(index, entries.get(target)); entries.set(target, temp);
        List<String> paths = new ArrayList<>(); for (PlaylistVideoEntity e : entries) paths.add(e.filePath);
        database.videoDao().updatePlaylistVideoOrder(playlistId, paths);
        runOnUiThread(this::showPlaylistVideos);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        ioExecutor.shutdownNow();
        if (mediaScanner != null) mediaScanner.shutdown();
        super.onDestroy();
    }

    private void openPlayer(Video video) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_URI, video.getContentUri());
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_PATH, video.getFilePath());
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, video.getTitle());
        startActivity(intent);
    }
}
