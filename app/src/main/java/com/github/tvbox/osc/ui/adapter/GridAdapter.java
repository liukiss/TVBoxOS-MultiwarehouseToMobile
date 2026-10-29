package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.ui.widget.RatioFrameLayout;
import com.github.tvbox.osc.util.ImgUtil;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class GridAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private boolean mShowList;
    private int defaultWidth;
    public ImgUtil.Style style;

    public GridAdapter(boolean showList, ImgUtil.Style style) {
        super(showList ? R.layout.item_list : R.layout.item_grid, new ArrayList<>());
        this.mShowList = showList;
        if (style != null) {
            if (style.type.equals("list")) this.mShowList = true;
            this.defaultWidth = ImgUtil.getStyleDefaultWidth(style);
        }
        this.style = style;
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        if (this.mShowList) {
            helper.setText(R.id.tvNote, item.note);
            helper.setText(R.id.tvName, item.name);
            ImageView ivThumb = helper.getView(R.id.ivThumb);
            String pic = item.pic == null ? "" : item.pic.trim();
            if (!TextUtils.isEmpty(pic)) {
                if (ImgUtil.isBase64Image(pic)) {
                    ivThumb.setImageDrawable(ImgUtil.roundBitmap(mContext.getResources(), ImgUtil.decodeBase64ToBitmap(pic), AutoSizeUtils.mm2px(mContext, 10)));
                } else {
                    ImgUtil.load(pic, ivThumb, AutoSizeUtils.mm2px(mContext, 10), AutoSizeUtils.mm2px(mContext, 240), AutoSizeUtils.mm2px(mContext, 336), item.name);
                }
            } else {
                ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
            }
            return;
        }

        TextView tvYear = helper.getView(R.id.tvYear);
        if (item.year <= 0) {
            tvYear.setVisibility(View.GONE);
        } else {
            tvYear.setText(String.valueOf(item.year));
            tvYear.setVisibility(View.VISIBLE);
        }
        TextView tvLang = helper.getView(R.id.tvLang);
        tvLang.setVisibility(View.GONE);
        TextView tvArea = helper.getView(R.id.tvArea);
        tvArea.setVisibility(View.GONE);
        if (TextUtils.isEmpty(item.note)) {
            helper.setVisible(R.id.tvNote, false);
        } else {
            helper.setVisible(R.id.tvNote, true);
            helper.setText(R.id.tvNote, item.note);
        }
        helper.setText(R.id.tvName, item.name);
        helper.setText(R.id.tvActor, item.actor);
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        int newWidth = ImgUtil.defaultWidth;
        int newHeight = ImgUtil.defaultHeight;
        if (style != null) {
            newWidth = defaultWidth;
            newHeight = (int) (newWidth / style.ratio);
        }
        String pic = item.pic == null ? "" : item.pic.trim();
        if (!TextUtils.isEmpty(pic)) {
            if (ImgUtil.isBase64Image(pic)) {
                ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(pic));
                ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                // 圆角交给 clipRounded 按 View 实际尺寸裁剪，这里不在解码尺寸上画圆角
                ImgUtil.load(pic, ivThumb, 0, AutoSizeUtils.mm2px(mContext, newWidth), AutoSizeUtils.mm2px(mContext, newHeight), item.name);
            }
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
            ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        applyStyleToImage(ivThumb);
    }

    private void applyStyleToImage(final ImageView ivThumb) {
        final ViewGroup container = (ViewGroup) ivThumb.getParent();
        // 手机版：高度在测量阶段按站点比例算出（非 style 站点沿用 XML 固定高度），
        // 不再布局完成后 post 改高，避免 RecyclerView 残留旧测量高度、行距随机变大。
        if (container instanceof RatioFrameLayout) {
            ((RatioFrameLayout) container).setRatio(style != null ? style.ratio : 0f);
        }
        if (style != null) {
            ViewGroup.LayoutParams containerParams = container.getLayoutParams();
            containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            container.setLayoutParams(containerParams);
        }
        // 圆角与非 style 站点的卡片圆角背景保持一致
        ImgUtil.clipRounded(ivThumb, AutoSizeUtils.mm2px(mContext, 10));
    }
}
