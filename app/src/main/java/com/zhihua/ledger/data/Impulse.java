package com.zhihua.ledger.data;

/** 付款前冷静提醒的一次记录。 */
public class Impulse {
    public static final String ABANDONED = "abandoned";
    public static final String CONTINUED = "continued";
    public static final String DISMISSED = "dismissed";

    public long id;
    public String packageName = "";
    public String appLabel = "";
    public long amountCents;
    public String category = Category.SHOPPING;
    public String decision = ABANDONED;
    public long createdAt;
    public long transactionId;
    public String note = "";
}
