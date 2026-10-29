package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.owen.tvrecyclerview.widget.GridLayoutManager;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SelectDialog<T> extends BaseDialog {
    private final boolean bottomSheet;

    public SelectDialog(@NonNull @NotNull Context context) {
        super(context);
        this.bottomSheet = false;
        setContentView(R.layout.dialog_select);
    }

    public SelectDialog(@NonNull @NotNull Context context, int resId) {
        super(context);
        this.bottomSheet = false;
        setContentView(resId);
    }

    /**
     * 手机版：bottomSheet 为 true 时使用贴底全宽面板布局，并从屏幕下缘向上弹出
     */
    public SelectDialog(@NonNull @NotNull Context context, boolean bottomSheet) {
        super(context);
        this.bottomSheet = bottomSheet;
        setContentView(bottomSheet ? R.layout.dialog_select_sheet : R.layout.dialog_select);
    }

    @Override
    public void show() {
        if (bottomSheet) {
            prepareBottomSheet();
        }
        super.show();
        if (bottomSheet && isShowing()) {
            applyBottomSheet(findViewById(R.id.title));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setTip(String tip) {
        ((TextView) findViewById(R.id.title)).setText(tip);
    }

    public void setAdapter(SelectDialogAdapter.SelectDialogInterface<T> sourceBeanSelectDialogInterface,
                           DiffUtil.ItemCallback<T> sourceBeanItemCallback,
                           List<T> data, int select) {
        final int selectIdx = select;
        SelectDialogAdapter<T> adapter = new SelectDialogAdapter<>(sourceBeanSelectDialogInterface, sourceBeanItemCallback);
        adapter.setData(data, select);
        TvRecyclerView tvRecyclerView = findViewById(R.id.list);
        tvRecyclerView.setAdapter(adapter);
        tvRecyclerView.setSelectedPosition(select);
        if (select<10){
            tvRecyclerView.setSelection(select);
        }
        tvRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                if (selectIdx >= 10) {
                    tvRecyclerView.smoothScrollToPosition(selectIdx);
                    tvRecyclerView.setSelectionWithSmooth(selectIdx);
                }
            }
        });
    }

}
