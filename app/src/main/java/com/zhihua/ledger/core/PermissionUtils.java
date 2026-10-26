package com.zhihua.ledger.core;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;

import com.zhihua.ledger.service.ImpulseAccessibilityService;

/** 权限与小米 HyperOS 设置入口。 */
public final class PermissionUtils {
    private PermissionUtils() {
    }

    public static boolean notificationAccess(Context c) {
        String flat = Settings.Secure.getString(c.getContentResolver(),
                "enabled_notification_listeners");
        return flat != null && flat.contains(c.getPackageName());
    }

    public static boolean accessibility(Context c) {
        String flat = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(flat)) return false;
        String target = new ComponentName(c, ImpulseAccessibilityService.class).flattenToString();
        String shortTarget = c.getPackageName() + "/." + ImpulseAccessibilityService.class.getSimpleName();
        for (String s : flat.split(":")) {
            if (s.equalsIgnoreCase(target) || s.equalsIgnoreCase(shortTarget)) return true;
        }
        return flat.contains(c.getPackageName() + "/" + ImpulseAccessibilityService.class.getName());
    }

    public static boolean smsPermission(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                && c.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean readSmsPermission(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean batteryOptimized(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && !pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    public static boolean isMiui() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    public static String brandName() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        if (m.length() == 0) return "Android";
        return m.substring(0, 1).toUpperCase() + m.substring(1);
    }

    public static boolean isHuawei() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        return m.contains("huawei") || m.contains("honor");
    }

    public static boolean isOppo() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        return m.contains("oppo") || m.contains("realme") || m.contains("oneplus");
    }

    public static boolean isVivo() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        return m.contains("vivo") || m.contains("iqoo");
    }

    public static void openNotificationSettings(Context c) {
        try {
            c.startActivity(new Intent("android.settings.NOTIFICATION_LISTENER_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
    }

    public static void openAccessibilitySettings(Context c) {
        try {
            c.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    public static void openAppDetails(Context c) {
        try {
            c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    public static void openBatterySettings(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            try {
                c.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean openMiuiAutoStart(Context c) {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 尝试打开各品牌的自启动/后台管理页，失败时打开应用详情。 */
    public static boolean openAutoStart(Context c) {
        String[][] candidates = {
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
                {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"}
        };
        for (String[] c0 : candidates) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(c0[0], c0[1]));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            } catch (Exception ignored) {
            }
        }
        openAppDetails(c);
        return false;
    }

    public static void requestSms(Activity a, int code) {
        if (Build.VERSION.SDK_INT >= 23) {
            a.requestPermissions(new String[]{android.Manifest.permission.READ_SMS,
                    android.Manifest.permission.RECEIVE_SMS}, code);
        }
    }

    public static void requestPostNotifications(Activity a, int code) {
        if (Build.VERSION.SDK_INT >= 33) {
            a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, code);
        }
    }
}
