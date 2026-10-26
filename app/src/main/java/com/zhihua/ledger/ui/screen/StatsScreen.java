package com.zhihua.ledger.ui.screen;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.zhihua.ledger.App;
import com.zhihua.ledger.R;
import com.zhihua.ledger.core.PermissionUtils;
import com.zhihua.ledger.core.StatsEngine;
import com.zhihua.ledger.data.DateX;
import com.zhihua.ledger.data.Db;
import com.zhihua.ledger.data.Impulse;
import com.zhihua.ledger.data.Money;
import com.zhihua.ledger.data.Transaction;
import com.zhihua.ledger.ui.Host;
import com.zhihua.ledger.ui.Ui;
import com.zhihua.ledger.ui.widget.BarChartView;
import com.zhihua.ledger.ui.widget.BudgetBarView;

import java.util.ArrayList;
import java.util.List;

public class StatsScreen extends ScrollView {
    private static final String[] TYPES = {DateX.DAY, DateX.WEEK, DateX.MONTH, DateX.YEAR};
    private static final String[] TABS = {"日", "周", "月", "年"};
    private final Activity activity;
    private final Host host;
    private final LinearLayout content;
    private String type = DateX.MONTH;
    private long anchor = System.currentTimeMillis();
    private long seq;

    public StatsScreen(Activity activity, Host host) {
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
        final String t = type;
        final long a = anchor;
        App.get(activity).io().execute(new Runnable() {
            @Override
            public void run() {
                final Data d = load(t, a);
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

    private Data load(String t, long a) {
        Data d = new Data();
        d.type = t;
        d.anchor = a;
        App app = App.get(activity);
        Db db = app.db();
        DateX.Period p = DateX.period(t, a);
        int bucketCount = DateX.DAY.equals(t) ? 7 : DateX.WEEK.equals(t) ? 8
                : DateX.MONTH.equals(t) ? 6 : 5;
        long chartStart = DateX.bucketStarts(t, a, bucketCount).get(0);
        long queryStart = Math.min(chartStart, p.prevStart);
        d.all = db.listBetween(queryStart, p.end);
        d.impulses = db.listImpulses(p.start, p.end);
        long now = System.currentTimeMillis();
        long ms = DateX.startOfMonth(now);
        d.monthSpent = db.totalBetween(ms, DateX.addMonths(ms, 1));
        d.budget = app.prefs().budget();
        d.report = StatsEngine.build(t, a, d.all, d.impulses, d.budget, d.monthSpent);
        d.accessibility = PermissionUtils.accessibility(activity);
        d.current = DateX.isCurrent(t, a);
        d.bucketCount = bucketCount;
        return d;
    }

    private void render(Data d) {
        content.removeAllViews();
        int pad = Ui.dp(activity, 20);
        content.setPadding(pad, Ui.dp(activity, 18), pad, Ui.dp(activity, 112));

        content.addView(header(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(segments());
        content.addView(periodNav(d));
        content.addView(summaryCard(d));
        content.addView(trendCard(d));
        content.addView(categoryCard(d));
        content.addView(insightCard(d));
        content.addView(impulseCard(d));
    }

    private View header() {
        LinearLayout col = Ui.col(activity);
        col.addView(Ui.title(activity, "消费分析", 24f));
        TextView sub = Ui.text(activity, "看清钱去了哪，再决定下一笔怎么花", 12f, Ui.INK2, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 5);
        col.addView(sub, lp);
        return col;
    }

    private View segments() {
        LinearLayout wrap = Ui.row(activity);
        wrap.setPadding(Ui.dp(activity, 3), Ui.dp(activity, 3),
                Ui.dp(activity, 3), Ui.dp(activity, 3));
        wrap.setBackground(Ui.round(activity, 0xFFE7EAE4, 10));
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapLp.topMargin = Ui.dp(activity, 16);
        wrap.setLayoutParams(wrapLp);
        for (int i = 0; i < TYPES.length; i++) {
            final String t = TYPES[i];
            boolean sel = t.equals(type);
            TextView tab = Ui.text(activity, TABS[i], 13f, sel ? Ui.INK : Ui.INK2, sel);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 8));
            if (sel) {
                tab.setBackground(Ui.round(activity, Color.WHITE, 8));
            }
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!t.equals(type)) {
                        type = t;
                        anchor = System.currentTimeMillis();
                        refresh();
                    }
                }
            });
            wrap.addView(tab, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return wrap;
    }

    private View periodNav(Data d) {
        LinearLayout row = Ui.row(activity);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = Ui.dp(activity, 12);
        row.setLayoutParams(rowLp);

        ImageView left = navArrow(R.drawable.ic_chevron_left);
        left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                anchor = DateX.shift(type, anchor, -1);
                refresh();
            }
        });
        row.addView(left);

