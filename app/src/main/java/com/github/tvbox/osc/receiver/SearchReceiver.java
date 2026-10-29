package com.github.tvbox.osc.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.github.tvbox.osc.ui.activity.MainActivity;

/**
 * @author pj567
 * @date :2021/1/5
 * @description:
 */
public class SearchReceiver extends BroadcastReceiver {
    public static String action = "android.content.movie.search.Action";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (action.equals(intent.getAction()) && intent.getExtras() != null) {
            Bundle args = new Bundle();
            args.putString("title", intent.getExtras().getString("title"));
            MainActivity.openTab(context, MainActivity.TAB_SEARCH, args);
        }
    }
}