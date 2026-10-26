package com.zhihua.ledger.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import com.zhihua.ledger.App;
import com.zhihua.ledger.core.Categorizer;
import com.zhihua.ledger.core.NotificationParser;
import com.zhihua.ledger.data.Db;
import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.Impulse;
import com.zhihua.ledger.data.PayLater;
import com.zhihua.ledger.data.Transaction;

/** 可选：读取 95533 建行短信作为通知的兜底。默认关闭。 */
public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        final App app = App.get(context);
        if (!app.prefs().smsEnabled()) return;
        SmsMessage[] messages;
        try {
            messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        } catch (Exception e) {
            return;
        }
        if (messages == null || messages.length == 0) return;

        final StringBuilder body = new StringBuilder();
        String address = "";
        long time = System.currentTimeMillis();
        for (SmsMessage m : messages) {
            if (m == null) continue;
            if (address.length() == 0) address = m.getOriginatingAddress();
            if (m.getTimestampMillis() > 0) time = m.getTimestampMillis();
            String part = m.getMessageBody();
            if (part != null) body.append(part);
        }
        if (!NotificationParser.looksLikeBankSms(address, body.toString())) {
            return;
        }

        final String sender = address;
        final String text = body.toString();
        final long smsTime = time;
        final PendingResult pending = goAsync();
        app.io().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    NotificationParser.Parsed p = NotificationParser.parse("建行短信", text,
                            "sms:" + sender, smsTime);
                    if (p != null) {
                        Db db = app.db();
                        if (!db.existsSimilar(p.amountCents, p.occurredAt, 90_000L)) {
                            Impulse impulse = db.findRecentImpulse(p.amountCents,
                                    System.currentTimeMillis(), 20L * 60 * 1000);
                            PayLater payLater = db.findPendingPayLater(p.amountCents, p.merchant,
                                    System.currentTimeMillis(), 30L * 24 * 3600 * 1000);
                            Categorizer.Result r = Categorizer.categorize(p.merchant, p.platform,
                                    p.rawText, db.learnedCategory(p.merchant));
                            Transaction t = new Transaction();
                            t.amountCents = p.amountCents;
                            t.merchant = p.merchant;
                            t.platform = p.platform;
                            t.category = impulse != null ? impulse.category
                                    : payLater != null ? payLater.category : r.category;
                            t.occurredAt = p.occurredAt;
                            t.cardTail = p.cardTail;
                            t.balanceCents = p.balanceCents;
                            t.source = "sms";
                            t.rawText = p.rawText;
                            t.fingerprint = p.fingerprint;
                            if (impulse != null) {
                                t.impulse = Impulse.ABANDONED.equals(impulse.decision) ? 2 : 1;
                                t.needsCategory = 0;
                            } else if (payLater != null) {
                                t.needsCategory = Category.OTHER.equals(payLater.category) ? 1 : 0;
                            } else {
                                t.needsCategory = r.confidence < 0.55
                                        || Category.OTHER.equals(r.category) ? 1 : 0;
                            }
                            long id = db.insertTransaction(t);
                            if (id > 0) {
                                if (impulse != null) db.linkImpulse(impulse.id, id);
                                if (payLater != null) db.markPayLaterPaid(payLater.id, id);
                                final Transaction saved = t;
                                app.main().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        boolean shown = saved.needsCategory == 1
                                                && ImpulseAccessibilityService.promptCategory(saved);
                                        if (!shown) app.notifyCaptured(saved);
                                    }
                                });
                            }
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    try {
                        pending.finish();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }
}
