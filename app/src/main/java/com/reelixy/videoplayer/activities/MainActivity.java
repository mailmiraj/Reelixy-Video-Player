package com.reelixy.videoplayer.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import androidx.core.widget.NestedScrollView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.reelixy.videoplayer.R;
import com.reelixy.videoplayer.adapters.VideoAdapter;
import com.reelixy.videoplayer.database.AppDatabase;
import com.reelixy.videoplayer.database.PlaybackHistoryEntity;
import com.reelixy.videoplayer.managers.MediaScanner;
import com.reelixy.videoplayer.managers.PermissionManager;
import com.reelixy.videoplayer.models.Video;
import com.reelixy.videoplayer.utils.TimeUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home screen: continue watching, recently added, folders, bottom navigation,
 * and the mini player. Scanning and history reads happen off the main thread;
 * results are always posted back before touching adapters.
 */
public class MainActivity extends AppCompatActivity {

    private PermissionManager permissionManager;
    private MediaScanner mediaScanner;
    private AppDatabase database;

    private View emptyState;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;
    private View btnScanDevice;
    private ProgressBar loadingIndicator;
    private View sectionContinueWatching;
    private View sectionRecentlyPlayed;
    private View miniPlayer;
    private String dismissedMiniVideoPath;
    private TextView tvLibrarySummary;
    private TextView tvLibraryCount;
    private TextView tvContinueCount;
    private TextView tvRecentlyPlayedCount;
    private TextView tvAllVideosCount;
    private TextView tvSnapshotMeta;
    private View btnBrowseLibrary;
    private View btnAllVideos;
    private View continueHero;
    private android.widget.ImageView ivHero;
    private TextView tvHeroTitle;
    private TextView tvHeroMeta;
    private ProgressBar progressHero;
    private ImageButton btnHeroPlay;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private boolean destroyed;

    private RecyclerView rvContinueWatching;
    private RecyclerView rvRecentlyAdded;
    private RecyclerView rvRecentlyPlayed;
    private RecyclerView rvAllVideos;
    private VideoAdapter continueWatchingAdapter;
    private VideoAdapter recentlyAddedAdapter;
    private VideoAdapter recentlyPlayedAdapter;
    private VideoAdapter allVideosAdapter;

    private List<Video> allVideos = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = AppDatabase.getInstance(this);
        mediaScanner = new MediaScanner(this);
        permissionManager = new PermissionManager(this);

        bindViews();
        setupRecyclerViews();
        setupBottomNav();
        setupTopBar();

