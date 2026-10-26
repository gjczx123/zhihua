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
import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.DateX;
import com.zhihua.ledger.data.Db;
import com.zhihua.ledger.data.Impulse;
import com.zhihua.ledger.data.Money;
import com.zhihua.ledger.data.PayLater;
import com.zhihua.ledger.data.Prefs;
import com.zhihua.ledger.data.Transaction;
import com.zhihua.ledger.ui.Host;
import com.zhihua.ledger.ui.TransactionRow;
import com.zhihua.ledger.ui.Ui;
import com.zhihua.ledger.ui.widget.BudgetBarView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class HomeScreen extends ScrollView {
    private final Activity activity;
    private final Host host;
    private final LinearLayout content;
    private long seq;
    private int pendingScroll;

    public HomeScreen(Activity activity, Host host) {
        super(activity);
        this.activity = activity;
        this.host = host;
        setBackgroundColor(Ui.BG);
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setClipToPadding(false);
        content = Ui.col(activity);
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        refresh();
    }

    public void refresh() {
        final long s = ++seq;
        App.get(activity).io().execute(new Runnable() {
            @Override
            public void run() {
                final Data d = load();
                App.get(activity).main().post(new Runnable() {
                    @Override
                    public void run() {
                        if (s != seq) return;
                        pendingScroll = getScrollY();
                        render(d);
                        post(new Runnable() {
                            @Override
                            public void run() {
                                scrollTo(0, Math.min(pendingScroll, Math.max(0,
                                        getChildAt(0).getHeight() - getHeight())));
                            }
                        });
                    }
                });
            }
        });
    }

    private Data load() {
        Data d = new Data();
        long now = System.currentTimeMillis();
        App app = App.get(activity);
        Db db = app.db();
        Prefs prefs = app.prefs();
        long day = DateX.startOfDay(now);
        long week = DateX.startOfWeek(now);
        long month = DateX.startOfMonth(now);
        long monthEnd = DateX.addMonths(month, 1);
        d.now = now;
        d.dayTotal = db.totalBetween(day, DateX.addDays(day, 1));
        d.dayCount = db.countBetween(day, DateX.addDays(day, 1));
        d.weekTotal = db.totalBetween(week, DateX.addDays(week, 7));
        d.weekCount = db.countBetween(week, DateX.addDays(week, 7));
        d.monthTotal = db.totalBetween(month, monthEnd);
        d.monthCount = db.countBetween(month, monthEnd);
        d.budget = prefs.budget();
        d.recent = db.listRecent(6);
        d.notifyAccess = PermissionUtils.notificationAccess(activity);
        d.accessibility = PermissionUtils.accessibility(activity);
        d.battery = PermissionUtils.batteryOptimized(activity);
        d.impulses = db.listImpulses(month, monthEnd);
        d.pendingCount = db.countPendingCategory();
        d.pendingFirst = d.pendingCount > 0 && db.listPendingCategory(1).size() > 0
                ? db.listPendingCategory(1).get(0) : null;
        d.payLater = db.listPendingPayLater(40);
        d.payLaterTotal = 0;
        for (PayLater p : d.payLater) d.payLaterTotal += p.amountCents;
        List<Transaction> list = db.listBetween(DateX.addMonths(month, -1), monthEnd);
        d.report = StatsEngine.build("month", now, list, d.impulses, d.budget, d.monthTotal);
        return d;
    }

    private void render(Data d) {
        content.removeAllViews();
        int pad = Ui.dp(activity, 20);
        content.setPadding(pad, Ui.dp(activity, 18), pad, Ui.dp(activity, 112));

        content.addView(header(d), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(hero(d));
        Ui.addFull(content, quickRow(d));
        View banner = setupBanner(d);
        if (banner != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(activity, 10);
            content.addView(banner, lp);
        }
        if (d.pendingCount > 0) content.addView(pendingCard(d));
        if (!d.payLater.isEmpty()) content.addView(payLaterCard(d));
        content.addView(insightCard(d));
        if (d.report.impulseCount > 0) content.addView(impulseCard(d));
        Ui.addFull(content, recentSection(d));
    }

    private View header(Data d) {
        LinearLayout wrap = Ui.col(activity);
        LinearLayout top = Ui.row(activity);
        TextView mark = Ui.text(activity, "知花", 13f, Ui.INK, true);
        mark.setLetterSpacing(0.02f);
        top.addView(mark);
        TextView ledger = Ui.text(activity, "  本月账本", 11.5f, Ui.INK3, false);
        top.addView(ledger);
        top.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));

        TextView add = Ui.text(activity, "＋", 22f, Ui.INK, false);
        add.setGravity(Gravity.CENTER);
        add.setContentDescription("记一笔");
        add.setBackground(Ui.ripple(activity, Color.TRANSPARENT, 0x14171A17, 999));
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openAdd();
            }
        });
        top.addView(add, new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48)));

        boolean alert = !d.notifyAccess || !d.accessibility;
        android.widget.FrameLayout bell = new android.widget.FrameLayout(activity);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_bell);
        icon.setColorFilter(Ui.INK2);
        icon.setPadding(Ui.dp(activity, 11), Ui.dp(activity, 11),
                Ui.dp(activity, 11), Ui.dp(activity, 11));
        bell.addView(icon, new android.widget.FrameLayout.LayoutParams(
                Ui.dp(activity, 46), Ui.dp(activity, 46)));
        bell.setBackground(Ui.ripple(activity, Color.TRANSPARENT, 0x14171A17, 999));
        bell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openSettings();
            }
        });
        bell.setContentDescription("设置与权限");
        if (alert) {
            View dot = new View(activity);
            dot.setBackground(Ui.round(activity, Ui.ORANGE, 999));
            android.widget.FrameLayout.LayoutParams dotLp = new android.widget.FrameLayout.LayoutParams(
                    Ui.dp(activity, 7), Ui.dp(activity, 7));
            dotLp.gravity = Gravity.TOP | Gravity.END;
            dotLp.topMargin = Ui.dp(activity, 8);
            dotLp.rightMargin = Ui.dp(activity, 7);
            bell.addView(dot, dotLp);
        }
        top.addView(bell, new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48)));
        wrap.addView(top);

        TextView date = Ui.title(activity, DateX.dayTitle(d.now), 24f);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.topMargin = Ui.dp(activity, 10);
        wrap.addView(date, dateLp);
        TextView sub = Ui.text(activity, "每一笔，都知道去向", 12f, Ui.INK2, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(activity, 4);
        wrap.addView(sub, subLp);
        return wrap;
    }

    private View hero(Data d) {
        LinearLayout hero = Ui.card(activity);
        hero.setPadding(Ui.dp(activity, 18), Ui.dp(activity, 16),
                Ui.dp(activity, 18), Ui.dp(activity, 16));
        LinearLayout.LayoutParams heroLp = Ui.cardLp(activity);
        heroLp.topMargin = Ui.dp(activity, 16);
        hero.setLayoutParams(heroLp);

        LinearLayout top = Ui.row(activity);
        top.addView(Ui.text(activity, "本月支出", 12.5f, Ui.INK2, false));
        top.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(Ui.text(activity, DateX.month(d.now) + " 月", 11.5f, Ui.INK3, false));
        hero.addView(top);

        LinearLayout amountRow = Ui.row(activity);
        amountRow.setGravity(Gravity.BOTTOM);
        TextView yuan = Ui.text(activity, "¥", 18f, Ui.INK2, true);
        amountRow.addView(yuan);
        TextView amount = Ui.text(activity, Money.format(d.monthTotal), 32f, Ui.INK, true);
        amount.setLetterSpacing(-0.02f);
        amount.setSingleLine(true);
        LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        amountLp.leftMargin = Ui.dp(activity, 5);
        amountRow.addView(amount, amountLp);
        LinearLayout.LayoutParams amountRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        amountRowLp.topMargin = Ui.dp(activity, 8);
        hero.addView(amountRow, amountRowLp);

        if (d.budget > 0) {
            BudgetBarView bar = new BudgetBarView(activity);
            bar.setTrackColor(0xFFE7EAE4);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 6));
            barLp.topMargin = Ui.dp(activity, 16);
            hero.addView(bar, barLp);
            bar.setProgress(d.monthTotal, d.budget);

            LinearLayout budgetRow = Ui.row(activity);
            LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            brLp.topMargin = Ui.dp(activity, 8);
            hero.addView(budgetRow, brLp);
            long left = d.budget - d.monthTotal;
            budgetRow.addView(Ui.text(activity, "预算 " + Money.yuan(d.budget), 11.5f,
                    Ui.INK3, false));
            budgetRow.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
            budgetRow.addView(Ui.text(activity, left >= 0 ? "剩余 " + Money.yuan(left)
                            : "超支 " + Money.yuan(-left),
                    11.5f, left >= 0 ? Ui.INK : Ui.RED, true));
        } else {
            LinearLayout setBudget = Ui.row(activity);
            LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sbLp.topMargin = Ui.dp(activity, 14);
            hero.addView(setBudget, sbLp);
            setBudget.addView(Ui.text(activity, "设置月预算，看到每天还能花多少", 12f,
                    Ui.INK2, false));
            setBudget.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
            TextView go = Ui.text(activity, "去设置 ›", 12f, Ui.INK, true);
            go.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.openSettings();
                }
            });
            setBudget.addView(go);
        }

        LinearLayout foot = Ui.row(activity);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footLp.topMargin = Ui.dp(activity, 12);
        hero.addView(foot, footLp);
        int days = Math.max(1, DateX.day(d.now));
        foot.addView(Ui.text(activity, "日均 " + Money.yuan(d.monthTotal / days), 11.5f,
                Ui.INK3, false));
        foot.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        foot.addView(Ui.text(activity, "已记 " + d.monthCount + " 笔", 11.5f, Ui.INK3, false));
        return hero;
    }

    private View quickRow(Data d) {
        LinearLayout card = Ui.card(activity);
        card.setPadding(0, Ui.dp(activity, 12), 0, Ui.dp(activity, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(activity, 12);
        card.setLayoutParams(lp);
        LinearLayout row = Ui.row(activity);
        row.addView(quickStat("今日支出", d.dayTotal, d.dayCount,
                d.dayTotal == 0 ? "今天还没花钱" : "按日看，更清醒"), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View divider = new View(activity);
        divider.setBackgroundColor(Ui.LINE);
        row.addView(divider, new LinearLayout.LayoutParams(Math.max(1, Ui.dp(activity, 0.7f)),
                Ui.dp(activity, 44)));
        row.addView(quickStat("本周支出", d.weekTotal, d.weekCount, "周一至今"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row);
        return card;
    }

    private View quickStat(String label, long total, int count, String hint) {
        LinearLayout col = Ui.col(activity);
        col.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), 0);
        col.addView(Ui.text(activity, label, 11.5f, Ui.INK2, false));
        TextView amount = Ui.text(activity, "¥" + Money.format(total), 20f, Ui.INK, true);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aLp.topMargin = Ui.dp(activity, 4);
        col.addView(amount, aLp);
        LinearLayout bottom = Ui.row(activity);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.topMargin = Ui.dp(activity, 3);
        col.addView(bottom, bLp);
        bottom.addView(Ui.text(activity, hint, 11f, Ui.INK3, false));
        bottom.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        bottom.addView(Ui.text(activity, count + " 笔", 11f, Ui.INK3, false));
        return col;
    }

    private View setupBanner(Data d) {
        List<String> missing = new ArrayList<>();
        if (!d.notifyAccess) missing.add("通知读取");
        if (!d.accessibility) missing.add("付款前提醒");
        if (d.battery) missing.add("关闭省电限制");
        if (missing.isEmpty()) return null;
        LinearLayout banner = Ui.row(activity);
        banner.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12),
                Ui.dp(activity, 14), Ui.dp(activity, 12));
        banner.setBackgroundResource(R.drawable.bg_card);
        banner.setClickable(true);
        banner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openSettings();
            }
        });
        View dot = Ui.dot(activity, Ui.ORANGE, 7);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                Ui.dp(activity, 7), Ui.dp(activity, 7));
        dotLp.rightMargin = Ui.dp(activity, 10);
        banner.addView(dot, dotLp);
        LinearLayout text = Ui.col(activity);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        banner.addView(text, textLp);
        text.addView(Ui.text(activity, "还差一步，自动记账没完全开启", 13f, Ui.INK, true));
        text.addView(Ui.text(activity, "未开启：" + join(missing), 11.5f, Ui.INK2, false));
        banner.addView(Ui.text(activity, "›", 20f, Ui.INK3, false));
        return banner;
    }

    private View payLaterCard(final Data d) {
        LinearLayout card = Ui.row(activity);
        card.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12),
                Ui.dp(activity, 14), Ui.dp(activity, 12));
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 10);
        lp.bottomMargin = Ui.dp(activity, 12);
        card.setLayoutParams(lp);
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPayLaterDialog(d);
            }
        });
        View dot = Ui.dot(activity, Ui.ACCENT, 7);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                Ui.dp(activity, 7), Ui.dp(activity, 7));
        dotLp.rightMargin = Ui.dp(activity, 10);
        card.addView(dot, dotLp);
        LinearLayout col = Ui.col(activity);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        card.addView(col, cl);
        col.addView(Ui.text(activity, d.payLater.size() + " 笔先用后付待付款", 13f, Ui.INK, true));
        col.addView(Ui.text(activity, "合计约 " + Money.yuan(d.payLaterTotal)
                + " · 实际扣款时记入账单", 11.5f, Ui.INK2, false));
        card.addView(Ui.text(activity, "查看 ›", 12f, Ui.INK, true));
        return card;
    }

    private void showPayLaterDialog(final Data d) {
        final List<PayLater> list = d.payLater;
        if (list.isEmpty()) {
            Ui.toast(activity, "没有待付款的先用后付");
            return;
        }
        LinearLayout box = Ui.col(activity);
        box.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 6),
                Ui.dp(activity, 16), Ui.dp(activity, 6));
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("先用后付 · 待付款")
                .setView(box)
                .setNegativeButton("关闭", null)
                .create();
        for (final PayLater p : list) {
            TextView row = Ui.text(activity, p.merchant + "  ¥" + Money.format(p.amountCents)
                    + "\n" + DateX.shortDate(p.createdAt) + " · " + Category.name(p.category)
                    + " · 点此处理", 12.5f, Ui.INK, true);
            row.setLineSpacing(Ui.dp(activity, 2), 1f);
            row.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10),
                    Ui.dp(activity, 12), Ui.dp(activity, 10));
            row.setBackground(Ui.round(activity, 0xFFF6F7F4, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(activity, 8);
            row.setLayoutParams(lp);
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    showPayLaterActions(p);
                }
            });
            box.addView(row);
        }
        dialog.show();
    }

    private void showPayLaterActions(final PayLater p) {
        String msg = p.merchant + "\n¥" + Money.format(p.amountCents)
                + " · " + Category.name(p.category)
                + "\n下单：" + DateX.dateTime(p.createdAt)
                + "\n\n实际付款后，知花也可以从银行/支付通知自动入账；"
                + "如果通知没来，可以在这里手动确认。";
        new AlertDialog.Builder(activity)
                .setTitle("先用后付")
                .setMessage(msg)
                .setNegativeButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        App.get(activity).db().deletePayLater(p.id);
                        host.refreshAll();
                    }
                })
                .setNeutralButton("取消", null)
                .setPositiveButton("标记已付款", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (p.amountCents <= 0) {
                            Ui.toast(activity, "金额未知，请先在账单里手动记一笔");
                            return;
                        }
                        App.get(activity).io().execute(new Runnable() {
                            @Override
                            public void run() {
                                Transaction t = new Transaction();
                                t.amountCents = p.amountCents;
                                t.merchant = p.merchant;
                                t.platform = p.platform;
                                t.category = p.category;
                                t.occurredAt = System.currentTimeMillis();
                                t.source = "paylater";
                                t.note = "先用后付转正式账单";
                                t.needsCategory = 0;
                                long id = App.get(activity).db().insertTransaction(t);
                                if (id > 0) {
                                    App.get(activity).db().markPayLaterPaid(p.id, id);
                                }
                                App.get(activity).main().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Ui.toast(activity, "已记入账单");
                                        host.refreshAll();
                                    }
                                });
                            }
                        });
                    }
                }).show();
    }

    private View pendingCard(final Data d) {
        LinearLayout card = Ui.row(activity);
        card.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12),
                Ui.dp(activity, 14), Ui.dp(activity, 12));
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 10);
        lp.bottomMargin = Ui.dp(activity, 12);
        card.setLayoutParams(lp);
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (d.pendingFirst != null) host.openEdit(d.pendingFirst);
                else host.selectTab(2);
            }
        });
        View dot = Ui.dot(activity, Ui.ORANGE, 7);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                Ui.dp(activity, 7), Ui.dp(activity, 7));
        dotLp.rightMargin = Ui.dp(activity, 10);
        card.addView(dot, dotLp);
        LinearLayout col = Ui.col(activity);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        card.addView(col, cl);
        col.addView(Ui.text(activity, d.pendingCount + " 笔消费待分类", 13f, Ui.INK, true));
        col.addView(Ui.text(activity, "补一个分类，知花会记住这个收款方", 11.5f, Ui.INK2, false));
        card.addView(Ui.text(activity, "去分类 ›", 12f, Ui.INK, true));
        return card;
    }

    private View insightCard(Data d) {
        LinearLayout card = Ui.card(activity);
        card.setLayoutParams(Ui.cardLp(activity));
        LinearLayout head = Ui.row(activity);
        head.addView(Ui.text(activity, "消费洞察", 16f, Ui.INK, true));
        head.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView more = Ui.text(activity, "查看分析 ›", 12f, Ui.INK2, true);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.selectTab(1);
            }
        });
        head.addView(more);
        card.addView(head);

        TextView summary = Ui.text(activity, d.report.habitSummary, 13.5f, Ui.INK, false);
        summary.setLineSpacing(Ui.dp(activity, 3), 1f);
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sumLp.topMargin = Ui.dp(activity, 8);
        card.addView(summary, sumLp);

        int shown = 0;
        for (StatsEngine.Insight in : d.report.insights) {
            if (shown >= 2) break;
            LinearLayout row = Ui.row(activity);
            row.setGravity(Gravity.TOP);
            View dot = Ui.dot(activity, in.tone == 2 ? Ui.ORANGE : Ui.INK3, 5);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                    Ui.dp(activity, 5), Ui.dp(activity, 5));
            dotLp.topMargin = Ui.dp(activity, 7);
            dotLp.rightMargin = Ui.dp(activity, 9);
            row.addView(dot, dotLp);
            LinearLayout col = Ui.col(activity);
            col.addView(Ui.text(activity, in.title, 12.5f, Ui.INK, true));
            TextView body = Ui.text(activity, in.text, 12f, Ui.INK2, false);
            body.setLineSpacing(Ui.dp(activity, 2), 1f);
            col.addView(body);
            row.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = Ui.dp(activity, 12);
            card.addView(row, rowLp);
            shown++;
        }
        return card;
    }

    private View impulseCard(Data d) {
        LinearLayout card = Ui.row(activity);
        card.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12),
                Ui.dp(activity, 14), Ui.dp(activity, 12));
        card.setBackgroundResource(R.drawable.bg_card);
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.selectTab(1);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(activity, 12);
        card.setLayoutParams(lp);
        View dot = Ui.dot(activity, Ui.GREEN, 7);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                Ui.dp(activity, 7), Ui.dp(activity, 7));
        dotLp.rightMargin = Ui.dp(activity, 10);
        card.addView(dot, dotLp);
        LinearLayout col = Ui.col(activity);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        card.addView(col, colLp);
        col.addView(Ui.text(activity, "本月冷静清单", 13f, Ui.INK, true));
        col.addView(Ui.text(activity, "暂缓 " + d.report.impulseCount + " 笔 · 约 "
                + Money.yuan(d.report.impulseSaved), 11.5f, Ui.INK2, false));
        card.addView(Ui.text(activity, "查看 ›", 12f, Ui.INK, true));
        return card;
    }

    private View recentSection(Data d) {
        LinearLayout section = Ui.col(activity);
        LinearLayout head = Ui.row(activity);
        head.addView(Ui.text(activity, "最近账单", 16f, Ui.INK, true));
        head.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView all = Ui.text(activity, "全部 ›", 12f, Ui.INK2, true);
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.selectTab(2);
            }
        });
        head.addView(all);
        section.addView(head);

        LinearLayout card = Ui.card(activity);
        card.setPadding(Ui.dp(activity, 4), Ui.dp(activity, 2),
                Ui.dp(activity, 4), Ui.dp(activity, 2));
        LinearLayout.LayoutParams cardLp = Ui.cardLp(activity);
        cardLp.topMargin = Ui.dp(activity, 8);
        section.addView(card, cardLp);

        if (d.recent.isEmpty()) {
            LinearLayout empty = Ui.col(activity);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(0, Ui.dp(activity, 24), 0, Ui.dp(activity, 24));
            TextView t1 = Ui.text(activity, "还没有账单", 15f, Ui.INK, true);
            t1.setGravity(Gravity.CENTER);
            empty.addView(t1);
            TextView t2 = Ui.text(activity, "开启通知读取后，银行消费会自动出现在这里", 12f, Ui.INK2, false);
            t2.setGravity(Gravity.CENTER);
            empty.addView(t2);
            TextView add = Ui.primaryButton(activity, "记第一笔");
            LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                    Ui.dp(activity, 150), Ui.dp(activity, 48));
            addLp.topMargin = Ui.dp(activity, 14);
            empty.addView(add, addLp);
            add.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.openAdd();
                }
            });
            card.addView(empty);
            return section;
        }

        for (int i = 0; i < d.recent.size(); i++) {
            final Transaction t = d.recent.get(i);
            card.addView(TransactionRow.build(activity, t, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.openEdit(t);
                }
            }));
            if (i < d.recent.size() - 1) {
                View line = new View(activity);
                line.setBackgroundColor(Ui.LINE);
                LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(activity, 0.7f)));
                lineLp.leftMargin = Ui.dp(activity, 16);
                lineLp.rightMargin = Ui.dp(activity, 16);
                card.addView(line, lineLp);
            }
        }
        return section;
    }

    private static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(" · ");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static class Data {
        long now;
        long dayTotal, weekTotal, monthTotal;
        int dayCount, weekCount, monthCount;
        long budget;
        List<Transaction> recent = new ArrayList<>();
        List<Impulse> impulses = new ArrayList<>();
        StatsEngine.Report report;
        boolean notifyAccess, accessibility, battery;
        int pendingCount;
        Transaction pendingFirst;
        List<PayLater> payLater = new ArrayList<>();
        long payLaterTotal;
    }
}

