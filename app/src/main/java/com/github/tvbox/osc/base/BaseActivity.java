package com.github.tvbox.osc.base;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowInsets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.PermissionChecker;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.util.AppManager;
import com.kingja.loadsir.callback.Callback;
import com.kingja.loadsir.core.LoadService;
import com.kingja.loadsir.core.LoadSir;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.AutoSizeCompat;
import me.jessyan.autosize.internal.CustomAdapt;
import xyz.doikki.videoplayer.util.CutoutUtil;

/**
 * @author pj567
 * @date :2020/12/17
 * @description:
 */
public abstract class BaseActivity extends AppCompatActivity implements CustomAdapt {
    protected Context mContext;
    private LoadService mLoadService;

    private static float screenRatio = -100.0f;
    // 手机版：AutoSizeConfig 是全局单例，这里记录已写入的基准，只有变化时才重设
    private static int autoSizeBaseWidth = 0;
    private static int autoSizeBaseHeight = 0;
    // 手机版：显示尺寸未与窗口一致前不写 AutoSize 基准（从横屏页返回的瞬间基准仍可能是横屏宽度）
    private int autoSizeRetryCount = 0;
    // 手机版：挂在 decorView 上等待密度就绪的动作，页面不可见时需要主动取消
    private Runnable pendingAutoSizeAction;
    private View pendingAutoSizeHost;
    private final Runnable refreshAutoSizeRunnable = new Runnable() {
        @Override
        public void run() {
            if (shouldRefreshAutoSize()) {
                refreshAutoSizeWhenStable();
            }
        }
    };
    private final Runnable hideSysBarRunnable = new Runnable() {
        @Override
        public void run() {
            hideSysBar();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            if (screenRatio < 0) {
                DisplayMetrics dm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(dm);
                updateScreenRatio(dm);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResID());
        mContext = this;
        initSystemUiListener();
        CutoutUtil.adaptCutoutAboveAndroidP(mContext, true);//设置刘海
        AppManager.getInstance().addActivity(this);
        init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSysBar();
        changeWallpaper(false);
        if (shouldRefreshAutoSize()) {
            refreshAutoSizeWhenStable();
            scheduleRefreshAutoSize();
        }
    }

    /**
     * 手机版：仅全屏播放（详情页全屏）与直播页面沉浸式隐藏系统栏，
     * 其他页面保留顶部状态栏（信号、电量等），避免遮挡。
     */
    public boolean isImmersionPage() {
        return false;
    }

    private static final int IMMERSIVE_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    /** 非沉浸页面：窗口绘制到状态栏下方，配合透明状态栏让窗口背景透出，内容再用 padding 下移 */
    private static final int EDGE_TO_EDGE_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;

    public void hideSysBar() {
        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        if (isImmersionPage()) {
            uiOptions |= IMMERSIVE_FLAGS;
        } else {
            uiOptions &= ~IMMERSIVE_FLAGS;
            uiOptions |= EDGE_TO_EDGE_FLAGS;
        }
        getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        applyStatusBarInset();
    }

    /**
     * 手机版：状态栏改为透明后，窗口内容会延伸到状态栏下方，
     * 这里给内容容器补上状态栏高度的上内边距，避免页面内容被状态栏遮挡；
     * 全屏沉浸页面不补内边距。
     */
    private void applyStatusBarInset() {
        View content = findViewById(android.R.id.content);
        if (content == null) return;
        int top = isImmersionPage() ? 0 : getStatusBarHeightPx();
        if (content.getPaddingTop() != top) {
            content.setPadding(content.getPaddingLeft(), top, content.getPaddingRight(), content.getPaddingBottom());
        }
    }

    private int getStatusBarHeightPx() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
                if (insets != null && insets.getSystemWindowInsetTop() > 0) {
                    return insets.getSystemWindowInsetTop();
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        int height = 0;
        try {
            Resources resources = super.getResources();
            int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                float autoDensity = resources.getDisplayMetrics().density;
                height = resources.getDimensionPixelSize(resourceId);
                // AutoSize 会改写 density，这里换算回真实像素
                DisplayMetrics realDm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(realDm);
                if (autoDensity > 0 && realDm.density > 0) {
                    height = Math.round(height / autoDensity * realDm.density);
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        if (height <= 0) {
            DisplayMetrics realDm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(realDm);
            height = Math.round(24 * realDm.density);
        }
        return height;
    }

    private void initSystemUiListener() {
        final View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                // 手机版：非沉浸页面保持状态栏常显，状态栏下拉等操作清掉标志后需重新应用
                if (!isImmersionPage()) {
                    if ((visibility & EDGE_TO_EDGE_FLAGS) != EDGE_TO_EDGE_FLAGS) {
                        decorView.removeCallbacks(hideSysBarRunnable);
                        decorView.post(hideSysBarRunnable);
                    }
                    return;
                }
                int hiddenBars = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
                if ((visibility & hiddenBars) != hiddenBars) {
                    decorView.removeCallbacks(hideSysBarRunnable);
                    decorView.postDelayed(hideSysBarRunnable, 300);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().getDecorView().removeCallbacks(refreshAutoSizeRunnable);
        getWindow().getDecorView().removeCallbacks(hideSysBarRunnable);
        // 手机版：页面不可见后不再执行待弹出的动作，避免在已销毁的页面上创建弹窗
        cancelPendingAutoSizeAction();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSysBar();
            if (shouldRefreshAutoSize()) {
                scheduleRefreshAutoSize();
            }
        }
    }

    protected boolean shouldRefreshAutoSize() {
        return false;
    }

    private void scheduleRefreshAutoSize() {
        View decorView = getWindow().getDecorView();
        decorView.removeCallbacks(refreshAutoSizeRunnable);
        decorView.postDelayed(refreshAutoSizeRunnable, 300);
    }

    public void refreshAutoSize() {
        try {
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            if (dm.widthPixels <= 0 || dm.heightPixels <= 0) {
                return;
            }
            updateScreenRatio(dm);
            applyAutoSizeDensity();
            getWindow().getDecorView().requestLayout();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    /**
     * 手机版：供弹窗在布局膨胀前调用，保证弹窗用的是正确的 mm 密度。
     * 弹窗的 Context 是 ContextThemeWrapper，可能持有独立的 Resources 实例
     * （系统配置变化时会被重置为原生密度，但不会经过 Activity 的 getResources 重写），
     * 所以这里除了校正全局基准，还要对弹窗自己的 Resources 再应用一次；
     * 否则 vs_* 尺寸和带 mm 描边的 shape 会按原生密度膨胀并被记进资源缓存，
     * 出现描边过粗、圆角接近半圆的异常。
     *
     * @param dialogResources 弹窗 Context 的 Resources，可为 null
     */
    public void prepareDialogAutoSize(Resources dialogResources) {
        applyAutoSizeDensity();
        if (dialogResources == null || dialogResources == super.getResources()) {
            return;
        }
        try {
            AutoSizeCompat.autoConvertDensityOfCustomAdapt(dialogResources, this);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    /** 手机版：按本页设计方向取显示尺寸的长边/短边，得到 AutoSize 的基准宽度 */
    private int calcAutoSizeWidth(DisplayMetrics dm) {
        return isLandscapePage() ? Math.max(dm.widthPixels, dm.heightPixels)
                : Math.min(dm.widthPixels, dm.heightPixels);
    }

    /** 手机版：与基准宽度对应的基准高度 */
    private int calcAutoSizeHeight(DisplayMetrics dm) {
        return isLandscapePage() ? Math.min(dm.widthPixels, dm.heightPixels)
                : Math.max(dm.widthPixels, dm.heightPixels);
    }

    /**
     * 手机版：先用"当前显示尺寸 + 本页设计方向"校正 AutoSize 的全局基准，再重写密度。
     * 从横屏页返回或旋转时系统会把 DisplayMetrics 重置为原生值，而本项目 vs_*、ts_* 都是 mm 单位，
     * mm 换算依赖 DisplayMetrics#xdpi；如果这里没有立即按正确基准重写，
     * 紧接着膨胀的 drawable（如 button_danmu_setting）会把数倍大的描边和圆角记进资源缓存，
     * 之后所有复用该 drawable 的控件（换源、指定搜索源条目）都会一直显示异常。
     */
    private void applyAutoSizeDensity() {
        try {
            // 直接用当前 Resources 里的 metrics：widthPixels/heightPixels 不会被 AutoSize 改写，
            // 始终反映显示的真实长宽，所以即使方向还没切换完也能算出正确的基准
            DisplayMetrics dm = super.getResources().getDisplayMetrics();
            int width = calcAutoSizeWidth(dm);
            int height = calcAutoSizeHeight(dm);
            if (width <= 0 || height <= 0) {
                return;
            }
            if (width != autoSizeBaseWidth || height != autoSizeBaseHeight) {
                // 只在基准变化时写全局配置
                autoSizeBaseWidth = width;
                autoSizeBaseHeight = height;
                AutoSizeConfig.getInstance().setScreenWidth(width).setScreenHeight(height);
            }
            AutoSizeCompat.autoConvertDensityOfCustomAdapt(super.getResources(), this);
        } catch (Throwable ignored) {
            // 该方法位于 getResources() 热路径，异常时不做任何输出，避免随每次资源访问刷日志
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        try {
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                updateScreenRatio(dm);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        // 手机版：方向变化后系统已把 DisplayMetrics 重置为原生密度，立即按新方向重写一次，
        // 避免错误 mm 密度下的 drawable 被膨胀并缓存
        applyAutoSizeDensity();
        autoSizeRetryCount = 0;
        getWindow().getDecorView().requestLayout();
    }

    /**
     * 手机版：当前显示方向是否已经和本页设计方向一致。
     * 从横屏直播全屏页返回的瞬间，Display 与窗口可能同时仍是横屏，
     * 只看"窗口与显示宽度一致"会误判为已稳定，从而把横屏宽度写成竖屏页面的基准。
     */
    private boolean isOrientationMatched(DisplayMetrics dm) {
        if (dm.widthPixels <= 0 || dm.heightPixels <= 0) {
            return false;
        }
        boolean displayPortrait = dm.widthPixels <= dm.heightPixels;
        return isLandscapePage() ? !displayPortrait : displayPortrait;
    }

    /** 手机版：窗口是否已经完成测量并与显示宽度一致 */
    private boolean isWindowMatched(DisplayMetrics dm, int decorWidth) {
        return decorWidth > 0 && dm.widthPixels > 0
                && Math.abs(dm.widthPixels - decorWidth) <= Math.max(8, dm.widthPixels / 10);
    }

    /** 手机版：方向与窗口是否已就绪（不含密度校验） */
    private boolean isAutoSizeLayoutReady() {
        try {
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            return isOrientationMatched(dm) && isWindowMatched(dm, getWindow().getDecorView().getWidth());
        } catch (Throwable th) {
            th.printStackTrace();
            return false;
        }
    }

    /**
     * 手机版：等显示方向与窗口就绪后再刷新 AutoSize 基准，最多重试 20 次（约 2 秒）。
     * 方向过渡期不做刷新，避免把横屏宽度写成竖屏页面的基准；
     * 超时静默放弃，下一次 onResume / 窗口焦点变化还会再触发。
     */
    public void refreshAutoSizeWhenStable() {
        if (isAutoSizeLayoutReady()) {
            autoSizeRetryCount = 0;
            refreshAutoSize();
            return;
        }
        if (autoSizeRetryCount++ < 20) {
            View decorView = getWindow().getDecorView();
            decorView.removeCallbacks(refreshAutoSizeRunnable);
            decorView.postDelayed(refreshAutoSizeRunnable, 100);
            return;
        }
        autoSizeRetryCount = 0;
    }

    /**
     * 手机版：等 AutoSize 基准可用后再执行（用于创建弹窗等按 mm 膨胀视图的操作）。
     * 判定可用之后先用当前窗口尺寸重写一次密度再执行，
     * 避免 Resources 里残留上次横屏写下的密度导致弹窗条目被放大。
     */
    public void postWhenAutoSizeStable(final Runnable action) {
        if (action == null) return;
        cancelPendingAutoSizeAction();
        final View decorView = getWindow().getDecorView();
        Runnable posted = new Runnable() {
            private int retry = 0;

            @Override
            public void run() {
                // 页面已销毁则丢弃，不再回调业务动作
                if (isFinishing() || isDestroyed()) {
                    clearPendingAutoSizeAction(this);
                    return;
                }
                if (isAutoSizeLayoutReady()) {
                    // 方向与窗口已就绪：先用当前窗口尺寸重写一次密度，再创建弹窗，
                    // 避免 Resources 里残留上一次横屏写下的密度
                    refreshAutoSize();
                    clearPendingAutoSizeAction(this);
                    action.run();
                    return;
                }
                if (retry++ < 20) {
                    decorView.postDelayed(this, 100);
                    return;
                }
                // 超时：刷新一次后仍执行，避免按钮无响应
                refreshAutoSize();
                clearPendingAutoSizeAction(this);
                action.run();
            }
        };
        pendingAutoSizeAction = posted;
        pendingAutoSizeHost = decorView;
        decorView.post(posted);
    }

    /** 手机版：取消尚未执行的"密度就绪后动作" */
    private void cancelPendingAutoSizeAction() {
        if (pendingAutoSizeAction != null && pendingAutoSizeHost != null) {
            pendingAutoSizeHost.removeCallbacks(pendingAutoSizeAction);
        }
        pendingAutoSizeAction = null;
        pendingAutoSizeHost = null;
    }

    private void clearPendingAutoSizeAction(Runnable posted) {
        if (pendingAutoSizeAction == posted) {
            pendingAutoSizeAction = null;
            pendingAutoSizeHost = null;
        }
    }

    private void updateScreenRatio(DisplayMetrics dm) {
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;
        int min = Math.min(screenWidth, screenHeight);
        if (min > 0) {
            screenRatio = (float) Math.max(screenWidth, screenHeight) / (float) min;
        }
    }

    @Override
    public Resources getResources() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // 手机版：基准可能被横屏页写成横屏宽度，这里每次都按当前显示尺寸校正后再应用密度，
            // 避免竖屏页面的 mm 换算被放大（弹窗描边/圆角异常即由此产生）
            applyAutoSizeDensity();
        }
        return super.getResources();
    }

    public boolean hasPermission(String permission) {
        boolean has = true;
        try {
            has = PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return has;
    }

    protected abstract int getLayoutResID();

    protected abstract void init();

    protected void setLoadSir(View view) {
        if (mLoadService == null) {
            mLoadService = LoadSir.getDefault().register(view, new Callback.OnReloadListener() {
                @Override
                public void onReload(View v) {
                }
            });
        }
    }

    protected void showLoading() {
        if (mLoadService != null) {
            mLoadService.showCallback(LoadingCallback.class);
        }
    }

    protected boolean isLoading() {
        if (mLoadService != null && mLoadService.getCurrentCallback() != null) {
            return mLoadService.getCurrentCallback().equals(LoadingCallback.class);
        }
        return false;
    }

    protected void showEmpty() {
        if (null != mLoadService) {
            mLoadService.showCallback(EmptyCallback.class);
        }
    }

    protected void showSuccess() {
        if (null != mLoadService) {
            mLoadService.showSuccess();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppManager.getInstance().finishActivity(this);
    }

    public void jumpActivity(Class<? extends BaseActivity> clazz) {
        Intent intent = new Intent(mContext, clazz);
        startActivity(intent);
    }

    public void jumpActivity(Class<? extends BaseActivity> clazz, Bundle bundle) {
        Intent intent = new Intent(mContext, clazz);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    protected String getAssetText(String fileName) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            AssetManager assets = getAssets();
            BufferedReader bf = new BufferedReader(new InputStreamReader(assets.open(fileName)));
            String line;
            while ((line = bf.readLine()) != null) {
                stringBuilder.append(line);
            }
            return stringBuilder.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 手机版适配：横屏页面（直播/推送）返回 true，使用 640 设计基准；
     * 竖屏页面返回 false，使用 360 设计基准。
     */
    protected boolean isLandscapePage() {
        return false;
    }

    @Override
    public float getSizeInDp() {
        return isLandscapePage() ? 640 : 360;
    }

    @Override
    public boolean isBaseOnWidth() {
        return true;
    }

    protected static BitmapDrawable globalWp = null;

    public void changeWallpaper(boolean force) {
        if (!force && globalWp != null)
            getWindow().setBackgroundDrawable(globalWp);
        try {
            File wp = new File(getFilesDir().getAbsolutePath() + "/wp");
            if (wp.exists()) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(wp.getAbsolutePath(), opts);
                // 从Options中获取图片的分辨率
                int imageHeight = opts.outHeight;
                int imageWidth = opts.outWidth;
                int picHeight = 720;
                int picWidth = 1080;
                int scaleX = imageWidth / picWidth;
                int scaleY = imageHeight / picHeight;
                int scale = 1;
                if (scaleX > scaleY && scaleY >= 1) {
                    scale = scaleX;
                }
                if (scaleX < scaleY && scaleX >= 1) {
                    scale = scaleY;
                }
                opts.inJustDecodeBounds = false;
                // 采样率
                opts.inSampleSize = scale;
                globalWp = new BitmapDrawable(BitmapFactory.decodeFile(wp.getAbsolutePath(), opts));
            } else {
                globalWp = null;
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            globalWp = null;
        }
        if (globalWp != null)
            getWindow().setBackgroundDrawable(globalWp);
        else
            getWindow().setBackgroundDrawableResource(R.drawable.app_bg);
    }
}