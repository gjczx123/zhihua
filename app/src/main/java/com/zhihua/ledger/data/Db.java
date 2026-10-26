package com.zhihua.ledger.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.zhihua.ledger.App;
import com.zhihua.ledger.core.NotificationParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 本地 SQLite 数据库。没有网络权限，所有账单只留在手机上。 */
public class Db extends SQLiteOpenHelper {
    private static final String NAME = "zhihua.db";
    private static final int VERSION = 3;

    public Db(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE transactions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "amount_cents INTEGER NOT NULL,"
                + "merchant TEXT NOT NULL DEFAULT '',"
                + "platform TEXT NOT NULL DEFAULT '',"
                + "category TEXT NOT NULL DEFAULT 'other',"
                + "occurred_at INTEGER NOT NULL,"
                + "card_tail TEXT NOT NULL DEFAULT '',"
                + "balance_cents INTEGER NOT NULL DEFAULT -1,"
                + "source TEXT NOT NULL DEFAULT 'manual',"
                + "raw_text TEXT NOT NULL DEFAULT '',"
                + "note TEXT NOT NULL DEFAULT '',"
                + "impulse INTEGER NOT NULL DEFAULT 0,"
                + "needs_category INTEGER NOT NULL DEFAULT 0,"
                + "fingerprint TEXT,"
                + "created_at INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE INDEX idx_tx_time ON transactions(occurred_at)");
        db.execSQL("CREATE INDEX idx_tx_category ON transactions(category)");
        db.execSQL("CREATE UNIQUE INDEX idx_tx_fingerprint ON transactions(fingerprint)");

        db.execSQL("CREATE TABLE rules ("
                + "merchant_key TEXT PRIMARY KEY,"
                + "category TEXT NOT NULL,"
                + "updated_at INTEGER NOT NULL"
                + ")");

        db.execSQL("CREATE TABLE impulses ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "package_name TEXT NOT NULL DEFAULT '',"
                + "app_label TEXT NOT NULL DEFAULT '',"
                + "amount_cents INTEGER NOT NULL DEFAULT 0,"
                + "category TEXT NOT NULL DEFAULT 'shopping',"
                + "decision TEXT NOT NULL DEFAULT 'abandoned',"
                + "created_at INTEGER NOT NULL,"
                + "transaction_id INTEGER NOT NULL DEFAULT 0,"
                + "note TEXT NOT NULL DEFAULT ''"
                + ")");
        db.execSQL("CREATE INDEX idx_impulse_time ON impulses(created_at)");

        db.execSQL("CREATE TABLE raw_captures ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "package_name TEXT NOT NULL DEFAULT '',"
                + "title TEXT NOT NULL DEFAULT '',"
                + "text TEXT NOT NULL DEFAULT '',"
                + "at INTEGER NOT NULL,"
                + "parsed INTEGER NOT NULL DEFAULT 0"
                + ")");

