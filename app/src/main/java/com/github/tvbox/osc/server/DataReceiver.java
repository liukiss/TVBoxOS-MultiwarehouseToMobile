package com.github.tvbox.osc.server;

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
public interface DataReceiver {

    /**
     * @param text
     */
    void onTextReceived(String text);


    void onApiReceived(String url);

    /** 远程推送多仓订阅链接（web"配置"页 → 添加仓库/线路；name 为推送页面手动输入的名称，可空） */
    void onStoreHouseReceived(String url, String name);

    /** 远程推送直播配置接口（web"配置"页 → 添加直播源；name 为推送页面手动输入的直播源名称，可空） */
    void onLiveApiReceived(String url, String name);

    void onDanmuApiReceived(String url);

    void onPushReceived(String url);
}
