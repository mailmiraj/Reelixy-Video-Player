package com.reelixy.videoplayer.activities;

import android.app.PictureInPictureParams;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.content.ContentValues;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.provider.MediaStore;
import android.database.Cursor;
import android.content.ContentUris;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

import com.reelixy.videoplayer.R;
import com.reelixy.videoplayer.database.AppDatabase;
import com.reelixy.videoplayer.database.FavoriteEntity;
import com.reelixy.videoplayer.database.PlaybackHistoryEntity;
import com.reelixy.videoplayer.managers.PlayerManager;
import com.reelixy.videoplayer.player.PlayerState;
import com.reelixy.videoplayer.player.PlaybackQueueManager;
import com.reelixy.videoplayer.utils.PreferenceUtils;
import com.reelixy.videoplayer.utils.TimeUtils;
import com.reelixy.videoplayer.views.GestureOverlayView;
import com.reelixy.videoplayer.player.PlayerGestureController;
import com.reelixy.videoplayer.player.PlayerUiController;
import com.reelixy.videoplayer.adapters.OptionAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The full-screen video player. Owns PlayerManager (playback engine), the
 * gesture overlay, auto-hiding controls, lock mode, PiP, and persistence of
 * watch progress / favorites. Deliberately keeps all playback-engine logic
 * inside PlayerManager — this class only wires UI to it.
 */
public class PlayerActivity extends AppCompatActivity implements GestureOverlayView.Listener {

    public static final String EXTRA_VIDEO_URI = "extra_video_uri";
    public static final String EXTRA_VIDEO_PATH = "extra_video_path";
    public static final String EXTRA_VIDEO_TITLE = "extra_video_title";

