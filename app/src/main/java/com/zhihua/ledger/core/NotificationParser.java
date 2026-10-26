package com.zhihua.ledger.core;

import com.zhihua.ledger.data.Money;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 建设银行储蓄卡消费通知解析器。
 * 兼容手机银行通知、95533 短信、以及“在商户消费人民币 xx 元”这类常见格式。
 * 纯 Java 实现，方便在电脑上直接跑用例。
 */
public final class NotificationParser {

    public static final class Parsed {
        public long amountCents;
        public String merchant = "";
        public String platform = "";
        public long occurredAt;
        public String cardTail = "";
        public long balanceCents = -1;
        public String rawText = "";
        public String fingerprint = "";
        public String title = "";
    }

    private static final String[] TRUSTED_BANK_PKGS = {
            "com.chinamworld.main",
            "com.ccb.longjiLife",
            "com.ccb.mobilebank",
            "com.chinamworld.ccb",
            "com.icbc",
            "com.android.bankabc",
            "com.chinamworld.bocmbci",
            "cmb.pb",
            "com.bankcomm.Bankcomm",
            "cn.com.spdb.mobilebank.per",
            "cn.com.cmbc.newmbank",
            "com.ecitic.bank.mobile",
            "com.cebbank.mobile.cemb",
            "com.yitong.mbank.psbc",
            "com.pingan.paces.ccms"
    };

    private static final String[] BANK_NAMES = {
            "建设银行", "工商银行", "农业银行", "中国银行", "交通银行", "招商银行",
            "邮储银行", "邮政储蓄", "浦发银行", "中信银行", "民生银行", "兴业银行",
            "光大银行", "华夏银行", "广发银行", "平安银行", "上海银行", "北京银行",
            "农商银行", "农村商业银行", "信用社"
    };

    private static final String[] PAYMENT_BLACKLIST = {
            "com.eg.android.AlipayGphone", "com.tencent.mm", "com.taobao.taobao",
            "com.xunmeng.pinduoduo", "com.ss.android.ugc.aweme", "com.jingdong.app.mall",
            "com.sina.weibo", "com.tencent.mobileqq", "com.unionpay"
    };

    private static final String[] SPECIFIC_PLATFORMS = {
            "拼多多", "抖音", "淘宝", "天猫", "京东", "美团", "饿了么", "盒马", "叮咚买菜",
            "多多买菜", "滴滴", "12306", "唯品会", "小红书", "得物", "闲鱼", "苏宁", "山姆",
            "肯德基", "麦当劳", "星巴克", "瑞幸", "蜜雪冰城", "喜茶", "奈雪", "海底捞"
    };

    private static final String[] CHANNEL_PLATFORMS = {"支付宝", "微信支付", "微信", "财付通", "云闪付", "银联"};

    private static final String[] GENERIC_TITLES = {
            "账户变动", "账户变动通知", "交易提醒", "消费提醒", "动账通知", "通知", "消息",
            "建设银行", "中国建设银行", "建行", "短信", "新消息"
    };

    private static final Pattern AMOUNT_CTX = Pattern.compile(
            "(消费|支出|支付|付款|扣款|交易金额|消费金额|支付金额|付款金额|订单金额)"
                    + "[^0-9\\n]{0,20}(?:人民币|RMB|CNY|¥)?\\s*"
                    + "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(元)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AMOUNT_CURRENCY = Pattern.compile(
            "(?:人民币|RMB|CNY|¥)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AMOUNT_DECIMAL = Pattern.compile(
            "([0-9][0-9,]*\\.[0-9]{1,2})\\s*元");

    private static final Pattern FULL_TIME = Pattern.compile(
            "(\\d{4})\\s*[-/年.]\\s*(\\d{1,2})\\s*[-/月.]\\s*(\\d{1,2})\\s*日?\\s*"
                    + "(\\d{1,2})\\s*[:：时]\\s*(\\d{2})");

    private static final Pattern MONTH_DAY_TIME = Pattern.compile(
            "(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日\\s*"
                    + "(\\d{1,2})\\s*[:：时]\\s*(\\d{2})");

