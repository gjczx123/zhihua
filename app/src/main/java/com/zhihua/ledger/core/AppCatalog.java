package com.zhihua.ledger.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import com.zhihua.ledger.data.Prefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 自动识别/手动选择需要监控的消费类 App。 */
public final class AppCatalog {
    public static final class Entry {
        public final String pkg;
        public final String label;
        public final boolean known;
        public final Drawable icon;
        public final boolean enabled;

        Entry(String pkg, String label, boolean known, Drawable icon, boolean enabled) {
            this.pkg = pkg;
            this.label = label;
            this.known = known;
            this.icon = icon;
            this.enabled = enabled;
        }
    }

    private static final String[][] KNOWN = {
            {"com.xunmeng.pinduoduo", "拼多多"},
            {"com.ss.android.ugc.aweme", "抖音"},
            {"com.taobao.taobao", "淘宝"},
            {"com.jingdong.app.mall", "京东"},
            {"com.tencent.mm", "微信支付"},
            {"com.eg.android.AlipayGphone", "支付宝"},
            {"com.sankuai.meituan", "美团"},
            {"me.ele", "饿了么"},
            {"com.xingin.xhs", "小红书"},
            {"com.shizhuang.duapp", "得物"},
            {"com.achievo.vipshop", "唯品会"},
            {"com.suning.mobile.ebuy", "苏宁易购"},
            {"com.taobao.idlefish", "闲鱼"},
            {"com.wudaokou.hippo", "盒马"},
            {"com.yaya.zone", "叮咚买菜"}
    };

    private static final String[] CONSUME_WORDS = {
            "购物", "商城", "外卖", "买菜", "优选", "超市", "团购", "特卖", "折扣",
            "支付", "钱包", "买药", "生鲜", "便利店", "咖啡", "奶茶", "出行", "打车",
            "酒店", "旅游", "电影", "票务", "百货", "数码", "母婴", "美妆"
    };

    private AppCatalog() {
    }

    public static boolean isKnown(String pkg) {
        for (String[] k : KNOWN) if (k[0].equals(pkg)) return true;
        return false;
    }

    public static String knownLabel(String pkg) {
        for (String[] k : KNOWN) if (k[0].equals(pkg)) return k[1];
        return "";
    }

    public static Set<String> knownPackages() {
        Set<String> set = new HashSet<>();
        for (String[] k : KNOWN) set.add(k[0]);
        return set;
    }

    /** 已安装的、值得出现在“监控应用”列表里的 App。 */
    public static List<Entry> detected(Context context) {
        Prefs prefs = com.zhihua.ledger.App.get(context).prefs();
        List<Entry> out = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (ai.packageName.equals(context.getPackageName())) continue;
            String label = safe(pm.getApplicationLabel(ai));
            boolean known = isKnown(ai.packageName);
            boolean enabled = prefs.monitor(ai.packageName);
            if (!known && !enabled) {
                boolean user = (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                        || (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                if (!user || !looksConsume(label)) continue;
            }
            Drawable icon = null;
            try {
                icon = pm.getApplicationIcon(ai.packageName);
            } catch (Exception ignored) {
            }
            out.add(new Entry(ai.packageName, label, known, icon, enabled));
        }
        sort(out);
        if (out.size() > 30) return new ArrayList<>(out.subList(0, 30));
        return out;
    }

    /** 手动添加应用时可选的全部 App。 */
    public static List<Entry> all(Context context) {
        Prefs prefs = com.zhihua.ledger.App.get(context).prefs();
        List<Entry> out = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (ai.packageName.equals(context.getPackageName())) continue;
            boolean launchable = pm.getLaunchIntentForPackage(ai.packageName) != null;
            if (!launchable) continue;
            String label = safe(pm.getApplicationLabel(ai));
            Drawable icon = null;
            try {
                icon = pm.getApplicationIcon(ai.packageName);
            } catch (Exception ignored) {
            }
            out.add(new Entry(ai.packageName, label, isKnown(ai.packageName), icon,
                    prefs.monitor(ai.packageName)));
        }
        sort(out);
        return out;
    }

    private static void sort(List<Entry> list) {
        Collections.sort(list, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                if (a.known != b.known) return a.known ? -1 : 1;
                if (a.enabled != b.enabled) return a.enabled ? -1 : 1;
                return a.label.compareToIgnoreCase(b.label);
            }
        });
    }

    private static boolean looksConsume(String label) {
        if (label == null) return false;
        String s = label.toLowerCase(Locale.CHINA);
        for (String w : CONSUME_WORDS) if (s.contains(w.toLowerCase(Locale.CHINA))) return true;
        return false;
    }

    private static String safe(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }
}
