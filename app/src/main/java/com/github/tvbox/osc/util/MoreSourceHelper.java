package com.github.tvbox.osc.util;

import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.MoreSourceBean;
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
 * 影视仓多仓多线路 —— 数据持久化工具
 * 移植自影视仓：
 *  - custom_store_house          仓库列表（List<MoreSourceBean>）
 *  - custom_store_house_selected 当前选中仓库
 *  - api_history_list            线路历史（List<MoreSourceBean>，影视仓线路框展示）
 *  - api_url_bean                当前线路 Bean
 *  - api_url                     （复用 TVBox 原生键 HawkConfig.API_URL = "api_url"）
 *  - api_history                 （复用 TVBox 原生键，String 历史，双向同步，兼容旧配置历史）
 */
public class MoreSourceHelper {

    public static final String KEY_STORE_HOUSE = "custom_store_house";
    public static final String KEY_STORE_HOUSE_SELECTED = "custom_store_house_selected";
    public static final String KEY_API_HISTORY_LIST = "api_history_list";
    public static final String KEY_API_URL_BEAN = "api_url_bean";
    public static final String KEY_LIVE_SOURCE_LIST = "live_source_list";
    /** 当前接口配置文件里的直播线路（JSON 文本；切换配置时整体替换，上一条配置的直播线路自动删除） */
    public static final String KEY_LIVE_CONFIG_LINES = "live_config_lines";
    /** KEY_LIVE_CONFIG_LINES 所属的接口配置地址 */
    public static final String KEY_LIVE_CONFIG_LINES_API = "live_config_lines_api";
    /** 最近一次加载成功的直播线路地址（切换线路加载失败时回退用） */
    public static final String KEY_LIVE_LAST_GOOD_URL = "live_last_good_api_url";
    /** 最近一次加载成功的直播线路分组序号 */
    public static final String KEY_LIVE_LAST_GOOD_INDEX = "live_last_good_group_index";
    /** "线路切换"弹窗最近一次展示的线路快照（直播"配置切换"与之保持同一份内容） */
    public static final String KEY_CONFIG_SWITCH_LINES = "config_switch_lines";

    // v2.1.6 自动命名计数器（仓库N/线路N/直播源N）
    public static final String KEY_AUTO_NAME_STORE = "auto_name_store_counter";
    public static final String KEY_AUTO_NAME_LINE = "auto_name_line_counter";
    public static final String KEY_AUTO_NAME_LIVE = "auto_name_live_counter";
    public static final String AUTO_PREFIX_STORE = "仓库";
    public static final String AUTO_PREFIX_LINE = "线路";
    public static final String AUTO_PREFIX_LIVE = "直播源";

    private static final int MAX_HISTORY = 30;

    /**
     * v2.1.6 自动命名（名称留空时）：按"前缀+递增编号"生成，编号一直累加，
     * 直至对应列表中已无任何自动命名项（全部删除）后重置为 1（resetAutoCounter 负责重置）。
     * 重复 URL 添加时复用列表中已有名称，不重新编号。
     */
    public static String autoNameForStore(List<MoreSourceBean> list, String url) {
        return autoName(list, url, KEY_AUTO_NAME_STORE, AUTO_PREFIX_STORE);
    }

    public static String autoNameForLine(List<MoreSourceBean> list, String url) {
        return autoName(list, url, KEY_AUTO_NAME_LINE, AUTO_PREFIX_LINE);
    }

    public static String autoNameForLive(List<MoreSourceBean> list, String url) {
        return autoName(list, url, KEY_AUTO_NAME_LIVE, AUTO_PREFIX_LIVE);
    }

    private static String autoName(List<MoreSourceBean> list, String url, String key, String prefix) {
        if (url == null || url.isEmpty()) return null;
        if (list != null) {
            for (MoreSourceBean b : list) {
                if (url.equals(b.getSourceUrl()) && b.getSourceName() != null && !b.getSourceName().isEmpty()) {
                    return b.getSourceName(); // 重复 URL：沿用已有名称
                }
            }
        }
        int n = Hawk.get(key, 0);
        String candidate;
        do {
            n++;
            candidate = prefix + n;
        } while (hasName(list, candidate)); // v2.1.7：跳过已被手动命名占用的编号，避免重名
        Hawk.put(key, n);
        return candidate;
    }

    private static boolean hasName(List<MoreSourceBean> list, String name) {
        if (list == null) return false;
        for (MoreSourceBean b : list) {
            if (name.equals(b.getSourceName())) return true;
        }
        return false;
    }