    private static final Pattern SHORT_MONTH_DAY_TIME = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*[-/]\\s*(\\d{1,2})\\s+"
                    + "(\\d{1,2})\\s*[:：]\\s*(\\d{2})(?!\\d)");

    private static final Pattern TIME_ONLY = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*[:：]\\s*(\\d{2})(?!\\d)");

    private static final Pattern CARD_TAIL = Pattern.compile(
            "尾号\\s*(?:为)?\\s*(\\d{4})|(\\d{4})\\s*的\\s*(?:储蓄卡|借记卡|龙卡)");

    private static final Pattern BALANCE = Pattern.compile(
            "余额\\s*(?:为|是|人民币|¥)?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)");

    private static final Pattern MERCHANT_LABEL = Pattern.compile(
            "(?:商户名称|商户|收款方|交易对方|对方户名|消费平台|支付平台|交易平台|平台)"
                    + "\\s*[:：]\\s*([^\\n，,。;；]{1,40})");

    private static final Pattern MERCHANT_ZAI = Pattern.compile(
            "在\\s*([^\\n，,。;；]{1,40}?)\\s*(?:消费|支付|支出|付款|扣款)");

    private static final Pattern MERCHANT_XIANG = Pattern.compile(
            "向\\s*([^\\n，,。;；]{1,40}?)\\s*(?:支付|付款|转账)");

    private NotificationParser() {
    }

    public static Parsed parse(String title, String text, String pkg, long postTime) {
        String t = title == null ? "" : title.trim();
        String body = text == null ? "" : text.trim();
        String raw = normalize(t + "\n" + body);
        if (raw.length() == 0) return null;
        if (postTime <= 0) postTime = System.currentTimeMillis();

        if (!isTrustedSource(pkg, raw)) return null;
        if (!hasAny(raw, "尾号", "储蓄卡", "借记卡", "龙卡")) return null;
        if (isRefundOrIncome(raw)) return null;
        if (isPromotion(raw)) return null;
        if (!hasTransactionWord(raw)) return null;

        long amount = extractAmount(raw);
        if (amount <= 0) return null;

        long occurredAt = extractTime(raw, postTime);
        String cardTail = extractCardTail(raw);
        long balance = extractBalance(raw);
        String merchant = extractMerchant(raw, t);
        String platform = extractPlatform(raw, merchant);
        if (merchant.length() == 0) merchant = platform.length() > 0 ? platform : "未识别商户";

        Parsed p = new Parsed();
        p.amountCents = amount;
        p.merchant = merchant;
        p.platform = platform;
        p.occurredAt = occurredAt;
        p.cardTail = cardTail;
        p.balanceCents = balance;
        p.rawText = raw.length() > 4000 ? raw.substring(0, 4000) : raw;
        p.title = t;
        p.fingerprint = fingerprint(pkg, amount, occurredAt, merchant);
        return p;
    }

    /**
     * 微信 / 支付宝自己的“支付成功”通知。作为银行卡通知的补充：
     * 只抓支付结果，不抓聊天消息；抓到后仍会和银行卡通知按金额+时间去重。
     */
    public static Parsed parsePaymentApp(String title, String text, String pkg, long postTime) {
        if (!"com.tencent.mm".equals(pkg) && !"com.eg.android.AlipayGphone".equals(pkg)) return null;
        String t = title == null ? "" : title.trim();
        String body = text == null ? "" : text.trim();
        String raw = normalize(t + "\n" + body);
        if (raw.length() == 0) return null;
        if (postTime <= 0) postTime = System.currentTimeMillis();
        if (!hasAny(raw, "支付成功", "付款成功", "交易成功", "已支付", "支付凭证")) return null;
        if (hasAny(raw, "退款", "到账", "红包", "转账", "提现", "收入", "入账")) return null;
        if (raw.contains("收款") && !raw.contains("收款方")) return null;
        if (!hasAny(raw, "¥", "￥", "元", "人民币")) return null;

        long amount = extractAmount(raw);
        if (amount <= 0) return null;
        String merchant = extractPaymentMerchant(raw, t);
        if (merchant.length() == 0) merchant = "未识别收款方";
        String platform = "com.tencent.mm".equals(pkg) ? "微信支付" : "支付宝";

        Parsed p = new Parsed();
        p.amountCents = amount;
        p.merchant = merchant;
        p.platform = platform;
        p.occurredAt = postTime;
        p.cardTail = "";
        p.balanceCents = -1;
        p.rawText = raw.length() > 4000 ? raw.substring(0, 4000) : raw;
        p.title = t;
        p.fingerprint = fingerprint(pkg, amount, p.occurredAt, merchant);
        return p;
    }

    public static boolean looksPaymentAppLike(String title, String text, String pkg) {
        if (!"com.tencent.mm".equals(pkg) && !"com.eg.android.AlipayGphone".equals(pkg)) return false;
        String raw = normalize((title == null ? "" : title) + "\n" + (text == null ? "" : text));
        return hasAny(raw, "支付成功", "付款成功", "支付凭证", "交易成功", "已支付")
                && hasAny(raw, "¥", "￥", "元", "人民币");
    }

    private static String extractPaymentMerchant(String raw, String title) {
        Matcher label = MERCHANT_LABEL.matcher(raw);
        if (label.find()) {
            String v = cleanMerchant(label.group(1));
            if (v.length() > 0) return v;
        }
        Matcher pay = Pattern.compile("(?:收款方|商户|付款给|支付给)\\s*[:：]?\\s*([^\\n，,。;；]{1,30})")
                .matcher(raw);
        if (pay.find()) {
            String v = cleanMerchant(pay.group(1));
            if (v.length() > 0) return v;
        }
        Matcher xiang = Pattern.compile("向\\s*([^\\n，,。;；]{1,30}?)\\s*(?:付款|支付)")
                .matcher(raw);
        if (xiang.find()) {
            String v = cleanMerchant(xiang.group(1));
            if (v.length() > 0) return v;
        }
        String tt = cleanMerchant(title);
        if (tt.length() > 0 && !isGenericTitle(tt) && !tt.contains("微信支付")
                && !tt.contains("支付宝") && !tt.contains("支付成功") && tt.length() <= 24) {
            return tt;
        }
        return "";
    }

    /** 用于“最近通知”调试：看起来像建行动账通知，但未必能解析出金额。 */
    public static boolean looksBankLike(String title, String text, String pkg) {
        String raw = normalize((title == null ? "" : title) + "\n" + (text == null ? "" : text));
        if (!isTrustedSource(pkg, raw)) return false;
        return hasAny(raw, "建设银行", "工商银行", "农业银行", "中国银行", "交通银行",
                "招商银行", "邮储银行", "储蓄卡", "借记卡", "龙卡", "尾号");
    }

    /** 短信发件人是否是银行（955xx / 96xxx / 名称含“银行”），且正文像动账短信。 */
    public static boolean looksLikeBankSms(String address, String body) {
        if (address == null || body == null) return false;
        String a = address.trim();
        boolean sender = a.matches("\\d{5}") || a.contains("银行") || a.contains("955")
                || a.contains("建行") || a.contains("工行") || a.contains("农行")
                || a.contains("中行") || a.contains("招行") || a.contains("邮储");
        if (!sender) return false;
        String raw = normalize(body);
        return hasAny(raw, BANK_NAMES) || raw.contains("银行")
                || raw.contains("储蓄卡") || raw.contains("借记卡") || raw.contains("信用卡");
    }

    public static boolean isTrustedSource(String pkg, String raw) {
        if (pkg == null) pkg = "";
        for (String p : TRUSTED_BANK_PKGS) {
            if (p.equals(pkg)) return true;
        }
        if (pkg.startsWith("sms:")) return true;
        if (pkg.contains("chinamworld") || pkg.contains("ccb")) return true;
        if (isPaymentApp(pkg)) return false;
        // 兜底：其他银行 App / 短信 / 厂商通知中心转发的动账通知。
        return hasAny(raw, BANK_NAMES)
                && raw.contains("尾号")
                && (raw.contains("储蓄卡") || raw.contains("借记卡") || raw.contains("龙卡"));
    }

    private static boolean isPaymentApp(String pkg) {
        for (String p : PAYMENT_BLACKLIST) {
            if (p.equals(pkg)) return true;
        }
        return false;
    }

    public static boolean hasTransactionWord(String raw) {
        return hasAny(raw, "消费", "支出", "支付", "付款", "扣款");
    }

    private static boolean isPromotion(String raw) {
        if (hasAny(raw, "送好礼", "刷卡有礼", "笔笔返", "有机会", "最高可", "报名", "领取",
                "抽奖", "邀请好友", "消费满") && !hasAny(raw, "消费人民币", "消费支出",
                "支付人民币", "付款人民币", "扣款人民币", "交易金额")) {
            return true;
        }
        boolean promo = hasAny(raw, "优惠券", "满减", "立减", "抽奖", "积分兑换", "理财",
                "基金", "保险", "贷款", "提额", "邀请", "报名", "领取", "活动", "分期");
        if (!promo) return false;
        return !hasAny(raw, "消费", "支出", "支付成功", "付款成功", "扣款");
    }

    private static boolean isRefundOrIncome(String raw) {
        if (hasAny(raw, "退款", "退货", "冲正", "撤销")) return true;
        boolean income = hasAny(raw, "入账", "收入", "转入", "存入", "利息", "工资", "报销");
        return income && !hasAny(raw, "消费", "支出", "支付", "付款", "扣款");
    }

    private static long extractAmount(String raw) {
        List<Candidate> list = new ArrayList<>();
        Matcher m = AMOUNT_CTX.matcher(raw);
        while (m.find()) {
            String between = raw.substring(m.start(1), m.start(2));
            if (hasAny(between, "余额", "额度", "可用", "剩余")) continue;
            String num = m.group(2);
            boolean hasYuan = m.group(3) != null || isYuanAfter(raw, m.end(2));
            boolean hasDecimal = num.contains(".");
            boolean hasCurrency = hasAny(between, "人民币", "¥", "RMB", "CNY");
            if (!hasYuan && !hasDecimal && !hasCurrency) continue;
            long cents = parseAmount(num);
            if (cents <= 0) continue;
            int score = keywordScore(m.group(1)) + (hasYuan ? 3 : 0) + (hasCurrency ? 2 : 0)
                    + (hasDecimal ? 2 : 0);
            list.add(new Candidate(cents, score, m.start()));
        }
        if (!list.isEmpty()) {
            Candidate best = list.get(0);
            for (Candidate c : list) if (c.score > best.score) best = c;
            return best.cents;
        }

        Matcher c = AMOUNT_CURRENCY.matcher(raw);
        while (c.find()) {
            if (isBalanceContext(raw, c.start())) continue;
            long cents = parseAmount(c.group(1));
            if (cents > 0) return cents;
        }
        Matcher d = AMOUNT_DECIMAL.matcher(raw);
        while (d.find()) {
            if (isBalanceContext(raw, d.start())) continue;
            long cents = parseAmount(d.group(1));
            if (cents > 0) return cents;
        }
        return 0;
    }

    private static boolean isYuanAfter(String raw, int index) {
        int end = Math.min(raw.length(), index + 2);
        return raw.substring(index, end).contains("元");
    }

    private static boolean isBalanceContext(String raw, int index) {
        int from = Math.max(0, index - 12);
        String before = raw.substring(from, index);
        return before.contains("余额") || before.contains("额度") || before.contains("可用");
    }

    private static int keywordScore(String keyword) {
        if ("消费".equals(keyword) || "支出".equals(keyword)) return 8;
        if ("支付".equals(keyword) || "付款".equals(keyword) || "扣款".equals(keyword)) return 6;
        return 4;
    }

    private static long parseAmount(String num) {
        try {
            String s = num.replace(",", "").trim();
            BigDecimal d = new BigDecimal(s);
            if (d.signum() <= 0) return 0;
            BigDecimal cents = d.movePointRight(2);
            if (cents.compareTo(BigDecimal.valueOf(100_000_000L)) > 0) return 0;
            return cents.longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private static long extractTime(String raw, long postTime) {
        Calendar base = Calendar.getInstance(Locale.CHINA);
        base.setTimeInMillis(postTime);

        Matcher full = FULL_TIME.matcher(raw);
        if (full.find()) {
            Calendar c = buildTime(postTime, Integer.parseInt(full.group(1)),
                    Integer.parseInt(full.group(2)), Integer.parseInt(full.group(3)),
                    Integer.parseInt(full.group(4)), Integer.parseInt(full.group(5)));
            if (c != null) return c.getTimeInMillis();
        }

        Matcher md = MONTH_DAY_TIME.matcher(raw);
        if (md.find()) {
            Calendar c = buildTime(postTime, base.get(Calendar.YEAR),
                    Integer.parseInt(md.group(1)), Integer.parseInt(md.group(2)),
                    Integer.parseInt(md.group(3)), Integer.parseInt(md.group(4)));
            if (c != null) {
                if (c.getTimeInMillis() > postTime + 2L * 24 * 3600 * 1000) {
                    c.add(Calendar.YEAR, -1);
                } else if (c.getTimeInMillis() < postTime - 300L * 24 * 3600 * 1000) {
                    c.add(Calendar.YEAR, 1);
                }
                return c.getTimeInMillis();
            }
        }

        Matcher smd = SHORT_MONTH_DAY_TIME.matcher(raw);
        if (smd.find()) {
            Calendar c = buildTime(postTime, base.get(Calendar.YEAR),
                    Integer.parseInt(smd.group(1)), Integer.parseInt(smd.group(2)),
                    Integer.parseInt(smd.group(3)), Integer.parseInt(smd.group(4)));
            if (c != null) {
                if (c.getTimeInMillis() > postTime + 2L * 24 * 3600 * 1000) {
                    c.add(Calendar.YEAR, -1);
                }
                return c.getTimeInMillis();
            }
        }

        Matcher only = TIME_ONLY.matcher(raw);
        if (only.find()) {
            Calendar c = buildTime(postTime, base.get(Calendar.YEAR),
                    base.get(Calendar.MONTH) + 1, base.get(Calendar.DAY_OF_MONTH),
                    Integer.parseInt(only.group(1)), Integer.parseInt(only.group(2)));
            if (c != null) {
                if (c.getTimeInMillis() > postTime + 2L * 3600 * 1000) {
                    c.add(Calendar.DAY_OF_YEAR, -1);
                }
                return c.getTimeInMillis();
            }
        }
        return postTime;
    }

    private static Calendar buildTime(long postTime, int year, int month, int day, int hour, int minute) {
        if (month < 1 || month > 12 || day < 1 || day > 31 || hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return null;
        }
        Calendar c = Calendar.getInstance(Locale.CHINA);
        c.setLenient(false);
        c.set(year, month - 1, day, hour, minute, 0);
        c.set(Calendar.MILLISECOND, 0);
        try {
            c.getTimeInMillis();
        } catch (Exception e) {
            return null;
        }
        return c;
    }

    private static String extractCardTail(String raw) {
        Matcher m = CARD_TAIL.matcher(raw);
        if (m.find()) {
            String v = m.group(1) != null ? m.group(1) : m.group(2);
            return v == null ? "" : v;
        }
        return "";
    }

    private static long extractBalance(String raw) {
        Matcher m = BALANCE.matcher(raw);
        if (m.find()) {
            long v = parseAmount(m.group(1));
            if (v > 0) return v;
        }
        return -1;
    }

    private static String extractMerchant(String raw, String title) {
        String candidate = "";
        Matcher label = MERCHANT_LABEL.matcher(raw);
        if (label.find()) candidate = label.group(1);
        if (candidate.length() == 0) {
            Matcher zai = MERCHANT_ZAI.matcher(raw);
            if (zai.find()) candidate = zai.group(1);
        }
        if (candidate.length() == 0) {
            Matcher xiang = MERCHANT_XIANG.matcher(raw);
            if (xiang.find()) candidate = xiang.group(1);
        }
        candidate = cleanMerchant(candidate);
        if (candidate.length() > 0) return candidate;

        String platform = extractPlatform(raw, "");
        if (platform.length() > 0) return platform;

        String tt = cleanMerchant(title);
        if (tt.length() > 0 && !isGenericTitle(tt) && tt.length() <= 24) return tt;
        return "";
    }

    private static boolean isGenericTitle(String title) {
        for (String g : GENERIC_TITLES) {
            if (title.equals(g) || title.startsWith(g + "通知") || title.contains("账户变动")) return true;
        }
        return false;
    }

    private static String cleanMerchant(String s) {
        if (s == null) return "";
        String v = s.trim();
        v = v.replaceAll("^[\\s:：,，。;；、-]+", "");
        v = v.replaceAll("[\\s:：,，。;；、-]+$", "");
        v = v.replaceAll("^(特约商户|商户|收款方|平台|消费平台|支付平台)[:：]?", "");
        v = v.replaceAll("(平台|商户)$", "");
        v = v.replaceAll("^支付宝[-—－]", "");
        v = v.replaceAll("^财付通[-—－]", "");
        v = v.replaceAll("^微信支付[-—－]", "");
        v = v.replaceAll("^银联[-—－]", "");
        v = v.replace("（中国）", "").replace("(中国)", "");
        if (v.contains("尾号") || v.contains("余额") || v.contains("人民币")
                || v.contains("储蓄卡") || v.contains("借记卡")) return "";
        if (v.length() > 30) v = v.substring(0, 30);
        return v;
    }

    private static String extractPlatform(String raw, String merchant) {
        String all = (merchant == null ? "" : merchant) + " " + raw;
        for (String p : SPECIFIC_PLATFORMS) {
            if (all.contains(p)) return normalizePlatform(p);
        }
        for (String p : CHANNEL_PLATFORMS) {
            if (all.contains(p)) return normalizePlatform(p);
        }
        return "";
    }

    private static String normalizePlatform(String p) {
        if ("抖音商城".equals(p) || "抖音电商".equals(p)) return "抖音";
        if ("微信".equals(p) || "财付通".equals(p)) return "微信支付";
        return p;
    }

    public static String normalizeMerchant(String merchant) {
        if (merchant == null) return "";
        String s = merchant.toLowerCase(Locale.CHINA)
                .replaceAll("\\s+", "")
                .replaceAll("[（）()【】\\[\\]·,，。.、:：;；'\"“”‘’]", "")
                .replaceAll("股份有限公司|有限责任公司|有限公司", "");
        s = s.replaceAll("^(支付宝|财付通|微信支付|银联|云闪付)[-—－]?", "");
        s = s.replaceAll("(官方旗舰店|旗舰店|专营店|专卖店|平台)$", "");
        if (s.length() > 24) s = s.substring(0, 24);
        return s;
    }

    private static String fingerprint(String pkg, long amount, long occurredAt, String merchant) {
        String key = (pkg == null ? "" : pkg) + "|" + amount + "|" + (occurredAt / 60000L)
                + "|" + normalizeMerchant(merchant);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) sb.append(String.format(Locale.CHINA, "%02x", bytes[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(key.hashCode()) + Integer.toHexString(key.length());
        }
    }

    private static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '０' && ch <= '９') sb.append((char) ('0' + (ch - '０')));
            else if (ch == '．') sb.append('.');
            else if (ch == '，') sb.append(',');
            else if (ch == '：') sb.append(':');
            else if (ch == '￥') sb.append('¥');
            else if (ch == '\u00A0' || ch == '\u3000') sb.append(' ');
            else if (ch == '\u200B' || ch == '\uFEFF') sb.append(' ');
            else sb.append(ch);
        }
        return sb.toString().replaceAll("[ \\t]+", " ").trim();
    }

    private static boolean hasAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private static final class Candidate {
        final long cents;
        final int score;
        final int index;

        Candidate(long cents, int score, int index) {
            this.cents = cents;
            this.score = score;
            this.index = index;
        }
    }
}

