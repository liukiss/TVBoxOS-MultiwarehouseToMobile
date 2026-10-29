package com.github.tvbox.osc.ui.adapter;

import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;

/**
 * 首页分类行的“全部标签”弹窗适配器：等宽网格排列，选中项高亮。
 */
public class CategoryGridAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
    private int selectedPos = 0;

    public CategoryGridAdapter() {
        super(R.layout.item_category_label, new ArrayList<>());
    }

    public void setSelectedPos(int position) {
        this.selectedPos = position;
        notifyDataSetChanged();
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        TextView tvTitle = helper.getView(R.id.tvTitle);
        tvTitle.setText(item.name);
        boolean selected = helper.getAdapterPosition() == selectedPos;
        tvTitle.setTextColor(selected ? 0xFFFFFFFF : 0xBBFFFFFF);
        tvTitle.getPaint().setFakeBoldText(selected);
        tvTitle.invalidate();
    }
}
