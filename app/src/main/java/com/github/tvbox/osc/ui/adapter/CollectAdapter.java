package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 收藏适配器：支持多选态（长按进入，选中/待选角标）
 */
public class CollectAdapter extends BaseQuickAdapter<VodCollect, BaseViewHolder> {
    private boolean selectMode;
    private final Set<String> selectedIds = new HashSet<>();

    public CollectAdapter() {
        super(R.layout.item_grid, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodCollect item) {
        FrameLayout tvDel = helper.getView(R.id.delFrameLayout);
        tvDel.setVisibility(View.GONE);

        boolean selected = selectMode && selectedIds.contains(keyOf(item));
        View selectBorder = helper.getView(R.id.selectBorder);
        ImageView selectBadge = helper.getView(R.id.ivSelectBadge);
        if (selectMode) {
            selectBorder.setVisibility(selected ? View.VISIBLE : View.GONE);
            selectBadge.setVisibility(View.VISIBLE);
            selectBadge.setImageResource(selected ? R.drawable.icon_source_checked : R.drawable.icon_source_unchecked);
        } else {
            selectBorder.setVisibility(View.GONE);
            selectBadge.setVisibility(View.GONE);
        }

        helper.setVisible(R.id.tvLang, false);
        helper.setVisible(R.id.tvArea, false);
        helper.setVisible(R.id.tvNote, false);
        helper.setText(R.id.tvName, item.name);
        TextView tvYear = helper.getView(R.id.tvYear);
        SourceBean source = ApiConfig.get().getSource(item.sourceKey);
        tvYear.setText(source != null ? source.getName() : "");

        ImageView ivThumb = helper.getView(R.id.ivThumb);
        if (!TextUtils.isEmpty(item.pic)) {
            ImgUtil.load(item.pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10), AutoSizeUtils.mm2px(mContext, ImgUtil.defaultWidth), AutoSizeUtils.mm2px(mContext, ImgUtil.defaultHeight), item.name);
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        }
    }

    private String keyOf(VodCollect item) {
        return item == null ? "" : String.valueOf(item.getId());
    }

    public void enterSelectMode(VodCollect first) {
        selectMode = true;
        selectedIds.clear();
        if (first != null) {
            selectedIds.add(keyOf(first));
        }
        notifyDataSetChanged();
    }

    public void exitSelectMode() {
        if (!selectMode && selectedIds.isEmpty()) return;
        selectMode = false;
        selectedIds.clear();
        notifyDataSetChanged();
    }

    public boolean isSelectMode() {
        return selectMode;
    }

    /** 切换单条选中状态，返回切换后的选中数量 */
    public int toggleSelect(VodCollect item) {
        String key = keyOf(item);
        if (!selectedIds.remove(key)) {
            selectedIds.add(key);
        }
        return selectedIds.size();
    }

    public void selectAll(boolean all) {
        selectedIds.clear();
        if (all) {
            for (VodCollect item : getData()) {
                selectedIds.add(keyOf(item));
            }
        }
    }

    public boolean isAllSelected() {
        List<VodCollect> data = getData();
        if (data.isEmpty()) return false;
        for (VodCollect item : data) {
            if (!selectedIds.contains(keyOf(item))) return false;
        }
        return true;
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    public List<VodCollect> getSelectedItems() {
        List<VodCollect> result = new ArrayList<>();
        for (VodCollect item : getData()) {
            if (selectedIds.contains(keyOf(item))) {
                result.add(item);
            }
        }
        return result;
    }

    /** 从列表数据中移除当前选中项，返回移除数量（调用方负责刷新） */
    public int removeSelectedItems() {
        List<VodCollect> data = getData();
        int removed = 0;
        Iterator<VodCollect> iterator = data.iterator();
        while (iterator.hasNext()) {
            if (selectedIds.contains(keyOf(iterator.next()))) {
                iterator.remove();
                removed++;
            }
        }
        selectedIds.clear();
        return removed;
    }
}
