package com.zhihua.ledger.data;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** 本地日期区间工具：周一为一周起点。 */
public final class DateX {
    public static final String DAY = "day";
    public static final String WEEK = "week";
    public static final String MONTH = "month";
    public static final String YEAR = "year";

    private static final String[] WEEKDAYS = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    private DateX() {
    }

    public static Calendar cal(long time) {
        Calendar c = Calendar.getInstance(Locale.CHINA);
        c.setTimeInMillis(time);
        return c;
    }

    public static long startOfDay(long time) {
        Calendar c = cal(time);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long startOfWeek(long time) {
        Calendar c = cal(startOfDay(time));
        int dow = c.get(Calendar.DAY_OF_WEEK);
        int offset = (dow + 5) % 7; // 周一=0 ... 周日=6
        c.add(Calendar.DAY_OF_YEAR, -offset);
        return c.getTimeInMillis();
    }

    public static long startOfMonth(long time) {
        Calendar c = cal(startOfDay(time));
        c.set(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    public static long startOfYear(long time) {
        Calendar c = cal(startOfMonth(time));
        c.set(Calendar.MONTH, Calendar.JANUARY);
        return c.getTimeInMillis();
    }

    public static long addDays(long time, int days) {
        Calendar c = cal(time);
        c.add(Calendar.DAY_OF_YEAR, days);
        return c.getTimeInMillis();
    }

    public static long addMonths(long time, int months) {
        Calendar c = cal(time);
        c.add(Calendar.MONTH, months);
        return c.getTimeInMillis();
    }

    public static long addYears(long time, int years) {
        Calendar c = cal(time);
        c.add(Calendar.YEAR, years);
        return c.getTimeInMillis();
    }

    public static long shift(String type, long time, int amount) {
        if (DAY.equals(type)) return addDays(time, amount);
        if (WEEK.equals(type)) return addDays(time, amount * 7);
        if (MONTH.equals(type)) return addMonths(time, amount);
        return addYears(time, amount);
    }

    public static Period period(String type, long anchor) {
        long start;
        long end;
        long prevStart;
        long prevEnd;
        if (DAY.equals(type)) {
            start = startOfDay(anchor);
            end = addDays(start, 1);
            prevStart = addDays(start, -1);
            prevEnd = start;
        } else if (WEEK.equals(type)) {
            start = startOfWeek(anchor);
            end = addDays(start, 7);
            prevStart = addDays(start, -7);
            prevEnd = start;
        } else if (MONTH.equals(type)) {
            start = startOfMonth(anchor);
            end = addMonths(start, 1);
            prevStart = addMonths(start, -1);
            prevEnd = start;
        } else {
            start = startOfYear(anchor);
            end = addYears(start, 1);
            prevStart = addYears(start, -1);
            prevEnd = start;
        }
        return new Period(type, anchor, start, end, prevStart, prevEnd,
                labelFor(type, anchor, start));
    }

    private static String labelFor(String type, long anchor, long start) {
        if (DAY.equals(type)) {
            return dayTitle(anchor);
        }
        if (WEEK.equals(type)) {
            boolean current = start == startOfWeek(System.currentTimeMillis());
            return (current ? "本周 · " : "") + weekRange(start);
        }
        if (MONTH.equals(type)) {
            return monthTitle(anchor);
        }
        return yearTitle(anchor);
    }

    public static String weekday(long time) {
        return WEEKDAYS[cal(time).get(Calendar.DAY_OF_WEEK) - 1];
    }

    public static int weekdayIndex(long time) {
        return cal(time).get(Calendar.DAY_OF_WEEK) - 1;
    }

    public static boolean isWeekend(long time) {
        int d = cal(time).get(Calendar.DAY_OF_WEEK);
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY;
    }

    public static int hour(long time) {
        return cal(time).get(Calendar.HOUR_OF_DAY);
    }

    public static int year(long time) {
        return cal(time).get(Calendar.YEAR);
    }

    public static int month(long time) {
        return cal(time).get(Calendar.MONTH) + 1;
    }

    public static int day(long time) {
        return cal(time).get(Calendar.DAY_OF_MONTH);
    }

    public static String monthDay(long time) {
        return month(time) + "月" + day(time) + "日";
    }

    public static String shortDate(long time) {
        return month(time) + "/" + day(time);
    }

    public static String time(long time) {
        Calendar c = cal(time);
        return String.format(Locale.CHINA, "%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    public static String dateTime(long time) {
        Calendar c = cal(time);
        return String.format(Locale.CHINA, "%04d-%02d-%02d %02d:%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    public static String dayTitle(long time) {
        Calendar now = Calendar.getInstance(Locale.CHINA);
        int y = year(time);
        String prefix = y == now.get(Calendar.YEAR) ? "" : y + "年";
        return prefix + monthDay(time) + " · " + weekday(time);
    }

    public static String monthTitle(long time) {
        return year(time) + "年" + month(time) + "月";
    }

    public static String yearTitle(long time) {
        return year(time) + "年";
    }

    public static String weekRange(long start) {
        long end = addDays(start, 6);
        if (year(start) == year(end)) {
            return month(start) + "月" + day(start) + "日 – " + month(end) + "月" + day(end) + "日";
        }
        return year(start) + "年" + month(start) + "月" + day(start) + "日 – "
                + year(end) + "年" + month(end) + "月" + day(end) + "日";
    }

    public static String periodLabel(String type, long anchor) {
        return period(type, anchor).label;
    }

    public static boolean isCurrent(String type, long anchor) {
        return period(type, anchor).start == period(type, System.currentTimeMillis()).start;
    }

    public static boolean canGoNext(String type, long anchor) {
        Period p = period(type, anchor);
        return p.start < period(type, System.currentTimeMillis()).start;
    }

    public static String bucketLabel(String type, long start) {
        if (DAY.equals(type) || WEEK.equals(type)) return shortDate(start);
        if (MONTH.equals(type)) return month(start) + "月";
        return String.valueOf(year(start));
    }

    public static String bucketFullLabel(String type, long start) {
        if (DAY.equals(type)) return monthDay(start) + " " + weekday(start);
        if (WEEK.equals(type)) return weekRange(start);
        if (MONTH.equals(type)) return monthTitle(start);
        return yearTitle(start);
    }

    public static List<Long> bucketStarts(String type, long anchor, int count) {
        long current = period(type, anchor).start;
        List<Long> out = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            if (DAY.equals(type)) out.add(addDays(current, -i));
            else if (WEEK.equals(type)) out.add(addDays(current, -7 * i));
            else if (MONTH.equals(type)) out.add(addMonths(current, -i));
            else out.add(addYears(current, -i));
        }
        return out;
    }

    public static int daysInPeriod(Period p, long now) {
        long dayMs = 24L * 60 * 60 * 1000;
        if (now < p.start) return 0;
        if (now >= p.end) return (int) Math.max(1, Math.round((p.end - p.start) / (double) dayMs));
        return Math.max(1, (int) Math.ceil((now - p.start) / (double) dayMs));
    }

    public static final class Period {
        public final String type;
        public final long anchor;
        public final long start;
        public final long end;
        public final long prevStart;
        public final long prevEnd;
        public final String label;

        Period(String type, long anchor, long start, long end, long prevStart, long prevEnd,
               String label) {
            this.type = type;
            this.anchor = anchor;
            this.start = start;
            this.end = end;
            this.prevStart = prevStart;
            this.prevEnd = prevEnd;
            this.label = label;
        }
    }
}
