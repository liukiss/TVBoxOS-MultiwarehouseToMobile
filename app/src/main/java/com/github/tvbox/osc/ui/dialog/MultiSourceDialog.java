package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MoreSourceBean;
import com.github.tvbox.osc.ui.adapter.MoreSourceAdapter;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MoreSourceHelper;
import com.github.tvbox.osc.util.OkGoHelper;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 多仓管理弹窗（移植自影视仓 com.github.tvbox.osc.ui.dialog.z0）
 *
 * 工作逻辑（对齐影视仓 + TVBox 原生配置地址）：
 *  1. 展示已保存仓库列表（Hawk custom_store_house）
 *  2. 添加仓库地址 → 请求 → b1 三分支解析：
 *     - storeHouse：合并去重 → 刷新列表 → 链式（api_url 为空时取第一个仓库）
 *     - urls      ：当前地址存为仓库 → 链式
 *     - sites     ：直接落地为当前线路（写 api_url + 历史 + 触发原生重载链）
 *  3. 链式取第一个仓库 → 取第一条线路 urls[0] → 落地 api_url
 *  4. 点击仓库项 → 弹"选择线路"框（LineSelectDialog）
 *  5. 落地统一走 MoreSourceHelper.applyLine（写 TVBox 原生键 api_url），
 *     并经 OnMultiSourceListener.onConfigChanged 触发设置页原生重载链
 */
public class MultiSourceDialog extends BaseDialog {

    /** 配置落地回调：由打开方（设置页）触发原生重载链 restartAppAfterConfigChanged */
    public interface OnMultiSourceListener {
        void onConfigChanged();
    }

    private final Context mContext;
    private final OnMultiSourceListener mListener;

    private RecyclerView mListView;
    private MoreSourceAdapter mAdapter;
    private EditText inputSourceName;
    private EditText inputSourceUrl;
    private View inputSubmit;
    private View delAll;
    private View playLoading;

    private final List<MoreSourceBean> storeHouse = new ArrayList<>();
    private final List<String> visitedUrls = new ArrayList<>(); // 链式防循环
    private MoreSourceBean currentAdd; // 当前正在添加的地址（不占用"选中仓库"）
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public MultiSourceDialog(@NonNull Context context, OnMultiSourceListener listener) {
        super(context);
        mContext = context;
        mListener = listener;
        setContentView(R.layout.dialog_more_source);
        setCanceledOnTouchOutside(true); // 点击弹窗外自动关闭
        initView();
        loadData();
    }

    @Override
    public void show() {
        prepareBottomSheet();
        super.show();
        applyBottomSheet(findViewById(R.id.dragHandle));
        if (getWindow() != null) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
    }

