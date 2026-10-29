package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MoreSourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.MoreSourceAdapter;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.MoreSourceHelper;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 直播地址管理弹窗（v2.1.0 新增，参照影视仓"添加直播源"）
 *
 * 功能：
 *  - 输入直播源名字/地址 → 添加 → 保存到 live_source_list（可删）
 *  - 添加/选中即写 TVBox 原生键 LIVE_API_URL + LIVE_API_HISTORY → 直播播放页"配置切换"自动出现并自动加载
 *  - 扫码远程推送：二维码与"配置地址"弹窗同一二维码（web 页面共用，直播地址走 liveApi 通道）
 *  - 存储权限：跳转系统应用权限设置页
 */
public class LiveSourceDialog extends BaseDialog {

    private final Context mContext;
    private TvRecyclerView mListView;
    private MoreSourceAdapter mAdapter;
    private EditText inputLiveName;
    private EditText inputLiveUrl;
    private TextView jumpWeb;
    private ImageView qrCode;

    private final List<MoreSourceBean> liveList = new ArrayList<>();

    /** 扫码推送/直播地址变化 → 实时刷新列表（v2.1.1） */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_LIVE_API_URL_CHANGE) {
            loadData();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    public LiveSourceDialog(@NonNull Context context) {
        super(context);
        mContext = context;
        setContentView(R.layout.dialog_live_source);
        setCanceledOnTouchOutside(true); // 点击弹窗外自动关闭
        initView();
        loadData();
    }

    private void initView() {
        mListView = findViewById(R.id.list);
        inputLiveName = findViewById(R.id.input_live_name);
        inputLiveUrl = findViewById(R.id.input_live_url);
        jumpWeb = findViewById(R.id.jump_web);
        qrCode = findViewById(R.id.qrCode);

        mAdapter = new MoreSourceAdapter(new MoreSourceAdapter.SelectDialogInterface() {
            @Override
            public void click(MoreSourceBean value) {
                // 点击已保存直播源 → 设为当前直播地址（直播"配置切换"中同步出现）
                if (value == null || value.getSourceUrl() == null || value.getSourceUrl().isEmpty()) return;
                Hawk.put(HawkConfig.LIVE_API_URL, value.getSourceUrl());
                HistoryHelper.setLiveApiHistory(value.getSourceUrl());
                Toast.makeText(mContext, "直播源已切换,进入直播生效!", Toast.LENGTH_SHORT).show();
                dismiss();
            }

            @Override
            public void del(MoreSourceBean value, ArrayList<MoreSourceBean> data) {
                MoreSourceHelper.removeLiveSource(value);
                liveList.remove(value);
                // v2.1.6：删除后若无自动命名直播源 → 计数器归零（下次从"直播源1"开始）
                MoreSourceHelper.resetAutoCounter(MoreSourceHelper.KEY_AUTO_NAME_LIVE,
                        MoreSourceHelper.AUTO_PREFIX_LIVE, MoreSourceHelper.getLiveSourceList());
                mAdapter.setData(new ArrayList<>(liveList));
            }
        });
        mListView.setAdapter(mAdapter);
        // v2.1.4：弹窗打开后默认聚焦列表首项（机顶盒遥控器 D-pad 可用）
        mListView.post(new Runnable() {
            @Override
            public void run() {
                mListView.requestFocus();
            }
        });

        findViewById(R.id.inputSubmit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addLiveSource();
            }
        });

        // 全部删除（逐条同步删除 live_source_list + LIVE_API_HISTORY，并清空当前直播地址显示）
        View delAll = findViewById(R.id.delAll);
        if (delAll != null) {
            delAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<MoreSourceBean> copy = new ArrayList<>(liveList);
                    for (MoreSourceBean bean : copy) {
                        MoreSourceHelper.removeLiveSource(bean);
                    }
                    // v2.1.2：彻底清除当前直播地址（按钮不再显示最后一次加载信息；直播加载回退 API_URL）
                    Hawk.put(HawkConfig.LIVE_API_URL, "");
                    // v2.1.6：全部删除 → 直播源自动命名计数器归零
                    MoreSourceHelper.resetAutoCounter(MoreSourceHelper.KEY_AUTO_NAME_LIVE,
                            MoreSourceHelper.AUTO_PREFIX_LIVE, MoreSourceHelper.getLiveSourceList());
                    loadData();
                }
            });
        }

        // 存储权限：跳转系统应用权限页
        View btnPerm = findViewById(R.id.btnPerm);
        if (btnPerm != null) {
            btnPerm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + mContext.getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intent);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            });
        }

        // 关闭按钮
        View btnClose = findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            });
        }

        refreshQRCode();
    }

    private void loadData() {
        liveList.clear();
        liveList.addAll(MoreSourceHelper.getLiveSourceList());
        mAdapter.setData(new ArrayList<>(liveList));
    }

    private void refreshQRCode() {
        try {
            String address = ControlManager.get().getAddress(false);
            jumpWeb.setText("扫码远程推送（直播地址）\n" + address);
            Bitmap bitmap = QRCodeGen.generateBitmap(address + "api.html",
                    AutoSizeUtils.mm2px(mContext, 260), AutoSizeUtils.mm2px(mContext, 260));
            qrCode.setImageBitmap(bitmap);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    /* ---------- 添加直播源 ---------- */
    private void addLiveSource() {
        String url = inputLiveUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(mContext, "请输入直播源地址", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = inputLiveName.getText().toString().trim();
        // 写 live_source_list + LIVE_API_URL + LIVE_API_HISTORY（直播"配置切换"数据源）
        MoreSourceHelper.addLiveSource(name, url);
        Toast.makeText(mContext, "直播源已添加,进入直播生效!", Toast.LENGTH_SHORT).show();
        inputLiveName.setText("");
        inputLiveUrl.setText("");
        loadData();
    }
}
