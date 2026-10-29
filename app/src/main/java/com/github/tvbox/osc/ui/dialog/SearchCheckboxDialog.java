package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.adapter.CheckboxSearchAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.ScreenUtils;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;

public class SearchCheckboxDialog extends BaseDialog{

    private TvRecyclerView mGridView;
    private CheckboxSearchAdapter checkboxSearchAdapter;
    public List<SourceBean> mSourceList;
    TextView checkAll;
    TextView clearAll;

    public HashMap<String, String> mCheckSourcees;

    public SearchCheckboxDialog(@NonNull @NotNull Context context, List<SourceBean> sourceList, HashMap<String, String> checkedSources) {
        super(context);
        if (context instanceof Activity) {
            setOwnerActivity((Activity) context);
        }
        setCanceledOnTouchOutside(true);
        setCancelable(true);
        mSourceList = sourceList;
        mCheckSourcees = checkedSources;
        setContentView(R.layout.dialog_checkbox_search);
        initView(context);
    }

    @Override
    public void dismiss() {
        checkboxSearchAdapter.setMCheckedSources();
        super.dismiss();
    }

    protected void initView(Context context) {
        mGridView = findViewById(R.id.mGridView);
        checkAll = findViewById(R.id.checkAll);
        clearAll = findViewById(R.id.clearAll);
        // 手机版：“全选/全不选”的胶囊背景在代码里生成，避免资源缓存里错误密度下的圆角/描边
        float pxPerMm = ScreenUtils.pxPerMm(context);
        checkAll.setBackground(ScreenUtils.createDialogControlBackground(context, pxPerMm));
        clearAll.setBackground(ScreenUtils.createDialogControlBackground(context, pxPerMm));
        checkboxSearchAdapter = new CheckboxSearchAdapter(new DiffUtil.ItemCallback<SourceBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem.getKey().equals(newItem.getKey());
            }

            @Override
            public boolean areContentsTheSame(@NonNull SourceBean oldItem, @NonNull SourceBean newItem) {
                return oldItem.getName().equals(newItem.getName());
            }
        });
        mGridView.setHasFixedSize(true);

        int size = mSourceList.size();
        // 手机版：两列更适合手机宽度，源较少时单列铺满
        int spanCount = size <= 3 ? 1 : 2;
        mGridView.setLayoutManager(new V7GridLayoutManager(getContext(), spanCount));

        mGridView.setAdapter(checkboxSearchAdapter);
        checkboxSearchAdapter.setData(mSourceList, mCheckSourcees);
        int pos = 0;
        if (mCheckSourcees != null) {
            for(int i=0; i<mSourceList.size(); i++) {
                String key = mSourceList.get(i).getKey();
                if (mCheckSourcees.containsKey(key)) {
                    pos = i;
                    break;
                }
            }
        }
//        final int scrollPosition = pos;
//        mGridView.post(new Runnable() {
//            @Override
//            public void run() {
//                mGridView.smoothScrollToPosition(scrollPosition);
//            }
//        });
        checkAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                mCheckSourcees = new HashMap<>();
                for(SourceBean sourceBean : mSourceList) {
                    mCheckSourcees.put(sourceBean.getKey(), "1");
                }
                checkboxSearchAdapter.setData(mSourceList, mCheckSourcees);
            }
        });
        clearAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FastClickCheckUtil.check(view);
                mCheckSourcees = new HashMap<>();
                checkboxSearchAdapter.setData(mSourceList, mCheckSourcees);
            }
        });
    }

    @Override
    public void show() {
        // 手机版：复用实例时先丢弃旧条目，避免横竖屏切换后按错误密度膨胀的 ViewHolder 继续被复用
        if (mGridView != null && checkboxSearchAdapter != null) {
            mGridView.setAdapter(null);
            mGridView.getRecycledViewPool().clear();
            mGridView.setAdapter(checkboxSearchAdapter);
        }
        prepareBottomSheet();
        super.show();
        if (!isShowing()) return;
        // 手机版：与换源面板一致，贴底全宽、加大高度，标题行可向下拖动关闭
        applyBottomSheet(findViewById(R.id.sheetTitleBar));
    }

    public void setMSourceList(List<SourceBean> SourceBeanList) {
        mSourceList = SourceBeanList;
        checkboxSearchAdapter.setData(mSourceList, mCheckSourcees);
    }
}
