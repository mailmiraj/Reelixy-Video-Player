package com.reelixy.videoplayer.adapters;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.reelixy.videoplayer.R;
import com.reelixy.videoplayer.models.Video;
import com.reelixy.videoplayer.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays a list of videos, either as grid cards ({@link #VIEW_GRID}) or
 * horizontal continue-watching cards ({@link #VIEW_HORIZONTAL}). One adapter
 * handles both layouts since the binding logic is nearly identical.
 */
public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    public static final int VIEW_GRID = 1;
    public static final int VIEW_HORIZONTAL = 2;

    public interface OnVideoClickListener {
        void onVideoClick(Video video);

        default void onVideoLongClick(Video video) {}
    }

    private final int viewType;
    private final OnVideoClickListener listener;
    private List<Video> videos = new ArrayList<>();
    /** filePath -> watched progress percent (0-100), only used in grid/horizontal continue-watching contexts */
    private java.util.Map<String, Integer> progressMap = new java.util.HashMap<>();
    private final java.util.Set<String> selectedPaths = new java.util.HashSet<>();

    public VideoAdapter(int viewType, OnVideoClickListener listener) {
        this.viewType = viewType;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<Video> newVideos) {
        final List<Video> nextVideos = newVideos == null
                ? new ArrayList<>()
                : new ArrayList<>(newVideos);
        final List<Video> oldVideos = videos;
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldVideos.size(); }
            @Override public int getNewListSize() { return nextVideos.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldVideos.get(oldPos).getId() == nextVideos.get(newPos).getId();
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                Video oldVideo = oldVideos.get(oldPos);
                Video newVideo = nextVideos.get(newPos);
                return safeEquals(oldVideo.getTitle(), newVideo.getTitle())
                        && safeEquals(oldVideo.getFilePath(), newVideo.getFilePath())
                        && safeEquals(oldVideo.getContentUri(), newVideo.getContentUri())
                        && oldVideo.getDurationMs() == newVideo.getDurationMs()
                        && oldVideo.getSizeBytes() == newVideo.getSizeBytes()
                        && oldVideo.getWidth() == newVideo.getWidth()
                        && oldVideo.getHeight() == newVideo.getHeight()
                        && oldVideo.getDateAddedSeconds() == newVideo.getDateAddedSeconds()
                        && safeEquals(oldVideo.getFolderName(), newVideo.getFolderName())
                        && safeEquals(oldVideo.getRelativePath(), newVideo.getRelativePath())
                        && safeEquals(oldVideo.getMimeType(), newVideo.getMimeType());
            }
        });
        videos = nextVideos;
        result.dispatchUpdatesTo(this);
    }

    private static boolean safeEquals(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    public void setSelectedPaths(java.util.Set<String> paths) {
        selectedPaths.clear();
        if (paths != null) selectedPaths.addAll(paths);
        notifyItemRangeChanged(0, getItemCount(), "selection");
    }

    public boolean isSelected(String filePath) { return selectedPaths.contains(filePath); }

    public void clearSelection() {
        if (selectedPaths.isEmpty()) return;
        selectedPaths.clear();
        notifyItemRangeChanged(0, getItemCount(), "selection");
    }

    public void setProgressMap(java.util.Map<String, Integer> progressMap) {
        this.progressMap = progressMap == null ? new java.util.HashMap<>() : new java.util.HashMap<>(progressMap);
        notifyItemRangeChanged(0, getItemCount(), "progress");
    }

    @Override
    public long getItemId(int position) {
        return videos.get(position).getId();
    }

    @Override
    public int getItemViewType(int position) {
        return viewType;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewTypeInt) {
        int layoutRes = viewTypeInt == VIEW_HORIZONTAL
                ? R.layout.item_video_horizontal
                : R.layout.item_video_grid;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
        return new VideoViewHolder(view, viewTypeInt);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        Video video = videos.get(position);
        holder.bind(video, progressMap.get(video.getFilePath()));
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    @Override
    public void onViewRecycled(@NonNull VideoViewHolder holder) {
        Glide.with(holder.itemView.getContext()).clear(holder.ivThumbnail);
        super.onViewRecycled(holder);
    }

    class VideoViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivThumbnail;
        final TextView tvDuration;
        final TextView tvTitle;
        final ProgressBar progressWatched;
        final ImageButton btnMore;
        final int type;

        VideoViewHolder(@NonNull View itemView, int type) {
            super(itemView);
            this.type = type;
            ivThumbnail = itemView.findViewById(R.id.ivThumbnail);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            progressWatched = itemView.findViewById(R.id.progressWatched);
            btnMore = itemView.findViewById(R.id.btnVideoMore);
        }

        void bind(Video video, Integer progressPercent) {
            tvTitle.setText(video.getTitle());
            tvDuration.setText(TimeUtils.formatDuration(video.getDurationMs()));
            Glide.with(itemView.getContext())
                    .load(Uri.parse(video.getContentUri()))
                    .placeholder(R.drawable.ic_video_placeholder)
                    .error(R.drawable.ic_video_placeholder)
                    .thumbnail(0.20f)
                    .dontAnimate()
                    .centerCrop()
                    .into(ivThumbnail);

            if (progressWatched != null) {
                if (progressPercent != null && progressPercent > 0) {
                    progressWatched.setVisibility(View.VISIBLE);
                    progressWatched.setProgress(progressPercent);
                } else if (type == VIEW_GRID) {
                    progressWatched.setVisibility(View.GONE);
                } else {
                    progressWatched.setProgress(0);
                }
            }

            boolean selected = selectedPaths.contains(video.getFilePath());
            itemView.setAlpha(selected ? 0.72f : 1f);
            itemView.setBackgroundResource(selected ? R.drawable.bg_rounded_surface : android.R.color.transparent);
            itemView.setOnClickListener(v -> listener.onVideoClick(video));
            itemView.setOnLongClickListener(v -> {
                listener.onVideoLongClick(video);
                return true;
            });
            if (btnMore != null) {
                btnMore.setOnClickListener(v -> listener.onVideoLongClick(video));
                btnMore.setContentDescription(itemView.getContext().getString(R.string.action_video_options));
            }
        }
    }
}
