package com.github.tvbox.osc.ui.fragment;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Epginfo;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LivePlayerManager;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.EpgUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.ImgUtil;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.live.TxtSubscribe;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import xyz.doikki.videoplayer.exo.ExoMediaSourceHelper;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * 手机版直播控制页：上方为按原始比例展示的直播播放器，下方为直播控制按钮。
 * 点击上方播放器进入 LivePlayActivity 全屏直播。
 */
public class LiveFragment extends BaseLazyFragment {

    private interface OnOptionSelected {
        void onSelected(int position);
    }

    private VideoView<xyz.doikki.videoplayer.player.AbstractPlayer> mVideoView;
    private View pbLiveLoading;
    private ImageView ivEpgChannelLogo;
    private TextView tvEpgChannelFallback;
    private TextView tvEpgChannelName;
    private TextView tvEpgCurrentTime;
    private TextView tvEpgCurrentName;
    private TextView tvEpgNextTime;
    private TextView tvEpgNextName;
    private TextView tvEpgSource;
    private TextView tvEpgResolution;
    private View vChannelDivider;
    private TvRecyclerView mGroupListView;
    private TvRecyclerView mChannelListView;
    private LiveChannelGroupAdapter liveChannelGroupAdapter;
    private LiveChannelItemAdapter liveChannelItemAdapter;
    private int selectedGroupIndex = -1;

    private LiveChannelItem currentChannelItem;
    private final LivePlayerManager livePlayerManager = new LivePlayerManager();
    private boolean liveConfigLoading = false;
    private boolean liveProxyLoading = false;
    private String pendingChannelName;
    private int pendingSourceIndex = -1;

