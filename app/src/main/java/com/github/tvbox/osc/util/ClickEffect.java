package com.github.tvbox.osc.util;

import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * 手机版：给普通按钮补充“按下缩放 + 轻微变暗”的点击反馈。
 * 只做视觉反馈，OnTouchListener 始终返回 false，不拦截原有的 OnClickListener。
 */
public class ClickEffect {

    /** 按下时的缩放比例 */
    private static final float PRESS_SCALE = 0.92f;
    /** 按下时的透明度 */
    private static final float PRESS_ALPHA = 0.75f;
    /** 按下动画时长 */
    private static final long DOWN_DURATION = 90L;
    /** 抬起回弹时长 */
    private static final long UP_DURATION = 140L;
    /** 回弹过冲强度 */
    private static final float OVERSHOOT_TENSION = 1.6f;

    private ClickEffect() {
    }

    public static void attach(View... views) {
        if (views == null) return;
        for (View view : views) {
            if (view == null) continue;
            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            if (!v.isEnabled()) return false;
                            press(v);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            release(v);
                            break;
                        default:
                            break;
                    }
                    return false;
                }
            });
        }
    }

    private static void press(View v) {
        v.animate().cancel();
        v.animate()
                .scaleX(PRESS_SCALE)
                .scaleY(PRESS_SCALE)
                .alpha(PRESS_ALPHA)
                .setDuration(DOWN_DURATION)
                .setInterpolator(new LinearInterpolator())
                .start();
    }

    private static void release(View v) {
        v.animate().cancel();
        v.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(UP_DURATION)
                .setInterpolator(new OvershootInterpolator(OVERSHOOT_TENSION))
                .start();
    }
}
