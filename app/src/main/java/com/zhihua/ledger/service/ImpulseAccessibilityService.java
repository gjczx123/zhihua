package com.zhihua.ledger.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.zhihua.ledger.App;
import com.zhihua.ledger.core.Categorizer;
import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.DateX;
import com.zhihua.ledger.data.Impulse;
import com.zhihua.ledger.data.Money;
import com.zhihua.ledger.data.PayLater;
import com.zhihua.ledger.data.Transaction;
import com.zhihua.ledger.ui.CategoryPromptView;
import com.zhihua.ledger.ui.ImpulseOverlayView;
import com.zhihua.ledger.ui.Ui;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在拼多多/抖音/淘宝/京东的收银台页面识别付款动作，弹出全屏冷静提醒。
 * 只读取当前窗口的文字，不保存页面内容，也不联网。
 */
public class ImpulseAccessibilityService extends AccessibilityService {
    private static ImpulseAccessibilityService instance;

    private static final String PDD = "com.xunmeng.pinduoduo";
    private static final String DOUYIN = "com.ss.android.ugc.aweme";
    private static final String TAOBAO = "com.taobao.taobao";
    private static final String JD = "com.jingdong.app.mall";
    private static final String WECHAT = "com.tencent.mm";
    private static final String ALIPAY = "com.eg.android.AlipayGphone";

    private static final String[] STRONG_WORDS = {
            "确认支付", "立即支付", "去支付", "提交订单", "确认付款", "立即付款",
            "收银台", "免密支付", "立即下单", "确认下单", "向商家付款"
    };
    private static final String[] SUCCESS_WORDS = {
            "支付成功", "付款成功", "支付完成", "已支付", "订单已提交", "支付结果"
    };
    private static final String[] PAY_LATER_WORDS = {
            "先用后付", "确认收货后付款", "确认收货后再付款", "0元下单", "先用后付支付"
    };

