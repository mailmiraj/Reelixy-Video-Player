package com.reelixy.videoplayer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.reelixy.videoplayer.R;
import com.reelixy.videoplayer.adapters.VideoAdapter;
import com.reelixy.videoplayer.database.AppDatabase;
import com.reelixy.videoplayer.database.FavoriteEntity;
import com.reelixy.videoplayer.database.PlaybackHistoryEntity;
import com.reelixy.videoplayer.managers.MediaScanner;
import com.reelixy.videoplayer.models.Video;
import com.reelixy.videoplayer.models.Folder;
import com.reelixy.videoplayer.utils.MediaFileManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Production search/library browser with debounced search, filter modes and grid/list toggle. */
public class SearchActivity extends AppCompatActivity {
    public static final String EXTRA_FOLDER = "extra_folder";
    public static final String EXTRA_FOLDER_PATH = "extra_folder_path";
    public static final String EXTRA_FILTER = "extra_filter";
    public static final String FILTER_ALL = "all";
    public static final String FILTER_FAVORITES = "favorites";
    public static final String FILTER_RECENT = "recent";

    private String initialFolder = "";
    private String initialFolderPath = "";
    private String activeFilter = FILTER_ALL;
    private List<Video> allVideos = new ArrayList<>();
    private VideoAdapter adapter;
    private View emptyState;
    private RecyclerView rvResults;
    private MediaScanner mediaScanner;
    private boolean destroyed;
    private TextView tvResultCount;
    private TextView btnViewMode;
    private boolean gridMode = true;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingFilter;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final Set<String> favoritePaths = new HashSet<>();
    private final Set<String> recentPaths = new HashSet<>();
    private Runnable pendingAuthorizedOperation;
    private final Set<String> selectedPaths = new HashSet<>();
    private View batchBar;
    private TextView tvBatchCount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        rvResults = findViewById(R.id.rvResults);
        emptyState = findViewById(R.id.emptyState);
        EditText etSearch = findViewById(R.id.etSearch);
        tvResultCount = findViewById(R.id.tvResultCount);
        btnViewMode = findViewById(R.id.btnViewMode);
        batchBar = findViewById(R.id.batchBar);
        tvBatchCount = findViewById(R.id.tvBatchCount);
        findViewById(R.id.btnBatchFavorite).setOnClickListener(v -> batchToggleFavorites());
        findViewById(R.id.btnBatchMove).setOnClickListener(v -> showBatchMoveDialog());
        findViewById(R.id.btnBatchPlaylist).setOnClickListener(v -> showBatchPlaylistDialog());
        findViewById(R.id.btnBatchShare).setOnClickListener(v -> shareSelected());
        findViewById(R.id.btnBatchDelete).setOnClickListener(v -> confirmBatchDelete());
        findViewById(R.id.btnBatchClear).setOnClickListener(v -> clearSelection());
        initialFolder = getIntent().getStringExtra(EXTRA_FOLDER);
        if (initialFolder == null) initialFolder = "";
        initialFolderPath = getIntent().getStringExtra(EXTRA_FOLDER_PATH);
        if (initialFolderPath == null) initialFolderPath = "";
        String incomingFilter = getIntent().getStringExtra(EXTRA_FILTER);
        if (incomingFilter != null) activeFilter = incomingFilter;

        adapter = new VideoAdapter(VideoAdapter.VIEW_GRID, new VideoAdapter.OnVideoClickListener() {
            @Override public void onVideoClick(Video video) {
                if (!selectedPaths.isEmpty()) toggleSelection(video);
                else openPlayer(video);
            }
            @Override public void onVideoLongClick(Video video) {
                if (selectedPaths.isEmpty()) { toggleSelection(video); }
                else toggleSelection(video);
            }
        });
        setGridMode(true);
        rvResults.setAdapter(adapter);
        rvResults.setHasFixedSize(true);
        rvResults.setItemViewCacheSize(6);

