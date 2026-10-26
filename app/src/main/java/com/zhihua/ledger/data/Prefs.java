package com.zhihua.ledger.data;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String NAME = "zhihua_prefs";
    private static final String KEY_BUDGET = "monthly_budget";
    private static final String KEY_SMS = "sms_enabled";
    private static final String KEY_NOTIFY_CAPTURE = "capture_notify";
    private static final String KEY_ONBOARDING = "onboarding_done";
    private static final String KEY_PAUSE = "pause_seconds";
    private static final String KEY_MONITOR_PREFIX = "monitor_";
    private static final String KEY_SCAN_PROMPT = "scan_prompt";
    private static final String KEY_WECHAT_NOTIFY = "wechat_payment_notify";
    private static final String KEY_ALIPAY_NOTIFY = "alipay_payment_notify";
    private static final String KEY_LAST_TRIGGER = "last_trigger";
    private static final String KEY_OVERLAY_TEST = "overlay_test";
    private static final String KEY_GUARD = "background_guard";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public long budget() {
        return sp.getLong(KEY_BUDGET, 0);
    }

    public void setBudget(long cents) {
        sp.edit().putLong(KEY_BUDGET, Math.max(0, cents)).apply();
    }

    public boolean smsEnabled() {
        return sp.getBoolean(KEY_SMS, false);
    }

    public void setSmsEnabled(boolean v) {
        sp.edit().putBoolean(KEY_SMS, v).apply();
    }

    public boolean captureNotify() {
        return sp.getBoolean(KEY_NOTIFY_CAPTURE, true);
    }

    public void setCaptureNotify(boolean v) {
        sp.edit().putBoolean(KEY_NOTIFY_CAPTURE, v).apply();
    }

    public boolean onboardingDone() {
        return sp.getBoolean(KEY_ONBOARDING, false);
    }

    public void setOnboardingDone() {
        sp.edit().putBoolean(KEY_ONBOARDING, true).apply();
    }

    public int pauseSeconds() {
        return sp.getInt(KEY_PAUSE, 15);
    }

    public void setPauseSeconds(int s) {
        sp.edit().putInt(KEY_PAUSE, Math.max(3, Math.min(60, s))).apply();
    }

    public boolean monitor(String pkg) {
        return sp.getBoolean(KEY_MONITOR_PREFIX + pkg, defaultMonitor(pkg));
    }

    public void setMonitor(String pkg, boolean v) {
        sp.edit().putBoolean(KEY_MONITOR_PREFIX + pkg, v).apply();
    }

    private boolean defaultMonitor(String pkg) {
        return "com.xunmeng.pinduoduo".equals(pkg)
                || "com.ss.android.ugc.aweme".equals(pkg)
                || "com.taobao.taobao".equals(pkg)
                || "com.jingdong.app.mall".equals(pkg)
                || "com.tencent.mm".equals(pkg)
                || "com.eg.android.AlipayGphone".equals(pkg)
                || "com.sankuai.meituan".equals(pkg)
                || "me.ele".equals(pkg);
    }

    public boolean scanPrompt() {
        return sp.getBoolean(KEY_SCAN_PROMPT, true);
    }

    public void setScanPrompt(boolean v) {
        sp.edit().putBoolean(KEY_SCAN_PROMPT, v).apply();
    }

    public boolean wechatPaymentNotify() {
        return sp.getBoolean(KEY_WECHAT_NOTIFY, true);
    }

    public void setWechatPaymentNotify(boolean v) {
        sp.edit().putBoolean(KEY_WECHAT_NOTIFY, v).apply();
    }

    public boolean alipayPaymentNotify() {
        return sp.getBoolean(KEY_ALIPAY_NOTIFY, true);
    }

    public void setAlipayPaymentNotify(boolean v) {
        sp.edit().putBoolean(KEY_ALIPAY_NOTIFY, v).apply();
    }

    public long lastTrigger() {
        return sp.getLong(KEY_LAST_TRIGGER, 0);
    }

    public void setLastTrigger(long v) {
        sp.edit().putLong(KEY_LAST_TRIGGER, v).apply();
    }

    public boolean overlayTestHint() {
        return sp.getBoolean(KEY_OVERLAY_TEST, true);
    }

    public boolean backgroundGuard() {
        return sp.getBoolean(KEY_GUARD, true);
    }

    public void setBackgroundGuard(boolean v) {
        sp.edit().putBoolean(KEY_GUARD, v).apply();
    }

    public void setOverlayTestHint(boolean v) {
        sp.edit().putBoolean(KEY_OVERLAY_TEST, v).apply();
    }
}
