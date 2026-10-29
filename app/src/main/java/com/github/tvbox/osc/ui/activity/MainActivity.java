package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.fragment.HistoryFragment;
import com.github.tvbox.osc.ui.fragment.HomeFragment;
import com.github.tvbox.osc.ui.fragment.LiveFragment;
import com.github.tvbox.osc.ui.fragment.SearchFragment;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * 手机版主界面：单 Activity + Fragment 结构，四个 Tab 共享同一个底部导航。
 */
public class MainActivity extends BaseActivity {

    public static final int TAB_HOME = 0;
    public static final int TAB_LIVE = 1;
    public static final int TAB_HISTORY = 2;
    public static final int TAB_SEARCH = 3;

    public static final String EXTRA_TAB = "tab";
    public static final String EXTRA_ARGS = "args";

    private static final String STATE_TAB = "current_tab";
    private static final String TAG_HOME = "tab_home";
    private static final String TAG_LIVE = "tab_live";
    private static final String TAG_HISTORY = "tab_history";
    private static final String TAG_SEARCH = "tab_search";

    private static final int COLOR_SELECTED = 0xFF02F8E1;
    private static final int COLOR_NORMAL = 0xCCFFFFFF;

    private HomeFragment homeFragment;
    private LiveFragment liveFragment;
    private HistoryFragment historyFragment;
    private SearchFragment searchFragment;
    private View bottomBar;
    private int currentTab = TAB_HOME;
    private int savedTab = TAB_HOME;
    private long mExitTime = 0;

