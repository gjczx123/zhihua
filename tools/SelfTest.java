import com.zhihua.ledger.core.Categorizer;
import com.zhihua.ledger.core.NotificationParser;
import com.zhihua.ledger.core.StatsEngine;
import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.DateX;
import com.zhihua.ledger.data.Impulse;
import com.zhihua.ledger.data.Money;
import com.zhihua.ledger.data.Transaction;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SelfTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        test("标准拼多多通知", "您尾号1234的储蓄卡于10月18日17:09在拼多多消费人民币35.50元，余额1234.56元。",
                "com.chinamworld.main", 3550, "拼多多", "拼多多", 123456);
        test("消费支出短信", "【建设银行】您尾号5678的储蓄卡10月18日12:30消费支出人民币128.00元，活期余额5000.00元。",
                "sms:95533", 12800, "未识别商户", "", -1);
        test("支付宝渠道", "【建设银行】您尾号1234的储蓄卡10月18日17:09向支付宝-拼多多支付35.50元。",
                "com.chinamworld.main", 3550, "拼多多", "拼多多", -1);
        test("美团外卖", "您尾号1234的储蓄卡10月18日17:09在美团外卖消费32.50元",
                "com.chinamworld.main", 3250, "美团外卖", "美团", -1);
        test("千分位金额", "您尾号1234的储蓄卡10月18日17:09在淘宝消费人民币1,299.00元",
                "com.chinamworld.main", 129900, "淘宝", "淘宝", -1);
        test("商户标签优先", "您尾号1234的储蓄卡10月18日17:09在淘宝消费人民币299.00元，商户：优衣库",
                "com.chinamworld.main", 29900, "优衣库", "淘宝", -1);
        test("交易商户", "您尾号1234的储蓄卡10月18日17:09消费人民币35.50元，交易商户：肯德基",
                "com.chinamworld.main", 3550, "肯德基", "肯德基", -1);
        test("抖音商城", "您尾号1234的储蓄卡10月18日17:09在抖音商城消费人民币129.00元",
                "com.chinamworld.main", 12900, "抖音商城", "抖音", -1);
        test("优惠不误识别", "您尾号1234的储蓄卡10月18日17:09在拼多多支付人民币35.50元，已优惠5元",
                "com.chinamworld.main", 3550, "拼多多", "拼多多", -1);
        test("可用余额", "尾号1234储蓄卡10月18日17:09消费人民币35.50元，可用余额1000元",
                "com.chinamworld.main", 3550, "未识别商户", "", -1);
        test("财付通渠道", "【建设银行】您尾号1234的储蓄卡10月18日17:09向财付通-美团支付35.50元。",
                "com.chinamworld.main", 3550, "美团", "美团", -1);

        expectNull("退款不记", "您尾号1234的储蓄卡10月18日17:09退款人民币35.50元", "com.chinamworld.main");
        expectNull("收入不记", "【建设银行】您尾号1234的储蓄卡10月18日入账人民币100.00元", "sms:95533");
        expectNull("营销不记", "【建设银行】刷卡消费满1000元送好礼，活动报名中", "sms:95533");
        expectNull("支付宝包不记", "您尾号1234的储蓄卡10月18日17:09在拼多多消费人民币35.50元",
                "com.eg.android.AlipayGphone");
        expectNull("无卡号不记", "建设银行消费满1000元送好礼", "com.chinamworld.main");

        NotificationParser.Parsed p = NotificationParser.parse("账户变动通知",
                "您尾号1234的储蓄卡2026-10-18 17:09在拼多多消费人民币35.50元",
                "com.chinamworld.main", System.currentTimeMillis());
        check("完整日期时间解析", p != null && DateX.year(p.occurredAt) == 2026
                && DateX.month(p.occurredAt) == 10 && DateX.day(p.occurredAt) == 18);

        check("分类-拼多多", Category.SHOPPING.equals(Categorizer.categorize("拼多多", "拼多多", "", null).category));
        check("分类-美团", Category.FOOD.equals(Categorizer.categorize("美团外卖", "美团", "", null).category));
        check("分类-优衣库", Category.CLOTHES.equals(Categorizer.categorize("优衣库", "", "", null).category));
        check("分类-滴滴", Category.TRANSPORT.equals(Categorizer.categorize("滴滴出行", "滴滴", "", null).category));
        check("分类-抖币", Category.FUN.equals(Categorizer.categorize("抖音", "抖音", "抖币充值", null).category));
        check("分类-学习纠正", Category.STUDY.equals(Categorizer.categorize("星巴克", "", "", Category.STUDY).category));
        check("分类-科技OpenAI", Category.TECH.equals(Categorizer.categorize("OpenAI", "", "ChatGPT Plus", null).category));
        check("分类-科技云服务", Category.TECH.equals(Categorizer.categorize("阿里云", "", "云服务器", null).category));
        check("分类-科技数码", Category.TECH.equals(Categorizer.categorize("某数码店", "", "机械键盘", null).category));
        check("分类-话费", Category.DAILY.equals(Categorizer.categorize("中国移动", "", "话费充值", null).category));
        check("分类-未知", Category.OTHER.equals(Categorizer.categorize("某某小店", "", "", null).category));
        check("分类-收款方小吃", Category.FOOD.equals(Categorizer.categorize("测试小吃店", "微信支付", "", null).category));

        NotificationParser.Parsed wp = NotificationParser.parsePaymentApp("微信支付凭证",
                "支付成功，付款金额 ¥35.50，收款方：测试小吃店", "com.tencent.mm",
                System.currentTimeMillis());
        check("微信支付通知", wp != null && wp.amountCents == 3550
                && "测试小吃店".equals(wp.merchant) && "微信支付".equals(wp.platform)
                && Category.FOOD.equals(Categorizer.categorize(wp.merchant, wp.platform,
                wp.rawText, null).category));

        NotificationParser.Parsed ap = NotificationParser.parsePaymentApp("支付成功",
                "付款金额 66.00元，商户：滴滴出行", "com.eg.android.AlipayGphone",
                System.currentTimeMillis());
        check("支付宝支付通知", ap != null && ap.amountCents == 6600
                && "滴滴出行".equals(ap.merchant) && "支付宝".equals(ap.platform));

        StatsEngine.Report month = buildReport();
        check("月统计总支出", month.total == expectedMonthTotal());
        check("月统计分类", month.categories.size() >= 1 && month.categories.get(0).total > 0);
        check("月统计趋势桶", month.buckets.size() == 6);
        check("月统计洞察", !month.insights.isEmpty() && !month.advice.isEmpty());
        check("月预算", month.budget == 100000 && month.monthSpent == expectedMonthTotal());
        check("冷静清单", month.impulseCount == 1 && month.impulseSaved == 19900);
        check("日统计", DateX.DAY.equals(month.type) == false);

        StatsEngine.Report day = buildCustom(DateX.DAY);
        check("日统计桶", day.buckets.size() == 7);
        check("周统计桶", buildCustom(DateX.WEEK).buckets.size() == 8);
        check("年统计桶", buildCustom(DateX.YEAR).buckets.size() == 5);

        check("金额紧凑显示-千", "3,762".equals(Money.compact(376280)));
        check("金额紧凑显示-万", "1.2万".equals(Money.compact(1234500)));
        check("金额紧凑显示-亿", "1亿".equals(Money.compact(10000000000L)));
        check("分类数量", Category.KEYS.length == 12 && Category.valid(Category.TECH));

        List<Transaction> one = new ArrayList<>();
        long now = System.currentTimeMillis();
        long today = DateX.startOfDay(now);
        one.add(tx(today + 10 * 3600 * 1000L, 3550, "拼多多", "拼多多", Category.SHOPPING));
        StatsEngine.Report early = StatsEngine.build(DateX.MONTH, now, one,
                new ArrayList<Impulse>(), 0, 3550);
        check("少量记录不硬下结论", early.count == 1 && early.insights.size() <= 2
                && early.advice.size() <= 2);

        List<Transaction> platforms = new ArrayList<>();
        platforms.add(tx(today + 9 * 3600 * 1000L, 1000, "拼多多", "拼多多", Category.SHOPPING));
        platforms.add(tx(today + 10 * 3600 * 1000L, 1000, "拼多多", "拼多多", Category.SHOPPING));
        platforms.add(tx(today + 11 * 3600 * 1000L, 9000, "盒马", "盒马", Category.DAILY));
        StatsEngine.Report pr = StatsEngine.build(DateX.MONTH, now, platforms,
                new ArrayList<Impulse>(), 0, 11000);
        check("常买平台按笔数", "拼多多".equals(pr.topPlatform) && pr.topPlatformCount == 2);

        System.out.println("\n通过 " + passed + " 项，失败 " + failed + " 项");
        if (failed > 0) System.exit(1);
    }

    private static StatsEngine.Report buildReport() {
        return buildCustom(DateX.MONTH);
    }

    private static StatsEngine.Report buildCustom(String type) {
        long now = System.currentTimeMillis();
        long today = DateX.startOfDay(now);
        long month = DateX.startOfMonth(now);
        List<Transaction> list = new ArrayList<>();
        list.add(tx(today + 10 * 3600 * 1000L, 3000, "美团外卖", "美团", Category.FOOD));
        list.add(tx(today + 15 * 3600 * 1000L, 2000, "拼多多", "拼多多", Category.SHOPPING));
        list.add(tx(today - 24 * 3600 * 1000L, 1000, "滴滴", "滴滴", Category.TRANSPORT));
        if (DateX.day(now) >= 2) {
            list.add(tx(month + 24 * 3600 * 1000L, 5000, "优衣库", "", Category.CLOTHES));
        }
        list.add(tx(DateX.addMonths(month, -1) + 9 * 24 * 3600 * 1000L, 7000, "房租", "", Category.HOME));
        List<Impulse> impulses = new ArrayList<>();
        Impulse i = new Impulse();
        i.amountCents = 19900;
        i.decision = Impulse.ABANDONED;
        i.createdAt = today + 3600 * 1000L;
        impulses.add(i);
        long ms = DateX.startOfMonth(now);
        long monthSpent = 0;
        for (Transaction t : list) if (t.occurredAt >= ms && t.occurredAt < DateX.addMonths(ms, 1)) monthSpent += t.amountCents;
        return StatsEngine.build(type, now, list, impulses, 100000, monthSpent);
    }

    private static long expectedMonthTotal() {
        long now = System.currentTimeMillis();
        long month = DateX.startOfMonth(now);
        long end = DateX.addMonths(month, 1);
        long today = DateX.startOfDay(now);
        long total = 3000 + 2000 + 1000;
        if (DateX.day(now) >= 2) total += 5000;
        if (today - 24 * 3600 * 1000L < month) total -= 1000;
        for (long t : new long[]{today + 10 * 3600 * 1000L, today + 15 * 3600 * 1000L,
                today - 24 * 3600 * 1000L, month + 24 * 3600 * 1000L}) {
            if (t < month || t >= end) total -= 0;
        }
        return total;
    }

    private static Transaction tx(long time, long amount, String merchant, String platform, String category) {
        Transaction t = new Transaction();
        t.occurredAt = time;
        t.amountCents = amount;
        t.merchant = merchant;
        t.platform = platform;
        t.category = category;
        return t;
    }

    private static void test(String name, String text, String pkg, long amount, String merchant,
                             String platform, long balance) {
        NotificationParser.Parsed p = NotificationParser.parse("账户变动", text, pkg, System.currentTimeMillis());
        if (p == null) {
            check(name, false);
            return;
        }
        boolean ok = p.amountCents == amount && merchant.equals(p.merchant)
                && platform.equals(p.platform)
                && (balance < 0 || p.balanceCents == balance);
        if (!ok) {
            System.out.println("  实际: amount=" + p.amountCents + " merchant=" + p.merchant
                    + " platform=" + p.platform + " balance=" + p.balanceCents);
        }
        check(name, ok);
    }

    private static void expectNull(String name, String text, String pkg) {
        NotificationParser.Parsed p = NotificationParser.parse("账户变动", text, pkg, System.currentTimeMillis());
        check(name, p == null);
        if (p != null) System.out.println("  不应识别: amount=" + p.amountCents + " merchant=" + p.merchant);
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("OK   " + name);
        } else {
            failed++;
            System.out.println("FAIL " + name);
        }
    }
}