    private static final String DEFAULT_EPG_ADDRESS = "http://epg.51zmt.top:8000/api/diyp/?ch={name}&date={date}";
    private static final Hashtable<String, ArrayList<Epginfo>> EPG_CACHE = new Hashtable<>();
    private static final long CHANNEL_SYNC_DELAY = 300L;
    // 频道列表条目的设计尺寸（竖屏 360 基准，单位 mm 即设计单位）
    private static final int CHANNEL_LIST_DESIGN_WIDTH = 360;
    private static final float ROW_HEIGHT_MM = 30f;
    private static final float ROW_TEXT_MM = 12f;
    private static final float ROW_PADDING_RIGHT_MM = 4f;
    private static final float ROW_MARGIN_H_MM = 3f;
    private static final float ROW_MARGIN_V_MM = 1f;
    private boolean channelItemSizeHooked = false;
    private int channelSyncRetryCount = 0;
    private final Runnable mDeferredChannelSync = new Runnable() {
        @Override
        public void run() {
            performDeferredChannelSync();
        }
    };
    private final Handler mHandler = new Handler();
    private int resolutionRetryCount = 0;
    private boolean resolutionPending = false;
    private final Runnable mUpdateResolutionRun = new Runnable() {
        @Override
        public void run() {
            updateResolutionInfo();
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live_control;
    }

    @Override
    protected void init() {
        setLoadSir(findViewById(R.id.live_content_root));
        mVideoView = findViewById(R.id.mVideoView);
        pbLiveLoading = findViewById(R.id.pbLiveLoading);
        ivEpgChannelLogo = findViewById(R.id.ivEpgChannelLogo);
        tvEpgChannelFallback = findViewById(R.id.tvEpgChannelFallback);
        tvEpgChannelName = findViewById(R.id.tvEpgChannelName);
        tvEpgCurrentTime = findViewById(R.id.tvEpgCurrentTime);
        tvEpgCurrentName = findViewById(R.id.tvEpgCurrentName);
        tvEpgNextTime = findViewById(R.id.tvEpgNextTime);
        tvEpgNextName = findViewById(R.id.tvEpgNextName);
        tvEpgSource = findViewById(R.id.tvEpgSource);
        tvEpgResolution = findViewById(R.id.tvEpgResolution);
        vChannelDivider = findViewById(R.id.vChannelDivider);
        mGroupListView = findViewById(R.id.mGroupListView);
        mChannelListView = findViewById(R.id.mChannelListView);
        initChannelListViews();

        livePlayerManager.init(mVideoView);
        mVideoView.setScreenScaleType(livePlayerManager.getLivePlayerScale());
        mVideoView.addOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int playState) {
                switch (playState) {
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        showPlayerLoading(true);
                        break;
                    case VideoView.STATE_PREPARED:
                    case VideoView.STATE_BUFFERED:
                    case VideoView.STATE_PLAYING:
                        showPlayerLoading(false);
                        scheduleResolutionUpdate();
                        break;
                    case VideoView.STATE_PAUSED:
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                    case VideoView.STATE_ERROR:
                    case VideoView.STATE_IDLE:
                        showPlayerLoading(false);
                        break;
                }
            }
        });

        findViewById(R.id.tvBackChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mActivity != null) mActivity.onBackPressed();
            }
        });
        // 点击上方播放器区域进入全屏直播
        findViewById(R.id.playerClickArea).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFullscreenLive();
            }
        });

        findViewById(R.id.tvLineSelectChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSourceDialog();
            }
        });
        findViewById(R.id.tvScaleChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showScaleDialog();
            }
        });
        findViewById(R.id.tvDecoderChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDecoderDialog();
            }
        });
        findViewById(R.id.tvTimeoutChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimeoutDialog();
            }
        });
        findViewById(R.id.tvFullscreenChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFullscreenLive();
            }
        });
        findViewById(R.id.tvMultiSourceChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMultiSourceDialog();
            }
        });
        findViewById(R.id.tvConfigChip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showConfigDialog();
            }
        });

        showPlayerLoading(true);
        loadLiveChannels();
    }

    @Override
    protected void onFragmentResume() {
        if (mVideoView == null || currentChannelItem == null) return;
        Hawk.put(HawkConfig.PLAYER_IS_LIVE, true);
        String hawkChannelName = Hawk.get(HawkConfig.LIVE_CHANNEL, "");
        boolean pendingChannelSwitch = !TextUtils.isEmpty(hawkChannelName)
                && !hawkChannelName.equals(currentChannelItem.getChannelName());
        if (pendingChannelSwitch) {
            // 全屏页换过台时先不播放旧频道，等待延迟同步后直接用新频道起播
            showPlayerLoading(true);
        } else {
            int playState = mVideoView.getCurrentPlayState();
            if (playState == VideoView.STATE_IDLE || playState == VideoView.STATE_ERROR) {
                playCurrentChannel();
            } else {
                mVideoView.start();
            }
        }
        // 从横屏全屏页返回的瞬间，显示尺寸可能仍是横屏值：
        // 此时重新膨胀的列表项会按横屏比例放大。延迟到窗口尺寸稳定后再同步并重建列表。
        scheduleDeferredChannelSync();
    }

    private void scheduleDeferredChannelSync() {
        View decorView = mActivity.getWindow().getDecorView();
        decorView.removeCallbacks(mDeferredChannelSync);
        channelSyncRetryCount = 0;
        decorView.postDelayed(mDeferredChannelSync, CHANNEL_SYNC_DELAY);
    }

    private void performDeferredChannelSync() {
        if (isActivityUnavailable()) return;
        View decorView = mActivity.getWindow().getDecorView();
        DisplayMetrics dm = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        int decorWidth = decorView.getWidth();
        // 本页锁定竖屏：必须等到 Display 已经回到竖屏、且窗口宽度与 Display 宽度一致，
        // 否则 refreshAutoSize() 会把横屏宽度当作基准，导致随后新建的列表条目被放大。
        boolean portrait = dm.widthPixels > 0 && dm.heightPixels > 0 && dm.widthPixels < dm.heightPixels;
        boolean windowMatched = decorWidth > 0 && dm.widthPixels > 0
                && Math.abs(dm.widthPixels - decorWidth) <= Math.max(8, dm.widthPixels / 10);
        if ((!portrait || !windowMatched) && channelSyncRetryCount++ < 20) {
            decorView.postDelayed(mDeferredChannelSync, 100);
            return;
        }
        channelSyncRetryCount = 0;
        if (mActivity instanceof BaseActivity) ((BaseActivity) mActivity).refreshAutoSize();
        boolean channelChanged = syncChannelFromHawk();
        if (channelChanged) {
            // 换台后返回：丢弃旧 ViewHolder，按当前竖屏密度重新创建列表项
            resetChannelAdapters();
        }
        refreshChannelLists();
    }

    private void resetChannelAdapters() {
        if (mGroupListView == null || mChannelListView == null) return;
        mGroupListView.setAdapter(null);
        mChannelListView.setAdapter(null);
        // 回收池中的旧条目是按错误密度膨胀的，必须一并丢弃，否则会被新适配器复用
        mGroupListView.getRecycledViewPool().clear();
        mChannelListView.getRecycledViewPool().clear();
        initChannelListViews();
    }

    @Override
    protected void onFragmentPause() {
        if (mVideoView == null) return;
        int playState = mVideoView.getCurrentPlayState();
        if (playState == VideoView.STATE_PLAYING) {
            mVideoView.pause();
        } else if (playState == VideoView.STATE_PREPARING
                || playState == VideoView.STATE_PREPARED
                || playState == VideoView.STATE_BUFFERING) {
            // 尚未真正进入播放态时 pause() 可能不生效，直接释放，
            // 防止离开页面后预准备完成、在后台继续出声
            mVideoView.release();
        }
    }

    /** 当前页面是否可见：异步加载回调据此决定是否起播 */
    private boolean isPageVisible() {
        return currentVisibleState && !isHidden() && getUserVisibleHint();
    }

    @Override
    public void onDestroy() {
        Hawk.put(HawkConfig.PLAYER_IS_LIVE, false);
        mHandler.removeCallbacksAndMessages(null);
        if (mActivity != null) {
            mActivity.getWindow().getDecorView().removeCallbacks(mDeferredChannelSync);
        }
        if (mVideoView != null) {
            mVideoView.release();
        }
        super.onDestroy();
    }

    private boolean isActivityUnavailable() {
        return mActivity == null || mActivity.isFinishing()
                || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && mActivity.isDestroyed());
    }

    private void openFullscreenLive() {
        if (mVideoView != null) {
            mVideoView.pause();
        }
        jumpActivity(LivePlayActivity.class);
    }

    private void showPlayerLoading(boolean show) {
        if (pbLiveLoading != null) {
            pbLiveLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void loadLiveChannels() {
        List<LiveChannelGroup> cachedGroups = ApiConfig.get().getChannelGroupList();
        if (ApiConfig.get().shouldReloadLiveConfig() || cachedGroups == null || cachedGroups.isEmpty()) {
            if (liveConfigLoading) return;
            liveConfigLoading = true;
            showLoading();
            ApiConfig.get().loadLiveConfig(true, new ApiConfig.LoadConfigCallback() {
                @Override
                public void success() {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            liveConfigLoading = false;
                            if (isActivityUnavailable()) return;
                            showSuccess();
                            setupChannelAndPlay();
                        }
                    });
                }

                @Override
                public void error(String msg) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            liveConfigLoading = false;
                            if (isActivityUnavailable()) return;
                            showEmpty();
                            Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void notice(String msg) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isActivityUnavailable()) {
                                Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            });
        } else {
            // 使用缓存的直播数据时也必须切回成功状态，否则 LoadSir 不会把页面内容挂回视图树，
            // 会出现页面空白但直播声音正常播放的现象
            showSuccess();
            setupChannelAndPlay();
        }
    }

    private void setupChannelAndPlay() {
        initSettingSelections();
        loadChannelsOrPlay();
    }

    private void loadChannelsOrPlay() {
        List<LiveChannelGroup> groups = ApiConfig.get().getChannelGroupList();
        if (groups == null || groups.isEmpty()) {
            showEmpty();
            return;
        }
        // 配置中的直播地址为本地代理时，需要先拉取代理返回的真实频道列表
        if (groups.size() == 1 && groups.get(0) != null && groups.get(0).getGroupName() != null
                && groups.get(0).getGroupName().startsWith("http://127.0.0.1")) {
            loadProxyLives(groups.get(0).getGroupName());
            return;
        }
        playLastOrDefaultChannel();
    }

    private void playLastOrDefaultChannel() {
        String channelName = pendingChannelName != null ? pendingChannelName : Hawk.get(HawkConfig.LIVE_CHANNEL, "");
        int sourceIndex = pendingSourceIndex;
        pendingChannelName = null;
        pendingSourceIndex = -1;
        boolean found = !TextUtils.isEmpty(channelName) && findChannelByName(channelName);
        if (!found) {
            found = selectDefaultChannel();
        }
        if (!found) {
            showEmpty();
            return;
        }
        if (sourceIndex >= 0 && currentChannelItem.getSourceNum() > 0) {
            currentChannelItem.setSourceIndex(Math.min(sourceIndex, currentChannelItem.getSourceNum() - 1));
        }
        playCurrentChannel();
        refreshChannelLists();
    }

    private void loadProxyLives(String proxyUrl) {
        if (liveProxyLoading) return;
        liveProxyLoading = true;
        showPlayerLoading(true);
        String url = proxyUrl;
        try {
            Uri parsedUrl = Uri.parse(proxyUrl);
            String ext = parsedUrl.getQueryParameter("ext");
            if (!TextUtils.isEmpty(ext)) {
                url = new String(Base64.decode(ext, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        if (!isValidLiveProxyUrl(url)) {
            liveProxyLoading = false;
            showEmpty();
            return;
        }
        final String liveUrl = url;
        if (liveUrl.contains(".py") || liveUrl.contains(".js")) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    Future<String> future = executor.submit(new Callable<String>() {
                        @Override
                        public String call() {
                            Spider spider = ApiConfig.get().getLiveCSP(liveUrl);
                            return spider.liveContent(liveUrl);
                        }
                    });
                    String content = null;
                    try {
                        content = future.get(ApiConfig.get().getLiveConnectTimeoutSeconds(), TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        future.cancel(true);
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    } finally {
                        executor.shutdown();
                    }
                    applyProxyLives(content);
                }
            }).start();
        } else {
            OkGo.<String>get(liveUrl).execute(new AbsCallback<String>() {
                @Override
                public String convertResponse(okhttp3.Response response) throws Throwable {
                    if (response.body() == null) return "";
                    return response.body().string();
                }

                @Override
                public void onSuccess(Response<String> response) {
                    applyProxyLives(response.body());
                }

                @Override
                public void onError(Response<String> response) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            liveProxyLoading = false;
                            if (!isActivityUnavailable()) {
                                showEmpty();
                                Toast.makeText(mActivity, "直播列表加载失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            });
        }
    }

    private void applyProxyLives(String content) {
        if (content == null || content.trim().isEmpty()) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    liveProxyLoading = false;
                    if (!isActivityUnavailable()) showEmpty();
                }
            });
            return;
        }
        try {
            JsonArray livesArray = TxtSubscribe.parseToJsonArray(content);
            ApiConfig.get().loadLives(livesArray);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        List<LiveChannelGroup> groups = ApiConfig.get().getChannelGroupList();
        if (groups == null || groups.isEmpty()) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    liveProxyLoading = false;
                    if (!isActivityUnavailable()) showEmpty();
                }
            });
            return;
        }
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                liveProxyLoading = false;
                if (isActivityUnavailable()) return;
                showSuccess();
                initSettingSelections();
                playLastOrDefaultChannel();
            }
        });
    }

    private boolean isValidLiveProxyUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String lowerUrl = url.trim().toLowerCase(Locale.US);
        return lowerUrl.startsWith("http://")
                || lowerUrl.startsWith("https://")
                || lowerUrl.startsWith("rtsp://")
                || lowerUrl.startsWith("rtmp://")
                || lowerUrl.startsWith("rtp://");
    }

    private boolean findChannelByName(String channelName) {
        List<LiveChannelGroup> groups = ApiConfig.get().getChannelGroupList();
        if (groups == null) return false;
        for (LiveChannelGroup group : groups) {
            if (group == null || !TextUtils.isEmpty(group.getGroupPassword())) continue;
            ArrayList<LiveChannelItem> channels = group.getLiveChannels();
            if (channels == null) continue;
            for (LiveChannelItem item : channels) {
                if (item != null && channelName.equals(item.getChannelName())) {
                    currentChannelItem = item;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean selectDefaultChannel() {
        List<LiveChannelGroup> groups = ApiConfig.get().getChannelGroupList();
        if (groups == null) return false;
        LiveChannelGroup fallbackGroup = null;
        for (LiveChannelGroup group : groups) {
            if (group == null) continue;
            ArrayList<LiveChannelItem> channels = group.getLiveChannels();
            if (channels == null || channels.isEmpty()) continue;
            if (fallbackGroup == null) {
                fallbackGroup = group;
            }
            if (!TextUtils.isEmpty(group.getGroupPassword())) continue;
            for (LiveChannelItem item : channels) {
                if (item != null && !TextUtils.isEmpty(item.getChannelName())
                        && item.getChannelName().contains("CCTV1")) {
                    currentChannelItem = item;
                    return true;
                }
            }
            currentChannelItem = channels.get(0);
            return true;
        }
        if (fallbackGroup != null && fallbackGroup.getLiveChannels() != null
                && !fallbackGroup.getLiveChannels().isEmpty()) {
            currentChannelItem = fallbackGroup.getLiveChannels().get(0);
            return true;
        }
        return false;
    }

    private void playCurrentChannel() {
        if (currentChannelItem == null || mVideoView == null) return;
        ArrayList<String> urls = currentChannelItem.getChannelUrls();
        if (urls == null || urls.isEmpty()) {
            showPlayerLoading(false);
            Toast.makeText(mActivity, "当前频道暂无可用线路", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentChannelItem.getSourceIndex() < 0 || currentChannelItem.getSourceIndex() >= urls.size()) {
            currentChannelItem.setSourceIndex(0);
        }
        // 记录当前频道，保证隐藏期间选定的频道在回到页面时不会被当成“全屏切换”而回退
        Hawk.put(HawkConfig.LIVE_CHANNEL, currentChannelItem.getChannelName());
        // 异步加载完成时页面可能已被切走：此时只选定频道，不起播；
        // 下次可见时由 onFragmentResume 负责起播
        if (!isPageVisible()) return;
        Hawk.put(HawkConfig.PLAYER_IS_LIVE, true);
        mVideoView.release();
        mVideoView.setUrl(currentChannelItem.getUrl(), liveChannelHeader());
        mVideoView.start();
        updateProgramInfo();
    }

    private void initChannelListViews() {
        mGroupListView.setHasFixedSize(true);
        mGroupListView.setLayoutManager(new V7LinearLayoutManager(mActivity, RecyclerView.VERTICAL, false));
        liveChannelGroupAdapter = new LiveChannelGroupAdapter();
        mGroupListView.setAdapter(liveChannelGroupAdapter);
        liveChannelGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                selectGroupAt(position);
            }
        });

        mChannelListView.setHasFixedSize(true);
        mChannelListView.setLayoutManager(new V7LinearLayoutManager(mActivity, RecyclerView.VERTICAL, false));
        liveChannelItemAdapter = new LiveChannelItemAdapter();
        mChannelListView.setAdapter(liveChannelItemAdapter);
        liveChannelItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                playChannelAt(position);
            }
        });
        if (!channelItemSizeHooked) {
            channelItemSizeHooked = true;
            installChannelItemSizeHook(mGroupListView);
            installChannelItemSizeHook(mChannelListView);
        }
    }

    /**
     * 从横屏全屏页返回时，AutoSize 的全局基准宽度可能还停留在横屏值，
     * 此时新膨胀的条目字号/行高会偏大。这里按“窗口宽度 / 360”直接换算像素，
     * 在条目挂载时校正一次，使列表尺寸不依赖 AutoSize 的刷新时机。
     */
    private void installChannelItemSizeHook(TvRecyclerView listView) {
        listView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {
                normalizeChannelItem(view);
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {
            }
        });
    }

    private void normalizeChannelItems() {
        normalizeChannelListItems(mGroupListView);
        normalizeChannelListItems(mChannelListView);
    }

    private void normalizeChannelListItems(TvRecyclerView listView) {
        if (listView == null) return;
        for (int i = 0; i < listView.getChildCount(); i++) {
            normalizeChannelItem(listView.getChildAt(i));
        }
    }

    private void normalizeChannelItem(View itemView) {
        if (itemView == null) return;
        int baseWidth = mActivity.getWindow().getDecorView().getWidth();
        if (baseWidth <= 0) return;
        float unit = (float) baseWidth / CHANNEL_LIST_DESIGN_WIDTH;
        if (itemView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            int marginH = Math.round(ROW_MARGIN_H_MM * unit);
            int marginV = Math.round(ROW_MARGIN_V_MM * unit);
            if (marginParams.leftMargin != marginH || marginParams.rightMargin != marginH
                    || marginParams.topMargin != marginV || marginParams.bottomMargin != marginV) {
                marginParams.setMargins(marginH, marginV, marginH, marginV);
                itemView.setLayoutParams(marginParams);
            }
        }
        normalizeChannelRowText(itemView.findViewById(R.id.tvChannelNum), unit, -1);
        normalizeChannelRowText(itemView.findViewById(R.id.tvChannelName), unit,
                Math.round(ROW_PADDING_RIGHT_MM * unit));
        normalizeChannelRowText(itemView.findViewById(R.id.tvChannelGroupName), unit, -1);
    }

    private void normalizeChannelRowText(TextView textView, float unit, int paddingRightPx) {
        if (textView == null) return;
        int rowHeight = Math.round(ROW_HEIGHT_MM * unit);
        int textSize = Math.round(ROW_TEXT_MM * unit);
        if (textView.getLayoutParams() != null && textView.getLayoutParams().height != rowHeight) {
            textView.getLayoutParams().height = rowHeight;
            textView.setLayoutParams(textView.getLayoutParams());
        }
        if (Math.round(textView.getTextSize()) != textSize) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        }
        if (paddingRightPx >= 0 && textView.getPaddingRight() != paddingRightPx) {
            textView.setPadding(textView.getPaddingLeft(), textView.getPaddingTop(),
                    paddingRightPx, textView.getPaddingBottom());
        }
    }

    private void selectGroupAt(int position) {
        List<LiveChannelGroup> groups = ApiConfig.get().getChannelGroupList();
        if (groups == null || position < 0 || position >= groups.size()) return;
        LiveChannelGroup group = groups.get(position);
        if (!TextUtils.isEmpty(group.getGroupPassword())) {
            Toast.makeText(mActivity, "该分组需要密码，请在直播全屏页切换", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<LiveChannelItem> channels = group.getLiveChannels();
        if (channels == null || channels.isEmpty()) {
            Toast.makeText(mActivity, "该分组暂无频道", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedGroupIndex = group.getGroupIndex();
        liveChannelGroupAdapter.setSelectedGroupIndex(selectedGroupIndex);
        liveChannelItemAdapter.setNewData(new ArrayList<>(channels));
        liveChannelItemAdapter.setSelectedChannelIndex(-1);
        mChannelListView.scrollToPosition(0);
        normalizeChannelItems();
    }

    private void playChannelAt(int position) {
        LiveChannelGroup group = findGroupByIndex(selectedGroupIndex);
        if (group == null) return;
        ArrayList<LiveChannelItem> channels = group.getLiveChannels();
        if (channels == null || position < 0 || position >= channels.size()) return;
        currentChannelItem = channels.get(position);
        playCurrentChannel();
        liveChannelItemAdapter.setSelectedChannelIndex(currentChannelItem.getChannelIndex());
    }

    private LiveChannelGroup findGroupByIndex(int groupIndex) {
        List<LiveChannelGroup> groups = ApiConfig.get().getChannelGroupList();
        if (groups == null) return null;
        for (LiveChannelGroup group : groups) {
            if (group != null && group.getGroupIndex() == groupIndex) return group;
        }
        return null;
    }

    private void refreshChannelLists() {
        List<LiveChannelGroup> groups = ApiConfig.get().getChannelGroupList();
        if (groups == null) return;
        liveChannelGroupAdapter.setNewData(new ArrayList<>(groups));
        boolean multiGroup = groups.size() > 1;
        mGroupListView.setVisibility(multiGroup ? View.VISIBLE : View.GONE);
        vChannelDivider.setVisibility(multiGroup ? View.VISIBLE : View.GONE);

        int groupPos = -1;
        ArrayList<LiveChannelItem> channels = null;
        if (currentChannelItem != null) {
            for (int i = 0; i < groups.size(); i++) {
                ArrayList<LiveChannelItem> list = groups.get(i).getLiveChannels();
                if (list != null && list.contains(currentChannelItem)) {
                    groupPos = i;
                    channels = list;
                    break;
                }
            }
        }
        if (channels == null) {
            for (int i = 0; i < groups.size(); i++) {
                LiveChannelGroup group = groups.get(i);
                if (!TextUtils.isEmpty(group.getGroupPassword())) continue;
                ArrayList<LiveChannelItem> list = group.getLiveChannels();
                if (list != null && !list.isEmpty()) {
                    groupPos = i;
                    channels = list;
                    break;
                }
            }
        }
        if (channels == null) {
            liveChannelItemAdapter.setNewData(new ArrayList<>());
            normalizeChannelItems();
            return;
        }
        selectedGroupIndex = groups.get(groupPos).getGroupIndex();
        liveChannelGroupAdapter.setSelectedGroupIndex(selectedGroupIndex);
        liveChannelItemAdapter.setNewData(new ArrayList<>(channels));
        int channelPos = currentChannelItem == null ? -1 : channels.indexOf(currentChannelItem);
        if (channelPos >= 0) {
            liveChannelItemAdapter.setSelectedChannelIndex(channels.get(channelPos).getChannelIndex());
            mChannelListView.scrollToPosition(channelPos);
            mGroupListView.scrollToPosition(groupPos);
        }
        normalizeChannelItems();
    }

    private void updateProgramInfo() {
        if (currentChannelItem == null) return;
        String channelName = currentChannelItem.getChannelName();
        tvEpgChannelName.setText(channelName);
        tvEpgSource.setText(currentChannelItem.getSourceNum() > 0
                ? "线路" + (currentChannelItem.getSourceIndex() + 1) + "/" + currentChannelItem.getSourceNum()
                : "1/1");
        updateChannelLogo();
        showDefaultPrograms();
        loadEpg(channelName);
        resetResolutionInfo();
    }

    private void updateChannelLogo() {
        String channelNameReal = normalizeEpgChannelName(getFirstPartBeforeSpace(currentChannelItem.getChannelName()));
        String[] epgInfo = EpgUtil.getEpgInfo(channelNameReal);
        String epgTagName = epgInfo != null && epgInfo.length > 1 && !TextUtils.isEmpty(epgInfo[1]) ? epgInfo[1] : channelNameReal;
        String logoUrl = currentChannelItem.getChannelLogo();
        if (TextUtils.isEmpty(logoUrl)) {
            logoUrl = epgInfo == null ? null : epgInfo[0];
        } else if (!"false".equals(logoUrl)) {
            logoUrl = logoUrl.replace("{name}", epgTagName);
        } else {
            logoUrl = null;
        }
        if (TextUtils.isEmpty(logoUrl)) {
            ivEpgChannelLogo.setVisibility(View.GONE);
            tvEpgChannelFallback.setVisibility(View.VISIBLE);
            tvEpgChannelFallback.setText(String.valueOf(currentChannelItem.getChannelNum()));
        } else {
            ivEpgChannelLogo.setVisibility(View.VISIBLE);
            tvEpgChannelFallback.setVisibility(View.GONE);
            ImgUtil.load(DefaultConfig.checkReplaceProxy(logoUrl), ivEpgChannelLogo, 1, 0, 0,
                    currentChannelItem.getChannelName(), ImageView.ScaleType.CENTER_INSIDE);
        }
    }

    private void showDefaultPrograms() {
        TimeZone timeZone = TimeZone.getTimeZone("GMT+8:00");
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        Date start = calendar.getTime();
        calendar.add(Calendar.MINUTE, 59);
        Date end = calendar.getTime();
        calendar.add(Calendar.MINUTE, 1);
        Date nextStart = calendar.getTime();
        calendar.add(Calendar.MINUTE, 59);
        Date nextEnd = calendar.getTime();
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        timeFormat.setTimeZone(timeZone);
        tvEpgCurrentTime.setText(timeFormat.format(start) + "-" + timeFormat.format(end));
        tvEpgCurrentName.setText("精彩节目");
        tvEpgNextTime.setText(timeFormat.format(nextStart) + "-" + timeFormat.format(nextEnd));
        tvEpgNextName.setText("暂无节目预告信息");
    }

    private void loadEpg(final String channelName) {
        final String channelNameReal = normalizeEpgChannelName(getFirstPartBeforeSpace(channelName));
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        timeFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        final String dateKey = timeFormat.format(new Date());
        final String cacheKey = channelName + "_" + dateKey;
        ArrayList<Epginfo> cached = EPG_CACHE.get(cacheKey);
        if (cached != null) {
            showEpgPrograms(channelName, cached);
            return;
        }
        String epgTagName = channelNameReal;
        String[] epgInfo = EpgUtil.getEpgInfo(channelNameReal);
        if (epgInfo != null && epgInfo.length > 1 && !TextUtils.isEmpty(epgInfo[1])) {
            epgTagName = epgInfo[1];
        }
        String address = Hawk.get(HawkConfig.EPG_URL, "");
        if (TextUtils.isEmpty(address) || address.trim().length() < 5) {
            address = DEFAULT_EPG_ADDRESS;
        }
        final String finalEpgTagName = epgTagName;
        final String url = buildEpgUrl(address.trim(), epgTagName, dateKey);
        okhttp3.OkHttpClient client = OkGoHelper.getDefaultClient();
        if (client == null) client = com.github.catvod.net.OkHttp.client();
        client.newCall(new okhttp3.Request.Builder().url(url).build()).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (response.code() != 200) {
                    response.close();
                    return;
                }
                final String body;
                try {
                    body = response.body() != null ? response.body().string() : "";
                } finally {
                    response.close();
                }
                final ArrayList<Epginfo> list = parseEpg(body, dateKey, finalEpgTagName);
                EPG_CACHE.put(cacheKey, list);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityUnavailable() || currentChannelItem == null) return;
                        if (!channelName.equals(currentChannelItem.getChannelName())) return;
                        showEpgPrograms(channelName, list);
                    }
                });
            }
        });
    }

    private ArrayList<Epginfo> parseEpg(String body, String dateKey, String epgTagName) {
        ArrayList<Epginfo> list = new ArrayList<>();
        if (TextUtils.isEmpty(body)) return list;
        Date date = parseEpgDate(dateKey);
        try {
            if (isXmlEpgResponse(body)) {
                list = parseXmlEpg(body, epgTagName, date);
            } else if (body.contains("epg_data") || body.trim().startsWith("{")) {
                list = parseJsonEpg(body, date);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private Date parseEpgDate(String dateKey) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
            return dateFormat.parse(dateKey);
        } catch (ParseException e) {
            return new Date();
        }
    }

    @SuppressLint("SetTextI18n")
    private void showEpgPrograms(String channelName, ArrayList<Epginfo> list) {
        if (currentChannelItem == null || !channelName.equals(currentChannelItem.getChannelName())) return;
        if (list == null || list.isEmpty()) return;
        Date now = new Date();
        int currentIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            Epginfo info = list.get(i);
            if (info == null || info.startdateTime == null || info.enddateTime == null) continue;
            if (!now.before(info.startdateTime) && now.before(info.enddateTime)) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex < 0) return;
        Epginfo current = list.get(currentIndex);
        tvEpgCurrentTime.setText(current.start + "-" + current.end);
        tvEpgCurrentName.setText(cleanEpgTitle(current.title));
        if (currentIndex + 1 < list.size()) {
            Epginfo next = list.get(currentIndex + 1);
            tvEpgNextTime.setText(next.start + "-" + next.end);
            tvEpgNextName.setText(cleanEpgTitle(next.title));
        } else {
            tvEpgNextTime.setText(current.end + "-23:59");
            tvEpgNextName.setText("暂无节目预告信息");
        }
    }

    private ArrayList<Epginfo> parseJsonEpg(String response, Date date) throws JSONException {
        ArrayList<Epginfo> epgList = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(response);
        String channelName = jsonObject.optString("channel_name", jsonObject.optString("channel", ""));
        if (isUnavailableEpgText(channelName)) {
            return epgList;
        }
        JSONArray epgArray = findJsonEpgArray(jsonObject);
        if (epgArray == null) {
            return epgList;
        }
        for (int i = 0; i < epgArray.length(); i++) {
            JSONObject item = epgArray.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String title = cleanEpgTitle(item.optString("title", item.optString("name", "")));
            if (TextUtils.isEmpty(title) || isUnavailableEpgText(title)) {
                continue;
            }
            String startText = item.optString("start", item.optString("start_time", item.optString("starttime", "")));
            String endText = item.optString("end", item.optString("end_time", item.optString("endtime", "")));
            Date startDate = parseJsonEpgDate(date, startText);
            Date endDate = parseJsonEpgDate(date, endText);
            if (startDate == null || endDate == null) {
                continue;
            }
            if (!endDate.after(startDate)) {
                endDate = new Date(endDate.getTime() + TimeUnit.DAYS.toMillis(1));
            }
            epgList.add(createXmlEpgInfo(date, title, startDate, endDate, epgList.size()));
        }
        return epgList;
    }

    private JSONArray findJsonEpgArray(JSONObject jsonObject) {
        JSONArray epgArray = jsonObject.optJSONArray("epg_data");
        if (epgArray != null) return epgArray;
        epgArray = jsonObject.optJSONArray("data");
        if (epgArray != null) return epgArray;
        epgArray = jsonObject.optJSONArray("list");
        if (epgArray != null) return epgArray;
        JSONObject dataObject = jsonObject.optJSONObject("data");
        if (dataObject != null) {
            epgArray = dataObject.optJSONArray("epg_data");
            if (epgArray != null) return epgArray;
            epgArray = dataObject.optJSONArray("list");
        }
        return epgArray;
    }

    private Date parseJsonEpgDate(Date date, String timeText) {
        if (timeText == null || timeText.trim().isEmpty()) {
            return null;
        }
        String trimText = timeText.trim();
        String[] fullPatterns = new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"};
        for (String pattern : fullPatterns) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat(pattern, Locale.getDefault());
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
                return dateFormat.parse(trimText);
            } catch (ParseException ignored) {
            }
        }
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        dayFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        String dayText = dayFormat.format(date);
        String[] timePatterns = new String[]{"HH:mm:ss", "HH:mm"};
        for (String pattern : timePatterns) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd " + pattern, Locale.getDefault());
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
                return dateFormat.parse(dayText + " " + trimText);
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    private String cleanEpgTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.replace(" --免费使用", "")
                .replace("--免费使用", "")
                .trim();
    }

    private boolean isUnavailableEpgText(String text) {
        return text != null && (text.contains("未提供") || text.contains("暂无"));
    }

    private ArrayList<Epginfo> parseXmlEpg(String xml, String channelName, Date date) {
        ArrayList<Epginfo> epgList = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringComments(true);
            factory.setCoalescing(true);
            try {
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Exception ignored) {
            }
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            document.getDocumentElement().normalize();

            String targetName = normalizeEpgChannelName(channelName);
            ArrayList<String> channelIds = new ArrayList<>();
            NodeList channelNodes = document.getElementsByTagName("channel");
            for (int i = 0; i < channelNodes.getLength(); i++) {
                Node channelNode = channelNodes.item(i);
                if (channelNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element channelElement = (Element) channelNode;
                String channelId = channelElement.getAttribute("id");
                if (targetName.equals(normalizeEpgChannelName(channelId))) {
                    channelIds.add(channelId);
                    continue;
                }
                NodeList displayNameNodes = channelElement.getElementsByTagName("display-name");
                for (int j = 0; j < displayNameNodes.getLength(); j++) {
                    String displayName = displayNameNodes.item(j).getTextContent();
                    if (targetName.equals(normalizeEpgChannelName(displayName))) {
                        channelIds.add(channelId);
                        break;
                    }
                }
            }

            Date dayStart = getDayStart(date);
            Date dayEnd = new Date(dayStart.getTime() + TimeUnit.DAYS.toMillis(1));
            NodeList programmeNodes = document.getElementsByTagName("programme");
            for (int i = 0; i < programmeNodes.getLength(); i++) {
                Node programmeNode = programmeNodes.item(i);
                if (programmeNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element programmeElement = (Element) programmeNode;
                String programmeChannel = programmeElement.getAttribute("channel");
                if (!channelIds.contains(programmeChannel) && !targetName.equals(normalizeEpgChannelName(programmeChannel))) {
                    continue;
                }

                Date startDate = parseXmlTvDate(programmeElement.getAttribute("start"));
                Date endDate = parseXmlTvDate(programmeElement.getAttribute("stop"));
                if (startDate == null || endDate == null || !endDate.after(startDate)) {
                    continue;
                }
                if (!startDate.before(dayEnd) || !endDate.after(dayStart)) {
                    continue;
                }

                String title = "";
                NodeList titleNodes = programmeElement.getElementsByTagName("title");
                if (titleNodes.getLength() > 0) {
                    title = titleNodes.item(0).getTextContent();
                }
                epgList.add(createXmlEpgInfo(date, title, startDate, endDate, epgList.size()));
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return epgList;
    }

    private Date getDayStart(Date date) throws ParseException {
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        dayFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        return dayFormat.parse(dayFormat.format(date));
    }

    private Date parseXmlTvDate(String dateText) {
        if (dateText == null || dateText.trim().isEmpty()) {
            return null;
        }
        String trimDate = dateText.trim();
        try {
            return new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault()).parse(trimDate);
        } catch (ParseException ignored) {
        }
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
            return dateFormat.parse(trimDate);
        } catch (ParseException ignored) {
        }
        return null;
    }

    private Epginfo createXmlEpgInfo(Date epgDate, String title, Date startDate, Date endDate, int index) {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        Epginfo epgInfo = new Epginfo(epgDate, title, epgDate, timeFormat.format(startDate), timeFormat.format(endDate), index);
        epgInfo.startdateTime = startDate;
        epgInfo.enddateTime = endDate;
        epgInfo.start = timeFormat.format(startDate);
        epgInfo.end = timeFormat.format(endDate);
        epgInfo.originStart = epgInfo.start;
        epgInfo.originEnd = epgInfo.end;
        epgInfo.datestart = Integer.parseInt(epgInfo.start.replace(":", ""));
        epgInfo.dateend = Integer.parseInt(epgInfo.end.replace(":", ""));
        return epgInfo;
    }

    private String normalizeEpgChannelName(String channelName) {
        if (channelName == null) {
            return "";
        }
        String trimName = channelName.trim();
        String compactName = trimName.replace("-", "").replace(" ", "");
        Matcher cctvMatcher = Pattern.compile("(?i)^(CCTV\\d+(?:\\+|K)?)(?:[\\u4e00-\\u9fa5].*|$)").matcher(compactName);
        if (cctvMatcher.matches()) {
            return cctvMatcher.group(1).toUpperCase(Locale.ROOT);
        }
        if (compactName.toUpperCase(Locale.ROOT).startsWith("CCTV")) {
            return compactName.toUpperCase(Locale.ROOT);
        }
        return trimName;
    }

    private String getFirstPartBeforeSpace(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        int spaceIndex = str.indexOf(' ');
        if (spaceIndex == -1) {
            return str;
        } else {
            return str.substring(0, spaceIndex);
        }
    }

    private boolean isXmlEpgAddress(String address) {
        if (address == null) {
            return false;
        }
        String lowerAddress = address.toLowerCase(Locale.ROOT);
        int queryIndex = lowerAddress.indexOf("?");
        if (queryIndex >= 0) {
            lowerAddress = lowerAddress.substring(0, queryIndex);
        }
        return lowerAddress.endsWith(".xml");
    }

    private boolean isXmlEpgResponse(String response) {
        if (response == null) {
            return false;
        }
        String trimResponse = response.trim();
        return trimResponse.startsWith("<?xml") || trimResponse.startsWith("<tv") || trimResponse.contains("<programme");
    }

    private String buildEpgUrl(String address, String epgTagName, String date) {
        if (address.contains("{name}") || address.contains("{date}")) {
            return address.replace("{name}", encodeEpgParam(epgTagName)).replace("{date}", date);
        } else if (isXmlEpgAddress(address)) {
            return address;
        } else {
            return address + (address.contains("?") ? "&" : "?") + "ch=" + encodeEpgParam(epgTagName) + "&date=" + date;
        }
    }

    private String encodeEpgParam(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private void resetResolutionInfo() {
        resolutionPending = true;
        resolutionRetryCount = 0;
        mHandler.removeCallbacks(mUpdateResolutionRun);
        if (tvEpgResolution != null) {
            tvEpgResolution.setVisibility(View.GONE);
        }
        mHandler.postDelayed(mUpdateResolutionRun, 300);
    }

    private void scheduleResolutionUpdate() {
        if (!resolutionPending) return;
        mHandler.removeCallbacks(mUpdateResolutionRun);
        mHandler.post(mUpdateResolutionRun);
    }

    private void updateResolutionInfo() {
        if (mVideoView == null || tvEpgResolution == null) return;
        if (!resolutionPending) return;
        int[] size = mVideoView.getVideoSize();
        if (size != null && size.length >= 2 && size[0] > 0 && size[1] > 0) {
            resolutionPending = false;
            tvEpgResolution.setText(size[0] + " x " + size[1]);
            tvEpgResolution.setVisibility(View.VISIBLE);
        } else if (resolutionRetryCount++ < 10) {
            mHandler.postDelayed(mUpdateResolutionRun, 300);
        } else {
            resolutionPending = false;
        }
    }

    private boolean syncChannelFromHawk() {
        String channelName = Hawk.get(HawkConfig.LIVE_CHANNEL, "");
        if (TextUtils.isEmpty(channelName)) return false;
        if (currentChannelItem != null && channelName.equals(currentChannelItem.getChannelName())) return false;
        if (findChannelByName(channelName)) {
            playCurrentChannel();
            return true;
        }
        return false;
    }

    private HashMap<String, String> liveChannelHeader() {
        if (currentChannelItem == null) return liveWebHeader();
        HashMap<String, String> header = new HashMap<>();
        HashMap<String, String> liveHeader = liveWebHeader();
        if (liveHeader != null) header.putAll(liveHeader);
        if (currentChannelItem.getHeaders() != null) header.putAll(currentChannelItem.getHeaders());
        if (!currentChannelItem.getChannelFormat().isEmpty()) {
            header.put(ExoMediaSourceHelper.HEADER_FORMAT, currentChannelItem.getChannelFormat());
        }
        if (header.isEmpty()) return null;
        return header;
    }

    private HashMap<String, String> liveWebHeader() {
        return Hawk.get(HawkConfig.LIVE_WEB_HEADER);
    }

    private void initSettingSelections() {
        List<LiveSettingGroup> groups = ApiConfig.get().getLiveSettingGroupList();
        if (groups == null || groups.size() < 7) return;
        refreshGroupSelection(1, livePlayerManager.getLivePlayerScale());
        refreshGroupSelection(2, livePlayerManager.getLivePlayerType());
        refreshGroupSelection(3, Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1));
        refreshGroupSelection(5, ApiConfig.getLiveGroupIndex());
        refreshGroupSelection(6, getCurrentLiveApiHistoryIndex());
    }

    private LiveSettingGroup findSettingGroup(int groupIndex) {
        List<LiveSettingGroup> groups = ApiConfig.get().getLiveSettingGroupList();
        if (groups == null) return null;
        for (LiveSettingGroup group : groups) {
            if (group != null && group.getGroupIndex() == groupIndex) return group;
        }
        return null;
    }

    private void refreshGroupSelection(int groupIndex, int itemIndex) {
        LiveSettingGroup group = findSettingGroup(groupIndex);
        if (group == null || group.getLiveSettingItems() == null) return;
        for (LiveSettingItem item : group.getLiveSettingItems()) {
            item.setItemSelected(item.getItemIndex() == itemIndex);
        }
    }

    private int getCurrentLiveApiHistoryIndex() {
        ArrayList<String> history = Hawk.get(HawkConfig.LIVE_API_HISTORY, new ArrayList<String>());
        if (history == null || history.isEmpty()) return 0;
        String current = Hawk.get(HawkConfig.LIVE_API_URL, "");
        int index = history.indexOf(current);
        return index >= 0 ? index : 0;
    }

    private void showSelectDialog(String title, ArrayList<String> items, int select, final OnOptionSelected callback) {
        if (items == null || items.isEmpty()) {
            Toast.makeText(mActivity, "暂无可选项", Toast.LENGTH_SHORT).show();
            return;
        }
        if (select < 0 || select >= items.size()) {
            select = 0;
        }
        final SelectDialog<String> dialog = new SelectDialog<>(mActivity);
        dialog.setTip(title);
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                dialog.cancel();
                callback.onSelected(pos);
            }

            @Override
            public String getDisplay(String val) {
                return val;
            }
        }, SelectDialogAdapter.stringDiff, items, select);
        dialog.show();
    }

    private ArrayList<String> settingItemNames(LiveSettingGroup group) {
        ArrayList<String> items = new ArrayList<>();
        if (group == null || group.getLiveSettingItems() == null) return items;
        for (LiveSettingItem item : group.getLiveSettingItems()) {
            items.add(item.getItemName());
        }
        return items;
    }

    private int indexOfItemIndex(LiveSettingGroup group, int itemIndex) {
        if (group == null || group.getLiveSettingItems() == null) return 0;
        List<LiveSettingItem> items = group.getLiveSettingItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getItemIndex() == itemIndex) return i;
        }
        return 0;
    }

    private int getItemIndexAt(LiveSettingGroup group, int position) {
        if (group == null || group.getLiveSettingItems() == null) return position;
        List<LiveSettingItem> items = group.getLiveSettingItems();
        if (position >= 0 && position < items.size()) {
            return items.get(position).getItemIndex();
        }
        return position;
    }

    private void showSourceDialog() {
        if (currentChannelItem == null) {
            Toast.makeText(mActivity, "频道加载中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> names = currentChannelItem.getChannelSourceNames();
        ArrayList<String> urls = currentChannelItem.getChannelUrls();
        int count = urls != null ? urls.size() : (names == null ? 0 : names.size());
        if (count <= 0) {
            Toast.makeText(mActivity, "当前频道暂无线路", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (names != null && i < names.size()) {
                items.add(names.get(i));
            } else {
                items.add("源" + (i + 1));
            }
        }
        showSelectDialog("线路选择", items, currentChannelItem.getSourceIndex(), new OnOptionSelected() {
            @Override
            public void onSelected(int position) {
                currentChannelItem.setSourceIndex(position);
                playCurrentChannel();
                refreshGroupSelection(0, position);
            }
        });
    }

    private void showScaleDialog() {
        final LiveSettingGroup group = findSettingGroup(1);
        if (group == null) return;
        showSelectDialog("画面比例", settingItemNames(group),
                indexOfItemIndex(group, livePlayerManager.getLivePlayerScale()), new OnOptionSelected() {
                    @Override
                    public void onSelected(int position) {
                        int itemIndex = getItemIndexAt(group, position);
                        livePlayerManager.changeLivePlayerScale(mVideoView, itemIndex);
                        refreshGroupSelection(1, itemIndex);
                    }
                });
    }

    private void showDecoderDialog() {
        final LiveSettingGroup group = findSettingGroup(2);
        if (group == null) return;
        showSelectDialog("播放解码", settingItemNames(group),
                indexOfItemIndex(group, livePlayerManager.getLivePlayerType()), new OnOptionSelected() {
                    @Override
                    public void onSelected(int position) {
                        int itemIndex = getItemIndexAt(group, position);
                        mVideoView.release();
                        livePlayerManager.changeLivePlayerType(mVideoView, itemIndex);
                        if (currentChannelItem != null) {
                            mVideoView.setUrl(currentChannelItem.getUrl(), liveChannelHeader());
                            mVideoView.start();
                        }
                        refreshGroupSelection(2, itemIndex);
                    }
                });
    }

    private void showTimeoutDialog() {
        final LiveSettingGroup group = findSettingGroup(3);
        if (group == null) return;
        showSelectDialog("超时换源", settingItemNames(group),
                indexOfItemIndex(group, Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1)), new OnOptionSelected() {
                    @Override
                    public void onSelected(int position) {
                        int itemIndex = getItemIndexAt(group, position);
                        Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, itemIndex);
                        refreshGroupSelection(3, itemIndex);
                    }
                });
    }

    private void showMultiSourceDialog() {
        final LiveSettingGroup group = findSettingGroup(5);
        if (group == null) return;
        showSelectDialog("多源切换", settingItemNames(group),
                indexOfItemIndex(group, ApiConfig.getLiveGroupIndex()), new OnOptionSelected() {
                    @Override
                    public void onSelected(int position) {
                        switchLiveApiGroup(getItemIndexAt(group, position));
                    }
                });
    }

    private void showConfigDialog() {
        final LiveSettingGroup group = findSettingGroup(6);
        if (group == null) return;
        showSelectDialog("配置切换", settingItemNames(group),
                indexOfItemIndex(group, getCurrentLiveApiHistoryIndex()), new OnOptionSelected() {
                    @Override
                    public void onSelected(int position) {
                        switchLiveConfig(getItemIndexAt(group, position));
                    }
                });
    }

    private void switchLiveApiGroup(int itemIndex) {
        if (itemIndex == ApiConfig.getLiveGroupIndex()) return;
        JsonArray liveGroups = Hawk.get(HawkConfig.LIVE_GROUP_LIST, new JsonArray());
        if (liveGroups == null || itemIndex < 0 || itemIndex >= liveGroups.size()) return;
        String channelName = currentChannelItem == null ? "" : currentChannelItem.getChannelName();
        int sourceIndex = currentChannelItem == null ? -1 : currentChannelItem.getSourceIndex();
        JsonObject groupObject = liveGroups.get(itemIndex).getAsJsonObject();
        ApiConfig.setLiveGroupIndex(itemIndex);
        refreshGroupSelection(5, itemIndex);
        ApiConfig.get().loadLiveApi(groupObject);
        if (ApiConfig.get().getChannelGroupList().isEmpty()) {
            currentChannelItem = null;
            if (mVideoView != null) mVideoView.release();
            showEmpty();
            return;
        }
        restoreChannelAndPlay(channelName, sourceIndex);
    }

    private void switchLiveConfig(int position) {
        ArrayList<String> history = Hawk.get(HawkConfig.LIVE_API_HISTORY, new ArrayList<String>());
        if (history == null || position < 0 || position >= history.size()) return;
        final String value = history.get(position);
        String oldValue = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (value.equals(oldValue)) return;
        final String channelName = currentChannelItem == null ? "" : currentChannelItem.getChannelName();
        final int sourceIndex = currentChannelItem == null ? -1 : currentChannelItem.getSourceIndex();
        Hawk.put(HawkConfig.LIVE_API_URL, value);
        HistoryHelper.setLiveApiHistory(value);
        ApiConfig.get().refreshLiveApiHistoryItems();
        refreshGroupSelection(6, position);
        showLoading();
        ApiConfig.get().loadLiveConfig(false, new ApiConfig.LoadConfigCallback() {
            @Override
            public void success() {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityUnavailable()) return;
                        showSuccess();
                        restoreChannelAndPlay(channelName, sourceIndex);
                    }
                });
            }

            @Override
            public void error(String msg) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityUnavailable()) return;
                        showSuccess();
                        Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void notice(String msg) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isActivityUnavailable()) {
                            Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private void restoreChannelAndPlay(String channelName, int sourceIndex) {
        initSettingSelections();
        pendingChannelName = channelName;
        pendingSourceIndex = sourceIndex;
        showPlayerLoading(true);
        loadChannelsOrPlay();
    }
}
