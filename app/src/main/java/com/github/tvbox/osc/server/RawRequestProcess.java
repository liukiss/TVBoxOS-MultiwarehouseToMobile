package com.github.tvbox.osc.server;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author pj567
 * @date :2021/1/5
 * @description: 资源文件加载
 */
public class RawRequestProcess implements RequestProcess {
    private Context mContext;
    private String fileName;
    private int resourceId;
    private String mimeType;

    public RawRequestProcess(Context context, String fileName, int resourceId, String mimeType) {
        this.mContext = context;
        this.fileName = fileName;
        this.resourceId = resourceId;
        this.mimeType = mimeType;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        return session.getMethod() == NanoHTTPD.Method.GET && this.fileName.equalsIgnoreCase(fileName);
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        InputStream inputStream = mContext.getResources().openRawResource(this.resourceId);
        try {
            NanoHTTPD.Response response = RemoteServer.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mimeType + "; charset=utf-8", inputStream, (long) inputStream.available());
            // 禁止缓存：确保 web 推送页面始终加载最新资源（避免浏览器缓存旧版 script.js 导致推送失效）
            response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.addHeader("Pragma", "no-cache");
            response.addHeader("Expires", "0");
            return response;
        } catch (IOException IOExc) {
            return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + IOExc.getMessage());
        }
    }
}