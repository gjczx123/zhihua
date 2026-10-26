package com.zhihua.ledger.core;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;

import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.Db;
import com.zhihua.ledger.data.Transaction;

/** 从系统短信里补录近 N 天建行 95533 消费短信。只在用户主动点击时运行。 */
public final class SmsImporter {
    public static final int NO_PERMISSION = -2;

    private SmsImporter() {
    }

    public static int importRecent(Context context, int days) {
        if (!PermissionUtils.readSmsPermission(context)) return NO_PERMISSION;
        Db db = com.zhihua.ledger.App.get(context).db();
        long from = System.currentTimeMillis() - Math.max(1, days) * 24L * 3600 * 1000;
        Uri uri = Telephony.Sms.Inbox.CONTENT_URI;
        Cursor c = context.getContentResolver().query(uri,
                new String[]{"_id", "address", "body", "date"},
                "date>=?", new String[]{String.valueOf(from)}, "date DESC");
        if (c == null) return 0;
        int inserted = 0;
        int checked = 0;
        try {
            while (c.moveToNext() && checked < 800) {
                checked++;
                String address = c.getString(1);
                String body = c.getString(2);
                long date = c.getLong(3);
                if (address == null) address = "";
                if (body == null || body.length() == 0) continue;
                if (!NotificationParser.looksLikeBankSms(address, body)) continue;
                NotificationParser.Parsed p = NotificationParser.parse("银行短信", body,
                        "sms:" + address, date);
                if (p == null) continue;
                if (db.existsSimilar(p.amountCents, p.occurredAt, 120_000L)) continue;

                Transaction t = new Transaction();
                t.amountCents = p.amountCents;
                t.merchant = p.merchant;
                t.platform = p.platform;
                t.occurredAt = p.occurredAt;
                t.cardTail = p.cardTail;
                t.balanceCents = p.balanceCents;
                t.source = "sms";
                t.rawText = p.rawText;
                t.fingerprint = p.fingerprint;
                String learned = db.learnedCategory(t.merchant);
                Categorizer.Result r = Categorizer.categorize(t.merchant, t.platform, t.rawText, learned);
                t.category = r.category;
                t.needsCategory = r.confidence < 0.55 || Category.OTHER.equals(r.category) ? 1 : 0;
                if (db.insertTransaction(t) > 0) inserted++;
            }
        } finally {
            c.close();
        }
        return inserted;
    }
}
