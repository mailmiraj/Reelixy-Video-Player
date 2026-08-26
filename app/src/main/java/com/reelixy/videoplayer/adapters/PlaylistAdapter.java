package com.reelixy.videoplayer.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.reelixy.videoplayer.R;
import com.reelixy.videoplayer.database.PlaylistEntity;

import java.util.ArrayList;
import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {

    public interface OnPlaylistClickListener {
        void onPlaylistClick(PlaylistEntity playlist);
        default void onPlaylistLongClick(PlaylistEntity playlist) {}
    }

    private final OnPlaylistClickListener listener;
    private List<PlaylistEntity> playlists = new ArrayList<>();
    private java.util.Map<Long, Integer> countMap = new java.util.HashMap<>();

    public PlaylistAdapter(OnPlaylistClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<PlaylistEntity> newPlaylists, java.util.Map<Long, Integer> counts) {
        final List<PlaylistEntity> next = newPlaylists == null
                ? new ArrayList<>() : new ArrayList<>(newPlaylists);
        final java.util.Map<Long, Integer> nextCounts = counts == null
                ? new java.util.HashMap<>() : new java.util.HashMap<>(counts);
        final List<PlaylistEntity> old = playlists;
        final java.util.Map<Long, Integer> oldCounts = countMap;
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                return old.get(oldPos).id == next.get(newPos).id;
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                PlaylistEntity a = old.get(oldPos);
                PlaylistEntity b = next.get(newPos);
                Integer oldCount = oldCounts.get(a.id);
                Integer newCount = nextCounts.get(b.id);
                return a.id == b.id
                        && java.util.Objects.equals(a.name, b.name)
                        && a.createdAtMillis == b.createdAtMillis
                        && java.util.Objects.equals(oldCount, newCount);
            }
        });
        this.playlists = next;
        this.countMap = nextCounts;
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        PlaylistEntity playlist = playlists.get(position);
        holder.tvName.setText(playlist.name);
        int count = countMap.containsKey(playlist.id) ? countMap.get(playlist.id) : 0;
        holder.tvCount.setText(count + " videos");
        holder.itemView.setOnClickListener(v -> listener.onPlaylistClick(playlist));
        holder.itemView.setOnLongClickListener(v -> { listener.onPlaylistLongClick(playlist); return true; });
        holder.btnMore.setOnClickListener(v -> listener.onPlaylistLongClick(playlist));
    }

    @Override
    public long getItemId(int position) {
        return playlists.get(position).id;
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvCount;
        final TextView btnMore;

        PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvPlaylistName);
            tvCount = itemView.findViewById(R.id.tvPlaylistCount);
            btnMore = itemView.findViewById(R.id.btnPlaylistMore);
        }
    }
}
