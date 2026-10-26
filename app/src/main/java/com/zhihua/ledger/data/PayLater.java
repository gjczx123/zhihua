package com.zhihua.ledger.data;

/** 先用后付：下单时先记一笔待付款，实际扣款时再转成正式账单。 */
public class PayLater {
    public static final String PENDING = "pending";
    public static final String PAID = "paid";

    public long id;
    public long amountCents;
    public String merchant = "";
    public String platform = "";
    public String category = Category.OTHER;
    public String appLabel = "";
    public long createdAt;
    public String status = PENDING;
    public long transactionId;
    public String note = "";

    public PayLater copy() {
        PayLater p = new PayLater();
        p.id = id;
        p.amountCents = amountCents;
        p.merchant = merchant;
        p.platform = platform;
        p.category = category;
        p.appLabel = appLabel;
        p.createdAt = createdAt;
        p.status = status;
        p.transactionId = transactionId;
        p.note = note;
        return p;
    }
}
