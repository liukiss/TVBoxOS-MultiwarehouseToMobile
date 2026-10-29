package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class SearchWordAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    public SearchWordAdapter() {
        super(R.layout.item_search_word_split, new ArrayList<>());
    }

    /**
     * 手机版：允许指定条目布局，聚合搜索页使用“搜索对象”全宽按钮样式
     */
    public SearchWordAdapter(int layoutResId) {
        super(layoutResId, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.tvSearchWord, item);
    }
}