    private void initView() {
        mListView = findViewById(R.id.list);
        playLoading = findViewById(R.id.play_loading);
        inputSourceName = findViewById(R.id.input_sourceName);
        inputSourceUrl = findViewById(R.id.input_source_url);
        inputSubmit = findViewById(R.id.inputSubmit);
        delAll = findViewById(R.id.delAll);

        View btnClose = findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            });
        }

        // 存储权限：跳转系统应用权限页（与直播地址弹窗一致）
        View btnPerm = findViewById(R.id.btnPerm);
        if (btnPerm != null) {
            btnPerm.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(android.net.Uri.parse("package:" + mContext.getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intent);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            });
        }

        mAdapter = new MoreSourceAdapter(new MoreSourceAdapter.SelectDialogInterface() {
            @Override
            public void click(MoreSourceBean value) {
                // 点击仓库项：选中 → 关当前框 → 弹"选择线路"框（纯仓库线路模式，不混历史）
                if (value == null || value.getSourceUrl().isEmpty()) return;
                MoreSourceHelper.setSelected(value);
                dismiss();
                LineSelectDialog dialog = new LineSelectDialog(mContext, value, false, new LineSelectDialog.OnLineSelectedListener() {
                    @Override
                    public void onLineSelected() {
                        if (mListener != null) mListener.onConfigChanged();
                    }
                });
                dialog.show();
            }

            @Override
            public void del(MoreSourceBean value, ArrayList<MoreSourceBean> data) {
                storeHouse.remove(value);
                MoreSourceHelper.saveStoreHouse(new ArrayList<>(storeHouse));
                // v2.1.6：删除后若无自动命名仓库 → 计数器归零（下次从"仓库1"开始）
                MoreSourceHelper.resetAutoCounter(MoreSourceHelper.KEY_AUTO_NAME_STORE,
                        MoreSourceHelper.AUTO_PREFIX_STORE, storeHouse);
                mAdapter.setData(new ArrayList<>(storeHouse));
                // v2.1.2：删除的是当前选中仓库 → 重置当前线路相关键（保留手动单线路）
                MoreSourceBean selected = MoreSourceHelper.getSelected();
                if (selected != null && value.getSourceUrl() != null
                        && value.getSourceUrl().equals(selected.getSourceUrl())) {
                    MoreSourceHelper.resetCurrentLine();
                }
            }
        });
        mListView.setLayoutManager(new LinearLayoutManager(mContext));
        mListView.getRecycledViewPool().clear();
        mListView.setAdapter(mAdapter);

        inputSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addStoreHouse();
            }
        });
        delAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                storeHouse.clear();
                MoreSourceHelper.saveStoreHouse(new ArrayList<>(storeHouse));
                // v2.1.6：全部删除 → 仓库自动命名计数器归零
                MoreSourceHelper.resetAutoCounter(MoreSourceHelper.KEY_AUTO_NAME_STORE,
                        MoreSourceHelper.AUTO_PREFIX_STORE, storeHouse);
                mAdapter.setData(new ArrayList<>(storeHouse));
                // v2.1.2：全部删除 → 彻底清理（配置地址/直播地址按钮、线路切换、直播配置切换不再残留）
                MoreSourceHelper.resetAllConfig();
            }
        });
    }

    private void loadData() {
        // 清空已废弃的 TVBox 原生"配置历史"键（原功能已移除，避免旧残留持续占用存储）
        try {
            Hawk.put(HawkConfig.API_HISTORY, new ArrayList<String>());
        } catch (Throwable ignored) {
        }
        // v2.0.6 一次性清理：此前版本会把仓库自动线路误存入"已保存线路"，旧数据无法区分单线路/仓库线路，升级后清空一次
        try {
            if (!Hawk.get("migrated_v206_history", false)) {
                Hawk.put(MoreSourceHelper.KEY_API_HISTORY_LIST, new ArrayList<MoreSourceBean>());
                Hawk.put("migrated_v206_history", true);
            }
        } catch (Throwable ignored) {
        }
        storeHouse.clear();
        storeHouse.addAll(MoreSourceHelper.getStoreHouse());
        mAdapter.setData(new ArrayList<>(storeHouse));
    }

    /* ---------- 添加仓库 ---------- */
    private void addStoreHouse() {
        String url = inputSourceUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(mContext, "请输入订阅链接", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = inputSourceName.getText().toString().trim();
        MoreSourceBean bean = new MoreSourceBean();
        // v2.1.6：名称留空时保持空，由解析结果自动命名（urls→"仓库N"，sites→"线路N"）
        bean.setSourceName(name);
        bean.setSourceUrl(url);
        bean.setShowDelete(true);
        selectStoreHouse(bean);
    }

    private void selectStoreHouse(MoreSourceBean bean) {
        currentAdd = bean; // 记录当前添加项（不抢占"选中仓库"，单线路落地后选中仓库保持不变）
        visitedUrls.clear(); // 新一轮添加/选中：重置链式防循环
        showLoading();
        loadStoreHouse(bean.getSourceUrl(), false);
    }

    /* ---------- 网络请求（OkGoHelper OkHttpClient，后台线程） ---------- */
    private void loadStoreHouse(String url, boolean isChainRequest) {
        final String finalUrl = url;
        final boolean chain = isChainRequest;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result = "";
                String error = "";
                Response response = null;
                try {
                    OkHttpClient client = OkGoHelper.getDefaultClient();
                    if (client == null) client = com.github.catvod.net.OkHttp.client();
                    Request request = new Request.Builder().url(finalUrl).build();
                    response = client.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        error = "HTTP " + response.code();
                    } else if (response.body() == null) {
                        error = "empty body";
                    } else {
                        result = response.body().string();
                    }
                } catch (Throwable th) {
                    error = th.getMessage();
                    if (TextUtils.isEmpty(error)) error = th.toString();
                } finally {
                    if (response != null && response.body() != null) response.body().close();
                }
                final String res = result;
                final String err = error;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!TextUtils.isEmpty(err)) {
                            hideLoading();
                            Toast.makeText(mContext, "仓库加载失败:" + err, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (chain) {
                            handleChainResponse(res); // 链式请求 → c1 语义（urls[0] 落地）
                        } else {
                            parseStoreHouseResponse(res); // 添加请求 → b1 三分支
                        }
                    }
                });
            }
        }).start();
    }

    /* ---------- b1 三分支解析（对齐影视仓 b1.b） ---------- */
    private void parseStoreHouseResponse(String result) {
        try {
            JSONObject json = new JSONObject(result);
            if (!json.has("storeHouse")) {
                if (json.has("urls")) {
                    // urls 分支：当前添加地址作为仓库保存 → 链式（影视仓 z0.d + z0.a）
                    MoreSourceBean sel = currentAdd;
                    if (sel != null && !sel.getSourceUrl().isEmpty()) {
                        // v2.1.6：名称留空 → 自动命名"仓库N"（重复 URL 沿用旧名）
                        if (sel.getSourceName() == null || sel.getSourceName().trim().isEmpty()) {
                            sel.setSourceName(MoreSourceHelper.autoNameForStore(storeHouse, sel.getSourceUrl()));
                        }
                        boolean exists = false;
                        for (MoreSourceBean b : storeHouse) {
                            if (b.getSourceUrl().equals(sel.getSourceUrl())) { exists = true; break; }
                        }
                        if (!exists) {
                            storeHouse.add(sel);
                            MoreSourceHelper.saveStoreHouse(new ArrayList<>(storeHouse));
                            mAdapter.setData(new ArrayList<>(storeHouse));
                        }
                    }
                    chainLoad();
                    return;
                }
                if (!json.has("sites")) {
                    hideLoading();
                    Toast.makeText(mContext, "你的仓库格式不对", Toast.LENGTH_SHORT).show();
                    return;
                }
                // sites 分支：直接是单线路/配置地址 → 落地（对齐影视仓 b1 sites 分支）
                // 落地当前添加的线路，但不改变"选中仓库"（单线路不是仓库）
                // 手动添加的单线路 → 保存到"已保存线路"（带删除按钮，永久展示直到用户删除）
                MoreSourceBean sel = currentAdd;
                if (sel != null) {
                    // v2.1.6：名称留空 → 自动命名"线路N"（重复 URL 沿用旧名）
                    if (sel.getSourceName() == null || sel.getSourceName().trim().isEmpty()) {
                        sel.setSourceName(MoreSourceHelper.autoNameForLine(MoreSourceHelper.getLineHistory(), sel.getSourceUrl()));
                    }
                    sel.setShowDelete(true);
                    applyLine(sel, true);
                } else {
                    hideLoading();
                }
                return;
            }
            // storeHouse 分支：合并去重 → 刷新列表 → 链式
            mergeStoreHouse(json.getJSONArray("storeHouse"));
            MoreSourceHelper.saveStoreHouse(new ArrayList<>(storeHouse));
            mAdapter.setData(new ArrayList<>(storeHouse));
            chainLoad();
        } catch (Throwable th) {
            hideLoading();
            Toast.makeText(mContext, "接口无效，请更换接口" + th.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** 合并仓库（按 sourceUrl 去重，新增项 showDelete=true） */
    private void mergeStoreHouse(JSONArray arr) {
        LinkedHashMap<String, MoreSourceBean> map = new LinkedHashMap<>();
        for (MoreSourceBean b : storeHouse) {
            if (b.getSourceUrl() != null) map.put(b.getSourceUrl(), b);
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString("sourceName", "");
            String url = o.optString("sourceUrl", "");
            if (url == null || url.isEmpty()) continue;
            MoreSourceBean b = new MoreSourceBean();
            b.setSourceName(name == null ? "" : name);
            b.setSourceUrl(url);
            b.setShowDelete(true);
            map.put(url, b);
        }
        storeHouse.clear();
        storeHouse.addAll(map.values());
    }

    /* ---------- 链式 z0.a()：仅 api_url 为空时取第一个仓库（影视仓 kotlin.collections.q.g0=first） ---------- */
    private void chainLoad() {
        String curApi = Hawk.get(HawkConfig.API_URL, "");
        if (curApi != null && !curApi.isEmpty()) {
            hideLoading();
            return; // 已有线路 → 不自动链式（对齐影视仓触发条件）
        }
        if (storeHouse.isEmpty()) {
            hideLoading();
            return;
        }
        MoreSourceBean first = storeHouse.get(0); // first
        if (visitedUrls.contains(first.getSourceUrl())) {
            hideLoading();
            return; // 防循环：该仓库已请求过
        }
        MoreSourceHelper.setSelected(first);
        visitedUrls.add(first.getSourceUrl());
        loadStoreHouse(first.getSourceUrl(), true);
    }

    /** 链式请求响应（对齐影视仓 c1 回调）：urls → 落地第一条；storeHouse 嵌套 → 继续链式 */
    private void handleChainResponse(String result) {
        try {
            JSONObject json = new JSONObject(result);
            if (json.has("urls")) {
                handleUrlsResponse(json);
                return;
            }
            if (json.has("storeHouse")) {
                mergeStoreHouse(json.getJSONArray("storeHouse"));
                MoreSourceHelper.saveStoreHouse(new ArrayList<>(storeHouse));
                mAdapter.setData(new ArrayList<>(storeHouse));
                chainLoad();
                return;
            }
            hideLoading();
            Toast.makeText(mContext, "接口无效，请更换接口", Toast.LENGTH_SHORT).show();
        } catch (Throwable th) {
            hideLoading();
            Toast.makeText(mContext, "接口无效，请更换接口" + th.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /* ---------- 落地（影视仓 c1：urls[0] → api_url） ---------- */
    private void handleUrlsResponse(JSONObject json) {
        try {
            JSONArray urls = json.getJSONArray("urls");
            if (urls.length() == 0) {
                hideLoading();
                return;
            }
            JSONObject first = urls.getJSONObject(0);
            MoreSourceBean line = new MoreSourceBean();
            line.setSourceUrl(first.getString("url"));
            line.setSourceName(first.optString("name", ""));
            line.setShowDelete(false);
            // 链式自动落地的仓库线路：只作为当前线路，不保存到"已保存线路"（避免其他仓库线路残留）
            applyLine(line, false);
        } catch (Throwable th) {
            hideLoading();
            Toast.makeText(mContext, "接口无效，请更换接口" + th.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** 统一落地：写 api_url（TVBox 原生键）+ 触发原生重载链 */
    private void applyLine(MoreSourceBean line, boolean saveToList) {
        if (line == null || line.getSourceUrl() == null || line.getSourceUrl().isEmpty()) {
            hideLoading();
            return;
        }
        MoreSourceHelper.applyLine(line, saveToList);
        hideLoading();
        Toast.makeText(mContext, "配置已切换,即将重新加载!", Toast.LENGTH_SHORT).show();
        dismiss();
        if (mListener != null) mListener.onConfigChanged();
    }

    private void showLoading() {
        if (playLoading != null) playLoading.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        if (playLoading != null) playLoading.setVisibility(View.GONE);
    }

    /* 供 LineSelectDialog 复用：解析 urls 响应并落地（影视仓 c1 语义） */
    public void onUrlsResponse(String result) {
        try {
            JSONObject json = new JSONObject(result);
            if (json.has("urls")) {
                handleUrlsResponse(json);
            } else {
                hideLoading();
                Toast.makeText(mContext, "接口无效，请更换接口", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable th) {
            hideLoading();
            Toast.makeText(mContext, "接口无效，请更换接口" + th.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