        createPayLaterTable(db);
    }

    private static void createPayLaterTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS pay_later ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "amount_cents INTEGER NOT NULL DEFAULT 0,"
                + "merchant TEXT NOT NULL DEFAULT '',"
                + "platform TEXT NOT NULL DEFAULT '',"
                + "category TEXT NOT NULL DEFAULT 'other',"
                + "app_label TEXT NOT NULL DEFAULT '',"
                + "created_at INTEGER NOT NULL,"
                + "status TEXT NOT NULL DEFAULT 'pending',"
                + "transaction_id INTEGER NOT NULL DEFAULT 0,"
                + "note TEXT NOT NULL DEFAULT ''"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_pay_later_status ON pay_later(status, created_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN needs_category INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) {
            createPayLaterTable(db);
        }
    }

    public long insertTransaction(Transaction t) {
        ContentValues v = values(t);
        if (t.fingerprint == null || t.fingerprint.length() == 0) v.putNull("fingerprint");
        if (t.createdAt == 0) v.put("created_at", System.currentTimeMillis());
        long id = getWritableDatabase().insertWithOnConflict("transactions", null, v,
                SQLiteDatabase.CONFLICT_IGNORE);
        if (id > 0) {
            t.id = id;
            notifyChanged();
        }
        return id;
    }

    public boolean existsSimilar(long amountCents, long occurredAt, long windowMs) {
        Cursor c = getReadableDatabase().query("transactions", new String[]{"id"},
                "amount_cents=? AND occurred_at BETWEEN ? AND ?",
                new String[]{String.valueOf(amountCents),
                        String.valueOf(occurredAt - windowMs),
                        String.valueOf(occurredAt + windowMs)},
                null, null, null, "1");
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public List<Transaction> listBetween(long start, long end) {
        return queryList("occurred_at>=? AND occurred_at<?",
                new String[]{String.valueOf(start), String.valueOf(end)},
                "occurred_at DESC");
    }

    public List<Transaction> listRecent(int limit) {
        return queryList(null, null, "occurred_at DESC", String.valueOf(limit));
    }

    public List<Transaction> listAll() {
        return queryList(null, null, "occurred_at DESC");
    }

    public long totalBetween(long start, long end) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount_cents),0) FROM transactions WHERE occurred_at>=? AND occurred_at<?",
                new String[]{String.valueOf(start), String.valueOf(end)});
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    public int countBetween(long start, long end) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM transactions WHERE occurred_at>=? AND occurred_at<?",
                new String[]{String.valueOf(start), String.valueOf(end)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public int countCategoryBetween(String category, long start, long end) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM transactions WHERE category=? AND occurred_at>=? AND occurred_at<?",
                new String[]{category, String.valueOf(start), String.valueOf(end)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public Map<String, Long> categoryTotals(long start, long end) {
        Map<String, Long> out = new HashMap<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT category, SUM(amount_cents) FROM transactions "
                        + "WHERE occurred_at>=? AND occurred_at<? GROUP BY category",
                new String[]{String.valueOf(start), String.valueOf(end)});
        try {
            while (c.moveToNext()) out.put(c.getString(0), c.getLong(1));
        } finally {
            c.close();
        }
        return out;
    }

    public void updateTransaction(Transaction t) {
        ContentValues v = new ContentValues();
        v.put("amount_cents", t.amountCents);
        v.put("merchant", safe(t.merchant));
        v.put("platform", safe(t.platform));
        v.put("category", Category.valid(t.category) ? t.category : Category.OTHER);
        v.put("occurred_at", t.occurredAt);
        v.put("card_tail", safe(t.cardTail));
        v.put("balance_cents", t.balanceCents);
        v.put("note", safe(t.note));
        v.put("impulse", t.impulse);
        v.put("needs_category", t.needsCategory);
        getWritableDatabase().update("transactions", v, "id=?",
                new String[]{String.valueOf(t.id)});
        notifyChanged();
    }

    public void updateCategory(long id, String category) {
        ContentValues v = new ContentValues();
        v.put("category", Category.valid(category) ? category : Category.OTHER);
        v.put("needs_category", 0);
        getWritableDatabase().update("transactions", v, "id=?", new String[]{String.valueOf(id)});
        notifyChanged();
    }

    public int countPendingCategory() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM transactions WHERE needs_category=1", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public List<Transaction> listPendingCategory(int limit) {
        return queryList("needs_category=1", null, "occurred_at DESC", String.valueOf(limit));
    }

    public Transaction findById(long id) {
        Cursor c = getReadableDatabase().query("transactions", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null, "1");
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    public void deleteTransaction(long id) {
        getWritableDatabase().delete("transactions", "id=?", new String[]{String.valueOf(id)});
        notifyChanged();
    }

    public void learnRule(String merchant, String category) {
        String key = NotificationParser.normalizeMerchant(merchant);
        if (key.length() == 0 || !Category.valid(category)) return;
        ContentValues v = new ContentValues();
        v.put("merchant_key", key);
        v.put("category", category);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("rules", null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String learnedCategory(String merchant) {
        String key = NotificationParser.normalizeMerchant(merchant);
        if (key.length() == 0) return null;
        Cursor c = getReadableDatabase().query("rules", new String[]{"category"},
                "merchant_key=?", new String[]{key}, null, null, null, "1");
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    public List<MerchantRule> listRules() {
        List<MerchantRule> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("rules", null, null, null, null, null,
                "updated_at DESC");
        try {
            while (c.moveToNext()) {
                MerchantRule r = new MerchantRule();
                r.merchantKey = c.getString(c.getColumnIndexOrThrow("merchant_key"));
                r.category = c.getString(c.getColumnIndexOrThrow("category"));
                r.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
                out.add(r);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public long insertImpulse(Impulse i) {
        ContentValues v = new ContentValues();
        v.put("package_name", safe(i.packageName));
        v.put("app_label", safe(i.appLabel));
        v.put("amount_cents", i.amountCents);
        v.put("category", Category.valid(i.category) ? i.category : Category.SHOPPING);
        v.put("decision", i.decision);
        v.put("created_at", i.createdAt == 0 ? System.currentTimeMillis() : i.createdAt);
        v.put("transaction_id", i.transactionId);
        v.put("note", safe(i.note));
        long id = getWritableDatabase().insert("impulses", null, v);
        if (id > 0) {
            i.id = id;
            notifyChanged();
        }
        return id;
    }

    /** 找支付通知对应的一次付款前确认。优先金额一致，其次金额未知。 */
    public Impulse findRecentImpulse(long amountCents, long at, long windowMs) {
        long from = at - windowMs;
        long to = at + 60_000L;
        Cursor c = getReadableDatabase().query("impulses", null,
                "transaction_id=0 AND decision!='dismissed' AND created_at BETWEEN ? AND ? "
                        + "AND (amount_cents=? OR amount_cents=0)",
                new String[]{String.valueOf(from), String.valueOf(to),
                        String.valueOf(amountCents)},
                null, null, "created_at DESC", "1");
        Impulse found = readImpulse(c);
        if (found != null) return found;
        c = getReadableDatabase().query("impulses", null,
                "transaction_id=0 AND decision!='dismissed' AND created_at BETWEEN ? AND ?",
                new String[]{String.valueOf(from), String.valueOf(to)},
                null, null, "created_at DESC", "1");
        return readImpulse(c);
    }

    private Impulse readImpulse(Cursor c) {
        try {
            if (!c.moveToFirst()) return null;
            Impulse i = new Impulse();
            i.id = c.getLong(c.getColumnIndexOrThrow("id"));
            i.packageName = c.getString(c.getColumnIndexOrThrow("package_name"));
            i.appLabel = c.getString(c.getColumnIndexOrThrow("app_label"));
            i.amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents"));
            i.category = c.getString(c.getColumnIndexOrThrow("category"));
            i.decision = c.getString(c.getColumnIndexOrThrow("decision"));
            i.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
            i.transactionId = c.getLong(c.getColumnIndexOrThrow("transaction_id"));
            i.note = c.getString(c.getColumnIndexOrThrow("note"));
            return i;
        } finally {
            c.close();
        }
    }

    public void linkImpulse(long impulseId, long transactionId) {
        ContentValues v = new ContentValues();
        v.put("transaction_id", transactionId);
        getWritableDatabase().update("impulses", v, "id=?", new String[]{String.valueOf(impulseId)});
    }

    public List<Impulse> listImpulses(long start, long end) {
        List<Impulse> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("impulses", null,
                "created_at>=? AND created_at<?",
                new String[]{String.valueOf(start), String.valueOf(end)},
                null, null, "created_at DESC");
        try {
            while (c.moveToNext()) {
                Impulse i = new Impulse();
                i.id = c.getLong(c.getColumnIndexOrThrow("id"));
                i.packageName = c.getString(c.getColumnIndexOrThrow("package_name"));
                i.appLabel = c.getString(c.getColumnIndexOrThrow("app_label"));
                i.amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents"));
                i.category = c.getString(c.getColumnIndexOrThrow("category"));
                i.decision = c.getString(c.getColumnIndexOrThrow("decision"));
                i.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
                i.transactionId = c.getLong(c.getColumnIndexOrThrow("transaction_id"));
                i.note = c.getString(c.getColumnIndexOrThrow("note"));
                out.add(i);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public void logCapture(String pkg, String title, String text, boolean parsed) {
        ContentValues v = new ContentValues();
        v.put("package_name", safe(pkg));
        v.put("title", safe(title).length() > 300 ? safe(title).substring(0, 300) : safe(title));
        v.put("text", safe(text).length() > 3000 ? safe(text).substring(0, 3000) : safe(text));
        v.put("at", System.currentTimeMillis());
        v.put("parsed", parsed ? 1 : 0);
        getWritableDatabase().insert("raw_captures", null, v);
        getWritableDatabase().execSQL("DELETE FROM raw_captures WHERE id NOT IN "
                + "(SELECT id FROM raw_captures ORDER BY at DESC LIMIT 120)");
    }

    public List<RawCapture> listCaptures(int limit) {
        List<RawCapture> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("raw_captures", null, null, null, null, null,
                "at DESC", String.valueOf(limit));
        try {
            while (c.moveToNext()) {
                RawCapture r = new RawCapture();
                r.id = c.getLong(c.getColumnIndexOrThrow("id"));
                r.packageName = c.getString(c.getColumnIndexOrThrow("package_name"));
                r.title = c.getString(c.getColumnIndexOrThrow("title"));
                r.text = c.getString(c.getColumnIndexOrThrow("text"));
                r.at = c.getLong(c.getColumnIndexOrThrow("at"));
                r.parsed = c.getInt(c.getColumnIndexOrThrow("parsed")) == 1;
                out.add(r);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public int countDemo() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM transactions WHERE source='demo'", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public long insertPayLater(PayLater p) {
        ContentValues v = new ContentValues();
        v.put("amount_cents", p.amountCents);
        v.put("merchant", safe(p.merchant));
        v.put("platform", safe(p.platform));
        v.put("category", Category.valid(p.category) ? p.category : Category.OTHER);
        v.put("app_label", safe(p.appLabel));
        v.put("created_at", p.createdAt == 0 ? System.currentTimeMillis() : p.createdAt);
        v.put("status", PayLater.PENDING);
        v.put("transaction_id", 0);
        v.put("note", safe(p.note));
        long id = getWritableDatabase().insert("pay_later", null, v);
        if (id > 0) {
            p.id = id;
            notifyChanged();
        }
        return id;
    }

    public List<PayLater> listPendingPayLater(int limit) {
        List<PayLater> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("pay_later", null, "status=?",
                new String[]{PayLater.PENDING}, null, null, "created_at DESC",
                String.valueOf(limit));
        try {
            while (c.moveToNext()) out.add(readPayLater(c));
        } finally {
            c.close();
        }
        return out;
    }

    public int countPendingPayLater() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM pay_later WHERE status=?", new String[]{PayLater.PENDING});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public long totalPendingPayLater() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount_cents),0) FROM pay_later WHERE status=?",
                new String[]{PayLater.PENDING});
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    /** 找实际扣款对应的先用后付。优先金额一致，其次收款方一致。 */
    public PayLater findPendingPayLater(long amountCents, String merchant, long at, long windowMs) {
        long from = at - Math.max(windowMs, 24L * 3600 * 1000);
        Cursor c = getReadableDatabase().query("pay_later", null,
                "status=? AND created_at>=? AND amount_cents=?",
                new String[]{PayLater.PENDING, String.valueOf(from), String.valueOf(amountCents)},
                null, null, "created_at DESC", "1");
        PayLater p = readPayLaterOrNull(c);
        if (p != null) return p;
        String key = NotificationParser.normalizeMerchant(merchant);
        if (key.length() > 0) {
            c = getReadableDatabase().query("pay_later", null,
                    "status=? AND created_at>=?",
                    new String[]{PayLater.PENDING, String.valueOf(from)},
                    null, null, "created_at DESC", "40");
            try {
                while (c.moveToNext()) {
                    PayLater q = readPayLater(c);
                    if (NotificationParser.normalizeMerchant(q.merchant).equals(key)) return q;
                }
            } finally {
                c.close();
            }
        }
        return null;
    }

    public void markPayLaterPaid(long id, long transactionId) {
        ContentValues v = new ContentValues();
        v.put("status", PayLater.PAID);
        v.put("transaction_id", transactionId);
        getWritableDatabase().update("pay_later", v, "id=?", new String[]{String.valueOf(id)});
        notifyChanged();
    }

    public void deletePayLater(long id) {
        getWritableDatabase().delete("pay_later", "id=?", new String[]{String.valueOf(id)});
        notifyChanged();
    }

    private PayLater readPayLaterOrNull(Cursor c) {
        try {
            return c.moveToFirst() ? readPayLater(c) : null;
        } finally {
            c.close();
        }
    }

    private PayLater readPayLater(Cursor c) {
        PayLater p = new PayLater();
        p.id = c.getLong(c.getColumnIndexOrThrow("id"));
        p.amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents"));
        p.merchant = c.getString(c.getColumnIndexOrThrow("merchant"));
        p.platform = c.getString(c.getColumnIndexOrThrow("platform"));
        p.category = c.getString(c.getColumnIndexOrThrow("category"));
        p.appLabel = c.getString(c.getColumnIndexOrThrow("app_label"));
        p.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        p.status = c.getString(c.getColumnIndexOrThrow("status"));
        p.transactionId = c.getLong(c.getColumnIndexOrThrow("transaction_id"));
        p.note = c.getString(c.getColumnIndexOrThrow("note"));
        return p;
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("transactions", null, null);
            db.delete("rules", null, null);
            db.delete("impulses", null, null);
            db.delete("raw_captures", null, null);
            db.delete("pay_later", null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        notifyChanged();
    }

    private List<Transaction> queryList(String selection, String[] args, String order) {
        return queryList(selection, args, order, null);
    }

    private List<Transaction> queryList(String selection, String[] args, String order, String limit) {
        List<Transaction> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("transactions", null, selection, args,
                null, null, order, limit);
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    private Transaction read(Cursor c) {
        Transaction t = new Transaction();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents"));
        t.merchant = c.getString(c.getColumnIndexOrThrow("merchant"));
        t.platform = c.getString(c.getColumnIndexOrThrow("platform"));
        t.category = c.getString(c.getColumnIndexOrThrow("category"));
        t.occurredAt = c.getLong(c.getColumnIndexOrThrow("occurred_at"));
        t.cardTail = c.getString(c.getColumnIndexOrThrow("card_tail"));
        t.balanceCents = c.getLong(c.getColumnIndexOrThrow("balance_cents"));
        t.source = c.getString(c.getColumnIndexOrThrow("source"));
        t.rawText = c.getString(c.getColumnIndexOrThrow("raw_text"));
        t.note = c.getString(c.getColumnIndexOrThrow("note"));
        t.impulse = c.getInt(c.getColumnIndexOrThrow("impulse"));
        t.needsCategory = c.getInt(c.getColumnIndexOrThrow("needs_category"));
        t.fingerprint = c.getString(c.getColumnIndexOrThrow("fingerprint"));
        t.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return t;
    }

    private ContentValues values(Transaction t) {
        ContentValues v = new ContentValues();
        v.put("amount_cents", t.amountCents);
        v.put("merchant", safe(t.merchant));
        v.put("platform", safe(t.platform));
        v.put("category", Category.valid(t.category) ? t.category : Category.OTHER);
        v.put("occurred_at", t.occurredAt);
        v.put("card_tail", safe(t.cardTail));
        v.put("balance_cents", t.balanceCents);
        v.put("source", safe(t.source));
        v.put("raw_text", safe(t.rawText));
        v.put("note", safe(t.note));
        v.put("impulse", t.impulse);
        v.put("needs_category", t.needsCategory);
        v.put("fingerprint", safe(t.fingerprint));
        v.put("created_at", t.createdAt == 0 ? System.currentTimeMillis() : t.createdAt);
        return v;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private void notifyChanged() {
        try {
            App.get().notifyDataChanged();
        } catch (Exception ignored) {
        }
    }
}
