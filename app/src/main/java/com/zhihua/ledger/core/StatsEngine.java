package com.zhihua.ledger.core;

import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.DateX;
import com.zhihua.ledger.data.Impulse;
import com.zhihua.ledger.data.Money;
import com.zhihua.ledger.data.Transaction;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 日/周/月/年统计、习惯总结与建议。全部在本地计算。 */
public final class StatsEngine {

    public static final class CatStat {
        public final String key;
        public final String name;
        public final String emoji;
        public final int color;
        public final long total;
        public final int count;
        public final double percent;

        CatStat(String key, long total, int count, double percent) {
            this.key = key;
            this.name = Category.name(key);
            this.emoji = Category.emoji(key);
            this.color = Category.color(key);
            this.total = total;
            this.count = count;
            this.percent = percent;
        }
    }

    public static final class Bucket {
        public final long start;
        public final String label;
        public final long total;
        public final boolean selected;

        Bucket(long start, String label, long total, boolean selected) {
            this.start = start;
            this.label = label;
            this.total = total;
            this.selected = selected;
        }
    }

    public static final class Insight {
        public final String title;
        public final String text;
        public final int tone; // 0 中性 1 好 2 提醒

        Insight(String title, String text, int tone) {
            this.title = title;
            this.text = text;
            this.tone = tone;
        }
    }

    public static final class Report {
        public String type;
        public long anchor;
        public long start;
        public long end;
        public String label = "";
        public String prevLabel = "";
        public long total;
        public int count;
        public long avg;
        public long max;
        public Transaction maxTx;
        public long prevTotal;
        public Integer changePercent;
        public boolean up;
        public long avgPerDay;
        public int days;
        public final List<CatStat> categories = new ArrayList<>();
        public final List<Bucket> buckets = new ArrayList<>();
        public final List<Insight> insights = new ArrayList<>();
        public final List<String> advice = new ArrayList<>();
        public String habitSummary = "";
        public long impulseSaved;
        public int impulseCount;
        public int impulseContinued;
        public long budget;
        public long monthSpent;
        public int budgetPercent;
        public int lateNightCount;
        public long lateNightTotal;
        public int smallCount;
        public long smallTotal;
        public int weekendTxCount;
        public long weekendDailyAvg;
        public long weekdayDailyAvg;
        public String topMerchant = "";
        public long topMerchantTotal;
        public int topMerchantCount;
        public String topPlatform = "";
        public long topPlatformTotal;
        public int topPlatformCount;
        public boolean empty;
    }

    private StatsEngine() {
    }

    public static Report build(String type, long anchor, List<Transaction> all,
                               List<Impulse> impulses, long monthlyBudget) {
        return build(type, anchor, all, impulses, monthlyBudget, -1);
    }

    public static Report build(String type, long anchor, List<Transaction> all,
                               List<Impulse> impulses, long monthlyBudget, long monthSpentOverride) {
        if (all == null) all = Collections.emptyList();
        if (impulses == null) impulses = Collections.emptyList();
        DateX.Period p = DateX.period(type, anchor);
        Report r = new Report();
        r.type = type;
        r.anchor = anchor;
        r.start = p.start;
        r.end = p.end;
        r.label = p.label;
        r.prevLabel = prevLabel(type);

        List<Transaction> current = filter(all, p.start, p.end);
        List<Transaction> previous = filter(all, p.prevStart, p.prevEnd);
        r.count = current.size();
        r.empty = current.isEmpty();
        r.total = sum(current);
        r.prevTotal = sum(previous);
        r.avg = r.count == 0 ? 0 : r.total / r.count;
        for (Transaction t : current) {
            if (r.maxTx == null || t.amountCents > r.max) {
                r.max = t.amountCents;
                r.maxTx = t;
            }
        }
        r.days = DateX.daysInPeriod(p, System.currentTimeMillis());
        r.avgPerDay = r.days <= 0 ? 0 : r.total / r.days;
        if (r.prevTotal > 0) {
            r.changePercent = (int) Math.round((r.total - r.prevTotal) * 100.0 / r.prevTotal);
            r.up = r.total > r.prevTotal;
        } else if (r.total > 0) {
            r.changePercent = null;
        }

        buildCategories(r, current);
        buildBuckets(r, type, anchor, all);
        buildMerchant(r, current);
        buildPlatform(r, current);
        buildTimePatterns(r, current, p);
        buildImpulses(r, impulses, p);
        buildBudget(r, all, monthlyBudget, monthSpentOverride);
        buildText(r, current);
        return r;
    }

