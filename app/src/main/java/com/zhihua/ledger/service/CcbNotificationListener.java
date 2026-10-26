package com.zhihua.ledger.service;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import com.zhihua.ledger.App;
import com.zhihua.ledger.core.Categorizer;
import com.zhihua.ledger.core.NotificationParser;
import com.zhihua.ledger.data.Db;
import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.Impulse;
import com.zhihua.ledger.data.PayLater;
import com.zhihua.ledger.data.Transaction;

import java.util.ArrayList;
import java.util.List;

/** 监听建行 App 通知，自动落账。 */
public class CcbNotificationListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        final String pkg = sbn.getPackageName();
        if (getPackageName().equals(pkg)) return;

        Bundle extras = sbn.getNotification().extras;
        final String title = str(extras.getCharSequence(Notification.EXTRA_TITLE));
        final String text = extractText(extras);
        final long postTime = sbn.getPostTime() > 0 ? sbn.getPostTime() : System.currentTimeMillis();

        NotificationParser.Parsed bankParsed = null;
        try {
            bankParsed = NotificationParser.parse(title, text, pkg, postTime);
        } catch (Exception ignored) {
        }

        final NotificationParser.Parsed parsed;
        final String source;
        if (bankParsed != null) {
            parsed = bankParsed;
            source = "notification";
        } else if (isPaymentApp(pkg) && paymentNotifyEnabled(pkg)) {
            NotificationParser.Parsed pay = null;
            try {
                pay = NotificationParser.parsePaymentApp(title, text, pkg, postTime);
            } catch (Exception ignored) {
            }
            if (pay == null) {
                if (NotificationParser.looksPaymentAppLike(title, text, pkg)) {
                    App.get().io().execute(new Runnable() {
                        @Override
                        public void run() {
                            App.get().db().logCapture(pkg, title, text, false);
                        }
                    });
                }
                return;
            }
            parsed = pay;
            source = "com.tencent.mm".equals(pkg) ? "wechat" : "alipay";
        } else {
            if (NotificationParser.looksBankLike(title, text, pkg)) {
                App.get().io().execute(new Runnable() {
                    @Override
                    public void run() {
                        App.get().db().logCapture(pkg, title, text, false);
                    }
                });
            }
            return;
        }

        App.get().io().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Db db = App.get().db();
                    db.logCapture(pkg, title, text, true);
                    if (db.existsSimilar(parsed.amountCents, parsed.occurredAt, 90_000L)) {
                        return;
                    }
                    Impulse impulse = db.findRecentImpulse(parsed.amountCents,
                            System.currentTimeMillis(), 20L * 60 * 1000);
                    PayLater payLater = db.findPendingPayLater(parsed.amountCents, parsed.merchant,
                            System.currentTimeMillis(), 30L * 24 * 3600 * 1000);
                    String learned = db.learnedCategory(parsed.merchant);
                    Categorizer.Result result = Categorizer.categorize(
                            parsed.merchant, parsed.platform, parsed.rawText, learned);

                    Transaction t = new Transaction();
                    t.amountCents = parsed.amountCents;
                    t.merchant = parsed.merchant;
                    t.platform = parsed.platform;
                    t.category = impulse != null ? impulse.category
                            : payLater != null ? payLater.category : result.category;
                    t.occurredAt = parsed.occurredAt;
                    t.cardTail = parsed.cardTail;
                    t.balanceCents = parsed.balanceCents;
                    t.source = source;
                    t.rawText = parsed.rawText;
                    t.fingerprint = parsed.fingerprint;
                    if (impulse != null) {
                        t.impulse = Impulse.ABANDONED.equals(impulse.decision) ? 2 : 1;
                        t.needsCategory = 0;
                    } else if (payLater != null) {
                        t.needsCategory = Category.OTHER.equals(payLater.category) ? 1 : 0;
                    } else {
                        t.needsCategory = result.confidence < 0.55
                                || Category.OTHER.equals(result.category) ? 1 : 0;
                    }
                    long id = db.insertTransaction(t);
                    if (id > 0) {
                        if (impulse != null) db.linkImpulse(impulse.id, id);
                        if (payLater != null) db.markPayLaterPaid(payLater.id, id);
                        final Transaction saved = t;
                        App.get().main().post(new Runnable() {
                            @Override
                            public void run() {
                                boolean shown = saved.needsCategory == 1
                                        && ImpulseAccessibilityService.promptCategory(saved);
                                if (!shown) App.get().notifyCaptured(saved);
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static boolean isPaymentApp(String pkg) {
        return "com.tencent.mm".equals(pkg) || "com.eg.android.AlipayGphone".equals(pkg);
    }

    private static boolean paymentNotifyEnabled(String pkg) {
        if ("com.tencent.mm".equals(pkg)) return App.get().prefs().wechatPaymentNotify();
        return App.get().prefs().alipayPaymentNotify();
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    private static String extractText(Bundle extras) {
        List<String> parts = new ArrayList<>();
        add(parts, extras.getCharSequence(Notification.EXTRA_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_INFO_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));

        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) add(parts, line);
        }

        try {
            Object[] messages = (Object[]) extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (messages != null) {
                for (Object o : messages) {
                    if (o instanceof Bundle) {
                        Bundle b = (Bundle) o;
                        add(parts, b.getCharSequence("text"));
                        add(parts, b.getCharSequence("message"));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (TextUtils.isEmpty(p)) continue;
            if (sb.indexOf(p) >= 0) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(p);
        }
        return sb.toString();
    }

    private static void add(List<String> parts, CharSequence cs) {
        if (cs != null) {
            String s = cs.toString().trim();
            if (s.length() > 0) parts.add(s);
        }
    }
}