    /** 删除后调用：对应列表中已无自动命名项（全部删除）→ 计数器归零，下次从 1 开始 */
    public static void resetAutoCounter(String key, String prefix, List<MoreSourceBean> list) {
        if (list != null) {
            for (MoreSourceBean b : list) {
                if (b.getSourceName() != null && b.getSourceName().matches("^" + prefix + "\\d+$")) {
                    return; // 仍有自动命名项 → 计数器保持（继续累加）
                }
            }
        }
        Hawk.put(key, 0);
    }

    /** 远程推送/静默添加结果回调 */
    public interface OnSourceRemoteCallback {
        void onDone(boolean success, String msg);
    }

    /* ---------- 仓库列表 ---------- */
    public static List<MoreSourceBean> getStoreHouse() {
        List<MoreSourceBean> list = Hawk.get(KEY_STORE_HOUSE, new ArrayList<MoreSourceBean>());
        return list == null ? new ArrayList<MoreSourceBean>() : list;
    }

    public static void saveStoreHouse(List<MoreSourceBean> list) {
        Hawk.put(KEY_STORE_HOUSE, list);
    }

    public static MoreSourceBean getSelected() {
        return Hawk.get(KEY_STORE_HOUSE_SELECTED, (MoreSourceBean) null);
    }

    public static void setSelected(MoreSourceBean bean) {
        Hawk.put(KEY_STORE_HOUSE_SELECTED, bean);
    }

    /* ---------- 线路历史（api_history_list + api_history 双写） ---------- */
    public static List<MoreSourceBean> getLineHistory() {
        List<MoreSourceBean> list = Hawk.get(KEY_API_HISTORY_LIST, new ArrayList<MoreSourceBean>());
        return list == null ? new ArrayList<MoreSourceBean>() : list;
    }

