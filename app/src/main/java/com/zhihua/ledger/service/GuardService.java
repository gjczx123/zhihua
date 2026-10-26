package com.zhihua.ledger.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.zhihua.ledger.App;
import com.zhihua.ledger.MainActivity;
import com.zhihua.ledger.R;
import com.zhihua.ledger.core.PermissionUtils;

/**
 * 可选的前台守护服务：降低无障碍/通知监听所在进程被系统回收的概率。
 * 不能代替系统的无障碍开关，也不能保证在所有 OEM 上不被杀。
 */
public class GuardService extends Service {
    public static final String CHANNEL = "guard";
    private static final int NOTIFICATION_ID = 1101;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;

    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            boolean a11y = PermissionUtils.accessibility(GuardService.this);
            boolean notify = PermissionUtils.notificationAccess(GuardService.this);
            updateNotification(a11y, notify);
            handler.postDelayed(this, 60_000L);
        }
    };

    public static void start(Context context) {
        if (!App.get(context).prefs().backgroundGuard()) return;
        try {
            Intent i = new Intent(context, GuardService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i);
            } else {
                context.startService(i);
            }
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, GuardService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        createChannel();
        if (!safeStartForeground()) {
            stopSelf();
            return;
        }
        handler.postDelayed(check, 2_000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        if (!safeStartForeground()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        handler.removeCallbacks(check);
        handler.postDelayed(check, 2_000L);
        return START_STICKY;
    }

    private boolean safeStartForeground() {
        try {
            startForeground(NOTIFICATION_ID, buildNotification(
                    PermissionUtils.accessibility(this),
                    PermissionUtils.notificationAccess(this)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "后台守护",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("让付款提醒和自动记账更稳定");
        ch.setShowBadge(false);
        ch.enableVibration(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(boolean a11y, boolean notify) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("tab", 3);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 12, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean ok = a11y && notify;
        String title = ok ? "知花正在守护付款提醒" : "付款提醒/自动记账需要恢复";
        String text = ok ? "无障碍与通知读取正常；点此查看设置"
                : "有一项权限被系统关闭，点此重新开启";
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(boolean a11y, boolean notify) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIFICATION_ID, buildNotification(a11y, notify));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
