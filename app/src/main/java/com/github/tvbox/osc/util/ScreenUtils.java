package com.github.tvbox.osc.util;

import static android.content.Context.UI_MODE_SERVICE;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import com.github.tvbox.osc.R;

import me.jessyan.autosize.internal.CustomAdapt;

public class ScreenUtils {

    public static double getSqrt(Activity activity) {
        WindowManager wm = activity.getWindowManager();
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        double x = Math.pow(dm.widthPixels / dm.xdpi, 2);
        double y = Math.pow(dm.heightPixels / dm.ydpi, 2);
        double screenInches = Math.sqrt(x + y);// 屏幕尺寸
        return screenInches;
    }

    private static boolean checkScreenLayoutIsTv(Context context) {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) > Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    private static boolean checkIsPhone(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
    }

    public static boolean isTv(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION || (checkScreenLayoutIsTv(context) && !checkIsPhone(context));
    }

    /**
     * 手机版：当前页面“1 个设计单位”对应多少像素。
     * 竖屏页面设计宽 360、横屏页面设计宽 640；取屏幕短边/长边换算，
     * 这样即使显示方向还没切换完（或 AutoSize 的全局基准被写坏），结果也稳定等于设计密度。
     * 本项目的 vs_* / ts_* 均为 mm 单位，其换算同样依赖 DisplayMetrics#xdpi，这里不再走资源换算。
     */
    public static float pxPerMm(Context context) {
        float designWidth = 360f;
        Context c = context;
        while (c instanceof ContextWrapper) {
            if (c instanceof CustomAdapt) {
                designWidth = ((CustomAdapt) c).getSizeInDp();
                break;
            }
            Context base = ((ContextWrapper) c).getBaseContext();
            if (base == null || base == c) break;
            c = base;
        }
        if (designWidth <= 0) designWidth = 360f;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        // 设计宽 360 属于竖屏页面，取短边；640 属于横屏页面，取长边
        int side = designWidth > 400f ? Math.max(dm.widthPixels, dm.heightPixels)
                : Math.min(dm.widthPixels, dm.heightPixels);
        return side > 0 ? side / designWidth : 3f;
    }

    /**
     * 手机版：在代码里生成弹窗控件的胶囊背景（圆角 6 单位、描边 1 单位，含获得焦点态）。
     * 不从 button_danmu_setting 资源膨胀，避免 Resources 里缓存的旧 drawable
     * 沿用错误 mm 密度下的超大圆角/描边（旋转或退出全屏后系统会重置 DisplayMetrics）。
     */
    public static Drawable createDialogControlBackground(Context context, float pxPerMm) {
        int stroke = Math.max(1, Math.round(pxPerMm));
        float radius = pxPerMm * 6f;
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},
                controlShape(context, radius, stroke, true));
        background.addState(new int[]{},
                controlShape(context, radius, stroke, false));
        return background;
    }

    private static GradientDrawable controlShape(Context context, float radius, int stroke, boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(radius);
        shape.setColor(ContextCompat.getColor(context,
                focused ? R.color.dialog_control_bg_focused : R.color.dialog_control_bg));
        shape.setStroke(stroke, ContextCompat.getColor(context,
                focused ? R.color.dialog_control_stroke_focused : R.color.dialog_control_stroke));
        return shape;
    }
}
