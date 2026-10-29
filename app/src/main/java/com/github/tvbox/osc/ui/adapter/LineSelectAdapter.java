package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MoreSourceBean;

import java.util.ArrayList;
import java.util.List;

/**
 * 线路选择列表 Adapter（移植自影视仓 util/e 弹窗列表）
 * 使用同步 notifyDataSetChanged 渲染，规避 TvRecyclerView 与 ListAdapter 异步 DiffUtil 的兼容问题
 */
public class LineSelectAdapter extends RecyclerView.Adapter<LineSelectAdapter.SelectViewHolder> {

    public interface SelectDialogInterface {
        void click(MoreSourceBean value);

        void del(MoreSourceBean value, ArrayList<MoreSourceBean> data);
    }

    class SelectViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvUrl;
        TextView tvDelete;
        View rowMain;

        public SelectViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvUrl = itemView.findViewById(R.id.tvUrl);
            tvDelete = itemView.findViewById(R.id.tvDelete);
            rowMain = itemView.findViewById(R.id.rowMain);
        }
    }

    private final SelectDialogInterface dialogInterface;
    private final List<MoreSourceBean> data = new ArrayList<>();
    private String currentUrl = "";

    public LineSelectAdapter(SelectDialogInterface dialogInterface) {
        this.dialogInterface = dialogInterface;
    }

    public void setCurrentUrl(String url) {
        this.currentUrl = url == null ? "" : url;
    }

    public void setData(List<MoreSourceBean> newData) {
        data.clear();
        if (newData != null) data.addAll(newData);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SelectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_more_source, parent, false);
        return new SelectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SelectViewHolder holder, int position) {
        if (position < 0 || position >= data.size()) return;
        MoreSourceBean item = data.get(position);
        if (item == null) return;
        String name = item.getSourceName();
        if (name == null || name.isEmpty()) name = item.getSourceUrl(); // 无名线路显示 URL
        holder.tvName.setText(name);
        holder.tvUrl.setText(item.getSourceUrl());
        // 当前正在使用的线路高亮
        holder.tvName.setTextColor(holder.itemView.getContext().getResources().getColor(
                item.getSourceUrl() != null && item.getSourceUrl().equals(currentUrl)
                        ? R.color.dialog_text_primary : R.color.dialog_text_hint));
        holder.tvDelete.setVisibility(item.isShowDelete() ? View.VISIBLE : View.GONE);
        holder.rowMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = holder.getAdapterPosition();
                if (dialogInterface != null && pos >= 0 && pos < data.size()) {
                    dialogInterface.click(data.get(pos));
                }
            }
        });
        holder.tvDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = holder.getAdapterPosition();
                if (dialogInterface != null && pos >= 0 && pos < data.size()) {
                    dialogInterface.del(data.get(pos), new ArrayList<>(data));
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
