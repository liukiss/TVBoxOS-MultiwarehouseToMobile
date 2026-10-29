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
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 历史记录适配器：支持多选态（长按进入，选中/待选角标）
 */
public class HistoryAdapter extends BaseQuickAdapter<VodInfo, BaseViewHolder> {
    private boolean selectMode;
    private final Set<String> selectedKeys = new HashSet<>();

    public HistoryAdapter() {
        super(R.layout.item_grid, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo item) {
        FrameLayout tvDel = helper.getView(R.id.delFrameLayout);
        tvDel.setVisibility(View.GONE);

        boolean selected = selectMode && selectedKeys.contains(keyOf(item));
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

        TextView tvYear = helper.getView(R.id.tvYear);
        SourceBean bean = ApiConfig.get().getSource(item.sourceKey);
        tvYear.setText(bean != null ? bean.getName() : "搜");
        helper.setVisible(R.id.tvLang, false);
        helper.setVisible(R.id.tvArea, false);
        if (item.note == null || item.note.isEmpty()) {
            helper.setVisible(R.id.tvNote, false);
        } else {
            helper.setText(R.id.tvNote, item.note);
        }
        helper.setText(R.id.tvName, item.name);
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        if (!TextUtils.isEmpty(item.pic)) {
            ImgUtil.load(item.pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10), AutoSizeUtils.mm2px(mContext, ImgUtil.defaultWidth), AutoSizeUtils.mm2px(mContext, ImgUtil.defaultHeight), item.name);
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
        }
    }

    private String keyOf(VodInfo item) {
        if (item == null) return "";
        return (item.sourceKey == null ? "" : item.sourceKey) + "\u0000" + (item.id == null ? "" : item.id);
    }

    public void enterSelectMode(VodInfo first) {
        selectMode = true;
        selectedKeys.clear();
        if (first != null) {
            selectedKeys.add(keyOf(first));
        }
        notifyDataSetChanged();
    }

    public void exitSelectMode() {
        if (!selectMode && selectedKeys.isEmpty()) return;
        selectMode = false;
        selectedKeys.clear();
        notifyDataSetChanged();
    }

    public boolean isSelectMode() {
        return selectMode;
    }

    public boolean isSelected(VodInfo item) {
        return selectMode && selectedKeys.contains(keyOf(item));
    }

    /** 切换单条选中状态，返回切换后的选中数量 */
    public int toggleSelect(VodInfo item) {
        String key = keyOf(item);
        if (!selectedKeys.remove(key)) {
            selectedKeys.add(key);
        }
        return selectedKeys.size();
    }

    public void selectAll(boolean all) {
        selectedKeys.clear();
        if (all) {
            for (VodInfo item : getData()) {
                selectedKeys.add(keyOf(item));
            }
        }
    }

    public boolean isAllSelected() {
        List<VodInfo> data = getData();
        if (data.isEmpty()) return false;
        for (VodInfo item : data) {
            if (!selectedKeys.contains(keyOf(item))) return false;
        }
        return true;
    }

    public int getSelectedCount() {
        return selectedKeys.size();
    }

    public List<VodInfo> getSelectedVodInfos() {
        List<VodInfo> result = new ArrayList<>();
        for (VodInfo item : getData()) {
            if (selectedKeys.contains(keyOf(item))) {
                result.add(item);
            }
        }
        return result;
    }

    /** 从列表数据中移除当前选中项，返回移除数量（调用方负责刷新） */
    public int removeSelectedItems() {
        List<VodInfo> data = getData();
        int removed = 0;
        Iterator<VodInfo> iterator = data.iterator();
        while (iterator.hasNext()) {
            if (selectedKeys.contains(keyOf(iterator.next()))) {
                iterator.remove();
                removed++;
            }
        }
        selectedKeys.clear();
        return removed;
    }
}
