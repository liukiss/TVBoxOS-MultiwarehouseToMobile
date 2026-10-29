package com.github.tvbox.osc.ui.adapter;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;


import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.ScreenUtils;
import com.github.tvbox.osc.util.SearchHelper;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CheckboxSearchAdapter extends ListAdapter<SourceBean, CheckboxSearchAdapter.ViewHolder> {

    public CheckboxSearchAdapter(DiffUtil.ItemCallback<SourceBean> diffCallback) {
        super(diffCallback);
    }

    @Override
    public ViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_checkbox_search, parent, false));
    }

    private void setCheckedSource(HashMap<String, String> checkedSources) {
        mCheckedSources = checkedSources;
    }

    private ArrayList<SourceBean> data = new ArrayList<>();
    public HashMap<String, String> mCheckedSources = new HashMap<>();

    @SuppressLint("NotifyDataSetChanged")
    public void setData(List<SourceBean> newData, HashMap<String, String> checkedSources) {
        data.clear();
        data.addAll(newData);
        setCheckedSource(checkedSources);
        notifyDataSetChanged();
    }

    public void setMCheckedSources() {
//        LOG.i(data.size()+"size----size"+mCheckedSources.size());
        SearchHelper.putCheckedSources(mCheckedSources,data.size()==mCheckedSources.size());
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        int pos = holder.getAdapterPosition();
        SourceBean sourceBean = data.get(pos);
        // 手机版：条目内的 mm 尺寸（高度、最小高度、字号、内边距）全部在绑定期按
        // “屏幕实际宽 / 360mm”换算后写死。条目要到弹窗第一帧布局时才膨胀，
        // 那一瞬间 Resources 的 mm 换算密度可能仍是系统原生值（约设计基准的 6.3 倍），
        // 会把条目撑得很高、内边距大到吃掉整个列宽，导致文字和勾选图标都不可见。
        float pxPerMm = ScreenUtils.pxPerMm(holder.itemView.getContext());
        applyItemSize(holder.itemView, pxPerMm, 27f);
        Context context = holder.itemView.getContext();
        // 手机版：胶囊背景在代码里生成，避免 Resources 缓存的旧 drawable 沿用错误 mm 密度
        holder.oneSearchSource.setBackground(ScreenUtils.createDialogControlBackground(context, pxPerMm));
        View inner = (View) holder.oneSearchSource.getParent();
        int padH = Math.round(pxPerMm * 5f);
        int padV = Math.round(pxPerMm * 1f);
        if (inner != null) {
            inner.setPadding(padH, padV, padH, padV);
        }
        holder.oneSearchSource.setMinimumHeight(Math.round(pxPerMm * 25f));
        holder.oneSearchSource.setTextSize(TypedValue.COMPLEX_UNIT_PX, pxPerMm * 12f);
        holder.oneSearchSource.setText(sourceBean.getName());
        holder.oneSearchSource.setOnCheckedChangeListener(null);
        if (mCheckedSources != null) {
            holder.oneSearchSource.setChecked(mCheckedSources.containsKey(sourceBean.getKey()));
        }
        // 手机版：复选框图标按设计单位换算尺寸（系统默认图标在 mm 设计尺寸下偏大），
        // 并按选中状态刷新，不再依赖 AutoSize 的全局基准
        Drawable icon = ContextCompat.getDrawable(context, R.drawable.selector_source_check);
        if (icon != null) {
            int size = Math.round(pxPerMm * 15f);
            icon.setBounds(0, 0, size, size);
            holder.oneSearchSource.setCompoundDrawables(icon, null, null, null);
            holder.oneSearchSource.setCompoundDrawablePadding(Math.round(pxPerMm * 5f));
            icon.setState(holder.oneSearchSource.getDrawableState());
        }
        holder.oneSearchSource.setTag(sourceBean);
        holder.oneSearchSource.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mCheckedSources.put(sourceBean.getKey(), "1");
                } else {
                    mCheckedSources.remove(sourceBean.getKey());
                }
                notifyItemChanged(pos);
            }
        });

    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public CheckBox oneSearchSource;

        public ViewHolder(View view) {
            super(view);
            oneSearchSource = (CheckBox) view.findViewById(R.id.oneSearchSource);
        }
    }

    /**
     * 手机版：把条目尺寸固定为"宽铺满 + 设计高度"。
     * 条目在 wrap_content 状态下有可能被测成整个列表高度（内部背景方框被拉得很高、文字被挤出可视区域），
     * 绑定后直接给出明确的 LayoutParams，测量时即按固定高度处理，不受父级测量结果影响。
     */
    private void applyItemSize(View item, float pxPerMm, float heightMm) {
        int height = Math.round(pxPerMm * heightMm);
        if (height <= 0) return;
        ViewGroup.LayoutParams params = item.getLayoutParams();
        if (params instanceof RecyclerView.LayoutParams) {
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) params;
            if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT && lp.height == height) {
                return;
            }
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = height;
            item.setLayoutParams(lp);
        } else {
            item.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        }
    }

}