    /**
     * 统一入口：其他页面通过它打开底部导航中的任意一页。
     */
    public static void openTab(Context context, int tab, Bundle args) {
        if (context == null) return;
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_TAB, tab);
        if (args != null) {
            intent.putExtra(EXTRA_ARGS, args);
        }
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_main;
    }

    @Override
    protected boolean shouldRefreshAutoSize() {
        return true;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            savedTab = savedInstanceState.getInt(STATE_TAB, TAB_HOME);
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void init() {
        ControlManager.get().startServer();
        bottomBar = findViewById(R.id.mainBottomBar);
        bindBottomBar();

        Intent intent = getIntent();
        Bundle args = intent != null ? intent.getExtras() : null;
        int tab = savedTab;
        if (intent != null && intent.hasExtra(EXTRA_TAB)) {
            tab = intent.getIntExtra(EXTRA_TAB, TAB_HOME);
            if (intent.hasExtra(EXTRA_ARGS)) {
                args = intent.getBundleExtra(EXTRA_ARGS);
            }
        }
        switchTab(tab, args);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent == null) return;
        if (!intent.hasExtra(EXTRA_TAB)) {
            // 从桌面图标等入口再次进入时回到主页顶部
            switchTab(TAB_HOME, intent.getExtras());
            return;
        }
        Bundle args = intent.hasExtra(EXTRA_ARGS) ? intent.getBundleExtra(EXTRA_ARGS) : intent.getExtras();
        switchTab(intent.getIntExtra(EXTRA_TAB, TAB_HOME), args);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
    }

    private void bindBottomBar() {
        if (bottomBar == null) return;
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.tvHome) {
                    switchTab(TAB_HOME, null);
                } else if (id == R.id.tvLive) {
                    switchTab(TAB_LIVE, null);
                } else if (id == R.id.tvHistory) {
                    switchTab(TAB_HISTORY, null);
                } else if (id == R.id.tvSearch) {
                    switchTab(TAB_SEARCH, null);
                }
            }
        };
        bottomBar.findViewById(R.id.tvHome).setOnClickListener(listener);
        bottomBar.findViewById(R.id.tvLive).setOnClickListener(listener);
        bottomBar.findViewById(R.id.tvHistory).setOnClickListener(listener);
        bottomBar.findViewById(R.id.tvSearch).setOnClickListener(listener);
        updateBottomBar(currentTab);
    }

    private void switchTab(int tab, Bundle args) {
        if (tab == TAB_HOME) {
            switchToHome(args);
        } else if (tab == TAB_LIVE) {
            switchToLive();
        } else if (tab == TAB_HISTORY) {
            switchToHistory();
        } else if (tab == TAB_SEARCH) {
            switchToSearch(args);
        } else {
            return;
        }
        if (currentTab == TAB_LIVE && tab != TAB_LIVE) {
            // 离开直播页后复位直播标记，避免影响普通点播播放器参数
            Hawk.put(HawkConfig.PLAYER_IS_LIVE, false);
        }
        if (tab != TAB_SEARCH) {
            hideKeyboard();
        }
        currentTab = tab;
        updateBottomBar(tab);
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
        focus.clearFocus();
    }

    private void switchToLive() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (liveFragment == null) {
            liveFragment = (LiveFragment) getSupportFragmentManager().findFragmentByTag(TAG_LIVE);
        }
        if (liveFragment == null) {
            liveFragment = new LiveFragment();
            ft.add(R.id.tabContainer, liveFragment, TAG_LIVE);
        } else {
            ft.show(liveFragment);
        }
        hideOther(ft, liveFragment);
        ft.commitNowAllowingStateLoss();
    }

    private void switchToHome(Bundle args) {
        boolean useCache = args != null && args.getBoolean("useCache", false);
        boolean alreadyHome = currentTab == TAB_HOME && homeFragment != null && homeFragment.isAdded();
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (homeFragment == null) {
            homeFragment = (HomeFragment) getSupportFragmentManager().findFragmentByTag(TAG_HOME);
        }
        if (homeFragment != null && useCache) {
            // 设置页修改配置后带 useCache 返回：重建主页以重新加载配置
            ft.remove(homeFragment);
            homeFragment = null;
        }
        if (homeFragment == null) {
            homeFragment = new HomeFragment();
            Bundle bundle = new Bundle();
            bundle.putBoolean("useCache", useCache);
            homeFragment.setArguments(bundle);
            ft.add(R.id.tabContainer, homeFragment, TAG_HOME);
        } else {
            ft.show(homeFragment);
        }
        hideOther(ft, homeFragment);
        ft.commitNowAllowingStateLoss();
        if (alreadyHome) {
            homeFragment.backToHome();
        }
    }

    private void switchToHistory() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (historyFragment == null) {
            historyFragment = (HistoryFragment) getSupportFragmentManager().findFragmentByTag(TAG_HISTORY);
        }
        if (historyFragment == null) {
            historyFragment = new HistoryFragment();
            ft.add(R.id.tabContainer, historyFragment, TAG_HISTORY);
        } else {
            ft.show(historyFragment);
        }
        hideOther(ft, historyFragment);
        ft.commitNowAllowingStateLoss();
    }

    private void switchToSearch(Bundle args) {
        boolean hasTitle = args != null && args.containsKey("title");
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (searchFragment == null) {
            searchFragment = (SearchFragment) getSupportFragmentManager().findFragmentByTag(TAG_SEARCH);
        }
        if (searchFragment != null && hasTitle) {
            // 带关键词进入：重建搜索页，相当于原实现新开一个搜索 Activity
            ft.remove(searchFragment);
            searchFragment = null;
        }
        if (searchFragment == null) {
            searchFragment = new SearchFragment();
            searchFragment.setArguments(args != null ? args : new Bundle());
            ft.add(R.id.tabContainer, searchFragment, TAG_SEARCH);
        } else {
            ft.show(searchFragment);
        }
        hideOther(ft, searchFragment);
        ft.commitNowAllowingStateLoss();
    }

    private void hideOther(FragmentTransaction ft, Fragment target) {
        hideIfNot(ft, homeFragment, target);
        hideIfNot(ft, liveFragment, target);
        hideIfNot(ft, historyFragment, target);
        hideIfNot(ft, searchFragment, target);
    }

    private void hideIfNot(FragmentTransaction ft, Fragment fragment, Fragment target) {
        if (fragment != null && fragment != target && fragment.isAdded() && !fragment.isHidden()) {
            ft.hide(fragment);
        }
    }

    private void updateBottomBar(int tab) {
        if (bottomBar == null) return;
        int[] icons = {R.id.ivHome, R.id.ivLive, R.id.ivHistory, R.id.ivSearch};
        int[] labels = {R.id.tvHomeLabel, R.id.tvLiveLabel, R.id.tvHistoryLabel, R.id.tvSearchLabel};
        for (int i = 0; i < icons.length; i++) {
            ImageView icon = bottomBar.findViewById(icons[i]);
            TextView label = bottomBar.findViewById(labels[i]);
            boolean selected = i == tab;
            if (icon != null) {
                icon.setAlpha(selected ? 1.0f : 0.75f);
            }
            if (label != null) {
                label.setTextColor(selected ? COLOR_SELECTED : COLOR_NORMAL);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (currentTab == TAB_SEARCH && searchFragment != null && searchFragment.handleBack()) {
            return;
        }
        if (currentTab == TAB_HISTORY && historyFragment != null && historyFragment.handleBack()) {
            return;
        }
        if (currentTab != TAB_HOME) {
            switchTab(TAB_HOME, null);
            return;
        }
        if (homeFragment != null && homeFragment.handleBack()) {
            return;
        }
        doExit();
    }

    private void doExit() {
        // 如果两次返回间隔小于 2000 毫秒，则退出应用
        if (System.currentTimeMillis() - mExitTime < 2000) {
            AppManager.getInstance().finishAllActivity();
            ControlManager.get().stopServer();
            finish();
            Process.killProcess(Process.myPid());
            System.exit(0);
        } else {
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (currentTab == TAB_HOME && homeFragment != null && homeFragment.handleKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            ControlManager.get().stopServer();
        }
    }
}
