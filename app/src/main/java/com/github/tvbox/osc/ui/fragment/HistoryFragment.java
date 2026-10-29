package com.github.tvbox.osc.ui.fragment;

import android.os.Bundle;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.FastSearchActivity;
import com.github.tvbox.osc.ui.activity.MainActivity;
import com.github.tvbox.osc.ui.adapter.HistoryAdapter;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 手机版：历史记录页（原 HistoryActivity 迁移为 Tab Fragment）
 */
public class HistoryFragment extends BaseLazyFragment {
    private View selectBar;
    private TextView tvSelectCount;
    private TextView tvSelectAll;
    private TextView tvDeleteSelected;
    private TextView tvCollectSelected;
    private TvRecyclerView mGridView;
    public static HistoryAdapter historyAdapter;
    private TipDialog confirmDialog;
    private boolean eventBusRegistered = false;
    /** 长按进入多选态后，短暂忽略紧随其后的点击事件 */
    private long ignoreClickUntil = 0;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_history;
    }

    @Override
    protected void init() {
        initView();
        initData();
    }

    private void initView() {
        EventBus.getDefault().register(this);
        eventBusRegistered = true;
        selectBar = findViewById(R.id.selectBar);
        tvSelectCount = findViewById(R.id.tvSelectCount);
        tvSelectAll = findViewById(R.id.tvSelectAll);
        tvDeleteSelected = findViewById(R.id.tvDeleteSelected);
        tvCollectSelected = findViewById(R.id.tvCollectSelected);
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, 3)); // 手机版：固定 3 列
        historyAdapter = new HistoryAdapter();
        mGridView.setAdapter(historyAdapter);

        tvSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                historyAdapter.selectAll(!historyAdapter.isAllSelected());
                historyAdapter.notifyDataSetChanged();
                syncSelectBar();
            }
        });
        tvDeleteSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteSelected();
            }
        });
        tvCollectSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collectSelected();
            }
        });

        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {

            }
        });
        historyAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (position == -1) return;
                // 长按刚进入多选态时，忽略紧随其后的这一下点击
                if (System.currentTimeMillis() < ignoreClickUntil) return;
                VodInfo vodInfo = historyAdapter.getData().get(position);
                if (vodInfo == null) return;
                if (historyAdapter.isSelectMode()) {
                    historyAdapter.toggleSelect(vodInfo);
                    historyAdapter.notifyItemChanged(position);
                    syncSelectBar();
                    return;
                }
                FastClickCheckUtil.check(view);
                Bundle bundle = new Bundle();
                bundle.putString("id", vodInfo.id);
                bundle.putString("sourceKey", vodInfo.sourceKey);
                SourceBean sourceBean = ApiConfig.get().getSource(vodInfo.sourceKey);
                if (sourceBean != null) {
                    bundle.putString("title", vodInfo.name);
                    bundle.putString("picture", vodInfo.pic);
                    jumpActivity(DetailActivity.class, bundle);
                } else {
                    bundle.putString("title", vodInfo.name);
                    if (Hawk.get(HawkConfig.FAST_SEARCH_MODE, true)) {
                        jumpActivity(FastSearchActivity.class, bundle);
                    } else {
                        MainActivity.openTab(mContext, MainActivity.TAB_SEARCH, bundle);
                    }
                }
            }
        });
        historyAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                if (position == -1) return false;
                VodInfo vodInfo = historyAdapter.getData().get(position);
                if (vodInfo == null) return false;
                if (!historyAdapter.isSelectMode()) {
                    historyAdapter.enterSelectMode(vodInfo);
                    ignoreClickUntil = System.currentTimeMillis() + 300L;
                    showSelectBar();
                    syncSelectBar();
                }
                return true;
            }
        });
    }

    private void showSelectBar() {
        if (selectBar == null) return;
        selectBar.animate().cancel();
        selectBar.setVisibility(View.VISIBLE);
        selectBar.setAlpha(0f);
        selectBar.setTranslationY(-AutoSizeUtils.mm2px(mContext, 12f));
        selectBar.animate().alpha(1f).translationY(0f).setDuration(180).start();
    }

    private void hideSelectBar() {
        if (selectBar == null || selectBar.getVisibility() != View.VISIBLE) return;
        selectBar.animate().cancel();
        selectBar.animate().alpha(0f).translationY(-AutoSizeUtils.mm2px(mContext, 12f)).setDuration(150).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (selectBar != null && (historyAdapter == null || !historyAdapter.isSelectMode())) {
                    selectBar.setVisibility(View.GONE);
                }
            }
        }).start();
    }

    /** 退出多选态并收起顶部操作条 */
    private void exitSelect() {
        if (historyAdapter != null) {
            historyAdapter.exitSelectMode();
        }
        hideSelectBar();
    }

    /** 刷新“已选 N 项”与全选文案；选中数为 0 时自动退出多选态 */
    private void syncSelectBar() {
        if (historyAdapter == null) return;
        int count = historyAdapter.getSelectedCount();
        if (count == 0) {
            exitSelect();
            return;
        }
        tvSelectCount.setText("已选 " + count + " 项");
        tvSelectAll.setText(historyAdapter.isAllSelected() ? "取消全选" : "全选");
    }

    private void deleteSelected() {
        final List<VodInfo> selected = historyAdapter.getSelectedVodInfos();
        final int count = selected.size();
        if (count == 0) {
            exitSelect();
            return;
        }
        if (count == 1) {
            deleteItems(selected);
            return;
        }
        confirmDialog = new TipDialog(mContext, "确定删除所选 " + count + " 条记录 ?", "删除", "取消", new TipDialog.OnListener() {
            @Override
            public void left() {
                dismissConfirmDialog();
                deleteItems(selected);
            }

            @Override
            public void right() {
                dismissConfirmDialog();
            }

            @Override
            public void cancel() {
                dismissConfirmDialog();
            }
        });
        confirmDialog.show();
    }

    private void dismissConfirmDialog() {
        if (confirmDialog != null && confirmDialog.isShowing()) {
            confirmDialog.dismiss();
        }
        confirmDialog = null;
    }

    private void deleteItems(List<VodInfo> items) {
        for (VodInfo item : items) {
            RoomDataManger.deleteVodRecord(item.sourceKey, item);
        }
        int removed = historyAdapter.removeSelectedItems();
        historyAdapter.notifyDataSetChanged();
        exitSelect();
        Toast.makeText(mContext, "已删除 " + removed + " 条记录", Toast.LENGTH_SHORT).show();
    }

    private void collectSelected() {
        List<VodInfo> selected = historyAdapter.getSelectedVodInfos();
        int count = selected.size();
        if (count == 0) {
            exitSelect();
            return;
        }
        for (VodInfo item : selected) {
            RoomDataManger.insertVodCollect(item.sourceKey, item);
        }
        exitSelect();
        Toast.makeText(mContext, "已收藏 " + count + " 项", Toast.LENGTH_SHORT).show();
    }

    /** 返回键：多选态下先退出多选态 */
    public boolean handleBack() {
        if (historyAdapter != null && historyAdapter.isSelectMode()) {
            exitSelect();
            return true;
        }
        return false;
    }

    private void initData() {
        List<VodInfo> allVodRecord = RoomDataManger.getAllVodRecord(100);
        List<VodInfo> vodInfoList = new ArrayList<>();
        for (VodInfo vodInfo : allVodRecord) {
            if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty()) vodInfo.note = "上次看到" + vodInfo.playNote;
            vodInfoList.add(vodInfo);
        }
        historyAdapter.setNewData(vodInfoList);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_HISTORY_REFRESH) {
            // 列表刷新后选中项可能已不存在，先退出多选态
            if (historyAdapter != null && historyAdapter.isSelectMode()) {
                exitSelect();
            }
            initData();
        }
    }

    @Override
    protected void onFragmentPause() {
        // 离开本页（切 Tab / 进入详情 / 退到后台）时清除多选态并收起顶部操作条
        exitSelect();
    }

    @Override
    public void onDestroy() {
        dismissConfirmDialog();
        if (eventBusRegistered) {
            EventBus.getDefault().unregister(this);
            eventBusRegistered = false;
        }
        super.onDestroy();
    }
}
