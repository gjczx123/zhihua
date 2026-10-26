package com.zhihua.ledger.data;

/** 未识别通知的原文，便于适配不同建行通知格式。 */
public class RawCapture {
    public long id;
    public String packageName = "";
    public String title = "";
    public String text = "";
    public long at;
    public boolean parsed;
}
