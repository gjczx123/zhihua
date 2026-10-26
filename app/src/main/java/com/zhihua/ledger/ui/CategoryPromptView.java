package com.zhihua.ledger.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.Money;
import com.zhihua.ledger.data.Transaction;

import java.util.LinkedHashMap;
import java.util.Map;

/** 自动分类失败后的轻量分类浮层：不拦支付，只让用户补一个分类。 */
public class CategoryPromptView extends FrameLayout {
    public interface Listener {
        void onSave(CategoryPromptView v, String category);

        void onSkip(CategoryPromptView v);
    }

    private final Listener listener;
    private final Transaction transaction;
    private final Map<String, TextView> chips = new LinkedHashMap<>();
    private String selected = "";
    private boolean chosen;
    private TextView saveButton;

    public CategoryPromptView(Context context, Transaction t, Listener listener) {
        super(context);
        this.listener = listener;
        this.transaction = t;
        setClickable(true);
        setFocusable(true);
        setBackgroundColor(0xCC08131B);
        build();
    }

    private void build() {
        Context c = getContext();
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout wrap = Ui.col(c);
        wrap.setGravity(Gravity.CENTER);
        wrap.setMinimumHeight(getResources().getDisplayMetrics().heightPixels);
        wrap.setPadding(Ui.dp(c, 16), Ui.dp(c, 26), Ui.dp(c, 16), Ui.dp(c, 26));
        scroll.addView(wrap, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = Ui.col(c);
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 18), Ui.dp(c, 22), Ui.dp(c, 18));
        card.setBackground(Ui.round(c, Color.WHITE, 20));
        card.setElevation(Ui.dp(c, 12));
        wrap.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = Ui.row(c);
        TextView badge = Ui.text(c, "待分类", 11.5f, Ui.ORANGE, true);
        badge.setPadding(Ui.dp(c, 9), Ui.dp(c, 5), Ui.dp(c, 9), Ui.dp(c, 5));
        badge.setBackground(Ui.round(c, Ui.ORANGE_SOFT, 999));
        top.addView(badge);
        top.addView(Ui.text(c, "  自动分类没认出来", 12f, Ui.INK2, false));
        top.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView close = Ui.text(c, "×", 24f, Ui.INK3, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭分类提醒");
        close.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 4), Ui.dp(c, 4));
        close.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSkip();
            }
        });
        top.addView(close);
        card.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = Ui.title(c, "这笔花在哪？", 26f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(c, 14);
        card.addView(title, tl);

        TextView sub = Ui.text(c, "选一个分类，知花会记住这个收款方，下次自动归类。", 12.5f, Ui.INK2, false);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Ui.dp(c, 6);
        card.addView(sub, sl);

        LinearLayout info = Ui.col(c);
        info.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        info.setBackground(Ui.round(c, 0xFFF6F7F4, 12));
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        il.topMargin = Ui.dp(c, 14);
        card.addView(info, il);
        info.addView(Ui.text(c, transaction.merchant == null || transaction.merchant.length() == 0
                ? "未识别收款方" : transaction.merchant, 13f, Ui.INK, true));
        TextView amount = Ui.text(c, "¥" + Money.format(transaction.amountCents)
                + (transaction.platform != null && transaction.platform.length() > 0
                ? "  ·  " + transaction.platform : ""), 22f, Ui.INK, true);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        al.topMargin = Ui.dp(c, 4);
        info.addView(amount, al);

        TextView catTitle = Ui.text(c, "选择分类", 12.5f, Ui.INK2, true);
        LinearLayout.LayoutParams ctl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ctl.topMargin = Ui.dp(c, 16);
        card.addView(catTitle, ctl);

        LinearLayout grid = Ui.col(c);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.topMargin = Ui.dp(c, 7);
        card.addView(grid, gl);
        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            LinearLayout row = Ui.row(c);
            for (int i = 0; i < 4; i++) {
                int idx = rowIndex * 4 + i;
                if (idx >= Category.KEYS.length) break;
                String key = Category.KEYS[idx];
                TextView chip = Ui.text(c, Category.name(key),
                        12f, Ui.INK2, true);
                chip.setGravity(Gravity.CENTER);
                chip.setTag(key);
                chip.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selected = (String) v.getTag();
                        chosen = true;
                        refreshChips();
                    }
                });
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0,
                        Ui.dp(c, 46), 1f);
                cl.setMargins(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
                row.addView(chip, cl);
                chips.put(key, chip);
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        refreshChips();

        saveButton = Ui.primaryButton(c, "保存分类");
        LinearLayout.LayoutParams sbl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 52));
        sbl.topMargin = Ui.dp(c, 14);
        card.addView(saveButton, sbl);
        saveButton.setEnabled(false);
        saveButton.setAlpha(0.45f);
        saveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!chosen) {
                    Ui.toast(getContext(), "先选一个分类");
                    return;
                }
                String cat = selected;
                hide(new Runnable() {
                    @Override
                    public void run() {
                        listener.onSave(CategoryPromptView.this, cat);
                    }
                });
            }
        });

        TextView skip = Ui.text(c, "先放着，之后在账单里改", 12f, Ui.INK2, true);
        skip.setGravity(Gravity.CENTER);
        skip.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 2));
        skip.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSkip();
            }
        });
        card.addView(skip);

        refreshChips();

        setAlpha(0f);
        setTranslationY(Ui.dp(c, 24));
        animate().alpha(1f).translationY(0f).setDuration(200)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void refreshChips() {
        for (Map.Entry<String, TextView> e : chips.entrySet()) {
            boolean sel = e.getKey().equals(selected);
            TextView v = e.getValue();
            if (sel) {
                v.setBackground(Ui.roundStroke(getContext(), 0xFFE7EAE4, Ui.INK, 10));
                v.setTextColor(Ui.INK);
            } else {
                v.setBackground(Ui.round(getContext(), 0xFFF6F7F4, 10));
                v.setTextColor(Ui.INK2);
            }
        }
        if (saveButton != null) {
            saveButton.setEnabled(chosen);
            saveButton.setAlpha(1f);
            if (chosen) {
                saveButton.setBackground(Ui.ripple(getContext(), Ui.INK, 0x33FFFFFF, 12));
                saveButton.setTextColor(Color.WHITE);
            } else {
                saveButton.setBackground(Ui.round(getContext(), 0xFFE7EAE4, 12));
                saveButton.setTextColor(Ui.INK3);
            }
        }
    }

    private void hideSkip() {
        if (!isEnabled()) return;
        setEnabled(false);
        hide(new Runnable() {
            @Override
            public void run() {
                listener.onSkip(CategoryPromptView.this);
            }
        });
    }

    private void hide(final Runnable end) {
        setEnabled(false);
        animate().alpha(0f).translationY(Ui.dp(getContext(), 16)).setDuration(150)
                .withEndAction(end).start();
    }
}