    private static final long CONTROLS_AUTO_HIDE_MS = 3000L;
    private static final long SEEK_STEP_MS = 10_000L;
    private static final float[] SPEED_OPTIONS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};

    private PlayerManager playerManager;
    private AppDatabase database;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PlayerView playerView;
    private GestureOverlayView gestureOverlay;
    private View controlsContainer;
    private View lockOverlay;
    private View errorState;
    private TextView tvVideoTitle;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private TextView tvGestureFeedback;
    private TextView tvHudChip;
    private TextView tvStatsHud;
    private TextView tvFocusBadge;
    private TextView tvUpNext;
    private View upNextCard;
    private TextView btnFocusMode;
    private SeekBar seekBar;
    private ImageButton btnPlayPause;
    private ImageButton btnLock;
    private ImageButton btnFullscreen;
    private ProgressBar bufferingIndicator;

    private String videoUriString;
    private String videoPath;
    private String videoTitle;

    private final PlayerState playerState = new PlayerState();
    private final PlaybackQueueManager queueManager = new PlaybackQueueManager();
    private long memoryLoadToken = 0L;
    private boolean isFullscreenLandscape = false;
    private final Runnable pauseDimRunnable = () -> applyPauseDim(true);
    private CountDownTimer sleepTimer;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private long gestureStartPosition = -1L;
    private long gestureTargetPosition = -1L;
    private float gestureAccumulatedDeltaX;
    private boolean zoomGestureActive;
    private boolean restoredUiState;

    private PlayerGestureController gestureController;
    private PlayerUiController uiController;
    private final Runnable loopMonitorRunnable = new Runnable() {
        @Override public void run() {
            if (playerState.loopStartMs >= 0 && playerState.loopEndMs > playerState.loopStartMs && playerManager != null) {
                long position = playerManager.getCurrentPosition();
                if (position >= playerState.loopEndMs) playerManager.seekTo(playerState.loopStartMs);
            }
            mainHandler.postDelayed(this, 250L);
        }
    };

    private final Runnable hideUpNextRunnable = () -> { if (!playerState.destroyed) hideUpNextCard(); };

    private final Runnable progressUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgressUi();
            mainHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        database = AppDatabase.getInstance(this);
        playerManager = new PlayerManager(this);
        restoredUiState = savedInstanceState != null;
        if (savedInstanceState != null) {            playerState.playbackSpeed = savedInstanceState.getFloat("player_speed", 1.0f);
            playerState.zoomScale = savedInstanceState.getFloat("player_zoom", 1.0f);
            playerState.statsHudEnabled = savedInstanceState.getBoolean("player_stats", false);
            playerState.focusMode = savedInstanceState.getBoolean("player_focus", false);
            playerState.locked = savedInstanceState.getBoolean("player_locked", false);
            playerState.loopStartMs = savedInstanceState.getLong("player_loop_start", -1L);
            playerState.loopEndMs = savedInstanceState.getLong("player_loop_end", -1L);
            playerState.shuffleEnabled = savedInstanceState.getBoolean("player_shuffle", false);
            playerState.repeatMode = savedInstanceState.getInt("player_repeat", 0);
            playerState.audioBoost = savedInstanceState.getFloat("player_audio_boost", 1.0f);
        }

        videoUriString = getIntent().getStringExtra(EXTRA_VIDEO_URI);
        videoPath = getIntent().getStringExtra(EXTRA_VIDEO_PATH);
        videoTitle = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);

        restoreVideoMemory();
        bindViews();
        setupPlayer();
        setupControls();
        setupGestures();
        setupUiController();

        if (PreferenceUtils.getBoolean(this, PreferenceUtils.KEY_KEEP_SCREEN_AWAKE, true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        loadUpNextQueue();
        startPlaybackWithResume();
    }


    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        outState.putFloat("player_speed", playerState.playbackSpeed);
        outState.putFloat("player_zoom", playerState.zoomScale);
        outState.putBoolean("player_stats", playerState.statsHudEnabled);
        outState.putBoolean("player_focus", playerState.focusMode);
        outState.putBoolean("player_locked", playerState.locked);
        outState.putLong("player_loop_start", playerState.loopStartMs);
        outState.putLong("player_loop_end", playerState.loopEndMs);
        outState.putBoolean("player_shuffle", playerState.shuffleEnabled);
        outState.putInt("player_repeat", playerState.repeatMode);
        outState.putFloat("player_audio_boost", playerState.audioBoost);
        super.onSaveInstanceState(outState);
    }

    private void bindViews() {
        playerView = findViewById(R.id.playerView);
        gestureOverlay = findViewById(R.id.gestureOverlay);
        controlsContainer = findViewById(R.id.controlsContainer);
        lockOverlay = findViewById(R.id.lockOverlay);
        errorState = findViewById(R.id.errorState);
        tvVideoTitle = findViewById(R.id.tvVideoTitle);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        tvGestureFeedback = findViewById(R.id.tvGestureFeedback);
        tvHudChip = findViewById(R.id.tvHudChip);
        tvStatsHud = findViewById(R.id.tvStatsHud);
        tvFocusBadge = findViewById(R.id.tvFocusBadge);
        upNextCard = findViewById(R.id.upNextCard);
        tvUpNext = findViewById(R.id.tvUpNext);
        btnFocusMode = findViewById(R.id.btnFocusMode);
        seekBar = findViewById(R.id.seekBar);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnLock = findViewById(R.id.btnLock);
        btnFullscreen = findViewById(R.id.btnFullscreen);
        bufferingIndicator = findViewById(R.id.bufferingIndicator);

        tvVideoTitle.setText(videoTitle != null ? videoTitle : "");
    }

    private void setupPlayer() {
        playerManager.init(playerView);
        playerManager.setListener(new PlayerManager.PlaybackListener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                bufferingIndicator.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                if (state == Player.STATE_ENDED) {
                    onPlaybackEnded();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                if (isPlaying) {
                    mainHandler.removeCallbacks(pauseDimRunnable);
                    applyPauseDim(false);
                } else if (playerState.pauseDimEnabled) {
                    mainHandler.removeCallbacks(pauseDimRunnable);
                    mainHandler.postDelayed(pauseDimRunnable, 8000L);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                showPlaybackError(error);
            }

            @Override
            public void onCues(List<String> cueTexts) {
                // Subtitle rendering is handled natively by PlayerView's built-in
                // subtitle view; this hook is available for a custom subtitle
                // overlay (font size / color / position from Settings) if desired.
            }
        });
    }

    private void setupControls() {
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());
        btnPlayPause.setOnClickListener(v -> playerManager.togglePlayPause());
        findViewById(R.id.btnRewind).setOnClickListener(v -> seekRelative(-getSkipDurationMs()));
        findViewById(R.id.btnForward).setOnClickListener(v -> seekRelative(getSkipDurationMs()));

        btnFullscreen.setOnClickListener(v -> toggleOrientation());
        btnLock.setOnClickListener(v -> setLocked(true));
        findViewById(R.id.btnUnlock).setOnClickListener(v -> setLocked(false));

        findViewById(R.id.btnVolume).setOnClickListener(v -> adjustVolume(true));
        findViewById(R.id.btnQuickSettings).setOnClickListener(this::showQuickSettingsMenu);
        findViewById(R.id.btnPlayerMore).setOnClickListener(this::showPlayerOptionsMenu);
        findViewById(R.id.btnUpNext).setOnClickListener(v -> showQueueDialog());
        findViewById(R.id.btnUpNextPlay).setOnClickListener(v -> playNextInQueue());
        btnFocusMode.setOnClickListener(v -> toggleFocusMode());
        findViewById(R.id.btnPip).setOnClickListener(v -> enterPipMode());
        playerView.post(() -> {
            applyZoomSilently(playerState.zoomScale);
        });
        findViewById(R.id.btnOpenWith).setOnClickListener(v -> openWithExternalPlayer());
        findViewById(R.id.btnRetry).setOnClickListener(v -> {
            errorState.setVisibility(View.GONE);
            controlsContainer.setVisibility(View.VISIBLE);
            startPlaybackWithResume();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    long duration = playerManager.getDuration();
                    tvCurrentTime.setText(TimeUtils.formatDuration(duration * progress / 1000));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                playerState.seeking = true;
                cancelAutoHide();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                long duration = playerManager.getDuration();
                long target = duration * seekBar.getProgress() / 1000;
                playerManager.seekTo(target);
                playerState.seeking = false;
                scheduleAutoHide();
            }
        });
    }

    private void setupGestures() {
        gestureController = new PlayerGestureController(gestureOverlay, this);
    }

    private void setupUiController() {
        uiController = new PlayerUiController(new PlayerUiController.Host() {
            @Override public View controlsContainer() { return controlsContainer; }
            @Override public View gestureOverlay() { return gestureOverlay; }
            @Override public View focusBadge() { return tvFocusBadge; }
            @Override public void onVisibilityChanged(boolean visible) {
                if (!visible) tvGestureFeedback.setVisibility(View.GONE);
            }
        }, playerState);
        uiController.setAutoHideMs(PreferenceUtils.getInt(this, PreferenceUtils.KEY_AUTO_HIDE_CONTROLS_MS, (int) CONTROLS_AUTO_HIDE_MS));
    }

    private void applyPauseDim(boolean dimmed) {
        if (playerState.destroyed) return;
        float alpha = dimmed ? 0.72f : 1f;
        playerView.setAlpha(alpha);
        tvFocusBadge.setAlpha(dimmed ? 0.72f : 1f);
    }

    private void resetZoom() {
        playerState.zoomScale = 1.0f;
        playerView.setScaleX(1f);
        playerView.setScaleY(1f);
        playerView.post(() -> {
            playerView.setPivotX(playerView.getWidth() / 2f);
            playerView.setPivotY(playerView.getHeight() / 2f);
        });
        playerView.setClipToOutline(false);
        persistVideoMemory();
    }

    private void applyZoom(float scale) {
        playerState.zoomScale = Math.max(1.0f, Math.min(2.5f, scale));
        playerView.post(() -> {
            playerView.setPivotX(playerView.getWidth() / 2f);
            playerView.setPivotY(playerView.getHeight() / 2f);
            playerView.setScaleX(playerState.zoomScale);
            playerView.setScaleY(playerState.zoomScale);
        });
        showGestureFeedback(String.format(java.util.Locale.US, "Zoom %.2fx", playerState.zoomScale));
    }


    private void showPlaybackError(PlaybackException error) {
        if (playerState.destroyed) return;
        errorState.setVisibility(View.VISIBLE);
        controlsContainer.setVisibility(View.GONE);
        TextView message = findViewById(R.id.tvPlaybackErrorMessage);
        if (message != null) message.setText(error != null && error.getMessage() != null && !error.getMessage().trim().isEmpty()
                ? error.getMessage() : getString(R.string.playback_error_details));
    }

    private void openWithExternalPlayer() {
        if (videoUriString == null || videoUriString.trim().isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(videoUriString), "video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.open_with)));
        } catch (ActivityNotFoundException ignored) {
            showGestureFeedback(getString(R.string.no_external_player));
        }
    }

    private void startPlaybackWithResume() {
        if (videoUriString == null || videoUriString.trim().isEmpty()) {
            errorState.setVisibility(View.VISIBLE);
            controlsContainer.setVisibility(View.GONE);
            return;
        }

        mainHandler.removeCallbacks(progressUpdateRunnable);
        final boolean shouldResume = PreferenceUtils.getBoolean(this, PreferenceUtils.KEY_RESUME_PLAYBACK, true);
        if (!shouldResume || videoPath == null) {
            beginPlayback(0L);
            return;
        }

        ioExecutor.execute(() -> {
            long startPosition = 0L;
            PlaybackHistoryEntity existing = database.videoDao().getHistoryForVideo(videoPath);
            if (existing != null && !existing.completed) {
                startPosition = Math.max(0L, existing.lastPositionMs);
            }
            final long resumePosition = startPosition;
            mainHandler.post(() -> {
                if (!playerState.destroyed) beginPlayback(resumePosition);
            });
        });
    }

    private void beginPlayback(long startPosition) {
        try {
            errorState.setVisibility(View.GONE);
            controlsContainer.setVisibility(View.VISIBLE);
            playerManager.play(Uri.parse(videoUriString), startPosition);
            playerManager.setPlaybackSpeed(playerState.playbackSpeed);
            playerManager.setSubtitlesEnabled(PreferenceUtils.getBoolean(this, PreferenceUtils.KEY_DEFAULT_SUBTITLES, true));
            playerManager.setPlayerVolume(playerState.audioBoost);
            mainHandler.removeCallbacks(progressUpdateRunnable);
            mainHandler.post(progressUpdateRunnable);
            mainHandler.removeCallbacks(loopMonitorRunnable);
        mainHandler.removeCallbacks(hideUpNextRunnable);
            mainHandler.post(loopMonitorRunnable);
            scheduleAutoHide();
        } catch (RuntimeException e) {
            errorState.setVisibility(View.VISIBLE);
            controlsContainer.setVisibility(View.GONE);
        }
    }

    private void onPlaybackEnded() {
        saveProgress(true);
        if (playerState.repeatMode == 1) {
            playerState.autoNextHandled = false;
            beginPlayback(0L);
            return;
        }
        if (playerState.autoNextHandled) return;
        playerState.autoNextHandled = true;
        if (PreferenceUtils.getBoolean(this, PreferenceUtils.KEY_AUTO_PLAY_NEXT, true) && !queueManager.isEmpty()) {
            PlaybackQueueManager.Item next = queueManager.peekNext();
            if (next != null) {
                playNextInQueue();
                return;
            }
            if (playerState.repeatMode == 2) {
                queueManager.select(-1);
                playerState.queueIndex = -1;
                playNextInQueue();
                return;
            }
        }
        hideUpNextCard();
    }

    private void loadUpNextQueue() {
        final String currentUri = videoUriString;
        ioExecutor.execute(() -> {
            final List<PlaybackQueueManager.Item> queue = PlaybackQueueManager.discoverUpNext(getContentResolver(), currentUri);
            mainHandler.post(() -> {
                if (playerState.destroyed) return;
                queueManager.replace(queue);
                playerState.queueIndex = -1;
                playerState.upNextShownForVideo = false;
                hideUpNextCard();
            });
        });
    }

    private void showUpNextCard(boolean delayed) {
        if (upNextCard == null || queueManager.isEmpty()) {
            hideUpNextCard();
            return;
        }
        PlaybackQueueManager.Item item = queueManager.peekNext();
        tvUpNext.setText(getString(R.string.player_next_up) + " • " + item.title);
        upNextCard.setVisibility(View.VISIBLE);
        mainHandler.removeCallbacks(hideUpNextRunnable);
        if (delayed) mainHandler.postDelayed(hideUpNextRunnable, 10_500L);
    }

    private void updateUpNextCountdown(long remainingMs) {
        if (upNextCard == null || queueManager.isEmpty()) return;
        PlaybackQueueManager.Item item = queueManager.peekNext();
        long seconds = Math.max(1L, (remainingMs + 999L) / 1000L);
        tvUpNext.setText(getString(R.string.player_next_up) + " • " + item.title + "  •  in " + seconds + "s");
    }

    private void hideUpNextCard() {
        if (upNextCard != null) upNextCard.setVisibility(View.GONE);
        mainHandler.removeCallbacks(hideUpNextRunnable);
    }

    private void playNextInQueue() {
        PlaybackQueueManager.Item next = queueManager.advance();
        if (next == null) { showUpNextCard(false); return; }
        playerState.queueIndex = queueManager.getCurrentIndex();
        videoUriString = next.uri;
        videoPath = next.path;
        videoTitle = next.title;
        tvVideoTitle.setText(videoTitle);
        playerState.autoNextHandled = false;
        playerState.upNextShownForVideo = false;
        hideUpNextCard();
        playerState.focusMode = false;
        playerState.statsHudEnabled = false;
        playerState.loopStartMs = -1L;
        playerState.loopEndMs = -1L;
        tvStatsHud.setVisibility(View.GONE);
        tvFocusBadge.setVisibility(View.GONE);
        restoredUiState = false;
        restoreVideoMemory();
        showGestureFeedback("Next • " + videoTitle);
        beginPlayback(0L);
        hideUpNextCard();
    }

    private void showQueueDialog() {
        if (queueManager.isEmpty()) {
            showGestureFeedback("Queue is empty");
            return;
        }
        List<String> labels = new ArrayList<>();
        for (int i = playerState.queueIndex + 1; i < queueManager.size(); i++) {
            PlaybackQueueManager.Item item = queueManager.get(i);
            labels.add((i - playerState.queueIndex) + ". " + item.title);
        }
        if (labels.isEmpty()) { showGestureFeedback("No more videos"); return; }
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.player_next_up)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    int target = playerState.queueIndex + 1 + which;
                    if (target < queueManager.size()) {
                        playerState.queueIndex = target - 1;
                        playNextInQueue();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    // ---- Gesture overlay callbacks ----

    @Override
    public void onSingleTap() {
        if (playerState.locked) return;
        if (playerState.focusMode) {
            toggleFocusMode();
            return;
        }
        if (playerState.controlsVisible) hideControls(); else showControls();
    }

    @Override
    public void onDoubleTapLeft() {
        if (playerState.locked) return;
        long step = getSkipDurationMs();
        seekRelative(-step);
        showGestureFeedback(formatSkip(-step));
    }

    @Override
    public void onDoubleTapCenter() {
        if (playerState.locked) return;
        if (playerState.zoomScale > 1.01f) {
            resetZoom();
            showGestureFeedback("Zoom 1.0x");
        } else {
            playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            applyZoom(2.0f);
            persistVideoMemory();
        }
    }

    @Override
    public void onDoubleTapRight() {
        if (playerState.locked) return;
        long step = getSkipDurationMs();
        seekRelative(step);
        showGestureFeedback(formatSkip(step));
    }

    @Override
    public void onVerticalSwipeLeft(float deltaY, boolean isStart) {
        if (playerState.locked) return;
        adjustBrightness(-deltaY);
    }

    @Override
    public void onVerticalSwipeRight(float deltaY, boolean isStart) {
        if (playerState.locked) return;
        adjustVolumeByDelta(-deltaY);
    }

    @Override
    public void onHorizontalSwipe(float deltaX, boolean isStart) {
        if (playerState.locked) return;
        if (isStart || gestureStartPosition < 0) {
            gestureStartPosition = playerManager.getCurrentPosition();
            gestureAccumulatedDeltaX = 0f;
        }
        gestureAccumulatedDeltaX += deltaX;
        long deltaMs = (long) (gestureAccumulatedDeltaX * 200);
        long duration = playerManager.getDuration();
        long target = Math.max(0L, gestureStartPosition + deltaMs);
        if (duration > 0) target = Math.min(target, duration);
        gestureTargetPosition = target;
        showGestureFeedback(TimeUtils.formatDuration(target));
    }

    @Override
    public void onPinchZoom(float scaleFactor, boolean isStart) {
        if (playerState.locked) return;
        if (isStart) {
            zoomGestureActive = true;
            showControls();
            return;
        }
        applyZoom(playerState.zoomScale * scaleFactor);
    }

    @Override
    public void onGestureEnd() {
        final boolean wasZoomGesture = zoomGestureActive;
        if (!playerState.locked && gestureTargetPosition >= 0) {
            playerManager.seekTo(gestureTargetPosition);
        }
        gestureStartPosition = -1L;
        gestureTargetPosition = -1L;
        gestureAccumulatedDeltaX = 0f;
        zoomGestureActive = false;
        tvGestureFeedback.setVisibility(View.GONE);
        if (wasZoomGesture) persistVideoMemory();
    }

    // ---- Playback helpers ----

    private long getSkipDurationMs() {
        int configured = PreferenceUtils.getInt(this, PreferenceUtils.KEY_SKIP_DURATION_MS, (int) SEEK_STEP_MS);
        return Math.max(1_000L, Math.min(120_000L, configured));
    }

    private String formatSkip(long deltaMs) {
        long seconds = Math.abs(deltaMs) / 1000L;
        if (seconds % 60L == 0L) return (deltaMs > 0 ? "+" : "-") + (seconds / 60L) + "m";
        return (deltaMs > 0 ? "+" : "-") + seconds + "s";
    }

    private void seekRelative(long deltaMs) {
        playerManager.seekRelative(deltaMs);
        showGestureFeedback((deltaMs > 0 ? "+" : "") + (deltaMs / 1000) + "s");
    }

    private void updateProgressUi() {
        if (playerState.seeking) return;
        long duration = playerManager.getDuration();
        long position = playerManager.getCurrentPosition();

        if (!queueManager.isEmpty() && duration > 0) {
            long remainingMs = Math.max(0L, duration - position);
            if (remainingMs > 0L && remainingMs <= 10_000L) {
                if (!playerState.upNextShownForVideo) {
                    playerState.upNextShownForVideo = true;
                    showUpNextCard(false);
                }
                updateUpNextCountdown(remainingMs);
            } else if (remainingMs > 10_000L && playerState.upNextShownForVideo) {
                playerState.upNextShownForVideo = false;
                hideUpNextCard();
            }
        } else if (queueManager.isEmpty()) {
            playerState.upNextShownForVideo = false;
            hideUpNextCard();
        }

        tvCurrentTime.setText(TimeUtils.formatDuration(position));
        tvTotalTime.setText(TimeUtils.formatDuration(duration));
        if (duration > 0) {
            seekBar.setProgress((int) (position * 1000 / duration));
            int bufferedPercent = (int) Math.max(0, Math.min(1000, playerManager.getBufferedPosition() * 1000 / duration));
            seekBar.setSecondaryProgress(bufferedPercent);
        }
        int bufferedPct = duration > 0 ? (int) Math.max(0, Math.min(100, playerManager.getBufferedPosition() * 100 / duration)) : 0;
        String loopLabel = (playerState.loopStartMs >= 0 && playerState.loopEndMs > playerState.loopStartMs) ? " • A–B" : "";
        tvHudChip.setText(String.format(java.util.Locale.US, "%.2fx • %d%% BUFFERED%s", playerManager.getPlaybackSpeed(), bufferedPct, loopLabel));
        if (playerState.statsHudEnabled) {
            tvStatsHud.setText(String.format(java.util.Locale.US,
                    "BUFFER %d%%\nSPEED %.2fx\nZOOM %.2fx\nPOS %s",
                    bufferedPct, playerManager.getPlaybackSpeed(), playerState.zoomScale, TimeUtils.formatDuration(position)));
        }
    }

    private void saveProgress(boolean completed) {
        if (videoPath == null || playerState.destroyed) return;
        long position = playerManager.getCurrentPosition();
        long duration = playerManager.getDuration();
        ioExecutor.execute(() -> database.videoDao().upsertHistory(new PlaybackHistoryEntity(
                videoPath, videoTitle, position, duration, System.currentTimeMillis(), completed)));
    }

    // ---- Controls visibility ----

    private void showControls() {
        if (uiController != null) uiController.show(true);
    }

    private void hideControls() {
        if (uiController != null) uiController.hide();
    }

    private void scheduleAutoHide() {
        if (uiController != null) uiController.show(true);
    }

    private void cancelAutoHide() {
        if (uiController != null) uiController.cancelAutoHide();
    }

    private void showGestureFeedback(String text) {
        tvGestureFeedback.setText(text);
        tvGestureFeedback.setVisibility(View.VISIBLE);
    }

    // ---- Lock mode ----

    private void setLocked(boolean locked) {
        lockOverlay.setVisibility(locked ? View.VISIBLE : View.GONE);
        if (uiController != null) uiController.setLocked(locked);
    }

    // ---- Orientation / fullscreen ----

    private void toggleOrientation() {
        isFullscreenLandscape = !isFullscreenLandscape;
        setRequestedOrientation(isFullscreenLandscape
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        btnFullscreen.setImageResource(landscape ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
        applyImmersiveMode(landscape);
        // Playback position/state is preserved automatically: PlayerManager and
        // its ExoPlayer instance are not recreated on rotation because this
        // activity declares android:configChanges in the manifest.
    }

    private void applyImmersiveMode(boolean immersive) {
        View decorView = getWindow().getDecorView();
        if (immersive) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    // ---- Volume / brightness gestures ----

    private void adjustVolume(boolean toggleMute) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                toggleMute ? AudioManager.ADJUST_TOGGLE_MUTE : AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI);
    }

    private void adjustVolumeByDelta(float delta) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        int direction = delta > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
        int current = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int percent = max > 0 ? (current * 100 / max) : 0;
        showGestureFeedback("Volume " + percent + "%");
    }

    private void adjustBrightness(float delta) {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        float current = params.screenBrightness;
        if (current < 0) current = 0.5f; // system default sentinel
        current += delta / 1000f;
        current = Math.max(0.01f, Math.min(1f, current));
        params.screenBrightness = current;
        getWindow().setAttributes(params);
        showGestureFeedback("Brightness " + (int) (current * 100) + "%");
    }

    // ---- Advanced controls: production bottom-sheet system ----

    private void cycleRepeatMode() {
        playerState.repeatMode = (playerState.repeatMode + 1) % 3;
        updateRepeatModeUi();
        String label = playerState.repeatMode == 0 ? "Repeat off"
                : playerState.repeatMode == 1 ? "Repeat one" : "Repeat all";
        showGestureFeedback(label);
    }

    private void toggleShuffleUpcoming() {
        playerState.shuffleEnabled = !playerState.shuffleEnabled;
        if (playerState.shuffleEnabled && queueManager != null) {
            queueManager.shuffleUpcoming();
        }
        updateShuffleUi();
        showGestureFeedback(playerState.shuffleEnabled ? "Shuffle upcoming on" : "Shuffle upcoming off");
    }

    private void updateRepeatModeUi() {
        if (tvHudChip == null) return;
        switch (playerState.repeatMode) {
            case 1: tvHudChip.setText(R.string.player_repeat_one_hud); break;
            case 2: tvHudChip.setText(R.string.player_repeat_all_hud); break;
            default: tvHudChip.setText(playerState.shuffleEnabled ? "SHUFFLE" : "READY");
        }
    }

    private void updateShuffleUi() {
        if (tvHudChip == null || playerState.repeatMode != 0) return;
        tvHudChip.setText(playerState.shuffleEnabled ? "SHUFFLE" : "READY");
    }

    private void showQuickSettingsMenu(View anchor) {
        showSpeedMenu();
    }

    private void showPlayerOptionsMenu(View anchor) {
        final String[] labels = {
                "Playback speed",
                "Subtitles",
                "Audio track",
                "Zoom & resize",
                "Sleep timer",
                "A–B repeat",
                "Cinematic preset",
                "Frame step",
                "Playback stats",
                "Audio boost",
                "Quick frame capture",
                "Save bookmark",
                "My bookmarks",
                "Up Next queue",
                "Repeat mode",
                "Shuffle upcoming",
                "Video information"
        };
        showOptionSheet("Player controls", Arrays.asList(labels), -1, index -> {
            switch (index) {
                case 0: showSpeedMenu(); break;
                case 1: showSubtitleMenu(); break;
                case 2: showAudioTrackMenu(); break;
                case 3: showZoomMenu(); break;
                case 4: showSleepTimerMenu(); break;
                case 5: showABRepeatMenu(); break;
                case 6: showCinematicPresetMenu(); break;
                case 7: showFrameStepMenu(); break;
                case 8: togglePlaybackStatsHud(); break;
                case 9: showAudioBoostMenu(); break;
                case 10: capturePlayerFrame(); break;
                case 11: saveBookmark(); break;
                case 12: showBookmarks(); break;
                case 13: showQueueDialog(); break;
                case 14: cycleRepeatMode(); break;
                case 15: toggleShuffleUpcoming(); break;
                case 16: showVideoInfoDialog(); break;
            }
        });
    }

    private void showOptionSheet(String title, List<String> labels, int selectedIndex,
                                 OptionAdapter.OnOptionSelectedListener listener) {
        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.bottomsheet_option_list, null, false);
        TextView titleView = content.findViewById(R.id.tvSheetTitle);
        TextView subtitleView = content.findViewById(R.id.tvSheetSubtitle);
        View closeButton = content.findViewById(R.id.btnSheetClose);
        androidx.recyclerview.widget.RecyclerView rv = content.findViewById(R.id.rvOptions);
        titleView.setText(title);
        if (subtitleView != null) subtitleView.setText(R.string.player_select_control);
        if (closeButton != null) closeButton.setOnClickListener(v -> dialog.dismiss());
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setHasFixedSize(true);
        rv.setAdapter(new OptionAdapter(labels, selectedIndex, index -> {
            dialog.dismiss();
            listener.onOptionSelected(index);
        }));
        rv.post(() -> {
            int rowHeight = (int) (56 * getResources().getDisplayMetrics().density);
            int minHeight = (int) (112 * getResources().getDisplayMetrics().density);
            int maxHeight = (int) (420 * getResources().getDisplayMetrics().density);
            int desired = rowHeight * Math.max(1, labels.size());
            android.view.ViewGroup.LayoutParams lp = rv.getLayoutParams();
            lp.height = Math.min(maxHeight, Math.max(minHeight, desired));
            rv.setLayoutParams(lp);
        });
        dialog.setContentView(content);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setDimAmount(0.55f);
        }
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
        });
        dialog.show();
    }

    private void showSpeedMenu() {
        List<String> labels = new ArrayList<>();
        int selected = 0;
        float closest = Float.MAX_VALUE;
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            float value = SPEED_OPTIONS[i];
            labels.add(String.format(java.util.Locale.US, "%.2gx — %s", value,
                    value == 1.0f ? "Normal" : "Custom"));
            float distance = Math.abs(playerState.playbackSpeed - value);
            if (distance < closest) { closest = distance; selected = i; }
        }
        final int defaultSelected = selected;
        showOptionSheet("Playback speed", labels, defaultSelected, index -> {
            playerState.playbackSpeed = SPEED_OPTIONS[index];
            playerManager.setPlaybackSpeed(playerState.playbackSpeed);
            persistVideoMemory();
        });
    }

    private void showSubtitleMenu() {
        List<String> labels = Arrays.asList("Off", "On");
        showOptionSheet("Subtitles", labels, -1, index ->
                playerManager.setSubtitlesEnabled(index == 1));
    }

    private void showAudioTrackMenu() {
        List<PlayerManager.AudioTrackOption> tracks = playerManager.getAudioTracks();
        if (tracks == null || tracks.isEmpty()) {
            showGestureFeedback("No alternate audio tracks");
            return;
        }
        List<String> labels = new ArrayList<>();
        for (PlayerManager.AudioTrackOption track : tracks) labels.add(track.label);
        showOptionSheet("Audio track", labels, -1, index ->
                playerManager.selectAudioTrack(tracks.get(index)));
    }

    private void showZoomMenu() {
        List<String> labels = Arrays.asList("Fit", "Fill", "Crop", "1.0x", "1.25x", "1.5x", "2.0x", "2.5x", "Reset");
        int selected = playerState.zoomScale == 1f ? 3
                : playerState.zoomScale == 1.25f ? 4
                : playerState.zoomScale == 1.5f ? 5
                : playerState.zoomScale == 2f ? 6
                : playerState.zoomScale == 2.5f ? 7 : 0;
        showOptionSheet("Zoom & resize", labels, selected, index -> {
            if (index == 0 || index == 1 || index == 2 || index == 8) {
                playerView.setResizeMode(index == 0 || index == 8
                        ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        : index == 1
                        ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        : androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                resetZoom();
            } else {
                playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
                float scale = index == 3 ? 1f : index == 4 ? 1.25f : index == 5 ? 1.5f : index == 6 ? 2f : 2.5f;
                applyZoom(scale);
                persistVideoMemory();
            }
        });
    }

    private void showFrameStepMenu() {
        List<String> labels = Arrays.asList("Previous frame", "Next frame");
        showOptionSheet("Frame step", labels, -1, index -> {
            playerManager.stepFrame(index == 1);
            showGestureFeedback(index == 1 ? "Next frame" : "Previous frame");
        });
    }

    private void showAudioBoostMenu() {
        List<String> labels = Arrays.asList("100% — Normal", "110%", "125%", "150%");
        showOptionSheet("Audio boost", labels, -1, index -> {
            float[] values = {1.0f, 1.1f, 1.25f, 1.5f};
            playerState.audioBoost = values[index];
            playerManager.setPlayerVolume(playerState.audioBoost);
            showGestureFeedback("Audio " + labels.get(index));
        });
    }

    private void showCinematicPresetMenu() {
        List<String> labels = Arrays.asList("Standard", "Cinema", "Anime", "Sport");
        showOptionSheet("Cinematic preset", labels, -1, index -> {
            playerView.setResizeMode(index == 3
                    ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    : index == 2
                    ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    : androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            resetZoom();
            float speed = index == 1 ? 0.96f : index == 3 ? 1.15f : 1.0f;
            playerState.playbackSpeed = speed;
            playerManager.setPlaybackSpeed(speed);
            showGestureFeedback("Preset applied");
        });
    }

    private void togglePlaybackStatsHud() {
        playerState.statsHudEnabled = !playerState.statsHudEnabled;
        tvStatsHud.setVisibility(playerState.statsHudEnabled ? View.VISIBLE : View.GONE);
        showGestureFeedback(playerState.statsHudEnabled ? "Stats HUD ON" : "Stats HUD OFF");
    }

    private void capturePlayerFrame() {
        try {
            Bitmap bitmap = Bitmap.createBitmap(playerRootWidth(), playerRootHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            findViewById(R.id.playerRoot).draw(canvas);
            String fileName = "VideoPlayer_" + System.currentTimeMillis() + ".png";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VideoPlayer");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out != null) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                showGestureFeedback("Frame saved");
            } else showGestureFeedback("Snapshot failed");
        } catch (Exception e) { showGestureFeedback("Snapshot unavailable"); }
    }

    private int playerRootWidth() { return Math.max(1, findViewById(R.id.playerRoot).getWidth()); }
    private int playerRootHeight() { return Math.max(1, findViewById(R.id.playerRoot).getHeight()); }

    private void showSleepTimerMenu() {
        List<String> labels = Arrays.asList("Off", "15 min", "30 min", "45 min", "60 min");
        showOptionSheet("Sleep timer", labels, -1, index -> {
            if (sleepTimer != null) sleepTimer.cancel();
            long[] minutesMs = {0, 15 * 60_000L, 30 * 60_000L, 45 * 60_000L, 60 * 60_000L};
            long ms = minutesMs[index];
            if (ms > 0) {
                sleepTimer = new CountDownTimer(ms, ms) {
                    @Override public void onTick(long millisUntilFinished) {}
                    @Override public void onFinish() { if (playerManager.isPlaying()) playerManager.togglePlayPause(); }
                }.start();
                showGestureFeedback("Sleep timer " + labels.get(index));
            }
        });
    }

    private void showABRepeatMenu() {
        List<String> labels = Arrays.asList("Set A at current position", "Set B at current position", "Clear A–B loop");
        showOptionSheet("A–B repeat", labels, -1, index -> {
            long pos = playerManager.getCurrentPosition();
            if (index == 0) {
                playerState.loopStartMs = pos;
                if (playerState.loopEndMs > 0 && playerState.loopEndMs <= playerState.loopStartMs) playerState.loopEndMs = -1L;
                showGestureFeedback("A = " + TimeUtils.formatDuration(pos));
            } else if (index == 1) {
                if (playerState.loopStartMs < 0) playerState.loopStartMs = Math.max(0, pos - 10_000L);
                playerState.loopEndMs = pos;
                if (playerState.loopEndMs <= playerState.loopStartMs) { playerState.loopStartMs = -1L; playerState.loopEndMs = -1L; }
                else showGestureFeedback("B = " + TimeUtils.formatDuration(pos));
            } else {
                playerState.loopStartMs = -1L;
                playerState.loopEndMs = -1L;
                showGestureFeedback("A–B cleared");
            }
        });
    }

    private void toggleFocusMode() {
        if (uiController == null) return;
        if (playerState.focusMode) {
            uiController.exitFocus();
            btnFocusMode.setText(R.string.player_focus);
        } else {
            lockOverlay.setVisibility(View.GONE);
            uiController.enterFocus();
            btnFocusMode.setText(R.string.player_exit_focus);
        }
    }

    private String bookmarkKey() {
        return "bookmarks:" + (videoPath == null ? String.valueOf(videoUriString) : videoPath);
    }

    private void saveBookmark() {
        if (videoPath == null || videoPath.trim().isEmpty()) {
            showGestureFeedback("Bookmark unavailable");
            return;
        }
        long pos = playerManager.getCurrentPosition();
        ioExecutor.execute(() -> {
            database.videoDao().addBookmark(videoPath, pos, System.currentTimeMillis());
            mainHandler.post(() -> {
                if (!playerState.destroyed) showGestureFeedback("Bookmark saved • " + TimeUtils.formatDuration(pos));
            });
        });
    }

    private void showBookmarks() {
        if (videoPath == null || videoPath.trim().isEmpty()) {
            showGestureFeedback("Bookmarks unavailable");
            return;
        }
        ioExecutor.execute(() -> {
            List<com.reelixy.videoplayer.database.BookmarkEntity> bookmarks = database.videoDao().getBookmarksSync(videoPath);
            mainHandler.post(() -> {
                if (playerState.destroyed) return;
                if (bookmarks.isEmpty()) {
                    showGestureFeedback("No bookmarks yet");
                    return;
                }
                final List<com.reelixy.videoplayer.database.BookmarkEntity> snapshot = new ArrayList<>(bookmarks);
                String[] items = new String[snapshot.size()];
                for (int i = 0; i < snapshot.size(); i++) {
                    items[i] = TimeUtils.formatDuration(snapshot.get(i).positionMs);
                }
                ListView listView = new ListView(this);
                listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items));
                android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                        .setTitle(R.string.player_my_bookmarks)
                        .setView(listView)
                        .setNegativeButton("Close", null)
                        .create();
                listView.setOnItemClickListener((parent, view, which, id) -> {
                    playerManager.seekTo(snapshot.get(which).positionMs);
                    showGestureFeedback("Jumped to " + items[which]);
                    dialog.dismiss();
                });
                dialog.show();
            });
        });
    }

    private String memoryKey(String suffix) {
        return "video_memory:" + suffix + ":" + (videoPath == null ? String.valueOf(videoUriString) : videoPath);
    }

    private void restoreVideoMemory() {
        if (restoredUiState) {
            if (playerManager != null) playerManager.setPlaybackSpeed(playerState.playbackSpeed);
            return;
        }
        playerState.playbackSpeed = PreferenceUtils.getFloat(this, PreferenceUtils.KEY_DEFAULT_SPEED, 1.0f);
        playerState.pauseDimEnabled = PreferenceUtils.getBoolean(this, PreferenceUtils.KEY_PAUSE_DIM, true);
        playerState.audioBoost = Math.max(1.0f, Math.min(1.5f, PreferenceUtils.getFloat(this, PreferenceUtils.KEY_DEFAULT_AUDIO_BOOST, 1.0f)));
        playerState.zoomScale = PreferenceUtils.getFloat(this, PreferenceUtils.KEY_DEFAULT_ZOOM, 1.0f);
        if (playerManager != null) playerManager.setPlayerVolume(playerState.audioBoost);
        if (videoPath == null || videoPath.trim().isEmpty()) return;
        final String requestedPath = videoPath;
        final long requestToken = ++memoryLoadToken;
        ioExecutor.execute(() -> {
            com.reelixy.videoplayer.database.VideoPreferenceEntity pref = database.videoDao().getVideoPreferenceSync(requestedPath);
            mainHandler.post(() -> {
                if (playerState.destroyed || requestToken != memoryLoadToken || !requestedPath.equals(videoPath) || pref == null) return;
                playerState.playbackSpeed = Math.max(0.25f, Math.min(4.0f, pref.speed));
                playerState.zoomScale = Math.max(1.0f, Math.min(2.5f, pref.zoom));
                if (playerManager != null) playerManager.setPlaybackSpeed(playerState.playbackSpeed);
                if (playerView != null) applyZoomSilently(playerState.zoomScale);
            });
        });
    }

    private void applyZoomSilently(float scale) {
        playerState.zoomScale = Math.max(1.0f, Math.min(2.5f, scale));
        playerView.post(() -> {
            playerView.setPivotX(playerView.getWidth() / 2f);
            playerView.setPivotY(playerView.getHeight() / 2f);
            playerView.setScaleX(playerState.zoomScale);
            playerView.setScaleY(playerState.zoomScale);
        });
    }

    private void persistVideoMemory() {
        if (videoPath == null || videoPath.trim().isEmpty()) return;
        final float speed = playerState.playbackSpeed;
        final float zoom = playerState.zoomScale;
        ioExecutor.execute(() -> database.videoDao().upsertVideoPreference(
                videoPath, speed, zoom, System.currentTimeMillis()));
    }

    private void showVideoInfoDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.video_information);
        StringBuilder message = new StringBuilder();
        message.append(getString(R.string.info_name)).append(": ").append(videoTitle).append("\n");
        message.append(getString(R.string.info_duration)).append(": ")
                .append(TimeUtils.formatDuration(playerManager.getDuration())).append("\n");
        message.append(getString(R.string.info_location)).append(": ").append(videoPath);
        builder.setMessage(message.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    // ---- Picture-in-Picture ----

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9))
                    .build();
            enterPictureInPictureMode(params);
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && PreferenceUtils.getBoolean(this, PreferenceUtils.KEY_AUTO_PIP, true)
                && playerManager.isPlaying()
                && !playerState.locked) {
            enterPipMode();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            if (uiController != null) uiController.hide();
            if (gestureController != null) gestureController.setEnabled(false);
        } else {
            if (gestureController != null) gestureController.setEnabled(!playerState.locked);
            if (!playerState.focusMode && !playerState.locked && uiController != null) uiController.show(true);
        }
    }

    // ---- Lifecycle ----

    @Override
    protected void onPause() {
        saveProgress(false);
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (playerManager != null) {
            mainHandler.removeCallbacks(progressUpdateRunnable);
            mainHandler.post(progressUpdateRunnable);
        }
        if (uiController != null && !playerState.locked && !playerState.focusMode) uiController.show(true);
    }

    @Override
    protected void onStop() {
        mainHandler.removeCallbacks(progressUpdateRunnable);
        if (uiController != null) uiController.cancelAutoHide();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        playerState.destroyed = true;
        if (sleepTimer != null) sleepTimer.cancel();
        mainHandler.removeCallbacksAndMessages(null);
        applyPauseDim(false);
        mainHandler.removeCallbacks(loopMonitorRunnable);
        mainHandler.removeCallbacks(hideUpNextRunnable);
        if (gestureController != null) gestureController.release();
        if (uiController != null) uiController.release();
        persistVideoMemory();
        ioExecutor.shutdownNow();
        if (playerManager != null) playerManager.release();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (playerState.locked) {
            // Locked screens require an explicit unlock tap; ignore back to
            // avoid an accidental exit while the phone is in a pocket/bag.
            return;
        }
        if (isFullscreenLandscape) {
            toggleOrientation();
            return;
        }
        super.onBackPressed();
    }
}
