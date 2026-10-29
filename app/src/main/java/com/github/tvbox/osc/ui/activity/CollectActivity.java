package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.adapter.CollectAdapter;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 手机版：收藏页，支持长按进入多选（全选 / 删除）
 */
public class CollectActivity extends BaseActivity {
    private View selectBar;
    private TextView tvSelectCount;
    private TextView tvSelectAll;
    private TextView tvDeleteSelected;
    private TvRecyclerView mGridView;
    public static CollectAdapter collectAdapter;
    private TipDialog confirmDialog;
    /** 长按进入多选态后，短暂忽略紧随其后的点击事件 */
    private long ignoreClickUntil = 0;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_collect;
    }

    @Override
    protected void init() {
        initView();
        initData();
    }

    private void initView() {
        EventBus.getDefault().register(this);
        selectBar = findViewById(R.id.selectBar);
        tvSelectCount = findViewById(R.id.tvSelectCount);
        tvSelectAll = findViewById(R.id.tvSelectAll);
        tvDeleteSelected = findViewById(R.id.tvDeleteSelected);
        mGridView = findViewById(R.id.mGridView);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(new V7GridLayoutManager(this.mContext, 3)); // 手机版：固定 3 列
        collectAdapter = new CollectAdapter();
        mGridView.setAdapter(collectAdapter);

        tvSelectAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collectAdapter.selectAll(!collectAdapter.isAllSelected());
                collectAdapter.notifyDataSetChanged();
                syncSelectBar();
            }
        });
        tvDeleteSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteSelected();
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
        collectAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (position == -1) return;
                // 长按刚进入多选态时，忽略紧随其后的这一下点击
                if (System.currentTimeMillis() < ignoreClickUntil) return;
                VodCollect vodInfo = collectAdapter.getData().get(position);
                if (vodInfo == null) return;
                if (collectAdapter.isSelectMode()) {
                    collectAdapter.toggleSelect(vodInfo);
                    collectAdapter.notifyItemChanged(position);
                    syncSelectBar();
                    return;
                }
                FastClickCheckUtil.check(view);
                if (ApiConfig.get().getSource(vodInfo.sourceKey) != null) {
                    Bundle bundle = new Bundle();
                    bundle.putString("id", vodInfo.vodId);
                    bundle.putString("sourceKey", vodInfo.sourceKey);
                    bundle.putString("title", vodInfo.name);
                    bundle.putString("picture", vodInfo.pic);
                    bundle.putBoolean("collect", true);
                    jumpActivity(DetailActivity.class, bundle);
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putString("title", vodInfo.name);
                    MainActivity.openTab(mContext, MainActivity.TAB_SEARCH, bundle);
                }
            }
        });
        collectAdapter.setOnItemLongClickListener(new BaseQuickAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(BaseQuickAdapter adapter, View view, int position) {
                if (position == -1) return false;
                VodCollect vodInfo = collectAdapter.getData().get(position);
                if (vodInfo == null) return false;
                if (!collectAdapter.isSelectMode()) {
                    collectAdapter.enterSelectMode(vodInfo);
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
                if (selectBar != null && (collectAdapter == null || !collectAdapter.isSelectMode())) {
                    selectBar.setVisibility(View.GONE);
                }
            }
        }).start();
    }

    /** 退出多选态并收起顶部操作条 */
    private void exitSelect() {
        if (collectAdapter != null) {
            collectAdapter.exitSelectMode();
        }
        hideSelectBar();
    }

    /** 刷新“已选 N 项”与全选文案；选中数为 0 时自动退出多选态 */
    private void syncSelectBar() {
        if (collectAdapter == null) return;
        int count = collectAdapter.getSelectedCount();
        if (count == 0) {
            exitSelect();
            return;
        }
        tvSelectCount.setText("已选 " + count + " 项");
        tvSelectAll.setText(collectAdapter.isAllSelected() ? "取消全选" : "全选");
    }

    private void deleteSelected() {
        final List<VodCollect> selected = collectAdapter.getSelectedItems();
        final int count = selected.size();
        if (count == 0) {
            exitSelect();
            return;
        }
        if (count == 1) {
            deleteItems(selected);
            return;
        }
        confirmDialog = new TipDialog(mContext, "确定删除所选 " + count + " 条收藏 ?", "删除", "取消", new TipDialog.OnListener() {
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

    private void deleteItems(List<VodCollect> items) {
        for (VodCollect item : items) {
            RoomDataManger.deleteVodCollect(item.getId());
        }
        int removed = collectAdapter.removeSelectedItems();
        collectAdapter.notifyDataSetChanged();
        exitSelect();
        Toast.makeText(mContext, "已删除 " + removed + " 条收藏", Toast.LENGTH_SHORT).show();
    }

    private void initData() {
        List<VodCollect> allVodRecord = RoomDataManger.getAllVodCollect();
        List<VodCollect> vodInfoList = new ArrayList<>();
        for (VodCollect vodInfo : allVodRecord) {
            vodInfoList.add(vodInfo);
        }
        collectAdapter.setNewData(vodInfoList);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_HISTORY_REFRESH) {
            // 列表刷新后选中项可能已不存在，先退出多选态
            if (collectAdapter != null && collectAdapter.isSelectMode()) {
                exitSelect();
            }
            initData();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 离开本页（进入详情 / 退到后台 / 关闭页面）时清除多选态并收起顶部操作条
        exitSelect();
    }

    @Override
    protected void onDestroy() {
        dismissConfirmDialog();
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (collectAdapter != null && collectAdapter.isSelectMode()) {
            exitSelect();
            return;
        }
        super.onBackPressed();
    }
}
