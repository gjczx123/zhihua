package com.zhihua.ledger;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.Db;
import com.zhihua.ledger.data.Money;
import com.zhihua.ledger.data.Prefs;
import com.zhihua.ledger.data.Transaction;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {
    public interface DataListener {
        void onDataChanged();
    }

    private static App instance;
    private Db db;
    private Prefs prefs;
    private ExecutorService io;
    private Handler main;
    private final List<DataListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = new Prefs(this);
        db = new Db(this);
        io = Executors.newSingleThreadExecutor();
        main = new Handler(Looper.getMainLooper());
        createChannel();
    }

    public static App get() {
        return instance;
    }

    public static App get(Context context) {
        return (App) context.getApplicationContext();
    }

    public Db db() {
        return db;
    }

    public Prefs prefs() {
        return prefs;
    }

    public ExecutorService io() {
        return io;
    }

    public Handler main() {
        return main;
    }

    public void addDataListener(DataListener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeDataListener(DataListener l) {
        listeners.remove(l);
    }

    public void notifyDataChanged() {
        main.post(new Runnable() {
            @Override
            public void run() {
                for (DataListener l : listeners) {
                    try {
                        l.onDataChanged();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel("capture", "自动记账",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("建行消费入账后的轻提示");
        ch.setShowBadge(false);
        ch.enableVibration(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);

        NotificationChannel pending = new NotificationChannel("pending", "待分类提醒",
                NotificationManager.IMPORTANCE_DEFAULT);
        pending.setDescription("自动记账后需要你选择分类");
        pending.setShowBadge(true);
        pending.setSound(null, null);
        nm.createNotificationChannel(pending);
    }

    public void notifyCaptured(Transaction t) {
        if (!prefs.captureNotify()) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (!nm.areNotificationsEnabled()) return;
        boolean pending = t.needsCategory == 1;
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("tab", 2);
        if (pending) i.putExtra("pending_tx_id", t.id);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, pending ? 11 : 10, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, pending ? "pending" : "capture")
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFF1F6B57)
                .setContentTitle(pending
                        ? "已记账 · " + Money.yuan(t.amountCents) + " · 待分类"
                        : "已记账 · " + Money.yuan(t.amountCents))
                .setContentText(pending
                        ? "点此选择分类：" + t.merchant
                        : t.merchant + " · " + Category.name(t.category))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true);
        nm.notify((int) (System.currentTimeMillis() % 100000), b.build());
    }
}