        LinearLayout center = Ui.col(activity);
        center.setGravity(Gravity.CENTER);
        TextView label = Ui.text(activity, d.report.label, 14f, Ui.INK, true);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        center.addView(label);
        if (!d.current) {
            TextView back = Ui.text(activity, "回到本期", 11f, Ui.INK2, true);
            back.setGravity(Gravity.CENTER);
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

        ImageView right = navArrow(R.drawable.ic_chevron_right);
        boolean can = DateX.canGoNext(type, anchor);
        right.setEnabled(can);
        right.setAlpha(can ? 1f : 0.28f);
        right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DateX.canGoNext(type, anchor)) {
                    anchor = DateX.shift(type, anchor, 1);
                    refresh();
                }
            }
        });
        row.addView(right);
        return row;
    }

    private ImageView navArrow(int res) {
        ImageView v = new ImageView(activity);
        v.setImageResource(res);
        v.setColorFilter(Ui.INK2);
        v.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 8),
                Ui.dp(activity, 8), Ui.dp(activity, 8));
        v.setBackground(Ui.ripple(activity, Color.TRANSPARENT, 0x14171A17, 999));
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 36)));
        return v;
    }

    private View summaryCard(Data d) {
        LinearLayout card = Ui.card(activity);
        card.setLayoutParams(Ui.cardLp(activity));
        LinearLayout top = Ui.row(activity);
        top.addView(Ui.text(activity, "总支出", 12.5f, Ui.INK2, false));
        top.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(changePill(d.report));
        card.addView(top);

        LinearLayout amountRow = Ui.row(activity);
        amountRow.setGravity(Gravity.BOTTOM);
        TextView yuan = Ui.text(activity, "¥", 16f, Ui.INK2, true);
        amountRow.addView(yuan);
        TextView amount = Ui.text(activity, Money.format(d.report.total), 30f, Ui.INK, true);
        amount.setSingleLine(true);
        amount.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        amountLp.leftMargin = Ui.dp(activity, 5);
        amountRow.addView(amount, amountLp);
        LinearLayout.LayoutParams arLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        arLp.topMargin = Ui.dp(activity, 8);
        card.addView(amountRow, arLp);

        String meta = d.report.count + " 笔 · 笔均 " + Money.yuan(d.report.avg)
                + (d.report.maxTx != null ? " · 最高 " + Money.yuan(d.report.max) : "");
        card.addView(Ui.text(activity, meta, 12f, Ui.INK2, false));

        if (d.report.categories.size() > 0) {
            StatsEngine.CatStat catTop = d.report.categories.get(0);
            TextView topLine = Ui.text(activity, "花得最多："
                            + catTop.name + " " + Money.yuan(catTop.total) + " · "
                            + Math.round(catTop.percent) + "%",
                    12.5f, Ui.INK, false);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.topMargin = Ui.dp(activity, 8);
            card.addView(topLine, tl);
        }
        if (d.report.topPlatform.length() > 0 && d.report.topPlatformCount >= 2) {
            TextView platformLine = Ui.text(activity, "常买平台：" + d.report.topPlatform
                    + " · " + Money.yuan(d.report.topPlatformTotal)
                    + "（" + d.report.topPlatformCount + " 笔）", 12f, Ui.INK2, false);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pl.topMargin = Ui.dp(activity, 5);
            card.addView(platformLine, pl);
        }

        if (d.budget > 0) {
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(activity, 0.7f)));
            dl.topMargin = Ui.dp(activity, 14);
            dl.bottomMargin = Ui.dp(activity, 12);
            View line = new View(activity);
            line.setBackgroundColor(Ui.LINE);
            card.addView(line, dl);

            LinearLayout budgetHead = Ui.row(activity);
            budgetHead.addView(Ui.text(activity, "本月预算", 11.5f, Ui.INK2, false));
            budgetHead.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
            int pct = d.report.budgetPercent;
            budgetHead.addView(Ui.text(activity, pct + "% · " + (pct >= 100 ? "已超支" : "进行中"),
                    11.5f, pct >= 80 ? Ui.RED : Ui.GREEN, true));
            card.addView(budgetHead);

            BudgetBarView bar = new BudgetBarView(activity);
            bar.setTrackColor(0xFFE7EAE4);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 7));
            bl.topMargin = Ui.dp(activity, 8);
            card.addView(bar, bl);
            bar.setProgress(d.report.monthSpent, d.budget);

            long left = d.budget - d.report.monthSpent;
            TextView leftText = Ui.text(activity, left >= 0
                    ? "本月还剩 " + Money.yuan(left) + " 可用"
                    : "本月已超出 " + Money.yuan(-left), 11f,
                    left >= 0 ? Ui.INK3 : Ui.RED, false);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ll.topMargin = Ui.dp(activity, 6);
            card.addView(leftText, ll);
        }
        return card;
    }

    private View changePill(StatsEngine.Report r) {
        String text;
        int color;
        int bg;
        if (r.total == 0) {
            text = r.prevTotal > 0 ? "本期还没花钱" : "暂无支出";
            color = Ui.INK3;
            bg = 0xFFEEF2F6;
        } else if (r.prevTotal == 0) {
            text = "上期无支出";
            color = Ui.INK3;
            bg = 0xFFEEF2F6;
        } else if (r.changePercent == null || r.changePercent == 0) {
            text = "与上期持平";
            color = Ui.INK3;
            bg = 0xFFEEF2F6;
        } else if (r.changePercent > 0) {
            text = "较上期 +" + r.changePercent + "%";
            color = Ui.ORANGE;
            bg = Ui.ORANGE_SOFT;
        } else {
            text = "较上期 " + r.changePercent + "%";
            color = Ui.GREEN;
            bg = Ui.GREEN_SOFT;
        }
        TextView v = Ui.text(activity, text, 11f, color, true);
        v.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 4),
                Ui.dp(activity, 8), Ui.dp(activity, 4));
        v.setBackground(Ui.round(activity, bg, 999));
        return v;
    }

    private View trendCard(Data d) {
        LinearLayout card = Ui.card(activity);
        card.setLayoutParams(Ui.cardLp(activity));
        LinearLayout head = Ui.row(activity);
        head.addView(Ui.text(activity, "支出趋势", 16f, Ui.INK, true));
        head.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        head.addView(Ui.text(activity, trendRange(d), 11f, Ui.INK3, false));
        card.addView(head);
        BarChartView chart = new BarChartView(activity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 178));
        lp.topMargin = Ui.dp(activity, 6);
        card.addView(chart, lp);
        chart.setData(d.report.buckets);
        return card;
    }

    private String trendRange(Data d) {
        if (DateX.DAY.equals(d.type)) return "近 7 天";
        if (DateX.WEEK.equals(d.type)) return "近 8 周";
        if (DateX.MONTH.equals(d.type)) return "近 6 个月";
        return "近 5 年";
    }

    private View categoryCard(Data d) {
        LinearLayout card = Ui.card(activity);
        card.setLayoutParams(Ui.cardLp(activity));
        LinearLayout head = Ui.row(activity);
        head.addView(Ui.text(activity, "分类去向", 16f, Ui.INK, true));
        head.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        head.addView(Ui.text(activity, d.report.categories.size() + " 类", 11.5f, Ui.INK3, false));
        card.addView(head);

        if (d.report.categories.isEmpty()) {
            TextView empty = Ui.text(activity, "这个周期还没有支出", 12f, Ui.INK2, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(activity, 18), 0, Ui.dp(activity, 18));
            card.addView(empty);
            return card;
        }

        for (int i = 0; i < d.report.categories.size(); i++) {
            StatsEngine.CatStat s = d.report.categories.get(i);
            LinearLayout item = Ui.col(activity);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            il.topMargin = Ui.dp(activity, i == 0 ? 12 : 10);
            card.addView(item, il);

            LinearLayout top = Ui.row(activity);
            top.addView(Ui.text(activity, s.name, 13f, Ui.INK, true));
            top.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
            top.addView(Ui.text(activity, "¥" + Money.format(s.total), 13f, Ui.INK, false));
            TextView pct = Ui.text(activity, "  " + Math.round(s.percent) + "%",
                    11.5f, Ui.INK3, false);
            top.addView(pct);
            item.addView(top);

            LinearLayout bar = Ui.row(activity);
            bar.setBackground(Ui.round(activity, 0xFFE7EAE4, 999));
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 3));
            barLp.topMargin = Ui.dp(activity, 5);
            item.addView(bar, barLp);
            float p = (float) Math.max(1, Math.min(100, Math.round(s.percent)));
            View fill = new View(activity);
            fill.setBackground(Ui.round(activity, Ui.ACCENT, 999));
            bar.addView(fill, new LinearLayout.LayoutParams(0, Ui.dp(activity, 3), p));
            View rest = new View(activity);
            bar.addView(rest, new LinearLayout.LayoutParams(0, Ui.dp(activity, 3),
                    Math.max(0.1f, 100 - p)));
        }
        return card;
    }

    private View insightCard(Data d) {
        LinearLayout card = Ui.card(activity);
        card.setLayoutParams(Ui.cardLp(activity));
        card.addView(Ui.text(activity, "习惯总结", 16f, Ui.INK, true));
        TextView summary = Ui.text(activity, d.report.habitSummary, 13.5f, Ui.INK, false);
        summary.setLineSpacing(Ui.dp(activity, 3), 1f);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Ui.dp(activity, 8);
        card.addView(summary, sl);

        int shown = 0;
        for (StatsEngine.Insight in : d.report.insights) {
            if (shown >= 4) break;
            LinearLayout row = Ui.row(activity);
            row.setGravity(Gravity.TOP);
            View dot = Ui.dot(activity, in.tone == 2 ? Ui.ORANGE : Ui.INK3, 5);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                    Ui.dp(activity, 5), Ui.dp(activity, 5));
            dl.topMargin = Ui.dp(activity, 7);
            dl.rightMargin = Ui.dp(activity, 9);
            row.addView(dot, dl);
            LinearLayout col = Ui.col(activity);
            col.addView(Ui.text(activity, in.title, 12.5f, Ui.INK, true));
            TextView body = Ui.text(activity, in.text, 12f, Ui.INK2, false);
            body.setLineSpacing(Ui.dp(activity, 2), 1f);
            col.addView(body);
            row.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.topMargin = Ui.dp(activity, 12);
            card.addView(row, rl);
            shown++;
        }

        card.addView(Ui.divider(activity));
        card.addView(Ui.text(activity, "给你的建议", 13f, Ui.INK, true));
        for (int i = 0; i < d.report.advice.size(); i++) {
            TextView body = Ui.text(activity, d.report.advice.get(i), 12.5f, Ui.INK2, false);
            body.setLineSpacing(Ui.dp(activity, 3), 1f);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.topMargin = Ui.dp(activity, 9);
            card.addView(body, rl);
        }
        return card;
    }

    private View impulseCard(Data d) {
        boolean has = d.report.impulseCount > 0 || d.report.impulseContinued > 0;
        LinearLayout card = Ui.row(activity);
        card.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 14),
                Ui.dp(activity, 16), Ui.dp(activity, 14));
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(activity, 4);
        card.setLayoutParams(lp);
        View dot = Ui.dot(activity, has ? Ui.GREEN : Ui.INK3, 7);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                Ui.dp(activity, 7), Ui.dp(activity, 7));
        dotLp.rightMargin = Ui.dp(activity, 10);
        card.addView(dot, dotLp);
        LinearLayout col = Ui.col(activity);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        card.addView(col, cl);
        if (has) {
            col.addView(Ui.text(activity, "冷静清单", 13f, Ui.INK, true));
            col.addView(Ui.text(activity, "暂缓 " + d.report.impulseCount + " 笔 · 约 "
                    + Money.yuan(d.report.impulseSaved)
                    + (d.report.impulseContinued > 0 ? " · 确认后购买 " + d.report.impulseContinued + " 笔" : ""),
                    11.5f, Ui.INK2, false));
        } else {
            col.addView(Ui.text(activity, "付款前，先停一下", 13f, Ui.INK, true));
            col.addView(Ui.text(activity, d.accessibility
                    ? "拼多多/抖音付款时会弹出确认，帮你按下暂停键"
                    : "开启无障碍提醒后，付款前会先问你一句", 11.5f, Ui.INK2, false));
        }
        TextView go = Ui.text(activity, has ? "查看 ›" : "开启 ›", 12f, Ui.INK, true);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (has) showImpulseList(d.impulses);
                else host.openSettings();
            }
        });
        card.addView(go);
        return card;
    }

    private void showImpulseList(List<Impulse> list) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Impulse i : list) {
            if (shown >= 20) break;
            if (Impulse.DISMISSED.equals(i.decision)) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(DateX.shortDate(i.createdAt)).append(' ').append(DateX.time(i.createdAt))
                    .append("  ").append(i.appLabel.length() > 0 ? i.appLabel : "购物 App")
                    .append("  ").append(i.amountCents > 0 ? Money.yuan(i.amountCents) : "金额未填")
                    .append("  ").append(Impulse.ABANDONED.equals(i.decision) ? "已暂缓" : "确认后购买");
            shown++;
        }
        if (sb.length() == 0) sb.append("这个周期还没有冷静记录。");
        new AlertDialog.Builder(activity)
                .setTitle("冷静清单")
                .setMessage(sb.toString())
                .setPositiveButton("知道了", null)
                .setNeutralButton("去设置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        host.openSettings();
                    }
                }).show();
    }

    private static class Data {
        String type;
        long anchor;
        List<Transaction> all = new ArrayList<>();
        List<Impulse> impulses = new ArrayList<>();
        StatsEngine.Report report;
        long budget, monthSpent;
        boolean accessibility, current;
        int bucketCount;
    }
}
