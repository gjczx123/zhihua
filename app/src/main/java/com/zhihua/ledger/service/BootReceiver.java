package com.zhihua.ledger.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.zhihua.ledger.App;

/** 开机后恢复后台守护。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (App.get(context).prefs().backgroundGuard()) {
            GuardService.start(context);
        }
    }
}
