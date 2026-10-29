package com.github.tvbox.osc.server;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author pj567
 * @date :2021/1/5
 * @description: 响应按键和输入
 */

public class InputRequestProcess implements RequestProcess {
    private RemoteServer remoteServer;

    public InputRequestProcess(RemoteServer remoteServer) {
        this.remoteServer = remoteServer;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        if (session.getMethod() == NanoHTTPD.Method.POST) {
            switch (fileName) {
                case "/action":
                    return true;
            }
        }
        return false;
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        DataReceiver mDataReceiver = remoteServer.getDataReceiver();
        switch (fileName) {
            case "/action":
                if (params.get("do") != null && mDataReceiver != null) {
                    String action = params.get("do");

                    switch (action) {
                        case "search": {
                            mDataReceiver.onTextReceived(params.get("word").trim());
                            break;
                        }
                        case "api": {
                            mDataReceiver.onApiReceived(params.get("url").trim());
                            break;
                        }
                        case "storeHouse": {
                            String url = params.get("url");
                            if (url != null) {
                                // v2.1.8：透传 web 页面手动输入的名称（可空）
                                String name = params.get("name");
                                mDataReceiver.onStoreHouseReceived(url.trim(), name == null ? "" : name.trim());
                            }
                            break;
                        }
                        case "liveApi": {
                            String liveUrl = params.get("url");
                            if (liveUrl != null) {
                                // v2.1.9：透传 web 页面手动输入的直播源名称（可空）
                                String liveName = params.get("name");
                                mDataReceiver.onLiveApiReceived(liveUrl.trim(), liveName == null ? "" : liveName.trim());
                            }
                            break;
                        }
                        case "danmuApi": {
                            mDataReceiver.onDanmuApiReceived(params.get("url").trim());
                            break;
                        }
                        case "push": {
                            String url = params.get("url");
                            if (url != null && url.trim().length() > 0) {
                                mDataReceiver.onPushReceived(url.trim());
                            }
                            break;
                        }
                    }
                }
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "ok");
            default:
                return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.NOT_FOUND, "Error 404, file not found.");
        }
    }
}
