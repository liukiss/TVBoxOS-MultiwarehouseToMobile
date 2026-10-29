package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MoreSourceBean;
import com.github.tvbox.osc.ui.adapter.LineSelectAdapter;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MoreSourceHelper;
import com.github.tvbox.osc.util.OkGoHelper;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
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
 * 线路选择弹窗（移植自影视仓 com.github.tvbox.osc.ui.dialog.util.e）
 *
 * 两种模式：
 *  - showHistory=true  （"线路切换"入口）：显示已保存线路；若当前选中仓库非空，再请求该仓库 urls 合并
 *  - showHistory=false （点击多仓弹窗中某个仓库）：只显示该仓库 urls 解析出的线路（不混历史）
 *
 * 选中线路 → MoreSourceHelper.applyLine（写 TVBox 原生键 api_url + 历史双写）
 *   → dismiss → OnLineSelectedListener.onLineSelected（触发设置页原生重载链）
 */
public class LineSelectDialog extends BaseDialog {

    public interface OnLineSelectedListener {
        void onLineSelected();
    }

    private final Context mContext;
    private final MoreSourceBean selectedStore;
    private final boolean showHistory;
    private final OnLineSelectedListener mListener;

    private TvRecyclerView mListView;
    private LineSelectAdapter mAdapter;
    private final List<MoreSourceBean> lineList = new ArrayList<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public LineSelectDialog(@NonNull Context context, MoreSourceBean selectedStore, boolean showHistory, OnLineSelectedListener listener) {
        super(context);
        mContext = context;
        this.selectedStore = selectedStore;
        this.showHistory = showHistory;
        mListener = listener;
        setContentView(R.layout.dialog_api_history);
        setCanceledOnTouchOutside(true); // 点击弹窗外自动关闭
        initView();
    }

