package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.widget.RatioFrameLayout;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ImgUtil;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeHotVodAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private int defaultWidth;
    private final ImgUtil.Style style;
    private String tvRateValue;

    public HomeHotVodAdapter(ImgUtil.Style style, String tvRate) {
        super(R.layout.item_user_hot_vod, new ArrayList<>());
        if (style != null) {
            this.defaultWidth = ImgUtil.getStyleDefaultWidth(style);
        }
        this.style = style;
        this.tvRateValue = tvRate;
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        FrameLayout tvDel = helper.getView(R.id.delFrameLayout);
        tvDel.setVisibility(HawkConfig.hotVodDelete ? View.VISIBLE : View.GONE);

        TextView tvRate = helper.getView(R.id.tvRate);
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 2) {
            SourceBean bean = ApiConfig.get().getSource(item.sourceKey);
            tvRateValue = bean != null ? bean.getName() : "";
        }
        tvRate.setText(tvRateValue);

        TextView tvNote = helper.getView(R.id.tvNote);
        if (item.note == null || item.note.isEmpty()) {
            tvNote.setVisibility(View.GONE);
        } else {
            tvNote.setText(item.note);
            tvNote.setVisibility(View.VISIBLE);
        }
        helper.setText(R.id.tvName, item.name);
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
                // 手机版：圆角交给 clipRounded 按 View 实际尺寸裁剪
                ivThumb.setImageBitmap(ImgUtil.decodeBase64ToBitmap(pic));
                ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                ImgUtil.load(pic, ivThumb, 0, AutoSizeUtils.mm2px(mContext, newWidth), AutoSizeUtils.mm2px(mContext, newHeight), item.name);
            }
        } else {
            ivThumb.setImageDrawable(ImgUtil.createTextDrawable(item.name));
            ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        // 手机版适配：横排热播（非网格）时固定 item 根宽，否则 match_parent 根宽会一屏只占一项
        if (!Hawk.get(HawkConfig.HOME_REC_STYLE, true)) {
            ViewGroup.LayoutParams rootLp = helper.itemView.getLayoutParams();
            if (rootLp != null) {
                rootLp.width = AutoSizeUtils.mm2px(mContext, 110);
                helper.itemView.setLayoutParams(rootLp);
            }
        }
        applyStyleToImage(ivThumb);
    }

    private void applyStyleToImage(final ImageView ivThumb) {
        final ViewGroup container = (ViewGroup) ivThumb.getParent();
        // 手机版：高度在测量阶段按站点比例算出（非 style 站点沿用 XML 固定高度），
        // 不再布局完成后 post 改高，避免 RecyclerView 残留旧测量高度、行距随机变大。
        // 横排热播的固定列宽已加在 item 根布局上，容器 match_parent 跟随该宽度。
        if (container instanceof RatioFrameLayout) {
            ((RatioFrameLayout) container).setRatio(style != null ? style.ratio : 0f);
        }
        // 圆角与卡片圆角背景保持一致
        ImgUtil.clipRounded(ivThumb, AutoSizeUtils.mm2px(mContext, 10));
    }
}