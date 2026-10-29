package com.github.tvbox.osc.ui.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.graphics.drawable.ColorDrawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.MainActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.CategoryGridAdapter;
import com.github.tvbox.osc.ui.adapter.HomePageAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.adapter.SortAdapter;
import com.github.tvbox.osc.ui.dialog.PushDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.ui.fragment.UserFragment;
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer;
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.ClickEffect;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class HomeFragment extends BaseLazyFragment {
    private LinearLayout topLayout;
    private LinearLayout contentLayout;
    private TextView tvDate;
    private TextView tvName;
    private TvRecyclerView mGridView;
    private NoScrollViewPager mViewPager;
    private PopupWindow categoryPopup;
    /** 手机版：上次尝试弹出分类弹窗的时间，用于忽略连续快速点击（等待密度就绪期间重复点击会排队重复弹出） */
    private long lastCategoryPopupTime;
    private TvRecyclerView rvCategories;
    private CategoryGridAdapter categoryAdapter;
    private SourceViewModel sourceViewModel;
    private SortAdapter sortAdapter;
    private HomePageAdapter pageAdapter;
    private View currentView;
    private final List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean isDownOrUp = false;
    private boolean sortChange = false;
    private int currentSelected = 0;
    private int sortFocused = 0;
    public View sortFocusView = null;
    private String loadingSourceKey;
    private String previousHomeName;
    private SourceBean previousHomeSource;
    private boolean homeSortLoading = false;
    private boolean refreshHomeRec = false;
    private final Handler mHandler = new Handler();
    private long mExitTime = 0;
    private boolean eventBusRegistered = false;
    private final Runnable mRunnable = new Runnable() {
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            Date date = new Date();
            SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy/MM/dd  E  HH:mm", Locale.CHINA);
            tvDate.setText(timeFormat.format(date));
            mHandler.postDelayed(this, 1000);
        }
    };
    private final Runnable refreshTopInfoTextSizeRunnable = new Runnable() {
        @Override
        public void run() {
            refreshTopInfoTextSize();
        }
    };
    private final Runnable refreshTopLayoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (topLayout == null || isActivityUnavailable() || currentSelected != 0 || topHide != 0) {
                return;
            }
            // OnePlus devices may finish applying immersive mode after the first measure.
            // Re-apply the visible top state once the final display metrics are available.
            if (mActivity instanceof BaseActivity) {
                ((BaseActivity) mActivity).hideSysBar();
            }
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) topLayout.getLayoutParams();
            // 手机版：状态栏下方不再预留大块空白，文案贴顶
            params.topMargin = AutoSizeUtils.mm2px(mActivity, 0f);
            params.height = AutoSizeUtils.mm2px(mActivity, 36.0f);
            topLayout.setLayoutParams(params);
            topLayout.setAlpha(1.0f);
            refreshTopInfoTextSize();
            topLayout.requestLayout();
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_home;
    }

    boolean useCacheConfig = false;

    @Override
    protected void init() {
        EventBus.getDefault().register(this);
        eventBusRegistered = true;
        initView();
        initViewModel();
        useCacheConfig = false;
        Bundle args = getArguments();
        if (args != null) {
            useCacheConfig = args.getBoolean("useCache", false);
        }
        initData();
    }

    private void initView() {
        this.topLayout = findViewById(R.id.topLayout);
        this.tvDate = findViewById(R.id.tvDate);
        this.tvName = findViewById(R.id.tvName);
        this.contentLayout = findViewById(R.id.contentLayout);
        this.mGridView = findViewById(R.id.mGridView);
        this.mViewPager = findViewById(R.id.mViewPager);
        // 手机版：左右滑动分类页后，同步分类标签与顶栏，不反向触发 setCurrentItem
        this.mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                onHomePageSelected(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
        // 手机版适配：顶栏触摸按钮
        findViewById(R.id.tvChangeSourceHome).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                showSiteSwitch();
            }
        });
        // 手机版：设置入口移到顶栏最右，只保留图标
        findViewById(R.id.tvSettingHome).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                jumpActivity(SettingActivity.class);
            }
        });
        // 手机版：收藏入口移到顶栏图标，位于“推送”左侧
        findViewById(R.id.tvCollectHome).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                jumpActivity(CollectActivity.class);
            }
        });
        // 手机版：原“设置”入口已移到底部导航，顶栏改为图标式“推送”按钮
        findViewById(R.id.tvPushHome).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                // 手机版：推送改为弹窗输入地址，不再打开横屏二维码页面
                new PushDialog(mActivity).show();
            }
        });
        // 手机版：顶栏四个图标按钮补充按压反馈（底部导航不做特效）
        ClickEffect.attach(
                findViewById(R.id.tvCollectHome),
                findViewById(R.id.tvPushHome),
                findViewById(R.id.tvChangeSourceHome),
                findViewById(R.id.tvSettingHome));
        // 分类行最右侧按钮：下拉弹窗展示全部分类标签
        findViewById(R.id.ivCategoryMore).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                showCategoryPopup();
            }
        });
        // 单 Activity 架构：底部导航由 MainActivity 统一持有，隐藏本布局内的副本
        View bottomBar = findViewById(R.id.userBottomBar);
        if (bottomBar != null) {
            bottomBar.setVisibility(View.GONE);
        }
        this.sortAdapter = new SortAdapter();
        this.mGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        // 手机版：分类行贴左显示并压缩列间距
        this.mGridView.setSpacingWithMargins(AutoSizeUtils.mm2px(this.mContext, 2.0f), AutoSizeUtils.dp2px(this.mContext, 4.0f));
        this.mGridView.setAdapter(this.sortAdapter);
        sortAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                mGridView.post(() -> {
                    View firstChild = Objects.requireNonNull(mGridView.getLayoutManager()).findViewByPosition(0);
                    if (firstChild != null) {
                        mGridView.setSelectedPosition(0);
                        firstChild.requestFocus();
                    }
                });
            }
        });
        this.mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null && !isDownOrUp) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            TextView textView = view.findViewById(R.id.tvTitle);
                            textView.getPaint().setFakeBoldText(false);
                            if (sortFocused == p) {
                                view.animate().scaleX(1.05f).scaleY(1.05f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
                                textView.setTextColor(getResources().getColor(R.color.color_FFFFFF));
                            } else {
                                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start();
                                textView.setTextColor(getResources().getColor(R.color.color_BBFFFFFF));
                                view.findViewById(R.id.tvFilter).setVisibility(View.GONE);
                                view.findViewById(R.id.tvFilterColor).setVisibility(View.GONE);
                            }
                            textView.invalidate();
                        }

                        public final int p = position;
                    }, 10);
                }
            }

            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null) {
                    currentView = view;
                    isDownOrUp = false;
                    sortChange = true;
                    view.animate().scaleX(1.05f).scaleY(1.05f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(true);
                    textView.setTextColor(getResources().getColor(R.color.color_FFFFFF));
                    textView.invalidate();
                    MovieSort.SortData sortData = sortAdapter.getItem(position);
                    if (!sortData.filters.isEmpty()) {
                        showFilterIcon(sortData.filterSelectCount());
                    }
                    sortFocusView = view;
                    sortFocused = position;
                    mHandler.removeCallbacks(mDataRunnable);
                    mHandler.postDelayed(mDataRunnable, 200);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                // 手机版：分类标签只负责切换分类，重复点击不再弹出换源弹窗（改由顶栏换源按钮触发）
                if (itemView != null && currentSelected == position) {
                    BaseLazyFragment baseLazyFragment = fragments.get(currentSelected);
                    if ((baseLazyFragment instanceof GridFragment) && !sortAdapter.getItem(position).filters.isEmpty()) {// 弹出筛选
                        ((GridFragment) baseLazyFragment).showFilter();
                    }
                }
            }
        });

        this.mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            public boolean onInBorderKeyEvent(int direction, View view) {
                if (direction == View.FOCUS_UP) {
                    BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                    if (baseLazyFragment instanceof UserFragment) {
                        refreshHomeSort();
                        return true;
                    }
                    if (baseLazyFragment instanceof GridFragment) {
                        ((GridFragment) baseLazyFragment).forceRefresh();
                        return true;
                    }
                }
                if (direction != View.FOCUS_DOWN) {
                    return false;
                }
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (!(baseLazyFragment instanceof GridFragment)) {
                    return false;
                }
                return !((GridFragment) baseLazyFragment).isLoad();
            }
        });
        tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                if(dataInitOk && jarInitOk){
                    String jar=ApiConfig.get().getHomeSourceBean().getJar();
                    String jarUrl=!jar.isEmpty()?jar:ApiConfig.get().getSpider();
                    String jarSource = jarUrl.split(";md5;")[0];
                    File cspCacheDir = new File(FileUtils.getFilePath() + "/csp/" + MD5.string2MD5(jarSource) + ".jar");
                    File jarCacheDir = new File(FileUtils.getCachePath() + "/jar/" + MD5.string2MD5(jarSource) + ".jar");
                    File jarFullCacheDir = new File(FileUtils.getCachePath() + "/jar/" + MD5.string2MD5(jarUrl) + ".jar");
                    Toast.makeText(mContext, "缓存已清除", Toast.LENGTH_LONG).show();
                    new Thread(() -> {
                        try {
                            FileUtils.deleteFile(cspCacheDir);
                            FileUtils.deleteFile(jarCacheDir);
                            FileUtils.deleteFile(jarFullCacheDir);
                            FileUtils.clearSpiderCacheFiles();
                            ApiConfig.get().clearSpiderCache();
                            refreshHome();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();

                }else {
                    jumpActivity(SettingActivity.class);
                }
            }
        });
        tvName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                jumpActivity(SettingActivity.class);
                return true;
            }
        });
        setLoadSir(this.contentLayout);
        //mHandler.postDelayed(mFindFocus, 500);
    }


    /** 下拉弹窗：把分类行的全部标签以多行等宽网格展示 */
    private void showCategoryPopup() {
        if (sortAdapter == null || sortAdapter.getData() == null || sortAdapter.getData().isEmpty()) {
            return;
        }
        if (categoryPopup != null && categoryPopup.isShowing()) {
            categoryPopup.dismiss();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCategoryPopupTime < 600 || !(mActivity instanceof BaseActivity)) {
            return;
        }
        // 手机版：与换源/指定搜索源保持一致，等密度基准稳定后再按 mm 膨胀弹窗，
        // 避免从横屏/全屏页返回的瞬间把条目按错误密度撑大
        lastCategoryPopupTime = now;
        ((BaseActivity) mActivity).postWhenAutoSizeStable(new Runnable() {
            @Override
            public void run() {
                if (isActivityUnavailable()) {
                    return;
                }
                showCategoryPopupNow();
            }
        });
    }

    private void showCategoryPopupNow() {
        if (sortAdapter == null || sortAdapter.getData() == null || sortAdapter.getData().isEmpty()) {
            return;
        }
        View content = LayoutInflater.from(mContext).inflate(R.layout.popup_home_categories, null);
        rvCategories = content.findViewById(R.id.rvCategories);
        rvCategories.setLayoutManager(new V7GridLayoutManager(mContext, 3));
        rvCategories.setSpacingWithMargins(AutoSizeUtils.mm2px(mContext, 2.0f), AutoSizeUtils.dp2px(mContext, 4.0f));
        categoryAdapter = new CategoryGridAdapter();
        categoryAdapter.setSelectedPos(currentSelected);
        rvCategories.setAdapter(categoryAdapter);
        categoryAdapter.setNewData(new ArrayList<>(sortAdapter.getData()));

        // 按行数计算弹窗高度，超出屏幕一半时内部滚动
        int columns = 3;
        int count = categoryAdapter.getData().size();
        int rows = (count + columns - 1) / columns;
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.55f);
        int height = Math.min(rows * AutoSizeUtils.mm2px(mContext, 26.0f) + AutoSizeUtils.mm2px(mContext, 8.0f), maxHeight);
        ViewGroup.LayoutParams lp = rvCategories.getLayoutParams();
        lp.height = height;
        rvCategories.setLayoutParams(lp);

        categoryAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (view != null) {
                    FastClickCheckUtil.check(view);
                }
                dismissCategoryPopup();
                selectSortFromPopup(position);
            }
        });
        // 数据已绑定后再挂点击监听，确保单次点击即可触发
        categoryAdapter.notifyDataSetChanged();

        categoryPopup = new PopupWindow(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        categoryPopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        categoryPopup.setOutsideTouchable(true);
        categoryPopup.setAnimationStyle(R.style.PopupDropDownAnim);
        categoryPopup.showAsDropDown(findViewById(R.id.sortBarLayout), 0, 0);
        categoryPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                categoryPopup = null;
            }
        });
        rvCategories.post(new Runnable() {
            @Override
            public void run() {
                if (rvCategories != null) {
                    rvCategories.setSelectedPosition(currentSelected);
                }
            }
        });
    }

    private void dismissCategoryPopup() {
        if (categoryPopup != null && categoryPopup.isShowing()) {
            categoryPopup.dismiss();
        }
    }

    /** 弹窗选中分类：把焦点移到分类行对应标签，复用原有的切换流程加载页面 */
    private void selectSortFromPopup(final int position) {
        if (sortAdapter == null || position < 0 || position >= sortAdapter.getData().size()) {
            return;
        }
        if (position == currentSelected) {
            return;
        }
        View child = mGridView.getLayoutManager() != null
                ? mGridView.getLayoutManager().findViewByPosition(position) : null;
        if (child != null) {
            child.requestFocus();
            return;
        }
        mGridView.setSelectedPosition(position);
        mGridView.scrollToPosition(position);
        mGridView.postDelayed(new Runnable() {
            @Override
            public void run() {
                RecyclerView.ViewHolder holder = mGridView.findViewHolderForAdapterPosition(position);
                if (holder != null) {
                    holder.itemView.requestFocus();
                } else {
                    sortFocused = position;
                    sortChange = true;
                    mHandler.removeCallbacks(mDataRunnable);
                    mHandler.postDelayed(mDataRunnable, 50);
                }
            }
        }, 150);
    }

    private boolean skipNextUpdate = false;

    /** 手机版：底部“主页”返回主界面（切到首个分类页并回到海报区顶部） */
    public void backToHome() {
        if (mViewPager.getCurrentItem() != 0) {
            mViewPager.setCurrentItem(0, true);
        }
        if (!fragments.isEmpty() && fragments.get(0) instanceof GridFragment) {
            ((GridFragment) fragments.get(0)).scrollTop();
        }
    }

    /** 手机版：ViewPager 滑动选中某页后同步分类标签（点击标签/弹窗切换时 position == currentSelected，直接返回） */
    private void onHomePageSelected(int position) {
        if (position == currentSelected) {
            return;
        }
        currentSelected = position;
        sortFocused = position;
        sortChange = false;
        mHandler.removeCallbacks(mDataRunnable);
        syncSortBarByPage(position);
        if (position >= 0 && position < fragments.size()) {
            BaseLazyFragment baseLazyFragment = fragments.get(position);
            if (baseLazyFragment instanceof GridFragment && ((GridFragment) baseLazyFragment).shouldReloadOnSelect()) {
                ((GridFragment) baseLazyFragment).forceRefresh();
            }
        }
    }

    private void syncSortBarByPage(final int position) {
        if (sortAdapter == null || sortAdapter.getData() == null
                || position < 0 || position >= sortAdapter.getData().size()) {
            return;
        }
        View child = mGridView.getLayoutManager() != null
                ? mGridView.getLayoutManager().findViewByPosition(position) : null;
        if (child == null) {
            mGridView.setSelectedPosition(position);
            mGridView.scrollToPosition(position);
            mGridView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    RecyclerView.ViewHolder holder = mGridView.findViewHolderForAdapterPosition(position);
                    if (holder != null) {
                        applySortVisual(holder.itemView, position);
                        holder.itemView.requestFocus();
                    }
                }
            }, 100);
            return;
        }
        applySortVisual(child, position);
        child.requestFocus();
    }

    /** 直接把分类标签切到选中态，不依赖触屏模式下可能失效的焦点回调 */
    private void applySortVisual(View view, int position) {
        if (view == null) {
            return;
        }
        if (sortFocusView != null && sortFocusView != view) {
            TextView oldTitle = sortFocusView.findViewById(R.id.tvTitle);
            if (oldTitle != null) {
                oldTitle.getPaint().setFakeBoldText(false);
                oldTitle.setTextColor(getResources().getColor(R.color.color_BBFFFFFF));
                oldTitle.invalidate();
            }
            sortFocusView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start();
            sortFocusView.findViewById(R.id.tvFilter).setVisibility(View.GONE);
            sortFocusView.findViewById(R.id.tvFilterColor).setVisibility(View.GONE);
        }
        currentView = view;
        sortFocusView = view;
        view.animate().scaleX(1.05f).scaleY(1.05f).setInterpolator(new BounceInterpolator()).setDuration(300).start();
        TextView textView = view.findViewById(R.id.tvTitle);
        if (textView != null) {
            textView.getPaint().setFakeBoldText(true);
            textView.setTextColor(getResources().getColor(R.color.color_FFFFFF));
            textView.invalidate();
        }
        MovieSort.SortData sortData = sortAdapter.getItem(position);
        if (sortData != null && !sortData.filters.isEmpty()) {
            showFilterIcon(sortData.filterSelectCount());
        } else {
            view.findViewById(R.id.tvFilter).setVisibility(View.GONE);
            view.findViewById(R.id.tvFilterColor).setVisibility(View.GONE);
        }
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, new Observer<AbsSortXml>() {
            @Override
            public void onChanged(AbsSortXml absXml) {
                if (skipNextUpdate) {
                    skipNextUpdate = false;
                    return;
                }
                if (!homeSortLoading && loadingSourceKey == null) {
                    return;
                }
                if (absXml != null && absXml.sourceKey != null && loadingSourceKey != null && !loadingSourceKey.equals(absXml.sourceKey)) {
                    return;
                }
                SourceBean home = ApiConfig.get().getHomeSourceBean();
                showSuccess();
                clearHomePages();
                List<MovieSort.SortData> newSortData;
                if (absXml != null && absXml.classes != null && absXml.classes.sortList != null) {
                    newSortData = DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), absXml.classes.sortList, true);
                } else {
                    newSortData = DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true);
                }
                updateSortData(newSortData);
                initViewPager(absXml);
                updateHomeRec(absXml);
                if (home != null && home.getName() != null && !home.getName().isEmpty()) tvName.setText(home.getName());
                tvName.clearAnimation();
                homeSortLoading = false;
                loadingSourceKey = null;
                previousHomeName = null;
                previousHomeSource = null;
            }
        });
    }

    private boolean dataInitOk = false;
    private boolean jarInitOk = false;
    private boolean searchSpiderWarmStarted = false;
    private TipDialog mConfigErrorDialog;

    private void initData() {
        if (dataInitOk && jarInitOk) {
            loadHomeSort(false);
            if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                LOG.e("有");
            } else {
                LOG.e("无");
            }
            if (!useCacheConfig && Hawk.get(HawkConfig.DEFAULT_LOAD_LIVE, false)) {
                jumpActivity(LivePlayActivity.class);
            }
            //爬虫预热 仅首次加载
            if(!useCacheConfig)warmSearchSpidersOnce();
            return;
        }
        tvNameAnimation();
        showLoading();
        if (dataInitOk && !jarInitOk) {
            if (!ApiConfig.get().getSpider().isEmpty()) {
                ApiConfig.get().loadJar(useCacheConfig, ApiConfig.get().getSpider(), new ApiConfig.LoadConfigCallback() {
                    @Override
                    public void success() {
                        jarInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
//                                if (!useCacheConfig) Toast.makeText(mActivity, "自定义jar加载成功", Toast.LENGTH_SHORT).show();
                                initData();
                            }
                        }, 50);
                    }

                    @Override
                    public void notice(String msg) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void error(String msg) {
                        jarInitOk = true;
                        dataInitOk = true;
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mActivity, msg+" jar load err", Toast.LENGTH_SHORT).show();
                                initData();
                            }
                        },50);
                    }
                });
            }
            return;
        }
        ApiConfig.get().loadConfig(useCacheConfig, new ApiConfig.LoadConfigCallback() {
            @Override
            public void notice(String msg) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void success() {
                dataInitOk = true;
                if (ApiConfig.get().getSpider().isEmpty()) {
                    jarInitOk = true;
                }
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        initData();
                    }
                }, 50);
            }

            @Override
            public void error(String msg) {
                if (msg.equalsIgnoreCase("-1")) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            dataInitOk = true;
                            jarInitOk = true;
                            initData();
                        }
                    });
                    return;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityUnavailable()) {
                            return;
                        }
                        if (mConfigErrorDialog == null)
                            mConfigErrorDialog = new TipDialog(mActivity, msg, "重试", "取消", new TipDialog.OnListener() {
                                @Override
                                public void left() {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }

                                @Override
                                public void right() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }

                                @Override
                                public void cancel() {
                                    dataInitOk = true;
                                    jarInitOk = true;
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissConfigErrorDialog();
                                            initData();
                                        }
                                    });
                                }
                            });
                        if (!mConfigErrorDialog.isShowing())
                            mConfigErrorDialog.show();
                    }
                });
            }
        }, mActivity);
    }

    private void warmSearchSpidersOnce() {
        if (searchSpiderWarmStarted) return;
        searchSpiderWarmStarted = true;
        ApiConfig.get().warmSearchSpiders();
    }

    private void loadHomeSort(boolean keepCurrentContent) {
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        homeSortLoading = keepCurrentContent;
        if (keepCurrentContent && home != null && home.getName() != null && !home.getName().isEmpty()) {
            previousHomeName = tvName.getText() == null ? null : tvName.getText().toString();
            tvName.setText(home.getName());
        }
        tvNameAnimation();
        if (home == null) {
            loadingSourceKey = null;
            if (!keepCurrentContent) showLoading();
            sourceViewModel.getSort(null);
            return;
        }
        loadingSourceKey = home.getKey();
        if (!keepCurrentContent) {
            showLoading();
        }
        sourceViewModel.getSort(loadingSourceKey);
    }

    private void initViewPager(AbsSortXml absXml) {
        if (sortAdapter.getData().size() > 0) {
            for (MovieSort.SortData data : sortAdapter.getData()) {
                if (data.id.equals("my0")) {
                    if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) == 1 && absXml != null && absXml.videoList != null && absXml.videoList.size() > 0) {
                        fragments.add(UserFragment.newInstance(absXml.videoList));
                    } else {
                        fragments.add(UserFragment.newInstance(null));
                    }
                } else {
                    fragments.add(GridFragment.newInstance(data));
                }
            }
            pageAdapter = new HomePageAdapter(getChildFragmentManager(), fragments);
            mViewPager.setPageTransformer(true, new DefaultTransformer());
            mViewPager.setAdapter(pageAdapter);
            mViewPager.setCurrentItem(currentSelected, false);
        }
    }

    private void clearHomePages() {
        mHandler.removeCallbacks(mDataRunnable);
        currentSelected = 0;
        sortFocused = 0;
        sortChange = false;
        sortFocusView = null;
        currentView = null;
        if (pageAdapter != null) {
            mViewPager.setAdapter(null);
            pageAdapter.removeAll();
            pageAdapter = null;
        } else if (!fragments.isEmpty()) {
            fragments.clear();
        }
    }

    private void updateSortData(List<MovieSort.SortData> newSortData) {
        if (newSortData == null) {
            newSortData = new ArrayList<>();
        }
        List<MovieSort.SortData> oldSortData = sortAdapter.getData();
        if (oldSortData.isEmpty()
                || newSortData.isEmpty()
                || oldSortData.get(0) == null
                || newSortData.get(0) == null
                || !"my0".equals(oldSortData.get(0).id)
                || !"my0".equals(newSortData.get(0).id)) {
            sortAdapter.setNewData(newSortData);
            return;
        }
        int oldTailCount = oldSortData.size() - 1;
        if (oldTailCount > 0) {
            oldSortData.subList(1, oldSortData.size()).clear();
            sortAdapter.notifyItemRangeRemoved(1, oldTailCount);
        }
        if (newSortData.size() > 1) {
            oldSortData.addAll(newSortData.subList(1, newSortData.size()));
            sortAdapter.notifyItemRangeInserted(1, newSortData.size() - 1);
        }
    }

    /**
     * 手机版：返回键先交给主页处理。
     *
     * @return true 表示已在页内消费；false 表示交给 MainActivity 决定切换 tab 或退出
     */
    @SuppressLint("NotifyDataSetChanged")
    public boolean handleBack() {
        // 打断加载
        if (homeSortLoading) {
            cancelHomeSortLoading();
            return true;
        }
        if (isLoading()) {
            refreshEmpty();
            return true;
        }
        // 如果处于 VOD 删除模式，则退出该模式并刷新界面
        if (HawkConfig.hotVodDelete) {
            HawkConfig.hotVodDelete = false;
            UserFragment.homeHotVodAdapter.notifyDataSetChanged();
            return true;
        }

        // 检查 fragments 状态
        if (this.fragments.size() <= 0 || this.sortFocused >= this.fragments.size() || this.sortFocused < 0) {
            return false;
        }

        BaseLazyFragment baseLazyFragment = this.fragments.get(this.sortFocused);
        if (baseLazyFragment instanceof GridFragment) {
            GridFragment grid = (GridFragment) baseLazyFragment;
            // 如果当前 Fragment 能恢复之前保存的 UI 状态，则直接返回
            if (grid.restoreView()) {
                return true;
            }
            // 如果 sortFocusView 存在且没有获取焦点，则请求焦点
            if (this.sortFocusView != null && !this.sortFocusView.isFocused()) {
                this.sortFocusView.requestFocus();
                return true;
            }
            // 如果当前不是第一个界面，则将列表设置到第一项
            else if (this.sortFocused != 0) {
                this.mGridView.setSelection(0);
                return true;
            }
            return false;
        } else if (baseLazyFragment instanceof UserFragment && UserFragment.tvHotList.canScrollVertically(-1)) {
            // 如果 UserFragment 列表可以向上滚动，则滚动到顶部
            UserFragment.tvHotList.scrollToPosition(0);
            this.mGridView.setSelection(0);
            return true;
        }
        return false;
    }

    @Override
    protected void onFragmentResume() {
        refreshTopInfoTextSize();
        mHandler.removeCallbacks(refreshTopInfoTextSizeRunnable);
        mHandler.postDelayed(refreshTopInfoTextSizeRunnable, 350);
        mHandler.removeCallbacks(refreshTopLayoutRunnable);
        mHandler.postDelayed(refreshTopLayoutRunnable, 450);
        mHandler.post(mRunnable);
    }


    @Override
    protected void onFragmentPause() {
        mHandler.removeCallbacks(refreshTopInfoTextSizeRunnable);
        mHandler.removeCallbacks(refreshTopLayoutRunnable);
        mHandler.removeCallbacks(mRunnable);
    }

    private void refreshTopInfoTextSize() {
        if (tvName == null || tvDate == null) {
            return;
        }
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ts_22));
        tvDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.ts_26));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            if (ApiConfig.get().getSource("push_agent") != null) {
                Intent newIntent = new Intent(mContext, DetailActivity.class);
                newIntent.putExtra("id", (String) event.obj);
                newIntent.putExtra("sourceKey", "push_agent");
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivity(newIntent);
            }
        } else if (event.type == RefreshEvent.TYPE_FILTER_CHANGE) {
            if (currentView != null) {
                showFilterIcon((int) event.obj);
            }
        } else if (event.type == RefreshEvent.TYPE_HOME_SOURCE_CHANGE) {
            refreshHome(false);
        }
    }

    private void showFilterIcon(int count) {
        boolean visible = count > 0;
        currentView.findViewById(R.id.tvFilterColor).setVisibility(visible ? View.VISIBLE : View.GONE);
        currentView.findViewById(R.id.tvFilter).setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private final Runnable mDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (sortChange) {
                sortChange = false;
                BaseLazyFragment baseLazyFragment = fragments.get(sortFocused);
                if (sortFocused != currentSelected) {
                    currentSelected = sortFocused;
                    mViewPager.setCurrentItem(sortFocused, false);
                    if (baseLazyFragment instanceof GridFragment && ((GridFragment) baseLazyFragment).shouldReloadOnSelect()) {
                        ((GridFragment) baseLazyFragment).forceRefresh();
                    }
                } else if (baseLazyFragment instanceof GridFragment && ((GridFragment) baseLazyFragment).shouldReloadOnSelect()) {
                    ((GridFragment) baseLazyFragment).forceRefresh();
                }
            }
        }
    };

    private long menuKeyDownTime = 0;
    private static final long LONG_PRESS_THRESHOLD = 2000; // 设置长按的阈值，单位是毫秒
    public boolean handleKeyEvent(KeyEvent event) {
        if (topHide < 0)
            return false;
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                menuKeyDownTime = System.currentTimeMillis();
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                long pressDuration = System.currentTimeMillis() - menuKeyDownTime;
                if (pressDuration >= LONG_PRESS_THRESHOLD) {
                    jumpActivity(SettingActivity.class);;
                }else {
                    showSiteSwitch();
                }
            }
        }
        return false;
    }

    byte topHide = 0;

    @Override
    public void onDestroy() {
        dismissCategoryPopup();
        dismissHomeDialogs();
        mHandler.removeCallbacksAndMessages(null);
        unregisterEventBus();
        super.onDestroy();
    }

    private void unregisterEventBus() {
        if (eventBusRegistered) {
            EventBus.getDefault().unregister(this);
            eventBusRegistered = false;
        }
    }

    private SelectDialog<SourceBean> mSiteSwitchDialog;

    void showSiteSwitch() {
        if (isActivityUnavailable()) return;
        // 手机版：从横屏直播页返回的瞬间 AutoSize 基准可能仍是横屏宽度，
        // 此时膨胀出来的弹窗条目会被放大，等显示尺寸稳定后再创建面板。
        if (mActivity instanceof BaseActivity) {
            ((BaseActivity) mActivity).postWhenAutoSizeStable(new Runnable() {
                @Override
                public void run() {
                    showSiteSwitchNow();
                }
            });
            return;
        }
        showSiteSwitchNow();
    }

    private void showSiteSwitchNow() {
        if (isActivityUnavailable()) return;
        List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        if (sites.isEmpty()) return;
        int select = sites.indexOf(ApiConfig.get().getHomeSourceBean());
        if (select < 0 || select >= sites.size()) select = 0;
        if (mSiteSwitchDialog == null) {
            // 手机版：换源改为贴底全宽面板，从屏幕下缘向上弹出
            mSiteSwitchDialog = new SelectDialog<>(mActivity, true);
            TvRecyclerView tvRecyclerView = mSiteSwitchDialog.findViewById(R.id.list);
            // 根据 sites 数量动态计算列数
            int spanCount = (int) Math.floor(sites.size() / 20.0);
            spanCount = Math.min(spanCount, 2);
            tvRecyclerView.setLayoutManager(new V7GridLayoutManager(mSiteSwitchDialog.getContext(), spanCount + 1));
            mSiteSwitchDialog.setTip("请选择首页数据源");
        }
        mSiteSwitchDialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
            @Override
            public void click(SourceBean value, int pos) {
                dismissSiteSwitchDialog();
                previousHomeSource = ApiConfig.get().getHomeSourceBean();
                ApiConfig.get().setSourceBean(value);
                refreshHome(false);
            }
            @Override
            public String getDisplay(SourceBean val) {
                return val.getName();
            }
        }, new DiffUtil.ItemCallback<SourceBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem == newItem;
            }
            @Override
            public boolean areContentsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem.getKey().equals(newItem.getKey());
            }
        }, sites, select);
        if (!mSiteSwitchDialog.isShowing())
            mSiteSwitchDialog.show();
    }

    private void refreshHome()
    {
        refreshHome(true);
    }

    private void refreshHomeSort() {
        refreshHomeRec = true;
        if (UserFragment.homeHotVodAdapter != null) {
            UserFragment.homeHotVodAdapter.setNewData(new ArrayList<Movie.Video>());
        }
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        if (home != null) {
            SourceViewModel.clearSortCache(home.getKey());
        }
        refreshHome(false);
    }

    private void updateHomeRec(AbsSortXml absXml) {
        if (!refreshHomeRec) return;
        refreshHomeRec = false;
        if (Hawk.get(HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC) != 1) return;
        if (absXml == null || absXml.videoList == null || UserFragment.homeHotVodAdapter == null) return;
        UserFragment.homeHotVodAdapter.setNewData(absXml.videoList);
    }

    private void refreshHome(final boolean restart)
    {
        if (Thread.currentThread() != android.os.Looper.getMainLooper().getThread()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    refreshHome(restart);
                }
            });
            return;
        }
        if (isActivityUnavailable()) {
            return;
        }
        dismissHomeDialogs();
        if (!restart) {
            loadHomeSort(true);
            return;
        }
        Intent intent = new Intent(mContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Bundle bundle = new Bundle();
        bundle.putBoolean("useCache", true);
        intent.putExtras(bundle);
        mActivity.startActivity(intent);
    }

    private boolean isActivityUnavailable() {
        return mActivity == null || mActivity.isFinishing()
                || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && mActivity.isDestroyed());
    }

    private void dismissHomeDialogs() {
        dismissConfigErrorDialog();
        dismissSiteSwitchDialog();
    }

    private void dismissConfigErrorDialog() {
        if (mConfigErrorDialog != null) {
            if (mConfigErrorDialog.isShowing()) {
                mConfigErrorDialog.dismiss();
            }
            mConfigErrorDialog = null;
        }
    }

    private void dismissSiteSwitchDialog() {
        if (mSiteSwitchDialog != null) {
            if (mSiteSwitchDialog.isShowing()) {
                mSiteSwitchDialog.dismiss();
            }
            mSiteSwitchDialog = null;
        }
    }

    private void refreshEmpty()
    {
        skipNextUpdate=true;
        showSuccess();
        cancelHomeSortLoading();
        clearHomePages();
        sortAdapter.setNewData(DefaultConfig.adjustSort(ApiConfig.get().getHomeSourceBean().getKey(), new ArrayList<>(), true));
        initViewPager(null);
        tvName.clearAnimation();
    }

    private void cancelHomeSortLoading() {
        homeSortLoading = false;
        loadingSourceKey = null;
        tvName.clearAnimation();
        if (previousHomeSource != null) {
            ApiConfig.get().setSourceBean(previousHomeSource);
        }
        if (previousHomeName != null && !previousHomeName.isEmpty()) {
            tvName.setText(previousHomeName);
        }
        previousHomeSource = null;
        previousHomeName = null;
    }

    private void tvNameAnimation()
    {
        tvName.clearAnimation();
        AlphaAnimation blinkAnimation = new AlphaAnimation(0.0f, 1.0f);
        blinkAnimation.setDuration(500);
        blinkAnimation.setStartOffset(20);
        blinkAnimation.setRepeatMode(Animation.REVERSE);
        blinkAnimation.setRepeatCount(Animation.INFINITE);
        tvName.startAnimation(blinkAnimation);
    }
}
