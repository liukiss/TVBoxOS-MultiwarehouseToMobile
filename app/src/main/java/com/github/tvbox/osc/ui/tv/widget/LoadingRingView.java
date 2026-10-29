package com.github.tvbox.osc.ui.tv.widget;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.github.tvbox.osc.R;

/**
 * 统一加载动画：中心旋转图标 + 双脉冲环。
 * 与海报区下拉刷新动画保持一致，供全 App 加载页复用。
 */
public class LoadingRingView extends FrameLayout {

    private static final int RING_DURATION = 1300;
    private static final int RING_DELAY = 650;
    private static final int SPIN_DURATION = 1000;
    private static final float ICON_RATIO = 0.72f;
    private static final float RING_START_SCALE = 0.92f;
    private static final float RING_END_SCALE = 1.38f;
    private static final float RING_START_ALPHA = 0.50f;

    private final View ringOuter;
    private final View ringInner;
    private final ImageView icon;
    private final int defaultSize;

    private ObjectAnimator spinAnimator;
    private ValueAnimator outerAnimator;
    private ValueAnimator innerAnimator;
    private boolean attached;
    private boolean windowVisible;

    public LoadingRingView(Context context) {
        this(context, null);
    }

    public LoadingRingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LoadingRingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        defaultSize = getResources().getDimensionPixelSize(R.dimen.vs_50);

        ringOuter = createRing(context);
        ringInner = createRing(context);
        icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageResource(R.drawable.icon_loading);

        addView(ringOuter, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        addView(ringInner, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        addView(icon, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        setClipChildren(false);
        setClipToPadding(false);
        resetRings();
    }

    private View createRing(Context context) {
        View ring = new View(context);
        ring.setBackgroundResource(R.drawable.shape_refresh_ring);
        return ring;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int diameter = resolveDiameter(widthMeasureSpec, heightMeasureSpec);
        int iconSize = Math.round(diameter * ICON_RATIO);
        int iconSpec = MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY);
        // 脉冲环以图标外沿为起点向外扩散，最大不超过视图边界，避免被方形区域裁切
        ringOuter.measure(iconSpec, iconSpec);
        ringInner.measure(iconSpec, iconSpec);
        icon.measure(iconSpec, iconSpec);
        setMeasuredDimension(diameter, diameter);
    }

    private int resolveDiameter(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int diameter;
        if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
            diameter = Math.min(widthSize, heightSize);
        } else if (widthMode == MeasureSpec.EXACTLY) {
            diameter = widthSize;
        } else if (heightMode == MeasureSpec.EXACTLY) {
            diameter = heightSize;
        } else {
            diameter = defaultSize;
        }
        return diameter > 0 ? diameter : defaultSize;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        windowVisible = getWindowVisibility() == VISIBLE;
        syncAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        syncAnimation();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        windowVisible = visibility == VISIBLE;
        syncAnimation();
    }

    private void syncAnimation() {
        if (attached && windowVisible && isShown()) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    private void startAnimation() {
        if (spinAnimator == null) {
            spinAnimator = ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 360f);
            spinAnimator.setDuration(SPIN_DURATION);
            spinAnimator.setInterpolator(new LinearInterpolator());
            spinAnimator.setRepeatCount(ValueAnimator.INFINITE);
            outerAnimator = createPulse(ringOuter, 0);
            innerAnimator = createPulse(ringInner, RING_DELAY);
        }
        if (!spinAnimator.isStarted()) {
            spinAnimator.start();
        }
        if (!outerAnimator.isStarted()) {
            outerAnimator.start();
        }
        if (!innerAnimator.isStarted()) {
            innerAnimator.start();
        }
    }

    private void stopAnimation() {
        if (spinAnimator != null) {
            spinAnimator.cancel();
        }
        if (outerAnimator != null) {
            outerAnimator.cancel();
        }
        if (innerAnimator != null) {
            innerAnimator.cancel();
        }
        icon.setRotation(0f);
        resetRings();
    }

    private void resetRings() {
        resetRing(ringOuter);
        resetRing(ringInner);
    }

    private void resetRing(View ring) {
        ring.setScaleX(RING_START_SCALE);
        ring.setScaleY(RING_START_SCALE);
        ring.setAlpha(RING_START_ALPHA);
    }

    private ValueAnimator createPulse(final View target, long startDelay) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(RING_DURATION);
        animator.setStartDelay(startDelay);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                float scale = RING_START_SCALE + (RING_END_SCALE - RING_START_SCALE) * value;
                target.setScaleX(scale);
                target.setScaleY(scale);
                target.setAlpha(RING_START_ALPHA * (1f - value));
            }
        });
        return animator;
    }
}