        btnViewMode.setOnClickListener(v -> setGridMode(!gridMode));

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingFilter != null) searchHandler.removeCallbacks(pendingFilter);
                final String query = s.toString();
                pendingFilter = () -> filter(query);
                searchHandler.postDelayed(pendingFilter, 180L);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        mediaScanner = new MediaScanner(this);
        dbExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            for (FavoriteEntity favorite : db.videoDao().getFavoritesSync()) favoritePaths.add(favorite.filePath);
            for (PlaybackHistoryEntity h : db.videoDao().getRecentlyPlayedSync()) recentPaths.add(h.filePath);
            runOnUiThread(() -> {
                if (destroyed) return;
                if (activeFilter.equals(FILTER_FAVORITES)) etSearch.setHint(getString(R.string.library_favorites));
                else if (activeFilter.equals(FILTER_RECENT)) etSearch.setHint(getString(R.string.library_recent));
            });
        });

        mediaScanner.scanAsync(new MediaScanner.ScanCallback() {
            @Override public void onScanComplete(List<Video> videos) {
                if (destroyed) return;
                allVideos = videos;
                if (!initialFolderPath.isEmpty() || !initialFolder.isEmpty()) {
                    // Folder navigation is a scope, not a text search. Keeping the
                    // folder name inside the search box caused the folder title to be
                    // applied as an additional query and could produce an empty list.
                    etSearch.setText("");
                    if (!initialFolder.isEmpty()) {
                        setTitle(initialFolder);
                        etSearch.setHint(initialFolder);
                    }
                    filter("");
                } else {
                    filter(etSearch.getText().toString());
                }
            }
            @Override public void onScanFailed(Exception e) {
                if (!destroyed) {
                    allVideos = new ArrayList<>();
                    tvResultCount.setText(getString(R.string.search_results_count, 0));
                    emptyState.setVisibility(View.VISIBLE);
                    rvResults.setVisibility(View.GONE);
                }
            }
        });

        etSearch.requestFocus();
    }

    private void setGridMode(boolean grid) {
        gridMode = grid;
        int span = grid ? 2 : 1;
        rvResults.setLayoutManager(grid ? new GridLayoutManager(this, span) : new LinearLayoutManager(this));
        adapter = new VideoAdapter(grid ? VideoAdapter.VIEW_GRID : VideoAdapter.VIEW_HORIZONTAL, new VideoAdapter.OnVideoClickListener() {
            @Override public void onVideoClick(Video video) {
                if (!selectedPaths.isEmpty()) toggleSelection(video);
                else openPlayer(video);
            }
            @Override public void onVideoLongClick(Video video) {
                if (selectedPaths.isEmpty()) { toggleSelection(video); }
                else toggleSelection(video);
            }
        });
        rvResults.setAdapter(adapter);
        adapter.setSelectedPaths(selectedPaths);
        btnViewMode.setText(grid ? "≡" : "▦");
        btnViewMode.setContentDescription(getString(grid ? R.string.view_list : R.string.view_grid));
        filterNow();
    }

    private void filterNow() {
        EditText etSearch = findViewById(R.id.etSearch);
        filter(etSearch == null ? "" : etSearch.getText().toString());
    }

    private void filter(String query) {
        String lower = query.toLowerCase(Locale.getDefault()).trim();
        List<Video> matches = new ArrayList<>();
        for (Video v : allVideos) {
            if (activeFilter.equals(FILTER_FAVORITES) && !favoritePaths.contains(v.getFilePath())) continue;
            if (activeFilter.equals(FILTER_RECENT) && !recentPaths.contains(v.getFilePath())) continue;
            if (!initialFolderPath.isEmpty()) {
                String videoFolderPath = v.getRelativePath();
                if (videoFolderPath == null || videoFolderPath.isEmpty()) videoFolderPath = v.getFilePath();
                if (!initialFolderPath.equals(videoFolderPath)) continue;
            } else if (!initialFolder.isEmpty() && !v.getFolderName().equalsIgnoreCase(initialFolder)) {
                continue;
            }
            if (!lower.isEmpty() && !(v.getTitle().toLowerCase(Locale.getDefault()).contains(lower)
                    || v.getFilePath().toLowerCase(Locale.getDefault()).contains(lower)
                    || v.getFolderName().toLowerCase(Locale.getDefault()).contains(lower))) continue;
            matches.add(v);
        }
        adapter.submitList(matches);
        tvResultCount.setText(getString(R.string.search_results_count, matches.size()));
        boolean empty = matches.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvResults.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void toggleSelection(Video video) {
        String path = video.getFilePath();
        if (!selectedPaths.add(path)) selectedPaths.remove(path);
        if (selectedPaths.isEmpty()) {
            batchBar.setVisibility(View.GONE);
        } else {
            batchBar.setVisibility(View.VISIBLE);
            tvBatchCount.setText(getString(R.string.selected_count, selectedPaths.size()));
        }
        adapter.setSelectedPaths(selectedPaths);
    }

    private void clearSelection() {
        selectedPaths.clear();
        batchBar.setVisibility(View.GONE);
        if (adapter != null) adapter.clearSelection();
    }

    private List<Video> getSelectedVideos() {
        List<Video> result = new ArrayList<>();
        for (Video video : allVideos) if (selectedPaths.contains(video.getFilePath())) result.add(video);
        return result;
    }

    private void batchToggleFavorites() {
        List<Video> selected = getSelectedVideos();
        if (selected.isEmpty()) return;
        dbExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            boolean allFavorite = true;
            for (Video v : selected) if (!favoritePaths.contains(v.getFilePath())) { allFavorite = false; break; }
            for (Video v : selected) {
                String path = v.getFilePath();
                if (allFavorite) { db.videoDao().removeFavorite(path); favoritePaths.remove(path); }
                else { db.videoDao().addFavorite(new FavoriteEntity(path, System.currentTimeMillis())); favoritePaths.add(path); }
            }
            runOnUiThread(() -> { clearSelection(); filterNow(); });
        });
    }

    private int pendingBatchIndex = 0;

    private void showBatchMoveDialog() {
        List<Video> selected = getSelectedVideos();
        if (selected.isEmpty()) return;
        List<Folder> folders = new ArrayList<>();
        for (Video candidate : allVideos) {
            String path = candidate.getRelativePath();
            if (path == null || path.isEmpty()) path = candidate.getFilePath();
            if (path == null || path.isEmpty()) continue;
            boolean exists = false;
            for (Folder folder : folders) if (folder.getPath().equals(path)) { exists = true; break; }
            if (!exists) folders.add(new Folder(candidate.getFolderName(), path, 0));
        }
        List<String> sourcePaths = new ArrayList<>();
        for (Video v : selected) { String p = v.getRelativePath(); if (p == null || p.isEmpty()) p = v.getFilePath(); sourcePaths.add(p); }
        folders.removeIf(f -> sourcePaths.contains(f.getPath()));
        if (folders.isEmpty()) { Toast.makeText(this, R.string.no_destination_folder, Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) names[i] = folders.get(i).getName();
        new android.app.AlertDialog.Builder(this).setTitle(R.string.action_move_to_folder).setItems(names, (d, which) -> runBatchMove(selected, folders.get(which), 0))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void runBatchMove(List<Video> selected, Folder target, int startIndex) {
        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                for (int i = startIndex; i < selected.size(); i++) {
                    pendingBatchIndex = i;
                    Video v = selected.get(i);
                    String oldPath = v.getFilePath();
                    String newPath = MediaFileManager.identityAfterMove(v, target.getPath());
                    MediaFileManager.moveVideo(this, v, target.getPath());
                    db.videoDao().migrateVideoReferences(oldPath, newPath);
                    pendingBatchIndex = i + 1;
                }
                runOnUiThread(() -> { clearSelection(); Toast.makeText(this, R.string.operation_completed, Toast.LENGTH_SHORT).show(); rescanLibrary(); });
            } catch (android.app.RecoverableSecurityException e) {
                final int resumeIndex = pendingBatchIndex;
                pendingAuthorizedOperation = () -> runBatchMove(selected, target, resumeIndex);
                runOnUiThread(() -> requestUserAuthorization(e));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, e.getMessage() == null ? getString(R.string.operation_failed) : e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showBatchPlaylistDialog() {
        List<Video> selected = getSelectedVideos();
        if (selected.isEmpty()) return;
        dbExecutor.execute(() -> {
            List<com.reelixy.videoplayer.database.PlaylistEntity> playlists = AppDatabase.getInstance(this).videoDao().getPlaylistsSync();
            runOnUiThread(() -> {
                if (playlists.isEmpty()) { Toast.makeText(this, R.string.create_playlist_first, Toast.LENGTH_SHORT).show(); return; }
                String[] names = new String[playlists.size()];
                for (int i=0;i<playlists.size();i++) names[i] = playlists.get(i).name;
                new android.app.AlertDialog.Builder(this).setTitle(R.string.action_add_to_playlist).setItems(names, (d, which) -> {
                    long playlistId = playlists.get(which).id;
                    dbExecutor.execute(() -> {
                        AppDatabase db = AppDatabase.getInstance(this);
                        int order = db.videoDao().getPlaylistVideoCount(playlistId);
                        for (Video v : selected) db.videoDao().addVideoToPlaylist(new com.reelixy.videoplayer.database.PlaylistVideoEntity(playlistId, v.getFilePath(), order++));
                        runOnUiThread(() -> { clearSelection(); Toast.makeText(this, R.string.added_to_playlist, Toast.LENGTH_SHORT).show(); });
                    });
                }).setNegativeButton(android.R.string.cancel, null).show();
            });
        });
    }

    private void shareSelected() {
        List<Video> selected = getSelectedVideos();
        if (selected.isEmpty()) return;
        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE);
        send.setType("video/*");
        ArrayList<Uri> uris = new ArrayList<>();
        for (Video v : selected) uris.add(Uri.parse(v.getContentUri()));
        send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { Intent chooser = Intent.createChooser(send, getString(R.string.share_videos)); chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(chooser); }
        catch (Exception e) { Toast.makeText(this, R.string.no_external_player, Toast.LENGTH_SHORT).show(); }
    }

    private void confirmBatchDelete() {
        List<Video> selected = getSelectedVideos();
        if (selected.isEmpty()) return;
        new android.app.AlertDialog.Builder(this).setTitle(R.string.action_delete_video)
                .setMessage(getString(R.string.delete_selected_confirmation, selected.size()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> runBatchDelete(selected, 0))
                .show();
    }

    private void runBatchDelete(List<Video> selected, int startIndex) {
        dbExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(this);
                for (int i = startIndex; i < selected.size(); i++) {
                    pendingBatchIndex = i;
                    Video v = selected.get(i);
                    MediaFileManager.deleteVideo(this, v);
                    db.videoDao().removeVideoReferences(v.getFilePath());
                    pendingBatchIndex = i + 1;
                }
                runOnUiThread(() -> { clearSelection(); Toast.makeText(this, R.string.operation_completed, Toast.LENGTH_SHORT).show(); rescanLibrary(); });
            } catch (android.app.RecoverableSecurityException e) {
                final int resumeIndex = pendingBatchIndex;
                pendingAuthorizedOperation = () -> runBatchDelete(selected, resumeIndex);
                runOnUiThread(() -> requestUserAuthorization(e));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, e.getMessage() == null ? getString(R.string.operation_failed) : e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showVideoActions(Video video) {
        PopupMenu menu = new PopupMenu(this, rvResults);
        menu.getMenu().add(0, 1, 0, getString(R.string.action_play));
        menu.getMenu().add(0, 2, 1, getString(favoritePaths.contains(video.getFilePath()) ? R.string.action_remove_favorite : R.string.action_favorite));
        menu.getMenu().add(0, 3, 2, getString(R.string.action_share));
        menu.getMenu().add(0, 4, 3, getString(R.string.action_details));
        menu.getMenu().add(0, 5, 4, getString(R.string.action_rename_video));
        menu.getMenu().add(0, 6, 5, getString(R.string.action_move_to_folder));
        menu.getMenu().add(0, 7, 6, getString(R.string.action_delete_video));
        menu.getMenu().add(0, 8, 7, getString(R.string.action_mark_watched));
        menu.getMenu().add(0, 9, 8, getString(R.string.action_mark_unwatched));
        android.view.Menu sub = menu.getMenu().addSubMenu(0, 10, 9, getString(R.string.action_add_to_playlist));
        dbExecutor.execute(() -> {
            List<com.reelixy.videoplayer.database.PlaylistEntity> playlists = AppDatabase.getInstance(this).videoDao().getPlaylistsSync();
            runOnUiThread(() -> {
                for (com.reelixy.videoplayer.database.PlaylistEntity pl : playlists) {
                    sub.add(0, (int) (1000 + pl.id), sub.size(), pl.name);
                }
            });
        });
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) { openPlayer(video); return true; }
            if (id == 2) { toggleFavorite(video); return true; }
            if (id == 3) { shareVideo(video); return true; }
            if (id == 4) { showVideoInfo(video); return true; }
            if (id == 5) { showRenameVideoDialog(video); return true; }
            if (id == 6) { showMoveVideoDialog(video); return true; }
            if (id == 7) { confirmDeleteVideo(video); return true; }
            if (id == 8) { markWatched(video, true); return true; }
            if (id == 9) { markWatched(video, false); return true; }
            if (id >= 1000) { addToPlaylist(video, id - 1000); return true; }
            return false;
        });
        menu.show();
    }

    private void showRenameVideoDialog(Video video) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(video.getTitle());
        input.setSelectAllOnFocus(true);
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.action_rename_video)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String requested = input.getText().toString().trim();
                    for (Video existing : allVideos) {
                        if (existing.getId() != video.getId() && existing.getFolderName().equalsIgnoreCase(video.getFolderName())
                                && existing.getTitle().equalsIgnoreCase(requested)) {
                            Toast.makeText(this, R.string.video_name_already_exists, Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    runMediaOperation(() -> {
                        String oldPath = video.getFilePath();
                        String newPath = MediaFileManager.identityAfterRename(video, requested);
                        MediaFileManager.renameVideo(this, video, requested);
                        AppDatabase.getInstance(this).videoDao().migrateVideoReferences(oldPath, newPath);
                    });
                })
                .show();
    }

    private void confirmDeleteVideo(Video video) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.action_delete_video)
                .setMessage(getString(R.string.delete_video_confirmation, video.getTitle()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> runMediaOperation(() -> {
                    MediaFileManager.deleteVideo(this, video);
                    AppDatabase.getInstance(this).videoDao().removeVideoReferences(video.getFilePath());
                }))
                .show();
    }

    private void showMoveVideoDialog(Video video) {
        final List<Folder> folders = new ArrayList<>();
        for (Video candidate : allVideos) {
            String path = candidate.getRelativePath();
            if (path == null || path.isEmpty()) path = candidate.getFilePath();
            if (path == null || path.isEmpty()) continue;
            boolean exists = false;
            for (Folder folder : folders) {
                if (folder.getPath().equals(path)) { exists = true; break; }
            }
            if (!exists) folders.add(new Folder(candidate.getFolderName(), path, 0));
        }
        folders.removeIf(folder -> folder.getPath().equals(video.getRelativePath()) || folder.getName().equalsIgnoreCase(video.getFolderName()));
        if (folders.isEmpty()) {
            Toast.makeText(this, R.string.no_destination_folder, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) {
            String path = folders.get(i).getPath();
            names[i] = folders.get(i).getName() + (path.contains("/") ? "  •  " + path : "");
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.action_move_to_folder)
                .setItems(names, (d, which) -> {
                    Folder target = folders.get(which);
                    runMediaOperation(() -> {
                        String oldPath = video.getFilePath();
                        String newPath = MediaFileManager.identityAfterMove(video, target.getPath());
                        MediaFileManager.moveVideo(this, video, target.getPath());
                        AppDatabase.getInstance(this).videoDao().migrateVideoReferences(oldPath, newPath);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void runMediaOperation(MediaOperation operation) {
        dbExecutor.execute(() -> {
            try {
                operation.run();
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.operation_completed, Toast.LENGTH_SHORT).show();
                    rescanLibrary();
                });
            } catch (android.app.RecoverableSecurityException e) {
                pendingAuthorizedOperation = () -> runMediaOperation(operation);
                runOnUiThread(() -> requestUserAuthorization(e));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        e.getMessage() == null ? getString(R.string.operation_failed) : e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private interface MediaOperation { void run() throws Exception; }

    private void rescanLibrary() {
        if (mediaScanner == null || destroyed) return;
        mediaScanner.scanAsync(new MediaScanner.ScanCallback() {
            @Override public void onScanComplete(List<Video> videos) {
                if (destroyed) return;
                allVideos = videos;
                filterNow();
            }
            @Override public void onScanFailed(Exception e) {
                if (!destroyed) filterNow();
            }
        });
    }

    private void requestUserAuthorization(android.app.RecoverableSecurityException e) {
        try {
            startIntentSenderForResult(e.getUserAction().getActionIntent().getIntentSender(), 7402, null, 0, 0, 0);
        } catch (Exception ex) {
            Toast.makeText(this, R.string.operation_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void showVideoInfo(Video video) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View content = getLayoutInflater().inflate(R.layout.dialog_video_info, null);
        android.widget.TableLayout table = content.findViewById(R.id.infoTable);
        addInfoRow(table, getString(R.string.info_name), video.getTitle());
        addInfoRow(table, getString(R.string.info_duration), com.reelixy.videoplayer.utils.TimeUtils.formatDuration(video.getDurationMs()));
        addInfoRow(table, getString(R.string.info_resolution), video.getWidth() + " × " + video.getHeight());
        addInfoRow(table, getString(R.string.info_size), android.text.format.Formatter.formatFileSize(this, video.getSizeBytes()));
        addInfoRow(table, getString(R.string.info_folder), video.getFolderName());
        addInfoRow(table, getString(R.string.info_type), video.getMimeType() == null ? "video/*" : video.getMimeType());
        addInfoRow(table, getString(R.string.info_location), video.getFilePath());
        builder.setView(content).setPositiveButton(android.R.string.ok, null).show();
    }

    private void addInfoRow(android.widget.TableLayout table, String label, String value) {
        android.widget.TableRow row = new android.widget.TableRow(this);
        TextView l = new TextView(this); l.setText(label); l.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        TextView v = new TextView(this); v.setText(value); v.setTextColor(ContextCompat.getColor(this, android.R.color.primary_text_light));
        row.addView(l); row.addView(v); table.addView(row);
    }

    private void markWatched(Video video, boolean watched) {
        dbExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            long duration = Math.max(0L, video.getDurationMs());
            long position = watched ? duration : 0L;
            db.videoDao().upsertHistory(new com.reelixy.videoplayer.database.PlaybackHistoryEntity(
                    video.getFilePath(), video.getTitle(), position, duration, System.currentTimeMillis(), watched));
            runOnUiThread(() -> Toast.makeText(this, watched ? R.string.marked_watched : R.string.marked_unwatched, Toast.LENGTH_SHORT).show());
        });
    }

    private void addToPlaylist(Video video, int playlistId) {
        dbExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            int nextOrder = db.videoDao().getPlaylistVideoCount(playlistId);
            db.videoDao().addVideoToPlaylist(new com.reelixy.videoplayer.database.PlaylistVideoEntity(playlistId, video.getFilePath(), nextOrder));
            runOnUiThread(() -> Toast.makeText(this, R.string.added_to_playlist, Toast.LENGTH_SHORT).show());
        });
    }

    private void toggleFavorite(Video video) {
        final String path = video.getFilePath();
        dbExecutor.execute(() -> {
            if (favoritePaths.contains(path)) {
                AppDatabase.getInstance(this).videoDao().removeFavorite(path);
                favoritePaths.remove(path);
            } else {
                AppDatabase.getInstance(this).videoDao().addFavorite(new FavoriteEntity(path, System.currentTimeMillis()));
                favoritePaths.add(path);
            }
            runOnUiThread(() -> filterNow());
        });
    }

    private void shareVideo(Video video) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(video.getMimeType() == null ? "video/*" : video.getMimeType());
        send.putExtra(Intent.EXTRA_STREAM, Uri.parse(video.getContentUri()));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            Intent chooser = Intent.createChooser(send, getString(R.string.share_video));
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
        }
        catch (Exception e) { Toast.makeText(this, R.string.no_external_player, Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 7402 && resultCode == RESULT_OK && pendingAuthorizedOperation != null) {
            Runnable operation = pendingAuthorizedOperation;
            pendingAuthorizedOperation = null;
            operation.run();
        } else if (requestCode == 7402) {
            pendingAuthorizedOperation = null;
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (pendingFilter != null) searchHandler.removeCallbacks(pendingFilter);
        if (mediaScanner != null) mediaScanner.shutdown();
        pendingAuthorizedOperation = null;
        selectedPaths.clear();
        dbExecutor.shutdownNow();
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
