package com.zhihua.ledger.data;

/** 一笔支出。 */
public class Transaction {
    public long id;
    public long amountCents;
    public String merchant = "";
    public String platform = "";
    public String category = Category.OTHER;
    public long occurredAt;
    public String cardTail = "";
    public long balanceCents = -1;
    public String source = "manual"; // notification / sms / manual / demo
    public String rawText = "";
    public String note = "";
    public int impulse; // 0 普通；1 付款前确认继续；2 冷静后仍购买
    public int needsCategory; // 1 表示自动分类失败，等待用户选分类
    public String fingerprint = "";
    public long createdAt;

    public Transaction copy() {
        Transaction t = new Transaction();
        t.id = id;
        t.amountCents = amountCents;
        t.merchant = merchant;
        t.platform = platform;
        t.category = category;
        t.occurredAt = occurredAt;
        t.cardTail = cardTail;
        t.balanceCents = balanceCents;
        t.source = source;
        t.rawText = rawText;
        t.note = note;
        t.impulse = impulse;
        t.needsCategory = needsCategory;
        t.fingerprint = fingerprint;
        t.createdAt = createdAt;
        return t;
    }

    public String sourceLabel() {
        if ("notification".equals(source)) return "银行通知";
        if ("sms".equals(source)) return "银行短信";
        if ("wechat".equals(source)) return "微信支付";
        if ("alipay".equals(source)) return "支付宝";
        if ("paylater".equals(source)) return "先用后付入账";
        if ("demo".equals(source)) return "示例数据";
        return "手动记账";
    }
}
