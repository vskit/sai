package com.apks.sai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ApkAdapter extends RecyclerView.Adapter<ApkAdapter.ViewHolder> {

    private final List<ApkItem> apkItems;
    private final OnItemCheckedChangeListener listener;

    public ApkAdapter(List<ApkItem> apkItems, OnItemCheckedChangeListener listener) {
        this.apkItems = apkItems;
        this.listener = listener;
    }

    public interface OnItemCheckedChangeListener {
        void onItemCheckedChanged(int position, boolean isChecked);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.apk_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ApkItem item = apkItems.get(position);
        holder.tvFileName.setText(item.getFileName());
        // 保存当前状态，避免在设置监听器时触发事件
        holder.checkBox.setOnCheckedChangeListener(null);

        // 设置视图状态
        holder.checkBox.setChecked(item.isSelected());

        // 设置新的监听器
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 更新数据模型
            item.setSelected(isChecked);

            // 通知监听器
            if (listener != null) {
                listener.onItemCheckedChanged(position, isChecked);
            }
        });

        // 整个条目可点击
        holder.itemView.setOnClickListener(v -> {
            boolean newChecked = !item.isSelected();
            holder.checkBox.setChecked(newChecked);
        });
    }

    @Override
    public int getItemCount() {
        return apkItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileName;
        TextView tvSizeInfo;
        CheckBox checkBox;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            checkBox = itemView.findViewById(R.id.checkBox);
        }
    }
}