    /** 添加一条线路到"已保存线路"（api_history_list，用于线路切换展示；不写 TVBox 原生配置历史键） */
    public static void addLineHistory(MoreSourceBean line) {
        if (line == null || line.getSourceUrl() == null || line.getSourceUrl().isEmpty()) return;
        List<MoreSourceBean> list = Hawk.get(KEY_API_HISTORY_LIST, new ArrayList<MoreSourceBean>());
        if (list == null) list = new ArrayList<>();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).getSourceUrl().equals(line.getSourceUrl())) {
                list.remove(i);
            }
        }
        list.add(0, line);
        while (list.size() > MAX_HISTORY) list.remove(list.size() - 1);
        Hawk.put(KEY_API_HISTORY_LIST, list);
    }

    /** 删除一条已保存线路（api_history_list） */
    public static void removeLineHistory(MoreSourceBean line) {
        if (line == null || line.getSourceUrl() == null) return;
        List<MoreSourceBean> list = Hawk.get(KEY_API_HISTORY_LIST, new ArrayList<MoreSourceBean>());
        if (list == null) list = new ArrayList<>();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).getSourceUrl().equals(line.getSourceUrl())) {
                list.remove(i);
            }
        }
        Hawk.put(KEY_API_HISTORY_LIST, list);
    }

    /* ---------- 当前线路 ---------- */
    public static MoreSourceBean getCurrentLineBean() {
        return Hawk.get(KEY_API_URL_BEAN, (MoreSourceBean) null);
    }

    /**
     * 彻底删除一条线路（v2.1.2）：
     * ① 从 api_history_list（线路切换弹窗数据源）移除
     * ② 从 LIVE_API_HISTORY（直播"配置切换"数据源）同步移除
     * ③ 若为当前点播/直播线路 → 清除对应当前键
     */
    public static void removeLineCompletely(MoreSourceBean line) {
        if (line == null || line.getSourceUrl() == null || line.getSourceUrl().isEmpty()) return;
        String url = line.getSourceUrl();
        removeLineHistory(line);
        HistoryHelper.removeLiveApiHistory(url);
        String curApi = Hawk.get(HawkConfig.API_URL, "");
        if (url.equals(curApi)) {
            Hawk.put(HawkConfig.API_URL, "");
            Hawk.delete(KEY_API_URL_BEAN);
        }
        String curLive = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (url.equals(curLive)) {
            Hawk.put(HawkConfig.LIVE_API_URL, "");
        }
    }

    /**
     * 删除当前选中仓库后：重置当前线路相关键（v2.1.2）。
     * 保留手动单线路（api_history_list）与直播源列表（live_source_list），
     * 仅清空当前点播/直播线路及其直播历史项。
     */
    public static void resetCurrentLine() {
        // v2.1.3：删除当前选中仓库 → 清除选中仓库（"线路切换"弹窗不再请求已删除仓库的线路）
        try {
            Hawk.delete(KEY_STORE_HOUSE_SELECTED);
        } catch (Throwable ignored) {
        }
        String curApi = Hawk.get(HawkConfig.API_URL, "");
        if (!curApi.isEmpty()) {
            HistoryHelper.removeLiveApiHistory(curApi);
        }
        Hawk.put(HawkConfig.API_URL, "");
        Hawk.delete(KEY_API_URL_BEAN);
        Hawk.put(HawkConfig.API_LINE_LIST, new ArrayList<String>());
        Hawk.put(HawkConfig.API_LINE_SOURCE, "");
        Hawk.put(HawkConfig.LIVE_API_URL, "");
        Hawk.put(HawkConfig.LIVE_GROUP_LIST, new com.google.gson.JsonArray());
        // 当前接口配置失效 → 同步清空其直播线路（用户自行添加的直播源保留）
        clearConfigLiveLines();
    }

    /**
     * 全部删除后的彻底清理（v2.1.2）：
     * 清空仓库、选中仓库、手动单线路、当前点播线路、当前直播地址及其历史、线路集合缓存。
     * 调用后：配置地址/直播地址按钮值、线路切换弹窗、直播"配置切换"均不再显示残留。
     */
    public static void resetAllConfig() {
        try {
            Hawk.delete(KEY_STORE_HOUSE_SELECTED);
        } catch (Throwable ignored) {
        }
        Hawk.put(KEY_API_HISTORY_LIST, new ArrayList<MoreSourceBean>());
        Hawk.put(HawkConfig.API_URL, "");
        Hawk.delete(KEY_API_URL_BEAN);
        Hawk.put(HawkConfig.API_LINE_LIST, new ArrayList<String>());
        Hawk.put(HawkConfig.API_LINE_SOURCE, "");
        Hawk.put(HawkConfig.LIVE_API_URL, "");
        Hawk.put(HawkConfig.LIVE_API_HISTORY, new ArrayList<String>());
        clearConfigLiveLines();
        // v2.1.6：全部删除 → 自动命名计数器归零（下次从 1 开始）
        Hawk.put(KEY_AUTO_NAME_STORE, 0);
        Hawk.put(KEY_AUTO_NAME_LINE, 0);
        Hawk.put(KEY_AUTO_NAME_LIVE, 0);
        Hawk.put(HawkConfig.LIVE_GROUP_LIST, new com.google.gson.JsonArray());
    }

    /**
     * 落地当前线路（影视仓选中线路 → TVBox 配置地址）
     * 写 TVBox 原生键 api_url（与影视仓 api_url 同一键），TVBox 原生 ApiConfig.loadConfig 读同一键即可完成接口加载初始化。
     *
     * @param saveToList 是否保存到"已保存线路"（线路切换展示）：
     *                   - true  ：仅手动添加的单线路（sites 分支）——永久保存，除非用户删除
     *                   - false ：仓库自动加载/仓库内选中的线路——只作为当前线路，不进入保存列表
     */
    public static void applyLine(MoreSourceBean line, boolean saveToList) {
        if (line == null || line.getSourceUrl() == null || line.getSourceUrl().isEmpty()) return;
        // ① 当前配置地址（TVBox 原生键，与影视仓 api_url 同一键）
        Hawk.put(HawkConfig.API_URL, line.getSourceUrl());
        // ② 当前线路 Bean（名称显示）
        Hawk.put(KEY_API_URL_BEAN, line);
        // ③ 线路切换 → 直播地址跟随（v2.1.1）：直播"多源切换"以当前线路接口文件的直播定义为准
        Hawk.put(HawkConfig.LIVE_API_URL, line.getSourceUrl());
        ApiConfig.setLiveGroupIndex(0); // 默认使用新接口配置文件的第一条直播线路
        // 删除上一条接口配置文件的直播线路（用户自行添加的直播源保留），并联动刷新"多源切换"
        clearConfigLiveLines();
        clearLastGoodLiveLine();
        ApiConfig.get().refreshLiveMultiSourceItems();
        // ④ 配置地址变更 → 重建"配置切换"快照（避免快照与当前线路不一致）
        syncConfigSwitchSnapshot();
        // ⑤ 仅手动添加的单线路保存到"已保存线路"（去重，上限 30）
        if (saveToList) {
            addLineHistory(line);
        }
    }

    /**
     * 配置地址变更后统一调用：重建"配置切换"快照并刷新列表。
     * 快照中已包含当前线路时不重建（保留"线路切换"弹窗渲染出的完整列表）。
     */
    public static void syncConfigSwitchSnapshot() {
        String currentUrl = Hawk.get(HawkConfig.API_URL, "");
        if (currentUrl == null || currentUrl.isEmpty()) return;
        for (MoreSourceBean bean : getConfigSwitchLines()) {
            if (currentUrl.equals(bean.getSourceUrl())) {
                ApiConfig.get().refreshLiveApiHistoryItems();
                return;
            }
        }
        List<MoreSourceBean> rebuilt = new ArrayList<>();
        MoreSourceBean cur = getCurrentLineBean();
        MoreSourceBean current = new MoreSourceBean();
        current.setSourceUrl(currentUrl);
        current.setSourceName(cur != null && currentUrl.equals(cur.getSourceUrl()) ? cur.getSourceName() : "");
        current.setShowDelete(false);
        rebuilt.add(current);
        for (MoreSourceBean bean : getLineHistory()) {
            if (bean == null || bean.getSourceUrl() == null || bean.getSourceUrl().isEmpty()) continue;
            if (currentUrl.equals(bean.getSourceUrl())) continue;
            MoreSourceBean copy = new MoreSourceBean();
            copy.setSourceName(bean.getSourceName());
            copy.setSourceUrl(bean.getSourceUrl());
            copy.setShowDelete(false);
            rebuilt.add(copy);
        }
        saveConfigSwitchLines(rebuilt);
        ApiConfig.get().refreshLiveApiHistoryItems();
    }

    /** 兼容旧调用：默认不保存到列表 */
    public static void applyLine(MoreSourceBean line) {
        applyLine(line, false);
    }

    /* ========== 直播地址管理（v2.1.0 新增） ========== */

    /** 直播地址列表（弹窗展示/删除） */
    public static List<MoreSourceBean> getLiveSourceList() {
        List<MoreSourceBean> list = Hawk.get(KEY_LIVE_SOURCE_LIST, new ArrayList<MoreSourceBean>());
        return list == null ? new ArrayList<MoreSourceBean>() : list;
    }

    public static void saveLiveSourceList(List<MoreSourceBean> list) {
        Hawk.put(KEY_LIVE_SOURCE_LIST, list);
    }

    /** 删除一条直播地址：从 live_source_list 移除，并同步从 LIVE_API_HISTORY（直播"配置切换"数据源）移除；不影响当前直播 */
    public static void removeLiveSource(MoreSourceBean bean) {
        if (bean == null || bean.getSourceUrl() == null) return;
        List<MoreSourceBean> list = getLiveSourceList();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).getSourceUrl().equals(bean.getSourceUrl())) {
                list.remove(i);
            }
        }
        saveLiveSourceList(list);
        HistoryHelper.removeLiveApiHistory(bean.getSourceUrl());
    }

    /**
     * 添加/启用直播源：
     * ① 写入 live_source_list（去重，上限 30，展示用）
     * ② 写 TVBox 原生键 LIVE_API_URL（当前直播地址，直播加载读取）
     * ③ 写 LIVE_API_HISTORY（直播播放页"配置切换"数据源）
     */
    public static void addLiveSource(String name, String url) {
        if (url == null || url.trim().isEmpty()) return;
        String finalUrl = url.trim();
        List<MoreSourceBean> list = getLiveSourceList();
        String existingName = null;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (finalUrl.equals(list.get(i).getSourceUrl())) {
                existingName = list.get(i).getSourceName();
                list.remove(i);
            }
        }
        MoreSourceBean bean = new MoreSourceBean();
        bean.setSourceUrl(finalUrl);
        if (name == null || name.trim().isEmpty()) {
            // v2.1.6：直播源名留空 → 自动命名（重复 URL 沿用旧名，否则递增"直播源N"）
            bean.setSourceName(existingName != null && !existingName.isEmpty()
                    ? existingName : autoNameForLive(list, finalUrl));
        } else {
            bean.setSourceName(name);
        }
        bean.setShowDelete(true);
        list.add(0, bean);
        while (list.size() > 30) list.remove(list.size() - 1);
        saveLiveSourceList(list);
        // 原生直播链路（直播历史仅记录用户自定义源，供"直播历史配置"读取）
        Hawk.put(HawkConfig.LIVE_API_URL, finalUrl);
        HistoryHelper.setLiveApiHistory(finalUrl);
    }

    /* ---------- 接口配置文件中的直播线路（直播地址弹窗 + 多源切换共用） ---------- */

    /** 保存当前接口配置文件里的直播线路（整体替换 → 上一条配置的直播线路自动删除） */
    public static void saveConfigLiveLines(String apiUrl, String livesJson) {
        Hawk.put(KEY_LIVE_CONFIG_LINES_API, apiUrl == null ? "" : apiUrl);
        Hawk.put(KEY_LIVE_CONFIG_LINES, livesJson == null || livesJson.isEmpty() ? "[]" : livesJson);
    }

    public static void clearConfigLiveLines() {
        Hawk.put(KEY_LIVE_CONFIG_LINES_API, "");
        Hawk.put(KEY_LIVE_CONFIG_LINES, "[]");
    }

    /** 接口配置文件中的直播线路（无删除按钮，顺序与配置文件一致） */
    public static List<MoreSourceBean> getConfigLiveLines() {
        String json = Hawk.get(KEY_LIVE_CONFIG_LINES, "[]");
        String apiUrl = Hawk.get(KEY_LIVE_CONFIG_LINES_API, "");
        List<MoreSourceBean> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json == null || json.isEmpty() ? "[]" : json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                MoreSourceBean bean = new MoreSourceBean();
                String name = obj.optString("name", "");
                bean.setSourceName(name == null || name.isEmpty() ? "线路" + (i + 1) : name);
                String url = obj.optString("url", "");
                if (url == null || url.isEmpty()) url = obj.optString("api", "");
                if (url == null || url.isEmpty()) url = apiUrl;
                bean.setSourceUrl(url);
                bean.setShowDelete(false);
                bean.setLiveGroupIndex(i);
                list.add(bean);
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    /** 直播地址弹窗与"多源切换"弹窗共用的列表：用户自行添加（可删除）在前，配置文件直播线路在后 */
    public static List<MoreSourceBean> getLiveDialogList() {
        List<MoreSourceBean> list = new ArrayList<>(getLiveSourceList());
        for (MoreSourceBean bean : list) bean.setShowDelete(true);
        list.addAll(getConfigLiveLines());
        return list;
    }

    /** 弹窗显示名：名称为空时回退地址 */
    public static String getLiveDisplayName(MoreSourceBean bean) {
        if (bean == null) return "";
        String name = bean.getSourceName() == null ? "" : bean.getSourceName().trim();
        return name.isEmpty() ? bean.getSourceUrl() : name;
    }

    /** 保存"线路切换"弹窗当前展示的线路快照（名称+地址），供直播"配置切换"同步展示 */
    public static void saveConfigSwitchLines(List<MoreSourceBean> lines) {
        try {
            JSONArray array = new JSONArray();
            if (lines != null) {
                for (MoreSourceBean bean : lines) {
                    if (bean == null || bean.getSourceUrl() == null || bean.getSourceUrl().isEmpty()) continue;
                    JSONObject obj = new JSONObject();
                    obj.put("name", bean.getSourceName() == null ? "" : bean.getSourceName());
                    obj.put("url", bean.getSourceUrl());
                    array.put(obj);
                }
            }
            Hawk.put(KEY_CONFIG_SWITCH_LINES, array.toString());
        } catch (Throwable ignored) {
        }
    }

    /** "线路切换"弹窗的线路快照；为空时回退到已保存线路/当前线路，保证"配置切换"不为空 */
    public static List<MoreSourceBean> getConfigSwitchLines() {
        List<MoreSourceBean> list = new ArrayList<>();
        String json = Hawk.get(KEY_CONFIG_SWITCH_LINES, "[]");
        try {
            JSONArray array = new JSONArray(json == null || json.isEmpty() ? "[]" : json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                String url = obj.optString("url", "");
                if (url == null || url.isEmpty()) continue;
                MoreSourceBean bean = new MoreSourceBean();
                bean.setSourceName(obj.optString("name", ""));
                bean.setSourceUrl(url);
                bean.setShowDelete(false);
                list.add(bean);
            }
        } catch (Throwable ignored) {
        }
        if (list.isEmpty()) {
            // 快照未生成时，用"已保存线路"兜底，尽量与"线路切换"弹窗内容一致
            for (MoreSourceBean bean : getLineHistory()) {
                if (bean == null || bean.getSourceUrl() == null || bean.getSourceUrl().isEmpty()) continue;
                MoreSourceBean copy = new MoreSourceBean();
                copy.setSourceName(bean.getSourceName());
                copy.setSourceUrl(bean.getSourceUrl());
                copy.setShowDelete(false);
                list.add(copy);
            }
        }
        if (list.isEmpty()) {
            MoreSourceBean cur = getCurrentLineBean();
            String apiUrl = Hawk.get(HawkConfig.API_URL, "");
            if (cur != null && cur.getSourceUrl() != null) {
                list.add(cur);
            } else if (apiUrl != null && !apiUrl.isEmpty()) {
                MoreSourceBean bean = new MoreSourceBean();
                bean.setSourceName(apiUrl);
                bean.setSourceUrl(apiUrl);
                bean.setShowDelete(false);
                list.add(bean);
            }
        }
        return list;
    }

    /** 线路弹窗显示名：有名称用名称，否则用地址 */
    public static String getLineDisplayName(MoreSourceBean bean) {
        if (bean == null) return "";
        String name = bean.getSourceName() == null ? "" : bean.getSourceName().trim();
        if (name.isEmpty() || name.equals(bean.getSourceUrl())) return bean.getSourceUrl();
        return name;
    }

    /** 当前直播对应的"配置切换"列表位置 */
    public static int getConfigSwitchIndex() {
        List<MoreSourceBean> list = getConfigSwitchLines();
        String liveUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");
        for (int i = 0; i < list.size(); i++) {
            if (liveUrl != null && !liveUrl.isEmpty() && liveUrl.equals(list.get(i).getSourceUrl())) return i;
        }
        for (int i = 0; i < list.size(); i++) {
            if (apiUrl != null && !apiUrl.isEmpty() && apiUrl.equals(list.get(i).getSourceUrl())) return i;
        }
        // 当前线路不在快照中 → 返回 -1（调用方不选中任何项，避免误选中第 0 项）
        return -1;
    }

    public static boolean isUserLiveSource(String url) {
        if (url == null || url.isEmpty()) return false;
        for (MoreSourceBean bean : getLiveSourceList()) {
            if (url.equals(bean.getSourceUrl())) return true;
        }
        return false;
    }

    private static int currentLiveGroupIndex() {
        String liveUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        String key = (liveUrl == null || liveUrl.isEmpty())
                ? HawkConfig.LIVE_GROUP_INDEX : HawkConfig.LIVE_GROUP_INDEX + "_" + liveUrl;
        return Hawk.get(key, 0);
    }

    /** 当前正在播放的直播线路：用户自定义源优先，否则为接口配置文件的第 N 条直播线路 */
    public static MoreSourceBean getCurrentLiveLine() {
        String liveUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (liveUrl != null && !liveUrl.isEmpty()) {
            for (MoreSourceBean bean : getLiveSourceList()) {
                if (liveUrl.equals(bean.getSourceUrl())) {
                    MoreSourceBean copy = new MoreSourceBean();
                    copy.setSourceName(bean.getSourceName());
                    copy.setSourceUrl(bean.getSourceUrl());
                    copy.setShowDelete(true);
                    return copy;
                }
            }
        }
        List<MoreSourceBean> lines = getConfigLiveLines();
        int index = currentLiveGroupIndex();
        if (index >= 0 && index < lines.size()) return lines.get(index);
        return lines.isEmpty() ? null : lines.get(0);
    }

    public static String getCurrentLiveLineName() {
        MoreSourceBean bean = getCurrentLiveLine();
        return bean == null ? "" : getLiveDisplayName(bean);
    }

    /** 当前直播线路在"直播地址/多源切换"列表中的位置 */
    public static int getCurrentLiveLineIndex() {
        MoreSourceBean current = getCurrentLiveLine();
        if (current == null) return 0;
        List<MoreSourceBean> list = getLiveDialogList();
        for (int i = 0; i < list.size(); i++) {
            MoreSourceBean bean = list.get(i);
            if (current.isShowDelete() == bean.isShowDelete()
                    && current.getSourceUrl().equals(bean.getSourceUrl())
                    && current.getSourceName().equals(bean.getSourceName())) {
                return i;
            }
        }
        return 0;
    }

    /** 当前直播线路指纹（用于判断线路是否变化） */
    public static String getCurrentLiveLineKey() {
        MoreSourceBean bean = getCurrentLiveLine();
        if (bean == null) return "";
        return (bean.isShowDelete() ? "user:" : "config:") + bean.getLiveGroupIndex() + ":" + bean.getSourceUrl();
    }

    /** 记录当前直播线路为"最近一次加载成功"（切换线路加载失败时回退到它） */
    public static void rememberGoodLiveLine() {
        String url = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (url == null || url.isEmpty()) url = Hawk.get(HawkConfig.API_URL, "");
        Hawk.put(KEY_LIVE_LAST_GOOD_URL, url == null ? "" : url);
        Hawk.put(KEY_LIVE_LAST_GOOD_INDEX, currentLiveGroupIndex());
    }

    public static void clearLastGoodLiveLine() {
        Hawk.put(KEY_LIVE_LAST_GOOD_URL, "");
        Hawk.put(KEY_LIVE_LAST_GOOD_INDEX, 0);
    }

    /**
     * 切换线路加载失败 → 回退到最近一次加载成功的直播线路。
     * 若当前线路本身就是最近成功线路，则返回 false（避免反复重试死循环）。
     */
    public static boolean restoreLastGoodLiveLine() {
        String goodUrl = Hawk.get(KEY_LIVE_LAST_GOOD_URL, "");
        if (goodUrl == null || goodUrl.isEmpty()) return false;
        String current = Hawk.get(HawkConfig.LIVE_API_URL, "");
        if (current == null || current.isEmpty()) current = Hawk.get(HawkConfig.API_URL, "");
        int index = Hawk.get(KEY_LIVE_LAST_GOOD_INDEX, 0);
        // 地址相同但直播线路分组不同，也允许回退（同一配置文件内切换直播线路失败的情况）
        if (goodUrl.equals(current) && index == currentLiveGroupIndex()) return false;
        Hawk.put(HawkConfig.LIVE_API_URL, goodUrl);
        String key = goodUrl.isEmpty()
                ? HawkConfig.LIVE_GROUP_INDEX : HawkConfig.LIVE_GROUP_INDEX + "_" + goodUrl;
        Hawk.put(key, index);
        return true;
    }

    /**
     * 直播列表加载失败时的回退：
     * 1) 先回到最近一次加载成功的直播线路；
     * 2) 不可用时退到接口配置地址的第一条直播线路；
     * 返回是否发生了回退。
     */
    public static boolean recoverToAvailableLiveLine() {
        if (restoreLastGoodLiveLine()) return true;
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");
        if (apiUrl == null || apiUrl.isEmpty()) return false;
        String liveUrl = Hawk.get(HawkConfig.LIVE_API_URL, "");
        boolean isUserSource = liveUrl != null && !liveUrl.isEmpty() && !liveUrl.equals(apiUrl);
        if (isUserSource || ApiConfig.getLiveGroupIndex() != 0) {
            Hawk.put(HawkConfig.LIVE_API_URL, apiUrl);
            ApiConfig.setLiveGroupIndex(0);
            return true;
        }
        return false;
    }

    /* ========== 远程推送/静默添加（不依赖 UI，供 ControlManager 等后台调用） ========== */

    /**
     * 静默添加订阅地址（对齐影视仓 b1 三分支 + z0 链式 + c1 落地）：
     *  - storeHouse → 合并仓库 → api_url 为空时链式取第一个仓库
     *  - urls      → 当前地址作为仓库保存 → 链式
     *  - sites     → 直接落地当前地址（单线路，保存到"已保存线路"）
     *
     * @param name 用户自定义名称（web 推送"名称"输入框，可空；空则自动命名"仓库N/线路N"）
     */
    public static void addSourceRemote(final String name, final String sourceUrl, final OnSourceRemoteCallback callback) {
        if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
            if (callback != null) callback.onDone(false, "地址为空");
            return;
        }
        final String finalName = (name == null) ? "" : name.trim();
        new Thread(new Runnable() {
            @Override
            public void run() {
                fetchAdd(finalName, sourceUrl.trim(), callback);
            }
        }).start();
    }

    private static void fetchAdd(String name, String sourceUrl, OnSourceRemoteCallback callback) {
        String result = "";
        String error = "";
        Response response = null;
        try {
            OkHttpClient client = OkGoHelper.getDefaultClient();
            if (client == null) client = com.github.catvod.net.OkHttp.client();
            Request request = new Request.Builder().url(sourceUrl).build();
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
        if (!TextUtils.isEmpty(error)) {
            if (callback != null) callback.onDone(false, "仓库加载失败:" + error);
            return;
        }
        parseAddJson(name, sourceUrl, result, callback);
    }

    private static void parseAddJson(String name, String sourceUrl, String result, OnSourceRemoteCallback callback) {
        try {
            JSONObject json = new JSONObject(result);
            List<MoreSourceBean> storeHouse = new ArrayList<>(getStoreHouse());
            List<String> visited = new ArrayList<>();
            handleAddJson(name, sourceUrl, storeHouse, visited, json, callback);
        } catch (Throwable th) {
            if (callback != null) callback.onDone(false, "接口无效:" + th.getMessage());
        }
    }

    /** b1 三分支解析（对齐影视仓 b1.b） */
    private static void handleAddJson(String name, String sourceUrl, List<MoreSourceBean> storeHouse, List<String> visited, JSONObject json, OnSourceRemoteCallback callback) {
        try {
            if (json.has("storeHouse")) {
                // storeHouse 分支：合并去重 → 保存 → 链式
                mergeStoreHouse(storeHouse, json.getJSONArray("storeHouse"));
                saveStoreHouse(storeHouse);
                chainLoadRemote(storeHouse, visited, callback);
                return;
            }
            if (json.has("urls")) {
                // urls 分支：当前添加地址作为仓库保存 → 链式（影视仓 z0.d + z0.a）
                MoreSourceBean sel = new MoreSourceBean();
                // v2.1.8：web 推送支持自定义名称（留空 → 自动命名"仓库N"）
                sel.setSourceName(!name.isEmpty() ? name : autoNameForStore(storeHouse, sourceUrl));
                sel.setSourceUrl(sourceUrl);
                sel.setShowDelete(true);
                boolean exists = false;
                for (MoreSourceBean b : storeHouse) {
                    if (sourceUrl.equals(b.getSourceUrl())) { exists = true; break; }
                }
                if (!exists) {
                    storeHouse.add(sel);
                    saveStoreHouse(storeHouse);
                }
                chainLoadRemote(storeHouse, visited, callback);
                return;
            }
            if (json.has("sites")) {
                // sites 分支：直接是单线路/配置地址 → 落地（保存到"已保存线路"）
                MoreSourceBean line = new MoreSourceBean();
                // v2.1.8：web 推送支持自定义名称（留空 → 自动命名"线路N"）
                line.setSourceName(!name.isEmpty() ? name : autoNameForLine(getLineHistory(), sourceUrl));
                line.setSourceUrl(sourceUrl);
                line.setShowDelete(true);
                applyLine(line, true);
                if (callback != null) callback.onDone(true, "配置已切换");
                return;
            }
            if (callback != null) callback.onDone(false, "仓库格式不对");
        } catch (Throwable th) {
            if (callback != null) callback.onDone(false, "接口无效:" + th.getMessage());
        }
    }

    /** 链式 z0.a()：仅 api_url 为空时取第一个仓库（影视仓 first 语义） */
    private static void chainLoadRemote(List<MoreSourceBean> storeHouse, List<String> visited, OnSourceRemoteCallback callback) {
        try {
            String curApi = Hawk.get(HawkConfig.API_URL, "");
            if (curApi != null && !curApi.isEmpty()) {
                if (callback != null) callback.onDone(true, "已添加");
                return;
            }
            if (storeHouse.isEmpty()) {
                if (callback != null) callback.onDone(true, "已添加");
                return;
            }
            MoreSourceBean first = storeHouse.get(0); // first
            if (visited.contains(first.getSourceUrl())) {
                if (callback != null) callback.onDone(true, "已添加");
                return; // 防循环
            }
            visited.add(first.getSourceUrl());
            setSelected(first);
            String result = fetchSync(first.getSourceUrl());
            if (result == null) {
                if (callback != null) callback.onDone(false, "仓库加载失败");
                return;
            }
            JSONObject json = new JSONObject(result);
            if (json.has("urls")) {
                // 落地 urls[0]（影视仓 c1：urls[0] → api_url）
                JSONArray urls = json.getJSONArray("urls");
                if (urls.length() == 0) {
                    if (callback != null) callback.onDone(true, "已添加");
                    return;
                }
                JSONObject u0 = urls.getJSONObject(0);
                MoreSourceBean line = new MoreSourceBean();
                line.setSourceUrl(u0.getString("url"));
                line.setSourceName(u0.optString("name", ""));
                line.setShowDelete(false);
                applyLine(line, false);
                if (callback != null) callback.onDone(true, "配置已切换");
                return;
            }
            if (json.has("storeHouse")) {
                // 嵌套仓库：合并 → 继续链式
                mergeStoreHouse(storeHouse, json.getJSONArray("storeHouse"));
                saveStoreHouse(storeHouse);
                chainLoadRemote(storeHouse, visited, callback);
                return;
            }
            if (callback != null) callback.onDone(false, "接口无效");
        } catch (Throwable th) {
            if (callback != null) callback.onDone(false, "接口无效:" + th.getMessage());
        }
    }

    /** 同步拉取（链式请求用） */
    private static String fetchSync(String url) {
        Response response = null;
        try {
            OkHttpClient client = OkGoHelper.getDefaultClient();
            if (client == null) client = com.github.catvod.net.OkHttp.client();
            Request request = new Request.Builder().url(url).build();
            response = client.newCall(request).execute();
            if (!response.isSuccessful() || response.body() == null) return null;
            return response.body().string();
        } catch (Throwable th) {
            return null;
        } finally {
            if (response != null && response.body() != null) response.body().close();
        }
    }

    /** 合并仓库（按 sourceUrl 去重，新增项 showDelete=true） */
    private static void mergeStoreHouse(List<MoreSourceBean> storeHouse, JSONArray arr) {
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
}
