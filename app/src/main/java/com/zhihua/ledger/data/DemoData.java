package com.zhihua.ledger.data;

import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.Db;
import com.zhihua.ledger.data.Impulse;
import com.zhihua.ledger.data.Transaction;

import java.util.Calendar;
import java.util.Locale;

/** 一键载入的示例数据，用来预览图表；设置里可一键清空。 */
public final class DemoData {
    private DemoData() {
    }

    public static int insert(Db db) {
        if (db.countDemo() > 0) return -1;
        int n = 0;
        long now = System.currentTimeMillis();
        for (int m = 0; m < 6; m++) {
            double f = 1.0 + m * 0.06;
            n += add(db, at(m, 1, 9, 0), (long) (180000 * f), "房租", "", Category.HOME);
            n += add(db, at(m, 1, 10, 12), (long) (9860 * f), "电费", "", Category.HOME);
            n += add(db, at(m, 3, 12, 20), (long) (3250 * f), "美团外卖", "美团", Category.FOOD);
            n += add(db, at(m, 4, 19, 30), (long) (29900 * f), "优衣库", "", Category.CLOTHES);
            n += add(db, at(m, 5, 21, 10), (long) (8900 * f), "拼多多", "拼多多", Category.SHOPPING);
            n += add(db, at(m, 6, 19, 0), (long) (5800 * f), "美团外卖", "美团", Category.FOOD);
            n += add(db, at(m, 7, 8, 40), (long) (2650 * f), "滴滴出行", "滴滴", Category.TRANSPORT);
            n += add(db, at(m, 8, 21, 0), (long) (12900 * f), "拼多多", "拼多多", Category.SHOPPING);
            n += add(db, at(m, 9, 15, 10), (long) (3500 * f), "星巴克", "", Category.FOOD);
            n += add(db, at(m, 10, 20, 0), (long) (39900 * f), "优衣库", "", Category.CLOTHES);
            n += add(db, at(m, 11, 20, 0), (long) (15680 * f), "盒马鲜生", "盒马", Category.DAILY);
            n += add(db, at(m, 13, 23, 20), (long) (12900 * f), "抖音商城", "抖音", Category.SHOPPING);
            n += add(db, at(m, 14, 20, 0), (long) (4500 * f), "万达影城", "", Category.FUN);
            n += add(db, at(m, 15, 10, 30), (long) (3850 * f), "益丰大药房", "", Category.HEALTH);
            n += add(db, at(m, 17, 14, 0), (long) (6800 * f), "西西弗书店", "", Category.STUDY);
            n += add(db, at(m, 12, 18, 30), (long) (20000 * f), "朋友生日红包", "", Category.SOCIAL);
            n += add(db, at(m, 16, 23, 40), (long) (5990 * f), "拼多多", "拼多多", Category.SHOPPING);
            n += add(db, at(m, 18, 16, 20), (long) (1800 * f), "瑞幸咖啡", "", Category.FOOD);
            n += add(db, at(m, 19, 10, 0), (long) (14800 * f), "OpenAI", "", Category.TECH);
            n += add(db, at(m, 21, 9, 30), (long) (9900 * f), "阿里云", "", Category.TECH);
        }

        n += impulse(db, now - 3L * 24 * 3600 * 1000, 19900, "拼多多",
                "com.xunmeng.pinduoduo", Impulse.ABANDONED, Category.SHOPPING);
        n += impulse(db, now - 8L * 24 * 3600 * 1000, 8900, "抖音",
                "com.ss.android.ugc.aweme", Impulse.ABANDONED, Category.SHOPPING);
        n += impulse(db, now - 12L * 24 * 3600 * 1000, 5900, "拼多多",
                "com.xunmeng.pinduoduo", Impulse.ABANDONED, Category.SHOPPING);
        n += impulse(db, now - 15L * 24 * 3600 * 1000, 12900, "抖音",
                "com.ss.android.ugc.aweme", Impulse.CONTINUED, Category.SHOPPING);
        return n;
    }

    private static int add(Db db, long time, long amount, String merchant, String platform,
                           String category) {
        if (time <= 0) return 0;
        Transaction t = new Transaction();
        t.amountCents = amount;
        t.merchant = merchant;
        t.platform = platform;
        t.category = category;
        t.occurredAt = time;
        t.source = "demo";
        t.rawText = "示例数据，可在设置中一键清除";
        t.createdAt = System.currentTimeMillis();
        return db.insertTransaction(t) > 0 ? 1 : 0;
    }

    private static int impulse(Db db, long time, long amount, String label, String pkg,
                               String decision, String category) {
        Impulse i = new Impulse();
        i.packageName = pkg;
        i.appLabel = label;
        i.amountCents = amount;
        i.category = category;
        i.decision = decision;
        i.createdAt = time;
        i.note = "示例数据";
        return db.insertImpulse(i) > 0 ? 1 : 0;
    }

    private static long at(int monthsAgo, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(Locale.CHINA);
        c.add(Calendar.MONTH, -monthsAgo);
        c.set(Calendar.DAY_OF_MONTH, 1);
        int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        if (day > max) day = max;
        c.set(Calendar.DAY_OF_MONTH, day);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long t = c.getTimeInMillis();
        if (t > System.currentTimeMillis()) return -1;
        return t;
    }
}
