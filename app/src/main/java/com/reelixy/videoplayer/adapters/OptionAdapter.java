package com.reelixy.videoplayer.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.reelixy.videoplayer.R;

import java.util.List;

/**
 * Generic single-select list used by every bottom sheet (speed, subtitle,
 * audio track, zoom mode, sleep timer). Caller supplies labels and which
 * index is currently selected.
 */
public class OptionAdapter extends RecyclerView.Adapter<OptionAdapter.OptionViewHolder> {

    public interface OnOptionSelectedListener {
        void onOptionSelected(int index);
    }

    private final List<String> labels;
    private int selectedIndex;
    private final OnOptionSelectedListener listener;

    public OptionAdapter(List<String> labels, int selectedIndex, OnOptionSelectedListener listener) {
        this.labels = labels;
        this.selectedIndex = selectedIndex;
        this.listener = listener;
    }

    @NonNull
    @Override
    public OptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_option_row, parent, false);
        return new OptionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OptionViewHolder holder, int position) {
        String label = labels.get(position);
        holder.tvLabel.setText(label);
        holder.tvIcon.setText(iconFor(label));
        String meta = metaFor(label);
        if (meta == null) {
            holder.tvMeta.setVisibility(View.GONE);
        } else {
            holder.tvMeta.setVisibility(View.VISIBLE);
            holder.tvMeta.setText(meta);
        }
        holder.ivChecked.setVisibility(position == selectedIndex ? View.VISIBLE : View.INVISIBLE);
        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) return;
            int previous = selectedIndex;
            selectedIndex = adapterPosition;
            if (previous >= 0 && previous < labels.size()) notifyItemChanged(previous);
            notifyItemChanged(selectedIndex);
            listener.onOptionSelected(selectedIndex);
        });
    }

    @Override
    public int getItemCount() {
        return labels.size();
    }

    static class OptionViewHolder extends RecyclerView.ViewHolder {
        final TextView tvLabel;
        final TextView tvIcon;
        final TextView tvMeta;
        final View ivChecked;

        OptionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLabel = itemView.findViewById(R.id.tvOptionLabel);
            tvIcon = itemView.findViewById(R.id.tvOptionIcon);
            tvMeta = itemView.findViewById(R.id.tvOptionMeta);
            ivChecked = itemView.findViewById(R.id.ivChecked);
        }
    }
    private static String iconFor(String label) {
        String v = label == null ? "" : label.toLowerCase(java.util.Locale.US);
        if (v.contains("speed")) return "×";
        if (v.contains("subtitle")) return "CC";
        if (v.contains("audio")) return "♫";
        if (v.contains("zoom") || v.contains("fit") || v.contains("fill") || v.contains("crop")) return "↗";
        if (v.contains("sleep")) return "Zz";
        if (v.contains("repeat")) return "↻";
        if (v.contains("shuffle")) return "⇄";
        if (v.contains("frame")) return "▤";
        if (v.contains("stats")) return "Σ";
        if (v.contains("boost")) return "↯";
        if (v.contains("bookmark")) return "★";
        if (v.contains("queue") || v.contains("next")) return "≡";
        if (v.contains("capture")) return "▣";
        if (v.contains("information")) return "i";
        if (v.contains("cinematic") || v.contains("preset")) return "✦";
        return "•";
    }

    private static String metaFor(String label) {
        String v = label == null ? "" : label.toLowerCase(java.util.Locale.US);
        if (v.contains("playback speed")) return "Choose how fast the video plays";
        if (v.contains("zoom & resize")) return "Fit, fill, crop and gesture zoom";
        if (v.contains("subtitle")) return "Toggle subtitle track visibility";
        if (v.contains("audio track")) return "Choose an available audio track";
        if (v.contains("sleep timer")) return "Pause automatically after a set time";
        if (v.contains("a–b repeat")) return "Loop a selected section";
        return null;
    }

}
