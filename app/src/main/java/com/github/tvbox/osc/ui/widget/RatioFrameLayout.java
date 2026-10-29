package com.github.tvbox.osc.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * 按 ratio（宽/高）在测量阶段直接算出高度的容器。
 * 手机版：高度在 measure 时就等于“列宽 / ratio”，不再等布局完成后 post 改高度，
 * 避免 RecyclerView 因事后改高导致的测量高度残留、行距随机变大。
 */
public class RatioFrameLayout extends FrameLayout {
    private float ratio = 0f;

    public RatioFrameLayout(Context context) {
        super(context);
    }

    public RatioFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RatioFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * @param ratio 宽/高；<=0 时退化为 XML 中声明的固定高度（非 style 站点）
     */
    public void setRatio(float ratio) {
        if (this.ratio == ratio) return;
        this.ratio = ratio;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (ratio > 0f && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            // EXACTLY / AT_MOST 都按可用宽度换算高度：卡片宽度由网格列宽或横排固定列宽决定，
            // 高度在测量阶段就确定，避免事后改高造成的测量高度残留。
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = Math.round(width / ratio);
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