        checkPermissionAndLoad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh lightweight home state after returning from PlayerActivity or
        // media-management screens. Avoid a scan while one is already running.
        if (permissionManager != null && permissionManager.hasPermission(this) && !allVideos.isEmpty()) {
            loadContinueWatching();
        }
    }

    private void bindViews() {
        emptyState = findViewById(R.id.emptyState);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);
        btnScanDevice = findViewById(R.id.btnScanDevice);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        sectionContinueWatching = findViewById(R.id.sectionContinueWatching);
        sectionRecentlyPlayed = findViewById(R.id.sectionRecentlyPlayed);
        miniPlayer = findViewById(R.id.miniPlayer);
        tvLibrarySummary = findViewById(R.id.tvLibrarySummary);
        tvLibraryCount = findViewById(R.id.tvLibraryCount);
        tvContinueCount = findViewById(R.id.tvContinueCount);
        tvRecentlyPlayedCount = findViewById(R.id.tvRecentlyPlayedCount);
        tvAllVideosCount = findViewById(R.id.tvAllVideosCount);
        tvSnapshotMeta = findViewById(R.id.tvSnapshotMeta);
        btnBrowseLibrary = findViewById(R.id.btnBrowseLibrary);
        btnAllVideos = findViewById(R.id.btnAllVideos);
        continueHero = findViewById(R.id.continueHero);
        ivHero = findViewById(R.id.ivHero);
        tvHeroTitle = findViewById(R.id.tvHeroTitle);
        tvHeroMeta = findViewById(R.id.tvHeroMeta);
        progressHero = findViewById(R.id.progressHero);
        btnHeroPlay = findViewById(R.id.btnHeroPlay);

        rvContinueWatching = findViewById(R.id.rvContinueWatching);
        rvRecentlyAdded = findViewById(R.id.rvRecentlyAdded);
        rvRecentlyPlayed = findViewById(R.id.rvRecentlyPlayed);
        rvAllVideos = findViewById(R.id.rvAllVideos);

        btnScanDevice.setOnClickListener(v -> checkPermissionAndLoad());
    }

    private void setupRecyclerViews() {
        continueWatchingAdapter = new VideoAdapter(VideoAdapter.VIEW_HORIZONTAL, new VideoAdapter.OnVideoClickListener() {
            @Override public void onVideoClick(Video video) { openPlayer(video); }
            @Override public void onVideoLongClick(Video video) { toggleFavorite(video); }
        });
        rvContinueWatching.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvContinueWatching.setAdapter(continueWatchingAdapter);
        rvContinueWatching.setHasFixedSize(true);
        rvContinueWatching.setItemViewCacheSize(3);

        recentlyAddedAdapter = new VideoAdapter(VideoAdapter.VIEW_HORIZONTAL, new VideoAdapter.OnVideoClickListener() {
            @Override public void onVideoClick(Video video) { openPlayer(video); }
            @Override public void onVideoLongClick(Video video) { toggleFavorite(video); }
        });
        rvRecentlyAdded.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvRecentlyAdded.setAdapter(recentlyAddedAdapter);
        rvRecentlyAdded.setHasFixedSize(true);
        rvRecentlyAdded.setItemViewCacheSize(3);

        recentlyPlayedAdapter = new VideoAdapter(VideoAdapter.VIEW_HORIZONTAL, new VideoAdapter.OnVideoClickListener() {
            @Override public void onVideoClick(Video video) { openPlayer(video); }
            @Override public void onVideoLongClick(Video video) { toggleFavorite(video); }
        });
        rvRecentlyPlayed.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvRecentlyPlayed.setAdapter(recentlyPlayedAdapter);
        rvRecentlyPlayed.setHasFixedSize(true);
        rvRecentlyPlayed.setItemViewCacheSize(3);

        allVideosAdapter = new VideoAdapter(VideoAdapter.VIEW_GRID, new VideoAdapter.OnVideoClickListener() {
            @Override public void onVideoClick(Video video) { openPlayer(video); }
            @Override public void onVideoLongClick(Video video) { toggleFavorite(video); }
        });
        rvAllVideos.setLayoutManager(new GridLayoutManager(this, 2));
        rvAllVideos.setAdapter(allVideosAdapter);
        rvAllVideos.setHasFixedSize(true);
        rvAllVideos.setItemViewCacheSize(4);

    }

    private void setupBottomNav() {
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            NestedScrollView scroll = findViewById(R.id.scrollContent);
            if (id == R.id.nav_home) {
                scroll.smoothScrollTo(0, 0);
                allVideosAdapter.submitList(new ArrayList<>(allVideos.subList(0, Math.min(allVideos.size(), 12))));
                return true;
            } else if (id == R.id.nav_folders) {
                startActivity(new Intent(this, LibraryActivity.class));
                return false;
            } else if (id == R.id.nav_search) {
                startActivity(new Intent(this, SearchActivity.class));
                return false;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return false;
            }
            return false;
        });
    }

    private void setupTopBar() {
        ImageButton btnMore = findViewById(R.id.btnMore);

        btnMore.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, btnMore);
            menu.getMenu().add(0, 1, 0, getString(R.string.rescan_media));
            menu.setOnMenuItemClickListener(item -> {
                checkPermissionAndLoad();
                return true;
            });
            menu.show();
        });

        View.OnClickListener openLibrary = v -> startActivity(new Intent(this, LibraryActivity.class));
        btnBrowseLibrary.setOnClickListener(openLibrary);
        btnAllVideos.setOnClickListener(openLibrary);

        btnHeroPlay.setOnClickListener(v -> {
            Object tag = continueHero.getTag();
            if (tag instanceof Video) openPlayer((Video) tag);
        });
        continueHero.setOnClickListener(v -> {
            Object tag = continueHero.getTag();
            if (tag instanceof Video) openPlayer((Video) tag);
        });
    }

    private void checkPermissionAndLoad() {
        if (permissionManager.hasPermission(this)) {
            scanLibrary();
        } else {
            showPermissionRequiredState();
        }
    }

    private void showPermissionRequiredState() {
        emptyState.setVisibility(View.VISIBLE);
        tvEmptyTitle.setText(R.string.storage_access_required);
        tvEmptySubtitle.setText("");
        ((android.widget.Button) btnScanDevice).setText(R.string.grant_permission);
        btnScanDevice.setOnClickListener(v -> permissionManager.requestPermission(granted -> {
            if (granted) {
                scanLibrary();
            }
        }));
    }

    private void scanLibrary() {
        loadingIndicator.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);

        mediaScanner.scanAsync(new MediaScanner.ScanCallback() {
            @Override
            public void onScanComplete(List<Video> videos) {
                if (destroyed) return;
                loadingIndicator.setVisibility(View.GONE);
                allVideos = new ArrayList<>(videos);
                tvLibraryCount.setText(getString(R.string.video_count, videos.size()));
                tvAllVideosCount.setText(getString(R.string.video_count, videos.size()));
                tvSnapshotMeta.setText(getString(R.string.video_count, videos.size()));
                tvLibrarySummary.setText(videos.size() == 1
                        ? getString(R.string.home_library_summary_ready_singular)
                        : getString(R.string.home_library_summary_ready, videos.size()));

                if (videos.isEmpty()) {
                    showEmptyLibraryState();
                    return;
                }

                emptyState.setVisibility(View.GONE);
                List<Video> recentPreview = new ArrayList<>(videos.subList(0, Math.min(videos.size(), 12)));
                List<Video> homePreview = new ArrayList<>(videos.subList(0, Math.min(videos.size(), 12)));
                recentlyAddedAdapter.submitList(recentPreview);
                allVideosAdapter.submitList(homePreview);
                loadContinueWatching();
            }

            @Override
            public void onScanFailed(Exception e) {
                if (destroyed) return;
                loadingIndicator.setVisibility(View.GONE);
                showEmptyLibraryState();
            }
        });
    }

    private void showEmptyLibraryState() {
        emptyState.setVisibility(View.VISIBLE);
        tvEmptyTitle.setText(R.string.no_videos_yet);
        tvEmptySubtitle.setText(R.string.videos_will_appear_here);
        tvEmptySubtitle.setVisibility(View.VISIBLE);
        ((android.widget.Button) btnScanDevice).setText(R.string.scan_device);
        btnScanDevice.setOnClickListener(v -> scanLibrary());
    }

    private void loadContinueWatching() {
        ioExecutor.execute(() -> {
            List<PlaybackHistoryEntity> history = database.videoDao().getContinueWatchingSync();
            List<PlaybackHistoryEntity> recentHistory = database.videoDao().getRecentlyPlayedSync();
            List<Video> matched = new ArrayList<>();
            List<Video> recentlyPlayed = new ArrayList<>();
            Map<String, Integer> progress = new LinkedHashMap<>();
            Map<String, Video> videosByPath = new LinkedHashMap<>();
            Map<String, PlaybackHistoryEntity> historyByPath = new LinkedHashMap<>();
            for (Video v : allVideos) videosByPath.put(v.getFilePath(), v);
            for (PlaybackHistoryEntity h : recentHistory) historyByPath.put(h.filePath, h);

            for (PlaybackHistoryEntity h : history) {
                Video v = videosByPath.get(h.filePath);
                if (v != null) {
                    matched.add(v);
                    if (h.durationMs > 0) {
                        progress.put(v.getFilePath(), (int) (h.lastPositionMs * 100 / h.durationMs));
                    }
                }
            }
            for (PlaybackHistoryEntity h : recentHistory) {
                Video v = videosByPath.get(h.filePath);
                if (v != null) recentlyPlayed.add(v);
            }
            if (recentlyPlayed.size() > 12) recentlyPlayed = new ArrayList<>(recentlyPlayed.subList(0, 12));

            final List<Video> recentPlayedFinal = recentlyPlayed;
            runOnUiThread(() -> {
                if (destroyed) return;
                boolean hasContinue = !matched.isEmpty();
                boolean hasRecentPlayed = !recentPlayedFinal.isEmpty();
                sectionContinueWatching.setVisibility(hasContinue ? View.VISIBLE : View.GONE);
                sectionRecentlyPlayed.setVisibility(hasRecentPlayed ? View.VISIBLE : View.GONE);
                continueHero.setVisibility(hasContinue ? View.VISIBLE : View.GONE);
                continueWatchingAdapter.submitList(matched);
                continueWatchingAdapter.setProgressMap(progress);
                recentlyPlayedAdapter.submitList(recentPlayedFinal);
                tvContinueCount.setText(getString(R.string.continue_count, matched.size()));
                tvRecentlyPlayedCount.setText(getString(R.string.video_count, recentPlayedFinal.size()));
                if (hasContinue) {
                    Video heroVideo = matched.get(0);
                    continueHero.setTag(heroVideo);
                    tvHeroTitle.setText(heroVideo.getTitle());
                    tvHeroMeta.setText(TimeUtils.formatDuration(heroVideo.getDurationMs()) + " • " + (heroVideo.getHeight() > 0 ? heroVideo.getHeight() + "p" : "Video"));
                    Integer pct = progress.get(heroVideo.getFilePath());
                    progressHero.setProgress(pct == null ? 0 : Math.max(0, Math.min(100, pct)));
                    Glide.with(this).load(Uri.parse(heroVideo.getContentUri())).placeholder(R.drawable.ic_video_placeholder).error(R.drawable.ic_video_placeholder).dontAnimate().centerCrop().into(ivHero);
                    bindMiniPlayer(heroVideo, pct == null ? 0 : pct);
                } else if (!hasRecentPlayed) {
                    hideMiniPlayer();
                } else {
                    Video latest = recentPlayedFinal.get(0);
                    PlaybackHistoryEntity latestHistory = historyByPath.get(latest.getFilePath());
                    int pct = latestHistory != null && latestHistory.durationMs > 0 ? (int)(latestHistory.lastPositionMs * 100 / latestHistory.durationMs) : 0;
                    bindMiniPlayer(latest, Math.max(0, Math.min(100, pct)));
                }
            });
        });
    }

    private void bindMiniPlayer(Video video, int progress) {
        if (miniPlayer == null || video == null) return;
        String videoPath = video.getFilePath();
        if (videoPath != null && videoPath.equals(dismissedMiniVideoPath)) {
            miniPlayer.setVisibility(View.GONE);
            return;
        }
        dismissedMiniVideoPath = null;
        miniPlayer.setTag(videoPath);
        miniPlayer.setVisibility(View.VISIBLE);
        TextView title = miniPlayer.findViewById(R.id.tvMiniTitle);
        android.widget.ImageView thumb = miniPlayer.findViewById(R.id.ivMiniThumb);
        android.widget.ProgressBar bar = miniPlayer.findViewById(R.id.progressMini);
        ImageButton play = miniPlayer.findViewById(R.id.btnMiniPlayPause);
        ImageButton close = miniPlayer.findViewById(R.id.btnMiniClose);
        title.setText(video.getTitle());
        title.setContentDescription(getString(R.string.cd_play) + " " + video.getTitle());
        bar.setProgress(progress);
        Glide.with(this).load(Uri.parse(video.getContentUri())).placeholder(R.drawable.ic_video_placeholder).error(R.drawable.ic_video_placeholder).dontAnimate().centerCrop().into(thumb);
        View.OnClickListener open = v -> openPlayer(video);
        miniPlayer.setOnClickListener(open);
        play.setContentDescription(getString(R.string.cd_play));
        play.setOnClickListener(open);
        close.setOnClickListener(v -> {
            dismissedMiniVideoPath = video.getFilePath();
            miniPlayer.setVisibility(View.GONE);
        });
    }

    private void hideMiniPlayer() {
        if (miniPlayer != null) miniPlayer.setVisibility(View.GONE);
    }

    private void toggleFavorite(Video video) {
        ioExecutor.execute(() -> {
            boolean isFav = database.videoDao().isFavorite(video.getFilePath());
            if (isFav) {
                database.videoDao().removeFavorite(video.getFilePath());
            } else {
                database.videoDao().addFavorite(new com.reelixy.videoplayer.database.FavoriteEntity(
                        video.getFilePath(), System.currentTimeMillis()));
            }
            runOnUiThread(() -> {
                if (!destroyed) {
                    android.widget.Toast.makeText(this,
                            isFav ? "Removed from favorites" : "Added to favorites",
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        });
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
