package com.zhihua.ledger.data;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** 金额统一用“分”存储，避免浮点误差。 */
public final class Money {
    private static final DecimalFormat GROUPED =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.CHINA));

    private Money() {
    }

    public static String format(long cents) {
        synchronized (GROUPED) {
            return GROUPED.format(cents / 100.0);
        }
    }

    public static String formatPlain(long cents) {
        long abs = Math.abs(cents);
        String sign = cents < 0 ? "-" : "";
        return sign + (abs / 100) + "." + String.format(Locale.CHINA, "%02d", abs % 100);
    }

    public static String yuan(long cents) {
        return "¥" + format(cents);
    }

    public static String compact(long cents) {
        long abs = Math.abs(cents);
        if (abs >= 10_000_000_000L) {
            return trimZero(abs / 10_000_000_000.0) + "亿";
        }
        if (abs >= 1_000_000L) {
            return trimZero(abs / 1_000_000.0) + "万";
        }
        if (abs >= 100_000L) {
            return String.format(Locale.CHINA, "%,d", cents / 100);
        }
        return format(cents);
    }

    private static String trimZero(double value) {
        if (value >= 100) return String.valueOf(Math.round(value));
        String s = String.format(Locale.CHINA, "%.1f", value);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** 解析用户输入的元，返回分；非法返回 -1。 */
    public static long parse(String raw) {
        if (raw == null) return -1;
        String s = raw.trim()
                .replace(",", "")
                .replace("，", "")
                .replace("¥", "")
                .replace("￥", "")
                .replace("元", "")
                .replace(" ", "");
        if (s.length() == 0) return -1;
        try {
            BigDecimal d = new BigDecimal(s);
            if (d.signum() <= 0) return -1;
            if (d.scale() > 2) d = d.setScale(2, BigDecimal.ROUND_HALF_UP);
            BigDecimal cents = d.movePointRight(2);
            if (cents.compareTo(BigDecimal.valueOf(100_000_000L)) > 0) return -1;
            return cents.longValue();
        } catch (Exception e) {
            return -1;
        }
    }
}
