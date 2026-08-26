package com.reelixy.videoplayer.managers;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the ExoPlayer instance and all playback-engine concerns: init, play,
 * pause, seek, speed, track selection, subtitle delivery, and release.
 * Activities talk to this class only — they never touch ExoPlayer directly.
 */
public class PlayerManager {

    public interface PlaybackListener {
        void onPlaybackStateChanged(int state); // Player.STATE_*
        void onIsPlayingChanged(boolean isPlaying);
        void onPlayerError(PlaybackException error);
        void onCues(List<String> cueTexts);
    }

    public static class AudioTrackOption {
        public final int rendererIndex;
        public final int groupIndex;
        public final int trackIndex;
        public final String label;
        public AudioTrackOption(int rendererIndex, int groupIndex, int trackIndex, String label) {
            this.rendererIndex = rendererIndex;
            this.groupIndex = groupIndex;
            this.trackIndex = trackIndex;
            this.label = label;
        }
    }

    private ExoPlayer exoPlayer;
    private DefaultTrackSelector trackSelector;
    private PlaybackListener listener;
    private final Context appContext;

    public PlayerManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void init(PlayerView playerView) {
        if (exoPlayer != null) return; // already initialized

        trackSelector = new DefaultTrackSelector(appContext);
        exoPlayer = new ExoPlayer.Builder(appContext)
                .setTrackSelector(trackSelector)
                .build();

        playerView.setPlayer(exoPlayer);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (listener != null) listener.onPlaybackStateChanged(playbackState);
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (listener != null) listener.onIsPlayingChanged(isPlaying);
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                if (listener != null) listener.onPlayerError(error);
            }

            @Override
            public void onCues(CueGroup cueGroup) {
                if (listener == null) return;
                List<String> texts = new ArrayList<>();
                for (int i = 0; i < cueGroup.cues.size(); i++) {
                    CharSequence text = cueGroup.cues.get(i).text;
                    if (text != null) texts.add(text.toString());
                }
                listener.onCues(texts);
            }
        });
    }

    public void setListener(PlaybackListener listener) {
        this.listener = listener;
    }

    public void play(Uri videoUri, long startPositionMs) {
        if (exoPlayer == null) return;
        MediaItem item = MediaItem.fromUri(videoUri);
        exoPlayer.setMediaItem(item, startPositionMs);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
    }

    public void togglePlayPause() {
        if (exoPlayer == null) return;
        if (exoPlayer.isPlaying()) {
            exoPlayer.pause();
        } else {
            exoPlayer.play();
        }
    }

    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }

    public void seekTo(long positionMs) {
        if (exoPlayer != null) exoPlayer.seekTo(positionMs);
    }

    public void seekRelative(long deltaMs) {
        if (exoPlayer == null) return;
        long target = exoPlayer.getCurrentPosition() + deltaMs;
        target = Math.max(0, Math.min(target, getDuration()));
        exoPlayer.seekTo(target);
    }

    public long getCurrentPosition() {
        return exoPlayer != null ? exoPlayer.getCurrentPosition() : 0;
    }

    public long getDuration() {
        if (exoPlayer == null) return 0;
        long duration = exoPlayer.getDuration();
        return duration == C.TIME_UNSET ? 0 : duration;
    }

    public long getBufferedPosition() {
        return exoPlayer != null ? exoPlayer.getBufferedPosition() : 0;
    }

    public void setPlaybackSpeed(float speed) {
        if (exoPlayer != null) exoPlayer.setPlaybackParameters(
                new androidx.media3.common.PlaybackParameters(speed));
    }

    public float getPlaybackSpeed() {
        return exoPlayer != null ? exoPlayer.getPlaybackParameters().speed : 1.0f;
    }

    /** Step one approximate video frame while paused. Uses a conservative 40ms
     * cadence that works well across common 24/25/30fps material. */
    public void stepFrame(boolean forward) {
        if (exoPlayer == null) return;
        if (exoPlayer.isPlaying()) exoPlayer.pause();
        long stepMs = 40L;
        long target = exoPlayer.getCurrentPosition() + (forward ? stepMs : -stepMs);
        target = Math.max(0L, Math.min(target, getDuration()));
        exoPlayer.seekTo(target);
    }

    public void setPlayerVolume(float volume) {
        if (exoPlayer != null) exoPlayer.setVolume(Math.max(0f, Math.min(1.5f, volume)));
    }

    public float getPlayerVolume() {
        return exoPlayer != null ? exoPlayer.getVolume() : 1.0f;
    }

    /** Returns available audio track options for the current media. */
    public List<AudioTrackOption> getAudioTracks() {
        List<AudioTrackOption> options = new ArrayList<>();
        if (exoPlayer == null) return options;
        Tracks tracks = exoPlayer.getCurrentTracks();
        for (int rendererIndex = 0; rendererIndex < tracks.getGroups().size(); rendererIndex++) {
            Tracks.Group group = tracks.getGroups().get(rendererIndex);
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                androidx.media3.common.Format format = group.getTrackFormat(trackIndex);
                String label = format.language != null ? format.language : ("Track " + (trackIndex + 1));
                options.add(new AudioTrackOption(rendererIndex, rendererIndex, trackIndex, label));
            }
        }
        return options;
    }

    public void selectAudioTrack(AudioTrackOption option) {
        if (exoPlayer == null || trackSelector == null) return;
        Tracks tracks = exoPlayer.getCurrentTracks();
        if (option.groupIndex >= tracks.getGroups().size()) return;
        Tracks.Group group = tracks.getGroups().get(option.groupIndex);

        DefaultTrackSelector.Parameters.Builder builder = trackSelector.getParameters().buildUpon();
        builder.setOverrideForType(
                new androidx.media3.common.TrackSelectionOverride(group.getMediaTrackGroup(), option.trackIndex));
        trackSelector.setParameters(builder);
    }

    public void setSubtitlesEnabled(boolean enabled) {
        if (trackSelector == null) return;
        trackSelector.setParameters(
                trackSelector.getParameters().buildUpon()
                        .setIgnoredTextSelectionFlags(enabled ? 0 : Integer.MAX_VALUE)
                        .setSelectUndeterminedTextLanguage(enabled)
                        .build());
    }

    public void release() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    public ExoPlayer getExoPlayer() {
        return exoPlayer;
    }
}
