package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.server.ControlManager;

import org.jetbrains.annotations.NotNull;

public class RemoteDialog extends BaseDialog {
    private TextView tvAddress;

    public RemoteDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_remote);
        setCanceledOnTouchOutside(false);
        tvAddress = findViewById(R.id.tvAddress);
        refreshAddress();
    }

    private void refreshAddress() {
        String address = ControlManager.get().getAddress(false);
        tvAddress.setText(String.format("手机/电脑浏览器访问以下地址进行网页遥控\n%s", address));
    }
}
