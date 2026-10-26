package com.zhihua.ledger.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.DateX;
import com.zhihua.ledger.data.Money;
import com.zhihua.ledger.data.Transaction;

/** 通用账单行。 */
public final class TransactionRow {
    private TransactionRow() {
    }

    public static LinearLayout build(Activity a, Transaction t, View.OnClickListener click) {
        LinearLayout row = Ui.row(a);
        row.setPadding(Ui.dp(a, 4), Ui.dp(a, 12), Ui.dp(a, 4), Ui.dp(a, 12));
        row.setMinimumHeight(Ui.dp(a, 56));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(Ui.ripple(a, Color.TRANSPARENT, 0x14171A17, 8));
        row.setOnClickListener(click);

        LinearLayout mid = Ui.col(a);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(mid, midLp);

        String name = t.merchant == null || t.merchant.length() == 0 ? "未识别商户" : t.merchant;
        TextView merchant = Ui.text(a, name, 14f, Ui.INK, true);
        merchant.setMaxLines(1);
        merchant.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mid.addView(merchant);

        StringBuilder subText = new StringBuilder(Category.name(t.category))
                .append(" · ").append(DateX.time(t.occurredAt));
        if (t.platform != null && t.platform.length() > 0
                && !t.platform.equals(t.merchant)) {
            subText.append(" · ").append(t.platform);
        }
        TextView sub = Ui.text(a, subText.toString(), 12f, Ui.INK3, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(a, 3);
        mid.addView(sub, subLp);

        LinearLayout right = Ui.col(a);
        right.setGravity(Gravity.END);
        TextView amount = Ui.text(a, "−" + Money.yuan(t.amountCents), 15f, Ui.INK, true);
        amount.setGravity(Gravity.END);
        right.addView(amount);
        if (t.needsCategory == 1) {
            TextView tag = Ui.text(a, "待分类", 11f, Ui.ORANGE, true);
            tag.setPadding(0, Ui.dp(a, 3), 0, 0);
            right.addView(tag);
        } else if (t.impulse == 2) {
            TextView tag = Ui.text(a, "冷静后仍买", 11f, Ui.ORANGE, true);
            tag.setPadding(0, Ui.dp(a, 3), 0, 0);
            right.addView(tag);
        } else if (t.impulse == 1) {
            TextView tag = Ui.text(a, "确认后购买", 11f, Ui.INK3, false);
            tag.setPadding(0, Ui.dp(a, 3), 0, 0);
            right.addView(tag);
        }
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rightLp.leftMargin = Ui.dp(a, 12);
        row.addView(right, rightLp);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }
}
