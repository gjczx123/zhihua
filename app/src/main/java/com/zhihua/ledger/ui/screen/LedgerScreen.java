package com.zhihua.ledger.ui.screen;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.zhihua.ledger.App;
import com.zhihua.ledger.R;
import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.DateX;
import com.zhihua.ledger.data.Db;
import com.zhihua.ledger.data.Money;
import com.zhihua.ledger.data.Transaction;
import com.zhihua.ledger.ui.Host;
import com.zhihua.ledger.ui.TransactionRow;
import com.zhihua.ledger.ui.Ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LedgerScreen extends ScrollView {
    private final Activity activity;
    private final Host host;
    private final LinearLayout content;
    private long anchor = System.currentTimeMillis();
    private String filter = "all";
    private long seq;

    public LedgerScreen(Activity activity, Host host) {
        super(activity);
        this.activity = activity;
        this.host = host;
        setBackgroundColor(Ui.BG);
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        content = Ui.col(activity);
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        refresh();
    }

    public void refresh() {
        final long s = ++seq;
        final long a = anchor;
        final String f = filter;
        App.get(activity).io().execute(new Runnable() {
            @Override
            public void run() {
                final Data d = load(a, f);
                App.get(activity).main().post(new Runnable() {
                    @Override
                    public void run() {
                        if (s != seq) return;
                        render(d);
                    }
                });
            }
        });
    }

    private Data load(long a, String f) {
        Data d = new Data();
        d.anchor = a;
        d.filter = f;
        Db db = App.get(activity).db();
        long start = DateX.startOfMonth(a);
        long end = DateX.addMonths(start, 1);
        d.start = start;
        d.end = end;
        d.all = db.listBetween(start, end);
        d.filtered = new ArrayList<>();
        for (Transaction t : d.all) {
            if ("pending".equals(f)) {
                if (t.needsCategory == 1) d.filtered.add(t);
            } else if ("all".equals(f) || f.equals(t.category)) {
                d.filtered.add(t);
            }
        }
        long total = 0;
        for (Transaction t : d.filtered) total += t.amountCents;
        d.total = total;
        d.count = d.filtered.size();
        d.avg = d.count == 0 ? 0 : total / d.count;
        d.current = DateX.isCurrent(DateX.MONTH, a);
        d.groups = group(d.filtered);
        d.budget = App.get(activity).prefs().budget();
        return d;
    }

    private Map<Long, List<Transaction>> group(List<Transaction> list) {
        Map<Long, List<Transaction>> map = new LinkedHashMap<>();
        for (Transaction t : list) {
            long day = DateX.startOfDay(t.occurredAt);
            List<Transaction> bucket = map.get(day);
            if (bucket == null) {
                bucket = new ArrayList<>();
                map.put(day, bucket);
            }
            bucket.add(t);
        }
        return map;
    }

    private void render(Data d) {
        content.removeAllViews();
        int pad = Ui.dp(activity, 20);
        content.setPadding(pad, Ui.dp(activity, 18), pad, Ui.dp(activity, 112));

        content.addView(header(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(monthNav(d));
        content.addView(summary(d));
        Ui.addFull(content, filters());
        if (d.filtered.isEmpty()) {
            Ui.addFull(content, empty());
        } else {
            for (Map.Entry<Long, List<Transaction>> e : d.groups.entrySet()) {
                content.addView(dayHeader(e.getKey(), e.getValue()));
                content.addView(dayCard(e.getValue()));
            }
        }
    }

    private View header() {
        LinearLayout row = Ui.row(activity);
        LinearLayout col = Ui.col(activity);
        col.addView(Ui.title(activity, "账单", 24f));
        TextView sub = Ui.text(activity, "每一笔，都有去处", 12f, Ui.INK2, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 5);
        col.addView(sub, lp);
        row.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView add = Ui.text(activity, "记一笔", 12.5f, Ui.INK, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 9),
                Ui.dp(activity, 14), Ui.dp(activity, 9));
        add.setBackground(Ui.roundStroke(activity, Color.WHITE, Ui.LINE, 999));
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openAdd();
            }
        });
        row.addView(add);
        return row;
    }

    private View monthNav(Data d) {
        LinearLayout row = Ui.row(activity);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Ui.dp(activity, 14);
        rowLp.bottomMargin = Ui.dp(activity, 10);
        row.setLayoutParams(rowLp);

        ImageView left = arrow(R.drawable.ic_chevron_left);
        left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                anchor = DateX.shift(DateX.MONTH, anchor, -1);
                refresh();
            }
        });
        row.addView(left);

        LinearLayout center = Ui.col(activity);
        center.setGravity(Gravity.CENTER);
        center.addView(Ui.text(activity, DateX.monthTitle(d.anchor), 15f, Ui.INK, true));
        if (!d.current) {
            TextView back = Ui.text(activity, "回到本月", 11f, Ui.INK2, true);
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    anchor = System.currentTimeMillis();
                    refresh();
                }
            });
            center.addView(back);
        }
        row.addView(center, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView right = arrow(R.drawable.ic_chevron_right);
        boolean can = DateX.canGoNext(DateX.MONTH, d.anchor);
        right.setEnabled(can);
        right.setAlpha(can ? 1f : 0.28f);
        right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DateX.canGoNext(DateX.MONTH, anchor)) {
                    anchor = DateX.shift(DateX.MONTH, anchor, 1);
                    refresh();
                }
            }
        });
        row.addView(right);
        return row;
    }

    private ImageView arrow(int res) {
        ImageView v = new ImageView(activity);
        v.setImageResource(res);
        v.setColorFilter(Ui.INK2);
        v.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 8),
                Ui.dp(activity, 8), Ui.dp(activity, 8));
        v.setBackground(Ui.ripple(activity, Color.TRANSPARENT, 0x14171A17, 999));
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 36)));
        return v;
    }

    private View summary(Data d) {
        LinearLayout card = Ui.card(activity);
        card.setLayoutParams(Ui.cardLp(activity));
        LinearLayout top = Ui.row(activity);
        top.addView(Ui.text(activity, "pending".equals(d.filter) ? "待分类支出"
                : "all".equals(d.filter) ? "本月支出" : "分类支出", 12f, Ui.INK2, false));
        top.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(Ui.text(activity, d.count + " 笔", 11f, Ui.INK3, false));
        card.addView(top);

        LinearLayout amountRow = Ui.row(activity);
        amountRow.setGravity(Gravity.BOTTOM);
        amountRow.addView(Ui.text(activity, "¥", 16f, Ui.INK2, true));
        TextView amount = Ui.text(activity, Money.format(d.total), 30f, Ui.INK, true);
        amount.setSingleLine(true);
        amount.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        al.leftMargin = Ui.dp(activity, 5);
        amountRow.addView(amount, al);
        LinearLayout.LayoutParams ar = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ar.topMargin = Ui.dp(activity, 8);
        card.addView(amountRow, ar);
        card.addView(Ui.text(activity, "笔均 " + Money.yuan(d.avg)
                + (d.budget > 0 ? " · 月预算 " + Money.yuan(d.budget) : ""), 11.5f, Ui.INK3, false));
        return card;
    }

    private View filters() {
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout row = Ui.row(activity);
        row.setPadding(0, Ui.dp(activity, 2), 0, Ui.dp(activity, 10));
        scroll.addView(row);
        row.addView(chip("全部", "all", null));
        row.addView(chip("待分类", "pending", null));
        for (String key : Category.KEYS) {
            row.addView(chip(Category.name(key), key, null));
        }
        return scroll;
    }

    private View chip(String label, String key, Integer color) {
        boolean sel = key.equals(filter);
        TextView t = Ui.text(activity, label, 12f, sel ? Color.WHITE : Ui.INK2, sel);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(Ui.dp(activity, 44));
        t.setPadding(Ui.dp(activity, 13), Ui.dp(activity, 8),
                Ui.dp(activity, 13), Ui.dp(activity, 8));
        if (sel) {
            t.setBackground(Ui.round(activity, Ui.INK, 999));
        } else {
            t.setBackground(Ui.roundStroke(activity, Color.WHITE, Ui.LINE, 999));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(activity, 7);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filter = key;
                refresh();
            }
        });
        return t;
    }

    private View dayHeader(long day, List<Transaction> list) {
        long total = 0;
        for (Transaction t : list) total += t.amountCents;
        LinearLayout row = Ui.row(activity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 8);
        lp.bottomMargin = Ui.dp(activity, 6);
        row.setLayoutParams(lp);
        row.addView(Ui.text(activity, DateX.dayTitle(day), 12f, Ui.INK2, true));
        row.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(Ui.text(activity, "−" + Money.yuan(total), 12f, Ui.INK2, false));
        return row;
    }

    private View dayCard(List<Transaction> list) {
        LinearLayout card = Ui.card(activity);
        card.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 4),
                Ui.dp(activity, 10), Ui.dp(activity, 4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(activity, 12);
        card.setLayoutParams(lp);
        for (int i = 0; i < list.size(); i++) {
            final Transaction t = list.get(i);
            card.addView(TransactionRow.build(activity, t, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.openEdit(t);
                }
            }));
            if (i < list.size() - 1) {
                View line = new View(activity);
                line.setBackgroundColor(Ui.LINE);
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(activity, 0.7f)));
                ll.leftMargin = Ui.dp(activity, 16);
                ll.rightMargin = Ui.dp(activity, 16);
                card.addView(line, ll);
            }
        }
        return card;
    }

    private View empty() {
        LinearLayout card = Ui.card(activity);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(Ui.dp(activity, 18), Ui.dp(activity, 32),
                Ui.dp(activity, 18), Ui.dp(activity, 32));
        TextView title = Ui.text(activity, "这个月还没有账单", 15f, Ui.INK, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.addView(title, tl);
        TextView sub = Ui.text(activity, "用储蓄卡消费后，通知会自动记进来", 12f, Ui.INK2, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Ui.dp(activity, 6);
        card.addView(sub, sl);
        TextView add = Ui.primaryButton(activity, "记一笔");
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                Ui.dp(activity, 140), Ui.dp(activity, 48));
        al.topMargin = Ui.dp(activity, 16);
        card.addView(add, al);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openAdd();
            }
        });
        return card;
    }

    private static class Data {
        long anchor, start, end;
        String filter;
        List<Transaction> all = new ArrayList<>();
        List<Transaction> filtered = new ArrayList<>();
        Map<Long, List<Transaction>> groups = new LinkedHashMap<>();
        long total, avg, budget;
        int count;
        boolean current;
    }
}
