package com.github.tvbox.osc.ui.adapter;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.ScreenUtils;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SelectDialogAdapter<T> extends ListAdapter<T, SelectDialogAdapter.SelectViewHolder> {

    class SelectViewHolder extends RecyclerView.ViewHolder {

        public SelectViewHolder(@NonNull @NotNull View itemView) {
            super(itemView);
        }
    }

    public interface SelectDialogInterface<T> {
        void click(T value, int pos);

        String getDisplay(T val);
    }


    public static DiffUtil.ItemCallback<String> stringDiff = new DiffUtil.ItemCallback<String>() {

        @Override
        public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
            return oldItem.equals(newItem);
        }
    };


    private ArrayList<T> data = new ArrayList<>();

    private int select = 0;

    private SelectDialogInterface dialogInterface;

    public SelectDialogAdapter(SelectDialogInterface dialogInterface, DiffUtil.ItemCallback diffCallback) {
        super(diffCallback);
        this.dialogInterface = dialogInterface;
    }

    public void setData(List<T> newData, int defaultSelect) {
        data.clear();
        data.addAll(newData);
        select = defaultSelect;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }


    @Override
    public SelectDialogAdapter.SelectViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new SelectDialogAdapter.SelectViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_select, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull SelectDialogAdapter.SelectViewHolder holder, @SuppressLint("RecyclerView") int position) {
        T value = data.get(position);
        String name = dialogInterface.getDisplay(value);
        TextView view = holder.itemView.findViewById(R.id.tvName);
        // 手机版：条目内的 mm 尺寸（高度、最小高度、字号、左右内边距）全部在绑定期按
        // “屏幕实际宽 / 360mm”换算后写死。条目要到弹窗第一帧布局时才膨胀，
        // 那一瞬间 Resources 的 mm 换算密度可能仍是系统原生值（约设计基准的 6.3 倍），
        // 会把条目撑到 793px 高、左右内边距各 227px 而超过列宽（308px），文字宽度变成 0 完全不可见。
        float pxPerMm = ScreenUtils.pxPerMm(view.getContext());
        applyItemSize(holder.itemView, pxPerMm, 30f);
        // 手机版：胶囊背景也在代码里生成（圆角 6 单位、描边 1 单位），
        // 不再依赖 button_danmu_setting 的 mm 换算与资源缓存
        view.setBackground(ScreenUtils.createDialogControlBackground(view.getContext(), pxPerMm));
        view.setMinimumHeight(Math.round(pxPerMm * 30f));
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, pxPerMm * 12f);
        int padH = Math.round(pxPerMm * 12f);
        int padV = Math.round(pxPerMm * 6f);
        view.setPadding(padH, padV, padH, padV);
        if (position == select) {
            view.setTextColor(0xff02f8e1);
            view .setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        }else {
            view.setTextColor(Color.WHITE);
            view .setTypeface(Typeface.defaultFromStyle(Typeface.NORMAL));
        }
        view.setText(name);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (position == select)
                    return;
                notifyItemChanged(select);
                select = position;
                notifyItemChanged(select);
                dialogInterface.click(value, position);
            }
        });
    }

    /**
     * 手机版：把条目尺寸固定为"宽铺满 + 设计高度"。
     * 条目在 wrap_content 状态下有可能被测成整个列表高度（内部背景方框被拉得很高、文字被挤出可视区域），
     * 绑定后直接给出明确的 LayoutParams，测量时即按固定高度处理，不受父级测量结果影响。
     */
    private void applyItemSize(View item, float pxPerMm, float heightMm) {
        int height = Math.round(pxPerMm * heightMm);
        if (height <= 0) return;
        ViewGroup.LayoutParams params = item.getLayoutParams();
        if (params instanceof RecyclerView.LayoutParams) {
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) params;
            if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT && lp.height == height) {
                return;
            }
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = height;
            item.setLayoutParams(lp);
        } else {
            item.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        }
    }
}