    private void initView() {
        TextView tvTitle = findViewById(R.id.title);
        tvTitle.setText("选择线路\n当前使用接口数据");
        mListView = findViewById(R.id.list);
        mAdapter = new LineSelectAdapter(new LineSelectAdapter.SelectDialogInterface() {
            @Override
            public void click(MoreSourceBean value) {
                if (value == null || value.getSourceUrl() == null || value.getSourceUrl().isEmpty()) return;
                // 落地但不重复保存：已保存单线路已在列表；仓库线路不进入保存列表（切换仓库后自动消失）
                MoreSourceHelper.applyLine(value, false);
                Toast.makeText(mContext, "配置已切换,即将重新加载!", Toast.LENGTH_SHORT).show();
                dismiss();
                if (mListener != null) mListener.onLineSelected();
            }

            @Override
            public void del(MoreSourceBean value, ArrayList<MoreSourceBean> data) {
                // v2.1.2：彻底删除（api_history_list + LIVE_API_HISTORY + 当前线路键）
                MoreSourceHelper.removeLineCompletely(value);
                lineList.remove(value);
                // v2.1.6：删除后若无自动命名线路 → 计数器归零（下次从"线路1"开始）
                MoreSourceHelper.resetAutoCounter(MoreSourceHelper.KEY_AUTO_NAME_LINE,
                        MoreSourceHelper.AUTO_PREFIX_LINE, MoreSourceHelper.getLineHistory());
                mAdapter.setData(new ArrayList<>(lineList));
                focusList();
            }
        });
        mListView.setAdapter(mAdapter);

        // 关闭按钮 / 点击弹窗外关闭
        View btnClose = findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            });
        }
        View root = findViewById(R.id.root);
        if (root != null) {
            root.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            });
        }

        // show 后再加载数据，确保列表 attach 后同步渲染
        mListView.post(new Runnable() {
            @Override
            public void run() {
                loadLines();
            }
        });
    }

    private void loadLines() {
        lineList.clear();
        String current = Hawk.get(HawkConfig.API_URL, "");
        mAdapter.setCurrentUrl(current);
        focusList();

        if (showHistory) {
            // 线路切换入口：已保存线路（不含 TVBox 原生配置历史）
            lineList.addAll(MoreSourceHelper.getLineHistory());
            // 兜底：至少展示当前正在使用的线路
            if (lineList.isEmpty() && current != null && !current.isEmpty()) {
                MoreSourceBean cur = new MoreSourceBean();
                cur.setSourceName(MoreSourceHelper.getCurrentLineBean() != null
                        ? MoreSourceHelper.getCurrentLineBean().getSourceName() : current);
                if (cur.getSourceName() == null || cur.getSourceName().isEmpty()) cur.setSourceName(current);
                cur.setSourceUrl(current);
                cur.setShowDelete(false);
                lineList.add(cur);
            }
        }

        // 无选中仓库 → 直接展示当前列表（纯仓库模式下为空）
        if (selectedStore == null || selectedStore.getSourceUrl() == null || selectedStore.getSourceUrl().isEmpty()) {
            mAdapter.setData(new ArrayList<>(lineList));
            focusList();
            return;
        }

        // 有选中仓库 → 请求该仓库配置解析 urls
        final String url = selectedStore.getSourceUrl();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result = "";
                String error = "";
                Response response = null;
                try {
                    OkHttpClient client = OkGoHelper.getDefaultClient();
                    if (client == null) client = com.github.catvod.net.OkHttp.client();
                    Request request = new Request.Builder().url(url).build();
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
                            Toast.makeText(mContext, "线路加载失败:" + err, Toast.LENGTH_SHORT).show();
                            mAdapter.setData(new ArrayList<>(lineList));
                            focusList();
                            return;
                        }
                        parseLines(res, url);
                    }
                });
            }
        }).start();
    }

    /** 解析响应：有 urls 按线路列表展示；无 urls 视为单线路配置（该地址本身就是一条线路），不再报"接口无效" */
    private void parseLines(String result, String requestUrl) {
        try {
            JSONObject json = new JSONObject(result);
            if (!json.has("urls")) {
                // 单线路配置（sites 格式）：把请求地址本身作为一条线路展示
                MoreSourceBean single = new MoreSourceBean();
                single.setSourceUrl(requestUrl);
                single.setSourceName(selectedStore != null && selectedStore.getSourceName() != null
                        && !selectedStore.getSourceName().isEmpty() ? selectedStore.getSourceName() : requestUrl);
                single.setShowDelete(false);
                lineList.clear();
                lineList.add(single);
                if (showHistory) {
                    // 合并已保存线路
                    for (MoreSourceBean b : MoreSourceHelper.getLineHistory()) {
                        if (!b.getSourceUrl().equals(requestUrl)) lineList.add(b);
                    }
                }
                mAdapter.setData(new ArrayList<>(lineList));
                focusList();
                return;
            }
            JSONArray urls = json.getJSONArray("urls");
            List<MoreSourceBean> remote = new ArrayList<>();
            for (int i = 0; i < urls.length(); i++) {
                JSONObject o = urls.getJSONObject(i);
                MoreSourceBean b = new MoreSourceBean();
                b.setSourceUrl(o.getString("url"));
                b.setSourceName(o.optString("name", ""));
                b.setShowDelete(false);
                remote.add(b);
            }
            LinkedHashMap<String, MoreSourceBean> map = new LinkedHashMap<>();
            // v2.1.5：先放已保存的单线路（手动添加的线路永远排在列表顶部可见，
            // 不被仓库线路挤出可视区；切换任意仓库均保持可见，除非用户自行删除）
            if (showHistory) {
                for (MoreSourceBean b : lineList) {
                    if (b.getSourceUrl() != null) map.put(b.getSourceUrl(), b);
                }
            }
            for (MoreSourceBean b : remote) {
                if (!map.containsKey(b.getSourceUrl())) map.put(b.getSourceUrl(), b);
            }
            lineList.clear();
            lineList.addAll(map.values());
            mAdapter.setData(new ArrayList<>(lineList));
            focusList();
        } catch (Throwable th) {
            Toast.makeText(mContext, "接口无效，请更换接口" + th.getMessage(), Toast.LENGTH_SHORT).show();
            mAdapter.setData(new ArrayList<>(lineList));
            focusList();
        }
    }

    /** v2.1.4：列表数据就绪后把遥控器焦点交给列表（TV D-pad 可选中线路/删除） */
    private void focusList() {
        mListView.post(new Runnable() {
            @Override
            public void run() {
                mListView.requestFocus();
            }
        });
    }
}
