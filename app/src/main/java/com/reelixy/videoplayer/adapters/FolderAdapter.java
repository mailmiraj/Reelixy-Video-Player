package com.reelixy.videoplayer.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.reelixy.videoplayer.R;
import com.reelixy.videoplayer.models.Folder;

import java.util.ArrayList;
import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {

    public interface OnFolderClickListener {
        void onFolderClick(Folder folder);
        default void onFolderLongClick(Folder folder) {}
    }

    private final OnFolderClickListener listener;
    private List<Folder> folders = new ArrayList<>();

    public FolderAdapter(OnFolderClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<Folder> newFolders) {
        final List<Folder> next = newFolders == null ? new ArrayList<>() : new ArrayList<>(newFolders);
        final List<Folder> old = folders;
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) { return old.get(oldPos).getPath().equals(next.get(newPos).getPath()); }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                return old.get(oldPos).getName().equals(next.get(newPos).getName())
                        && old.get(oldPos).getVideoCount() == next.get(newPos).getVideoCount();
            }
        });
        folders = next;
        result.dispatchUpdatesTo(this);
    }

    @Override public long getItemId(int position) { return folders.get(position).getPath().hashCode(); }

    @NonNull
    @Override public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder, parent, false);
        return new FolderViewHolder(view);
    }

    @Override public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) { holder.bind(folders.get(position)); }
    @Override public int getItemCount() { return folders.size(); }

    class FolderViewHolder extends RecyclerView.ViewHolder {
        final TextView tvFolderName;
        final TextView tvFolderCount;
        final TextView tvFolderBadge;
        final View btnFolderMore;

        FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFolderName = itemView.findViewById(R.id.tvFolderName);
            tvFolderCount = itemView.findViewById(R.id.tvFolderCount);
            tvFolderBadge = itemView.findViewById(R.id.tvFolderBadge);
            btnFolderMore = itemView.findViewById(R.id.btnFolderMore);
        }

        void bind(Folder folder) {
            tvFolderName.setText(folder.getName());
            tvFolderCount.setText(itemView.getContext().getString(R.string.video_count, folder.getVideoCount()));
            tvFolderBadge.setText(String.valueOf(folder.getVideoCount()));
            itemView.setContentDescription(itemView.getContext().getString(R.string.cd_folder_item, folder.getName(), folder.getVideoCount()));
            itemView.setOnClickListener(v -> listener.onFolderClick(folder));
            itemView.setOnLongClickListener(v -> {
                listener.onFolderLongClick(folder);
                return true;
            });
            btnFolderMore.setOnClickListener(v -> listener.onFolderLongClick(folder));
        }
    }
}