    private static void buildCategories(Report r, List<Transaction> current) {
        Map<String, Long> totals = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Transaction t : current) {
            String key = Category.valid(t.category) ? t.category : Category.OTHER;
            Long v = totals.get(key);
            totals.put(key, (v == null ? 0 : v) + t.amountCents);
            Integer c = counts.get(key);
            counts.put(key, (c == null ? 0 : c) + 1);
        }
        List<CatStat> list = new ArrayList<>();
        for (Map.Entry<String, Long> e : totals.entrySet()) {
            int count = counts.get(e.getKey()) == null ? 0 : counts.get(e.getKey());
            double pct = r.total == 0 ? 0 : e.getValue() * 100.0 / r.total;
            list.add(new CatStat(e.getKey(), e.getValue(), count, pct));
        }
        Collections.sort(list, new Comparator<CatStat>() {
            @Override
            public int compare(CatStat a, CatStat b) {
                return Long.compare(b.total, a.total);
            }
        });
        r.categories.addAll(list);
    }

    private static void buildBuckets(Report r, String type, long anchor, List<Transaction> all) {
        int count = DateX.DAY.equals(type) ? 7 : DateX.WEEK.equals(type) ? 8
                : DateX.MONTH.equals(type) ? 6 : 5;
        List<Long> starts = DateX.bucketStarts(type, anchor, count);
        for (int i = 0; i < starts.size(); i++) {
            long start = starts.get(i);
            long end = DateX.shift(type, start, 1);
            long total = sum(filter(all, start, end));
            r.buckets.add(new Bucket(start, DateX.bucketLabel(type, start), total,
                    i == starts.size() - 1));
        }
    }

    private static void buildMerchant(Report r, List<Transaction> current) {
        Map<String, Long> totals = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Transaction t : current) {
            String key = t.merchant == null || t.merchant.length() == 0 ? "未识别商户" : t.merchant;
            Long v = totals.get(key);
            totals.put(key, (v == null ? 0 : v) + t.amountCents);
            Integer c = counts.get(key);
            counts.put(key, (c == null ? 0 : c) + 1);
        }
        long best = -1;
        for (Map.Entry<String, Long> e : totals.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                r.topMerchant = e.getKey();
                r.topMerchantTotal = e.getValue();
                r.topMerchantCount = counts.get(e.getKey()) == null ? 0 : counts.get(e.getKey());
            }
        }
    }

    private static void buildPlatform(Report r, List<Transaction> current) {
        Map<String, Long> totals = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Transaction t : current) {
            String p = t.platform == null ? "" : t.platform.trim();
            if (p.length() == 0) continue;
            Long v = totals.get(p);
            totals.put(p, (v == null ? 0 : v) + t.amountCents);
            Integer c = counts.get(p);
            counts.put(p, (c == null ? 0 : c) + 1);
        }
        int bestCount = -1;
        long bestTotal = -1;
        for (Map.Entry<String, Long> e : totals.entrySet()) {
            int count = counts.get(e.getKey()) == null ? 0 : counts.get(e.getKey());
            if (count > bestCount || (count == bestCount && e.getValue() > bestTotal)) {
                bestCount = count;
                bestTotal = e.getValue();
                r.topPlatform = e.getKey();
                r.topPlatformTotal = e.getValue();
                r.topPlatformCount = count;
            }
        }
    }

    private static void buildTimePatterns(Report r, List<Transaction> current, DateX.Period p) {        for (Transaction t : current) {
            int h = DateX.hour(t.occurredAt);
            if (h >= 22 || h < 3) {
                r.lateNightCount++;
                r.lateNightTotal += t.amountCents;
            }
            if (t.amountCents <= 2000) {
                r.smallCount++;
                r.smallTotal += t.amountCents;
            }
            if (DateX.isWeekend(t.occurredAt)) r.weekendTxCount++;
        }
        if (DateX.DAY.equals(p.type)) return;

        Map<Long, Long> daily = new HashMap<>();
        for (Transaction t : current) {
            long day = DateX.startOfDay(t.occurredAt);
            Long v = daily.get(day);
            daily.put(day, (v == null ? 0 : v) + t.amountCents);
        }
        long now = System.currentTimeMillis();
        long end = Math.min(p.end, now);
        long cursor = p.start;
        long weekendTotal = 0;
        long weekdayTotal = 0;
        int weekendDays = 0;
        int weekdayDays = 0;
        while (cursor < end && cursor < p.end) {
            long day = DateX.startOfDay(cursor);
            long amount = daily.get(day) == null ? 0 : daily.get(day);
            if (DateX.isWeekend(day)) {
                weekendTotal += amount;
                weekendDays++;
            } else {
                weekdayTotal += amount;
                weekdayDays++;
            }
            cursor = DateX.addDays(cursor, 1);
        }
        r.weekendDailyAvg = weekendDays == 0 ? 0 : weekendTotal / weekendDays;
        r.weekdayDailyAvg = weekdayDays == 0 ? 0 : weekdayTotal / weekdayDays;
    }

    private static void buildImpulses(Report r, List<Impulse> impulses, DateX.Period p) {
        for (Impulse i : impulses) {
            if (i.createdAt < p.start || i.createdAt >= p.end) continue;
            if (Impulse.ABANDONED.equals(i.decision)) {
                r.impulseCount++;
                r.impulseSaved += i.amountCents;
            } else if (Impulse.CONTINUED.equals(i.decision)) {
                r.impulseContinued++;
            }
        }
    }

    private static void buildBudget(Report r, List<Transaction> all, long monthlyBudget,
                                    long monthSpentOverride) {
        r.budget = monthlyBudget;
        if (monthSpentOverride >= 0) {
            r.monthSpent = monthSpentOverride;
        } else {
            long ms = DateX.startOfMonth(System.currentTimeMillis());
            r.monthSpent = sum(filter(all, ms, DateX.addMonths(ms, 1)));
        }
        if (monthlyBudget > 0) {
            r.budgetPercent = (int) Math.round(r.monthSpent * 100.0 / monthlyBudget);
        }
    }

    private static void buildText(Report r, List<Transaction> current) {
        if (r.empty) {
            r.habitSummary = "这个周期还没有支出。开启建行通知读取，或先手动记一笔，习惯分析会从这里开始。";
            r.insights.add(new Insight("还没有数据", "有了 3 笔以上记录，就能看出分类占比和消费节奏。", 0));
            r.advice.add("先在「设置」开启通知读取，再正常用建行储蓄卡消费即可自动记账。");
            r.advice.add("如果建行 App 的通知格式特殊，可在「解析测试」里粘贴一条通知，立刻校准。");
            return;
        }

        CatStat top = r.categories.isEmpty() ? null : r.categories.get(0);
        if (r.count < 3) {
            r.habitSummary = "目前只有 " + r.count + " 笔记录，先积累几天；分类占比和消费节奏会随着记录变多而更准确。";
            r.insights.add(new Insight("记录还很少", "再记 2 笔，就能开始看分类和平台占比。", 0));
            if (r.budget > 0 && r.budgetPercent >= 80) {
                r.insights.add(new Insight("预算提醒",
                        "本月已用 " + r.budgetPercent + "%，剩余 " + Money.yuan(Math.max(0, r.budget - r.monthSpent)) + "。", 2));
            }
            r.advice.add("先正常用建行储蓄卡消费，保持通知读取开启；3 笔之后再来看看分类和平台。");
            if (r.budget <= 0) {
                r.advice.add("设一个月预算，哪怕只是大概的数，后面的建议会更有针对性。");
            }
            return;
        }
        StringBuilder habit = new StringBuilder();
        if (top != null) {
            habit.append("你的支出主要集中在「").append(top.name).append("」，占 ")
                    .append(Math.round(top.percent)).append("%。");
        }
        if (r.weekendTxCount >= 3 && r.count >= 5
                && r.weekdayDailyAvg > 0 && r.weekendDailyAvg > r.weekdayDailyAvg * 1.35) {
            habit.append("周末日均是工作日的 ")
                    .append(String.format(Locale.CHINA, "%.1f", (double) r.weekendDailyAvg / r.weekdayDailyAvg))
                    .append(" 倍。");
        } else if (r.lateNightCount >= 3 && r.total > 0 && r.lateNightTotal * 100 / r.total >= 15) {
            habit.append("有 ").append(r.lateNightCount).append(" 笔发生在 22 点后。");
        } else if (r.topMerchantCount >= 3) {
            habit.append("最常去的是「").append(r.topMerchant).append("」。");
        }
        if (r.topPlatform.length() > 0 && r.topPlatformCount >= 2 && r.total > 0
                && r.topPlatformTotal * 100 / r.total >= 25) {
            habit.append("常买平台是「").append(r.topPlatform).append("」，占 ")
                    .append(Math.round(r.topPlatformTotal * 100.0 / r.total)).append("%。");
        }
        r.habitSummary = habit.toString();

        // 1. 环比
        if (r.changePercent != null && Math.abs(r.changePercent) >= 15) {
            boolean up = r.changePercent > 0;
            r.insights.add(new Insight(up ? "支出比上期高 " + r.changePercent + "%"
                            : "支出比上期低 " + Math.abs(r.changePercent) + "%",
                    "上期 " + Money.yuan(r.prevTotal) + "，本期 " + Money.yuan(r.total) + "。"
                            + (top != null ? "主要花在「" + top.name + "」。" : ""),
                    up ? 2 : 1));
        } else if (r.prevTotal == 0) {
            r.insights.add(new Insight("新周期已开始", "这是这个周期第一次有记录，先积累几天再看趋势。", 0));
        }

        // 2. 分类集中度
        if (top != null && top.percent >= 45 && top.total >= 5000) {
            r.insights.add(new Insight("「" + top.name + "」占了大头",
                    "本期 " + Math.round(top.percent) + "%，共 " + Money.yuan(top.total)
                            + "，平均每笔 " + Money.yuan(top.total / Math.max(1, top.count)) + "。",
                    Category.SHOPPING.equals(top.key) || Category.FUN.equals(top.key) ? 2 : 0));
        }

        // 3. 预算
        if (r.budget > 0 && r.budgetPercent >= 80) {
            long left = r.budget - r.monthSpent;
            r.insights.add(new Insight(r.budgetPercent >= 100 ? "本月已超预算" : "预算快用完了",
                    "本月已用 " + r.budgetPercent + "%，" + (left >= 0 ? "还剩 " + Money.yuan(left) : "超出 " + Money.yuan(-left)) + "。",
                    2));
        }

        // 4. 深夜
        if (r.lateNightCount >= 3 && r.total > 0 && r.lateNightTotal * 100 / r.total >= 15) {
            r.insights.add(new Insight("深夜消费偏多",
                    r.lateNightCount + " 笔发生在 22:00–03:00，共 " + Money.yuan(r.lateNightTotal) + "。", 2));
        }

        // 5. 购物平台
        long shoppingPlatform = 0;
        for (CatStat c : r.categories) {
            if (Category.SHOPPING.equals(c.key) || Category.CLOTHES.equals(c.key)
                    || Category.FUN.equals(c.key)) {
                shoppingPlatform += c.total;
            }
        }
        if (r.total > 0 && r.count >= 3 && shoppingPlatform * 100 / r.total >= 50) {
            r.insights.add(new Insight("购物类支出占了一半以上",
                    "购物、衣服、娱乐合计 " + Money.yuan(shoppingPlatform) + "，占 "
                            + Math.round(shoppingPlatform * 100.0 / r.total) + "%。", 2));
        }

        // 5.5 常买平台
        if (r.topPlatform.length() > 0 && r.total > 0 && r.topPlatformCount >= 2
                && r.topPlatformTotal * 100 / r.total >= 25) {
            r.insights.add(new Insight("「" + r.topPlatform + "」是主要出口",
                    r.topPlatformCount + " 笔，共 " + Money.yuan(r.topPlatformTotal) + "，占 "
                            + Math.round(r.topPlatformTotal * 100.0 / r.total) + "%。", 2));
        }

        // 6. 小额高频
        if (r.smallCount >= 8 && r.total > 0 && r.smallTotal * 100 / r.total >= 10) {
            r.insights.add(new Insight("小额高频",
                    "≤¥20 的消费有 " + r.smallCount + " 笔，合计 " + Money.yuan(r.smallTotal)
                            + "，单笔不起眼，加起来不少。", 2));
        }

        // 7. 周末
        if (r.weekendTxCount >= 3 && r.count >= 5
                && r.weekdayDailyAvg > 0 && r.weekendDailyAvg > r.weekdayDailyAvg * 1.35) {
            r.insights.add(new Insight("周末更容易花钱",
                    "周末日均 " + Money.yuan(r.weekendDailyAvg) + "，工作日日均 "
                            + Money.yuan(r.weekdayDailyAvg) + "。", 2));
        }

        // 8. 冷静清单
        if (r.impulseCount > 0) {
            r.insights.add(new Insight("冷静清单生效",
                    "暂缓了 " + r.impulseCount + " 笔，约 " + Money.yuan(r.impulseSaved) + "。", 1));
        }

        while (r.insights.size() > 4) r.insights.remove(r.insights.size() - 1);

        // 建议
        if (r.budget > 0 && r.budgetPercent >= 80) {
            Calendar nowCal = Calendar.getInstance(Locale.CHINA);
            int leftDays = Math.max(1, nowCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    - nowCal.get(Calendar.DAY_OF_MONTH) + 1);
            long left = Math.max(0, r.budget - r.monthSpent);
            r.advice.add("本月还剩约 " + leftDays + " 天、" + Money.yuan(left)
                    + " 可花，日均控制在 " + Money.yuan(left / leftDays) + " 以内；先暂停非必要购物。");
        }
        boolean shoppingAdvice = false;
        if (top != null && (Category.SHOPPING.equals(top.key) || Category.FOOD.equals(top.key)
                || Category.FUN.equals(top.key))) {
            if (Category.FOOD.equals(top.key)) {
                r.advice.add("吃喝是最大项（" + Money.yuan(top.total) + "）。给外卖设一个周预算，"
                        + "每周至少 3 天自己做饭或吃食堂，通常能省下 30%。");
            } else {
                shoppingAdvice = true;
                r.advice.add("把拼多多/抖音的付款前提醒保持开启：单价超过 ¥50 的非必需品，"
                        + "先放进冷静清单 24 小时，第二天还想要再买。");
            }
        }
        if (r.lateNightCount >= 3) {
            r.advice.add("22:30 后把购物 App 移出首屏，或开启勿扰模式。深夜下单前先睡一觉，"
                    + "第二天再看，能过滤掉大部分冲动。");
        }
        if (isShoppingPlatform(r.topPlatform) && !shoppingAdvice && r.total > 0
                && r.topPlatformTotal * 100 / r.total >= 25) {
            r.advice.add("「" + r.topPlatform + "」占 " + Math.round(r.topPlatformTotal * 100.0 / r.total)
                    + "%。付款前让知花多问一句；单价超过 ¥50 的非必需品，先放冷静清单 24 小时。");
        }
        if (r.smallCount >= 8) {
            long weekly = Math.max(1, r.smallCount / 4);
            r.advice.add("≤¥20 的小额消费有 " + r.smallCount + " 笔。试试每周只集中买 " + weekly
                    + " 次，把零散支出合并成一笔计划支出。");
        }
        if (r.weekendTxCount >= 3 && r.count >= 5
                && r.weekdayDailyAvg > 0 && r.weekendDailyAvg > r.weekdayDailyAvg * 1.35) {
            r.advice.add("周末出门前先定一个上限（比如 " + Money.yuan(Math.max(5000, r.weekendDailyAvg))
                    + "），只带这张预算，花完就回家。");
        }
        if (r.impulseCount > 0 && r.advice.size() < 2) {
            r.advice.add("冷静清单已经帮你暂缓 " + Money.yuan(r.impulseSaved)
                    + "。每周回看一次清单，把真正需要的再买，不需要的删掉。");
        }
        if (r.advice.size() < 2 && r.categories.size() > 1) {
            CatStat second = r.categories.get(1);
            if (Category.CLOTHES.equals(second.key)) {
                r.advice.add("衣服是第二大项（" + Money.yuan(second.total) + "）。买之前算一下每次穿着成本，"
                        + "超过 ¥100 的单品先放冷静清单 24 小时。");
            } else if (Category.FOOD.equals(second.key)) {
                r.advice.add("吃喝是第二大项（" + Money.yuan(second.total) + "）。给外卖设周预算，"
                        + "每周至少 3 天自己做饭或吃食堂。");
            } else if (Category.SHOPPING.equals(second.key)) {
                r.advice.add("购物是第二大项（" + Money.yuan(second.total) + "）。把想买的东西先放购物车，"
                        + "24 小时后再决定，通常能删掉一半。");
            }
        }
        if (r.advice.size() < 2 && top != null && Category.HOME.equals(top.key)) {
            r.advice.add("居住是最大项（" + Math.round(top.percent) + "%），属于相对固定的支出。"
                    + "先把房租/水电单独留出，再用剩下的钱安排吃喝和购物，月底就不必靠省饭钱补窟窿。");
        }
        if (r.advice.isEmpty()) {
            r.advice.add("这个周期花得很稳。继续保持记账，月底用「年」视图看看长期趋势。");
            r.advice.add("给月预算留出 20% 的弹性空间，遇到人情、医疗等突发支出时不至于打乱节奏。");
        } else if (r.advice.size() < 2) {
            r.advice.add("把月预算按 50% 必要开支、30% 想要、20% 储蓄来分；先存后花，比月底再省更容易。");
        }
        while (r.advice.size() > 3) r.advice.remove(r.advice.size() - 1);
    }

    private static boolean isShoppingPlatform(String p) {
        return "拼多多".equals(p) || "抖音".equals(p) || "淘宝".equals(p) || "天猫".equals(p)
                || "京东".equals(p) || "唯品会".equals(p) || "小红书".equals(p);
    }

    private static String prevLabel(String type) {
        if (DateX.DAY.equals(type)) return "昨天";
        if (DateX.WEEK.equals(type)) return "上周";
        if (DateX.MONTH.equals(type)) return "上月";
        return "去年";
    }

    private static List<Transaction> filter(List<Transaction> list, long start, long end) {
        List<Transaction> out = new ArrayList<>();
        for (Transaction t : list) {
            if (t.occurredAt >= start && t.occurredAt < end) out.add(t);
        }
        return out;
    }

    private static long sum(List<Transaction> list) {
        long s = 0;
        for (Transaction t : list) s += t.amountCents;
        return s;
    }
}