    private static final Pattern AMOUNT_CTX = Pattern.compile(
            "(实付|应付|需支付|合计|总计|支付金额|付款金额|订单金额|商品金额|小计)"
                    + "[^0-9]{0,8}[¥￥]?\\s*([0-9]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern AMOUNT_YEN = Pattern.compile("[¥￥]\\s*([0-9]+(?:\\.[0-9]{1,2})?)");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private ImpulseOverlayView overlay;
    private CategoryPromptView categoryOverlay;
    private long lastInspectAt;
    private long lastTriggerAt;
    private String lastSignature = "";
    private String pendingPkg;
    private final Runnable pendingInspect = new Runnable() {
        @Override
        public void run() {
            if (pendingPkg != null) inspect(pendingPkg);
        }
    };

    public static boolean showTest(Context context) {
        if (instance == null) return false;
        if (instance.overlay != null || instance.categoryOverlay != null) return true;
        instance.showOverlay("拼多多", PDD, 19900, Category.SHOPPING, true, false,
                App.get(context).prefs().pauseSeconds());
        return true;
    }

    public static boolean promptCategory(Transaction t) {
        if (instance == null || instance.overlay != null || instance.categoryOverlay != null) {
            return false;
        }
        final Transaction tx = t;
        instance.handler.post(new Runnable() {
            @Override
            public void run() {
                if (instance != null) instance.showCategoryPromptInternal(tx);
            }
        });
        return true;
    }

    public static boolean isConnected() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    | AccessibilityEvent.TYPE_VIEW_CLICKED
                    | AccessibilityEvent.TYPE_VIEW_FOCUSED
                    | AccessibilityEvent.TYPE_VIEW_SCROLLED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.notificationTimeout = 120;
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || overlay != null || categoryOverlay != null) return;
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        if (getPackageName().equals(pkg)) return;
        if (!App.get().prefs().monitor(pkg)) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_CLICKED
                && type != AccessibilityEvent.TYPE_VIEW_FOCUSED
                && type != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (now - lastInspectAt < 700) return;
        lastInspectAt = now;
        pendingPkg = pkg;
        handler.removeCallbacks(pendingInspect);
        handler.postDelayed(pendingInspect, 380);
    }

    private void inspect(String pkg) {
        if (overlay != null || categoryOverlay != null) return;
        String text;
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            CharSequence rootPkg = root.getPackageName();
            if (rootPkg == null || !pkg.equals(rootPkg.toString())) return;
            text = collect(root);
        } catch (Exception e) {
            return;
        }
        if (text.length() < 4) return;
        if (containsAny(text, SUCCESS_WORDS)) return;
        if (containsAny(text, PAY_LATER_WORDS)) {
            if (recordPayLater(pkg, text)) return;
        }
        if (!containsAny(text, STRONG_WORDS)) return;

        boolean scanMode = WECHAT.equals(pkg) || ALIPAY.equals(pkg);
        if (scanMode && !App.get().prefs().scanPrompt()) return;

        long amount = extractAmount(text);
        String signature = pkg + "|" + amount + "|" + text.hashCode();
        long now = System.currentTimeMillis();
        if (signature.equals(lastSignature) && now - lastTriggerAt < 3L * 60 * 1000) return;
        if (now - lastTriggerAt < 75_000L) return;

        String category = inferCategory(pkg, text);
        int pause = scanMode ? Math.min(8, App.get().prefs().pauseSeconds())
                : App.get().prefs().pauseSeconds();
        showOverlay(labelOf(pkg), pkg, amount, category, false, scanMode, pause);
    }

    private boolean recordPayLater(String pkg, String text) {
        long now = System.currentTimeMillis();
        if (now - lastTriggerAt < 45_000L) return true;
        long amount = extractAmount(text);
        String merchant = extractPageMerchant(text);
        if (merchant.length() == 0) merchant = labelOf(pkg);
        if (amount <= 0 && merchant.length() == 0) return false;
        final PayLater pay = new PayLater();
        pay.amountCents = amount;
        pay.merchant = merchant;
        pay.platform = labelOf(pkg);
        pay.category = inferCategory(pkg, text);
        pay.appLabel = labelOf(pkg);
        pay.createdAt = now;
        pay.note = "先用后付，等待实际付款";
        lastTriggerAt = now;
        lastSignature = "paylater|" + pkg + "|" + amount + "|" + merchant;
        App.get().io().execute(new Runnable() {
            @Override
            public void run() {
                com.zhihua.ledger.data.Db db = App.get().db();
                if (db.findPendingPayLater(pay.amountCents, pay.merchant,
                        System.currentTimeMillis(), 10L * 60 * 1000) != null) return;
                if (db.insertPayLater(pay) > 0) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Ui.toast(ImpulseAccessibilityService.this,
                                    "已记下先用后付" + (pay.amountCents > 0
                                            ? " ¥" + Money.format(pay.amountCents) : "")
                                            + "，付款后自动入账");
                        }
                    });
                }
            }
        });
        return true;
    }

    private String extractPageMerchant(String text) {
        Matcher m = Pattern.compile("(?:收款方|商户|付款给|支付给)\\s*[:：]?\\s*([^\\n，,。;；]{1,30})")
                .matcher(text);
        if (m.find()) {
            String v = m.group(1).trim();
            if (v.length() > 0) return v;
        }
        return "";
    }

    private void showOverlay(String appLabel, String pkg, long amount, String category,
                             boolean test, boolean scanMode, int pauseSeconds) {
        if (overlay != null || categoryOverlay != null) return;
        if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        final ImpulseOverlayView view = new ImpulseOverlayView(this, appLabel, amount,
                category, pauseSeconds, scanMode, new ImpulseOverlayView.Listener() {
            @Override
            public void onAbandon(ImpulseOverlayView v, long amountCents, String cat) {
                finish(v, Impulse.ABANDONED, amountCents, cat, pkg, appLabel, test);
            }

            @Override
            public void onContinue(ImpulseOverlayView v, long amountCents, String cat) {
                finish(v, Impulse.CONTINUED, amountCents, cat, pkg, appLabel, test);
            }

            @Override
            public void onDismiss(ImpulseOverlayView v, long amountCents, String cat) {
                finish(v, Impulse.DISMISSED, amountCents, cat, pkg, appLabel, test);
            }
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        try {
            wm.addView(view, lp);
            overlay = view;
            lastTriggerAt = System.currentTimeMillis();
            lastSignature = pkg + "|" + amount + "|";
            loadStats(view, category, test);
        } catch (Exception e) {
            overlay = null;
            Ui.toast(this, "无法显示冷静提醒，请确认无障碍服务仍然开启");
        }
    }

    private void showCategoryPromptInternal(final Transaction t) {
        if (categoryOverlay != null || overlay != null || t == null) return;
        if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        final CategoryPromptView view = new CategoryPromptView(this, t,
                new CategoryPromptView.Listener() {
                    @Override
                    public void onSave(CategoryPromptView v, final String category) {
                        removeCategoryOverlay(v);
                        final long id = t.id;
                        final String merchant = t.merchant;
                        App.get().io().execute(new Runnable() {
                            @Override
                            public void run() {
                                App.get().db().updateCategory(id, category);
                                if (merchant != null && merchant.length() > 0
                                        && !merchant.startsWith("未识别")) {
                                    App.get().db().learnRule(merchant, category);
                                }
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Ui.toast(ImpulseAccessibilityService.this,
                                                "已归类为「" + Category.name(category) + "」");
                                    }
                                });
                            }
                        });
                    }

                    @Override
                    public void onSkip(CategoryPromptView v) {
                        removeCategoryOverlay(v);
                        Ui.toast(ImpulseAccessibilityService.this,
                                "之后可以在账单里点这一笔改分类");
                    }
                });
        WindowManager.LayoutParams lp = overlayParams();
        try {
            wm.addView(view, lp);
            categoryOverlay = view;
        } catch (Exception e) {
            categoryOverlay = null;
            App.get().notifyCaptured(t);
            Ui.toast(this, "无法显示分类提醒，已发送待分类通知");
        }
    }

    private WindowManager.LayoutParams overlayParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return lp;
    }

    private void removeCategoryOverlay(CategoryPromptView view) {
        try {
            if (view != null && view.getParent() != null) wm.removeView(view);
        } catch (Exception ignored) {
        }
        if (categoryOverlay == view) categoryOverlay = null;
    }

    private void loadStats(final ImpulseOverlayView view, final String category, final boolean test) {
        App.get().io().execute(new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long start = DateX.startOfMonth(now);
                long end = DateX.addMonths(start, 1);
                long monthSpent = App.get().db().totalBetween(start, end);
                int same = App.get().db().countCategoryBetween(category, start, end) + 1;
                long budget = App.get().prefs().budget();
                final long ms = monthSpent;
                final int sc = same;
                final long b = budget;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (overlay == view) view.setStats(ms, sc, b);
                    }
                });
            }
        });
    }

    private void finish(ImpulseOverlayView view, String decision, long amount, String category,
                        String pkg, String appLabel, boolean test) {
        removeOverlay(view);
        if (test) {
            Ui.toast(this, "测试完成，这笔不会记入账单");
            return;
        }
        Impulse i = new Impulse();
        i.packageName = pkg;
        i.appLabel = appLabel;
        i.amountCents = amount;
        i.category = category;
        i.decision = decision;
        i.createdAt = System.currentTimeMillis();
        App.get().db().insertImpulse(i);
        lastTriggerAt = System.currentTimeMillis();
        if (Impulse.ABANDONED.equals(decision)) {
            Ui.toast(this, "已放进冷静清单" + (amount > 0 ? " · 省下 " + Money.yuan(amount) : ""));
        } else if (Impulse.CONTINUED.equals(decision)) {
            Ui.toast(this, "已记录，支付后建行通知会自动入账");
        }
    }

    private void removeOverlay(ImpulseOverlayView view) {
        try {
            if (view != null && view.getParent() != null) wm.removeView(view);
        } catch (Exception ignored) {
        }
        if (overlay == view) overlay = null;
    }

    private String collect(AccessibilityNodeInfo root) {
        Set<String> parts = new LinkedHashSet<>();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited < 1400) {
            AccessibilityNodeInfo node = queue.pollFirst();
            if (node == null) continue;
            visited++;
            CharSequence t = node.getText();
            if (t != null && t.length() > 0 && t.length() < 300) parts.add(t.toString().trim());
            CharSequence d = node.getContentDescription();
            if (d != null && d.length() > 0 && d.length() < 200) parts.add(d.toString().trim());
            int n = node.getChildCount();
            for (int i = 0; i < n && i < 40; i++) {
                try {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) queue.add(child);
                } catch (Exception ignored) {
                }
            }
            if (parts.size() > 260) break;
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() + p.length() > 7000) break;
            sb.append(p).append(' ');
        }
        return sb.toString();
    }

    private long extractAmount(String text) {
        Matcher m = AMOUNT_CTX.matcher(text);
        long best = 0;
        int bestScore = -1;
        while (m.find()) {
            long v = parse(m.group(2));
            if (v <= 0) continue;
            int score = scoreOf(m.group(1));
            if (score > bestScore) {
                bestScore = score;
                best = v;
            }
        }
        if (best > 0) return best;
        Matcher y = AMOUNT_YEN.matcher(text);
        while (y.find()) {
            long v = parse(y.group(1));
            if (v > 0 && v <= 100_000_00L) best = v;
        }
        return best;
    }

    private int scoreOf(String word) {
        if (word.contains("实付") || word.contains("应付") || word.contains("需支付")) return 5;
        if (word.contains("合计") || word.contains("总计")) return 4;
        if (word.contains("支付金额") || word.contains("订单金额")) return 3;
        return 2;
    }

    private long parse(String s) {
        try {
            double d = Double.parseDouble(s);
            if (d <= 0 || d > 100000) return 0;
            return Math.round(d * 100);
        } catch (Exception e) {
            return 0;
        }
    }

    private String inferCategory(String pkg, String text) {
        if (WECHAT.equals(pkg) || ALIPAY.equals(pkg)) {
            Categorizer.Result r = Categorizer.categorize("", "", text, null);
            return r.category;
        }
        if (text.contains("抖币") || text.contains("充值") || text.contains("会员")) return Category.FUN;
        if (text.contains("衣") || text.contains("鞋") || text.contains("裤") || text.contains("裙")) {
            return Category.CLOTHES;
        }
        if (text.contains("外卖") || text.contains("美食") || text.contains("奶茶")
                || text.contains("咖啡") || text.contains("餐")) return Category.FOOD;
        if (text.contains("药") || text.contains("医院")) return Category.HEALTH;
        if (text.contains("书") || text.contains("课程")) return Category.STUDY;
        if (DOUYIN.equals(pkg) && !text.contains("商城") && !text.contains("订单")) return Category.FUN;
        return Category.SHOPPING;
    }

    private static boolean containsAny(String text, String[] words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }

    private static String labelOf(String pkg) {
        if (PDD.equals(pkg)) return "拼多多";
        if (DOUYIN.equals(pkg)) return "抖音";
        if (TAOBAO.equals(pkg)) return "淘宝";
        if (JD.equals(pkg)) return "京东";
        if (WECHAT.equals(pkg)) return "微信支付";
        if (ALIPAY.equals(pkg)) return "支付宝";
        return "购物 App";
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (overlay != null) {
            try {
                wm.removeView(overlay);
            } catch (Exception ignored) {
            }
            overlay = null;
        }
        if (categoryOverlay != null) {
            try {
                wm.removeView(categoryOverlay);
            } catch (Exception ignored) {
            }
            categoryOverlay = null;
        }
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
