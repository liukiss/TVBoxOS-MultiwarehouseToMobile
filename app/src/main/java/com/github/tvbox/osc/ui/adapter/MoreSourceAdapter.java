package com.github.tvbox.osc.ui.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MoreSourceBean;

import java.util.ArrayList;
import java.util.List;

/**
 * 多仓仓库列表 Adapter（移植自影视仓 z0 弹窗列表）
 * 支持：点击仓库项（→ 线路选择框）、删除仓库
 */
public class MoreSourceAdapter extends ListAdapter<MoreSourceBean, MoreSourceAdapter.SelectViewHolder> {

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

    public MoreSourceAdapter(SelectDialogInterface dialogInterface) {
        super(new DiffUtil.ItemCallback<MoreSourceBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull MoreSourceBean oldItem, @NonNull MoreSourceBean newItem) {
                return oldItem.getSourceUrl().equals(newItem.getSourceUrl());
            }

            @Override
            public boolean areContentsTheSame(@NonNull MoreSourceBean oldItem, @NonNull MoreSourceBean newItem) {
                return oldItem.getSourceName().equals(newItem.getSourceName())
                        && oldItem.getSourceUrl().equals(newItem.getSourceUrl())
                        && oldItem.isShowDelete() == newItem.isShowDelete();
            }
        });
        this.dialogInterface = dialogInterface;
    }

    @NonNull
    @Override
    public SelectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_more_source, parent, false);
        return new SelectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SelectViewHolder holder, int position) {
        MoreSourceBean item = getItem(position);
        if (item == null) return;
        holder.tvName.setText(item.getSourceName());
        holder.tvUrl.setText(item.getSourceUrl());
        holder.tvDelete.setVisibility(item.isShowDelete() ? View.VISIBLE : View.GONE);
        holder.rowMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dialogInterface != null) dialogInterface.click(getItem(holder.getBindingAdapterPosition()));
            }
        });
        holder.tvDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dialogInterface != null) dialogInterface.del(getItem(holder.getBindingAdapterPosition()), new ArrayList<>(getCurrentList()));
            }
        });
        holder.rowMain.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                MoreSourceBean bean = getItem(holder.getBindingAdapterPosition());
                copySourceUrl(v, bean == null ? null : bean.getSourceUrl());
                return true;
            }
        });
    }

    /** 长按复制源地址并提示 */
    private static void copySourceUrl(View v, String url) {
        if (url == null || url.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("sourceUrl", url));
        }
        Toast.makeText(v.getContext(), "成功复制源地址", Toast.LENGTH_SHORT).show();
    }

    public void setData(List<MoreSourceBean> newData) {
        submitList(newData);
    }
}
