package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.receiver.PushReceiver;

import org.jetbrains.annotations.NotNull;

/**
 * 手机版推送弹窗：替代原横屏二维码网页推送。
 * 输入播放地址后交给 PushReceiver，走与网页推送一致的
 * “push_agent 源 → 详情页 → 播放”流程。
 */
public class PushDialog extends BaseDialog {
    private EditText inputPushUrl;

    public PushDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_push);
        setCanceledOnTouchOutside(true);
        inputPushUrl = findViewById(R.id.etPushUrl);
        findViewById(R.id.tvPushSubmit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                push();
            }
        });
        inputPushUrl.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    push();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void show() {
        super.show();
        if (!isShowing()) return;
        Window window = getWindow();
        if (window != null) {
            // 键盘弹起时压缩弹窗，避免遮挡输入框
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        inputPushUrl.requestFocus();
    }

    private void push() {
        String url = inputPushUrl.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(getContext(), "请输入播放地址", Toast.LENGTH_SHORT).show();
            return;
        }
        PushReceiver.send(getContext(), url);
        dismiss();
    }
}
