package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

/**
 * @author acer
 * @date 2018/7/24
 */

public class NoScrollViewPager extends ViewPager {

    public NoScrollViewPager(@NonNull Context context) {
        this(context, null);
    }

    public NoScrollViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * 手机版：不再屏蔽触摸事件，由 ViewPager 自己接管左右滑动切换分类页
     */
    @Override
    public boolean executeKeyEvent(KeyEvent event) {
        return false;
    }
}
