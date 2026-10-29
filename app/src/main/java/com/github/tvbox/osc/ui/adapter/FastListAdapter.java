package com.github.tvbox.osc.ui.adapter;

import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.util.ArrayList;

public class FastListAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    private static final String SEARCH_ALL_NAME = "\u5168\u90e8";
    private String selectedName = SEARCH_ALL_NAME;

    public FastListAdapter() {
        super(R.layout.item_search_word_hot, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        TextView textView = helper.getView(R.id.tvSearchWord);
        textView.setText(item);
        // “全部”与站点按钮同款矩形样式，用蓝色背景区分
        textView.setBackgroundResource(SEARCH_ALL_NAME.equals(item)
                ? R.drawable.bg_fast_site_word_all : R.drawable.bg_fast_site_word);
        updateSelection(textView, item);
    }

    public void setSelectedName(String selectedName) {
        this.selectedName = TextUtils.isEmpty(selectedName) ? "\u5168\u90e8" : selectedName;
    }

    public void refreshVisibleSelection(ViewGroup parent) {
        if (parent == null) return;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView) {
                updateSelection((TextView) child, ((TextView) child).getText().toString());
            }
        }
    }

    private void updateSelection(TextView textView, String item) {
        boolean selected = TextUtils.equals(item, selectedName);
        textView.setSelected(selected);
        textView.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }
}
