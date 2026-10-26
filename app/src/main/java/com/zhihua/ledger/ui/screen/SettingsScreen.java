package com.zhihua.ledger.ui.screen;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.zhihua.ledger.App;
import com.zhihua.ledger.R;
import com.zhihua.ledger.core.AppCatalog;
import com.zhihua.ledger.core.Categorizer;
import com.zhihua.ledger.core.NotificationParser;
import com.zhihua.ledger.core.PermissionUtils;
import com.zhihua.ledger.core.SmsImporter;
import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.DateX;
import com.zhihua.ledger.data.Db;
import com.zhihua.ledger.data.DemoData;
import com.zhihua.ledger.data.Money;
import com.zhihua.ledger.data.Prefs;
import com.zhihua.ledger.data.RawCapture;
import com.zhihua.ledger.data.Transaction;
import com.zhihua.ledger.service.GuardService;
import com.zhihua.ledger.service.ImpulseAccessibilityService;
import com.zhihua.ledger.ui.Host;
import com.zhihua.ledger.ui.Ui;
import com.zhihua.ledger.ui.widget.BudgetBarView;

import java.util.ArrayList;
import java.util.List;

public class SettingsScreen extends ScrollView {
    private static final String PDD = "com.xunmeng.pinduoduo";
    private static final String DOUYIN = "com.ss.android.ugc.aweme";
    private static final String TAOBAO = "com.taobao.taobao";
    private static final String JD = "com.jingdong.app.mall";
    private static final String WECHAT = "com.tencent.mm";
    private static final String ALIPAY = "com.eg.android.AlipayGphone";

    private final Activity activity;
    private final Host host;
    private final LinearLayout content;
    private long seq;

    public SettingsScreen(Activity activity, Host host) {
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
        App.get(activity).io().execute(new Runnable() {
            @Override
            public void run() {
                final Data d = new Data();
                d.notify = PermissionUtils.notificationAccess(activity);
                d.access = PermissionUtils.accessibility(activity);
                d.sms = PermissionUtils.smsPermission(activity);
                d.battery = PermissionUtils.batteryOptimized(activity);
                d.budget = App.get(activity).prefs().budget();
                d.captures = App.get(activity).db().listCaptures(20).size();
                d.demo = App.get(activity).db().countDemo();
                d.apps = AppCatalog.detected(activity);
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

    private void render(Data d) {
        int y = getScrollY();
        content.removeAllViews();
        int pad = Ui.dp(activity, 20);
        content.setPadding(pad, Ui.dp(activity, 18), pad, Ui.dp(activity, 112));
        content.addView(header(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(statusCard(d));
        Ui.addFull(content, automationSection(d));
        Ui.addFull(content, impulseSection(d));
        Ui.addFull(content, budgetSection(d));
        Ui.addFull(content, dataSection(d));
        Ui.addFull(content, deviceSection());
        Ui.addFull(content, aboutSection());
        post(new Runnable() {
            @Override
            public void run() {
                scrollTo(0, Math.min(y, Math.max(0, content.getHeight() - getHeight())));
            }
        });
    }

    private View header() {
        LinearLayout col = Ui.col(activity);
        col.addView(Ui.title(activity, "设置", 24f));
        col.addView(Ui.text(activity, "把自动记账和付款提醒调到最顺手", 12f, Ui.INK2, false));
        return col;
    }

    private View statusCard(Data d) {
        LinearLayout card = Ui.card(activity);
        card.setPadding(Ui.dp(activity, 18), Ui.dp(activity, 16),
                Ui.dp(activity, 18), Ui.dp(activity, 16));
        LinearLayout.LayoutParams lp = Ui.cardLp(activity);
        lp.topMargin = Ui.dp(activity, 14);
        card.setLayoutParams(lp);

        LinearLayout top = Ui.row(activity);
        top.addView(Ui.text(activity, "自动记账状态", 16f, Ui.INK, true));
        top.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        int ok = (d.notify ? 1 : 0) + (d.access ? 1 : 0) + (d.battery ? 0 : 1);
        top.addView(Ui.text(activity, ok + " / 3 就绪", 12f, Ui.INK3, false));
        card.addView(top);

        LinearLayout chips = Ui.row(activity);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = Ui.dp(activity, 14);
        card.addView(chips, cl);
        chips.addView(statusChip("通知读取", d.notify), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        chips.addView(statusChip("付款提醒", d.access), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        chips.addView(statusChip("省电", !d.battery), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView hint = Ui.text(activity, !d.notify
                        ? "先开启通知读取，银行消费才能自动入账"
                        : !d.access ? "再开启付款前提醒，付款时会先问你一句"
                        : d.battery ? "建议把知花设为“无限制”，避免后台被系统清理"
                        : "都开好了。正常用储蓄卡消费即可。",
                12f, Ui.INK2, false);
        hint.setLineSpacing(Ui.dp(activity, 2), 1f);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Ui.dp(activity, 12);
        card.addView(hint, hl);

        TextView action = Ui.text(activity, !d.notify ? "开启通知读取 ›"
                        : !d.access ? "开启付款提醒 ›" : "查看手机设置建议 ›",
                12.5f, Ui.INK, true);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        al.topMargin = Ui.dp(activity, 10);
        card.addView(action, al);
        action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!d.notify) PermissionUtils.openNotificationSettings(activity);
                else if (!d.access) PermissionUtils.openAccessibilitySettings(activity);
                else showMiuiGuide();
            }
        });
        return card;
    }

    private View statusChip(String label, boolean ok) {
        LinearLayout row = Ui.row(activity);
        View dot = Ui.dot(activity, ok ? Ui.GREEN : Ui.ORANGE, 6);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                Ui.dp(activity, 6), Ui.dp(activity, 6));
        dl.rightMargin = Ui.dp(activity, 7);
        row.addView(dot, dl);
        row.addView(Ui.text(activity, label, 11.5f, Ui.INK2, false));
        row.addView(Ui.text(activity, ok ? " 已开" : " 未开", 11.5f,
                ok ? Ui.INK3 : Ui.ORANGE, false));
        return row;
    }

    private View automationSection(Data d) {
        LinearLayout section = Ui.col(activity);
        section.addView(Ui.sectionTitle(activity, "自动记账"));
        LinearLayout card = Ui.card(activity);
        card.setPadding(Ui.dp(activity, 4), Ui.dp(activity, 4),
                Ui.dp(activity, 4), Ui.dp(activity, 4));
        section.addView(card, Ui.cardLp(activity));

        addRow(card, settingRow("🔔", "通知读取", "银行 App 消费通知自动入账",
                d.notify ? "已开启" : "未开启", d.notify ? Ui.GREEN : Ui.ORANGE,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PermissionUtils.openNotificationSettings(activity);
                    }
                }), true);

        Switch smsSwitch = styledSwitch(App.get(activity).prefs().smsEnabled());
        smsSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                App.get(activity).prefs().setSmsEnabled(isChecked);
                if (isChecked && !PermissionUtils.smsPermission(activity)) {
                    PermissionUtils.requestSms(activity, 501);
                    Ui.toast(activity, "请允许短信权限；授权后再打开一次开关");
                }
                refresh();
            }
        });
        addRow(card, switchRow("💬", "银行短信兜底", "可选：同时解析 955xx 银行消费短信", smsSwitch), true);

        Switch wechatNotify = styledSwitch(App.get(activity).prefs().wechatPaymentNotify());
        wechatNotify.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                App.get(activity).prefs().setWechatPaymentNotify(isChecked);
            }
        });
        addRow(card, switchRow("微", "微信支付通知", "读取微信“支付成功”通知作为补充", wechatNotify), true);

        Switch alipayNotify = styledSwitch(App.get(activity).prefs().alipayPaymentNotify());
        alipayNotify.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                App.get(activity).prefs().setAlipayPaymentNotify(isChecked);
            }
        });
        addRow(card, switchRow("支", "支付宝支付通知", "读取支付宝“支付成功”通知作为补充", alipayNotify), true);

        addRow(card, settingRow("📥", "补录近 90 天短信", "只读取银行短信，在本机解析",
                "", Ui.INK3, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        importSms();
                    }
                }), true);

        addRow(card, settingRow("🧪", "解析测试", "粘贴一条通知，校准识别规则",
                "", Ui.INK3, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showParseTest("", "");
                    }
                }), true);

        addRow(card, settingRow("🗂️", "最近通知记录", "识别失败时，看看原文长什么样",
                d.captures > 0 ? d.captures + " 条" : "", Ui.INK3,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showCaptures();
                    }
                }), true);

        Switch notifySwitch = styledSwitch(App.get(activity).prefs().captureNotify());
        notifySwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                App.get(activity).prefs().setCaptureNotify(isChecked);
            }
        });
        addRow(card, switchRow("🔕", "记账后轻提示", "入账后发一条不响铃的通知", notifySwitch), false);
        return section;
    }

    private View impulseSection(Data d) {
        LinearLayout section = Ui.col(activity);
        section.addView(Ui.sectionTitle(activity, "付款前冷静提醒"));
        LinearLayout card = Ui.card(activity);
        card.setPadding(Ui.dp(activity, 4), Ui.dp(activity, 4),
                Ui.dp(activity, 4), Ui.dp(activity, 4));
        section.addView(card, Ui.cardLp(activity));

        addRow(card, settingRow("🛡️", "无障碍服务", "识别收银台页面，弹出全屏确认",
                d.access ? "已开启" : "未开启", d.access ? Ui.GREEN : Ui.ORANGE,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PermissionUtils.openAccessibilitySettings(activity);
                    }
                }), true);

        Switch scanSwitch = styledSwitch(App.get(activity).prefs().scanPrompt());
        scanSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                App.get(activity).prefs().setScanPrompt(isChecked);
            }
        });
        addRow(card, switchRow("📷", "扫码付款提醒", "微信/支付宝扫商家码付款时也弹确认", scanSwitch), true);

        TextView appHead = Ui.text(activity, "提醒的消费软件（自动识别，可添加）",
                11f, Ui.INK3, false);
        appHead.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 10),
                Ui.dp(activity, 14), Ui.dp(activity, 4));
        addRow(card, appHead, true);

        if (d.apps.isEmpty()) {
            LinearLayout empty = Ui.col(activity);
            empty.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 4),
                    Ui.dp(activity, 14), Ui.dp(activity, 10));
            empty.addView(Ui.text(activity, "没有自动识别到消费软件，可以手动添加。",
                    11.5f, Ui.INK2, false));
            addRow(card, empty, true);
        }
        for (AppCatalog.Entry app : d.apps) {
            String icon = app.label.length() == 0 ? "App" : app.label.substring(0, 1);
            addRow(card, switchRow(icon, app.label,
                    app.known ? "自动识别 · " + app.pkg : app.pkg, monitorSwitch(app.pkg)), true);
        }
        addRow(card, settingRow("➕", "添加其他应用", "从已安装应用里手动选择",
                "", Ui.PRIMARY, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showAppPicker();
                    }
                }), false);

        LinearLayout pauseRow = Ui.col(activity);
        pauseRow.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 10),
                Ui.dp(activity, 14), Ui.dp(activity, 12));
        LinearLayout pauseTop = Ui.row(activity);
        pauseTop.addView(Ui.text(activity, "冷静倒计时", 13.5f, Ui.INK, true));
        pauseTop.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        pauseTop.addView(Ui.text(activity, "秒", 11f, Ui.INK3, false));
        pauseRow.addView(pauseTop);
        LinearLayout chips = Ui.row(activity);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.topMargin = Ui.dp(activity, 8);
        pauseRow.addView(chips, cl);
        int current = App.get(activity).prefs().pauseSeconds();
        for (int sec : new int[]{5, 10, 15, 20, 30}) {
            boolean sel = sec == current;
            TextView chip = Ui.text(activity, sec + "s", 11.5f, sel ? Color.WHITE : Ui.INK2, sel);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, Ui.dp(activity, 6), 0, Ui.dp(activity, 6));
            chip.setBackground(Ui.round(activity, sel ? Ui.PRIMARY : 0xFFF1F5F8, 999));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    App.get(activity).prefs().setPauseSeconds(sec);
                    refresh();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = Ui.dp(activity, 5);
            chips.addView(chip, lp);
        }
        addRow(card, pauseRow, true);

        addRow(card, settingRow("▶️", "测试提醒", "先看看全屏提醒长什么样",
                "", Ui.INK3, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!ImpulseAccessibilityService.isConnected()) {
                            Ui.toast(activity, "请先开启无障碍服务");
                            PermissionUtils.openAccessibilitySettings(activity);
                            return;
                        }
                        ImpulseAccessibilityService.showTest(activity);
                    }
                }), false);
        return section;
    }

    private Switch monitorSwitch(final String pkg) {
        final Prefs prefs = App.get(activity).prefs();
        Switch s = styledSwitch(prefs.monitor(pkg));
        s.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                prefs.setMonitor(pkg, isChecked);
            }
        });
        return s;
    }

    private void showAppPicker() {
        App.get(activity).io().execute(new Runnable() {
            @Override
            public void run() {
                final List<AppCatalog.Entry> all = AppCatalog.all(activity);
                App.get(activity).main().post(new Runnable() {
                    @Override
                    public void run() {
                        if (all.isEmpty()) {
                            Ui.toast(activity, "没有可添加的应用");
                            return;
                        }
                        final boolean[] checked = new boolean[all.size()];
                        final String[] items = new String[all.size()];
                        for (int i = 0; i < all.size(); i++) {
                            AppCatalog.Entry e = all.get(i);
                            checked[i] = e.enabled;
                            items[i] = e.label + "\n" + e.pkg;
                        }
                        new AlertDialog.Builder(activity)
                                .setTitle("选择要提醒的消费软件")
                                .setMultiChoiceItems(items, checked,
                                        new DialogInterface.OnMultiChoiceClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which,
                                                                boolean isChecked) {
                                                checked[which] = isChecked;
                                            }
                                        })
                                .setNegativeButton("取消", null)
                                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Prefs prefs = App.get(activity).prefs();
                                        for (int i = 0; i < all.size(); i++) {
                                            prefs.setMonitor(all.get(i).pkg, checked[i]);
                                        }
                                        refresh();
                                    }
                                })
                                .show();
                    }
                });
            }
        });
    }

    private View budgetSection(Data d) {
        LinearLayout section = Ui.col(activity);
        section.addView(Ui.sectionTitle(activity, "预算"));
        LinearLayout card = Ui.card(activity);
        card.setPadding(Ui.dp(activity, 4), Ui.dp(activity, 4),
                Ui.dp(activity, 4), Ui.dp(activity, 4));
        section.addView(card, Ui.cardLp(activity));
        addRow(card, settingRow("💰", "月预算", d.budget > 0 ? "当前 " + Money.yuan(d.budget) : "还没设置",
                d.budget > 0 ? "修改" : "去设置", Ui.PRIMARY, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showBudgetDialog();
                    }
                }), true);

        LinearLayout tip = Ui.col(activity);
        tip.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 4),
                Ui.dp(activity, 14), Ui.dp(activity, 12));
        tip.addView(Ui.text(activity, "建议把月预算拆成：必要开支 50%、想要 30%、储蓄 20%。"
                + "知花会用预算进度提醒你，但不会限制你花钱。", 11.5f, Ui.INK2, false));
        addRow(card, tip, false);
        return section;
    }

    private View dataSection(Data d) {
        LinearLayout section = Ui.col(activity);
        section.addView(Ui.sectionTitle(activity, "数据"));
        LinearLayout card = Ui.card(activity);
        card.setPadding(Ui.dp(activity, 4), Ui.dp(activity, 4),
                Ui.dp(activity, 4), Ui.dp(activity, 4));
        section.addView(card, Ui.cardLp(activity));
        addRow(card, settingRow("📤", "导出 CSV", "导出全部账单，Excel 可直接打开",
                "导出", Ui.PRIMARY, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        host.exportCsv();
                    }
                }), true);
        addRow(card, settingRow("🧪", "载入示例数据", d.demo > 0 ? "已载入 " + d.demo + " 条" : "预览图表效果，可一键清除",
                d.demo > 0 ? "已载入" : "载入", Ui.INK3, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        loadDemo();
                    }
                }), true);
        addRow(card, settingRow("🧹", "清空全部数据", "账单、规则、冷静清单都会删除",
                "清空", Ui.RED, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        clearAll();
                    }
                }), false);
        return section;
    }

    private View deviceSection() {
        LinearLayout section = Ui.col(activity);
        section.addView(Ui.sectionTitle(activity, "手机适配 / 后台保护"));
        LinearLayout card = Ui.card(activity);
        card.setPadding(Ui.dp(activity, 4), Ui.dp(activity, 4),
                Ui.dp(activity, 4), Ui.dp(activity, 4));
        section.addView(card, Ui.cardLp(activity));
        addRow(card, settingRow("📱", "省电与自启动", "所有品牌通用：避免后台被清理",
                "查看指南", Ui.PRIMARY, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showMiuiGuide();
                    }
                }), true);
        addRow(card, settingRow("⚙️", "应用权限", "通知、短信、电池优化都在这里",
                "打开", Ui.INK3, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PermissionUtils.openAppDetails(activity);
                    }
                }), true);

        Switch guardSwitch = styledSwitch(App.get(activity).prefs().backgroundGuard());
        guardSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                App.get(activity).prefs().setBackgroundGuard(isChecked);
                if (isChecked) GuardService.start(activity);
                else GuardService.stop(activity);
            }
        });
        addRow(card, switchRow("🛡️", "后台守护",
                "常驻低优先级通知，降低被系统杀掉后收不到提醒的概率", guardSwitch), true);

        addRow(card, settingRow("🚀", "自启动管理", "尝试打开当前品牌的自启动页",
                "打开", Ui.INK3, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!PermissionUtils.openAutoStart(activity)) {
                            Ui.toast(activity, "没有找到自启动页面，请按指南在应用设置里手动开启");
                        }
                    }
                }), false);
        return section;
    }

    private View aboutSection() {
        LinearLayout section = Ui.col(activity);
        section.addView(Ui.sectionTitle(activity, "关于"));
        LinearLayout card = Ui.card(activity);
        card.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 14),
                Ui.dp(activity, 16), Ui.dp(activity, 14));
        card.addView(Ui.text(activity, "知花 · 每一笔，都知道去向", 14f, Ui.INK, true));
        String version = "";
        try {
            version = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        TextView v = Ui.text(activity, "v" + version + " · 本地记账 · 无网络权限", 11.5f, Ui.INK3, false);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vl.topMargin = Ui.dp(activity, 5);
        card.addView(v, vl);
        TextView note = Ui.text(activity, "知花是个人独立记账工具，不是中国建设银行官方应用。"
                + "账单只保存在这台手机上，卸载即删除。", 11.5f, Ui.INK2, false);
        note.setLineSpacing(Ui.dp(activity, 2), 1f);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = Ui.dp(activity, 10);
        card.addView(note, nl);
        TextView privacy = Ui.text(activity, "隐私说明 ›", 11.5f, Ui.PRIMARY, true);
        privacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPrivacy();
            }
        });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pl.topMargin = Ui.dp(activity, 8);
        card.addView(privacy, pl);
        section.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return section;
    }

    private void addRow(LinearLayout card, View row, boolean divider) {
        Ui.fillWidth(row);
        card.addView(row);
        if (divider) {
            View line = new View(activity);
            line.setBackgroundColor(Ui.LINE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(activity, 0.7f)));
            lp.leftMargin = Ui.dp(activity, 16);
            lp.rightMargin = Ui.dp(activity, 16);
            card.addView(line, lp);
        }
    }

    private View settingRow(String ignoredIcon, String title, String subtitle, String status,
                            int statusColor, View.OnClickListener click) {
        LinearLayout row = Ui.row(activity);
        row.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 12),
                Ui.dp(activity, 16), Ui.dp(activity, 12));
        row.setMinimumHeight(Ui.dp(activity, 56));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(Ui.ripple(activity, Color.TRANSPARENT, 0x14171A17, 0));
        row.setOnClickListener(click);
        LinearLayout col = Ui.col(activity);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(col, cl);
        col.addView(Ui.text(activity, title, 14f, Ui.INK, true));
        TextView sub = Ui.text(activity, subtitle, 12f, Ui.INK2, false);
        sub.setLineSpacing(Ui.dp(activity, 1), 1f);
        col.addView(sub);
        if (status != null && status.length() > 0) {
            TextView s = Ui.text(activity, status, 11.5f, statusColor, true);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sl.rightMargin = Ui.dp(activity, 6);
            row.addView(s, sl);
        }
        row.addView(Ui.text(activity, "›", 18f, Ui.INK3, false));
        return row;
    }

    private View switchRow(String ignoredIcon, String title, String subtitle, Switch sw) {
        LinearLayout row = Ui.row(activity);
        row.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 10),
                Ui.dp(activity, 16), Ui.dp(activity, 10));
        row.setMinimumHeight(Ui.dp(activity, 56));
        LinearLayout col = Ui.col(activity);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(col, cl);
        col.addView(Ui.text(activity, title, 14f, Ui.INK, true));
        TextView sub = Ui.text(activity, subtitle, 12f, Ui.INK2, false);
        sub.setLineSpacing(Ui.dp(activity, 1), 1f);
        col.addView(sub);
        row.addView(sw);
        return row;
    }

    private Switch styledSwitch(boolean checked) {
        Switch s = new Switch(activity);
        s.setChecked(checked);
        s.setTrackTintList(android.content.res.ColorStateList.valueOf(
                checked ? Ui.INK : 0xFFD8DDD7));
        s.setThumbTintList(android.content.res.ColorStateList.valueOf(
                checked ? Color.WHITE : 0xFF8A908B));
        s.setOnCheckedChangeListener(null);
        return s;
    }

    private void showBudgetDialog() {
        LinearLayout box = Ui.col(activity);
        box.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 14),
                Ui.dp(activity, 20), Ui.dp(activity, 6));
        box.addView(Ui.text(activity, "每月可支配预算", 12f, Ui.INK2, false));
        EditText input = new EditText(activity);
        long current = App.get(activity).prefs().budget();
        input.setText(current > 0 ? Money.formatPlain(current) : "");
        input.setHint("例如 5000");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setTextSize(24);
        input.setBackgroundResource(com.zhihua.ledger.R.drawable.bg_input);
        input.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8),
                Ui.dp(activity, 12), Ui.dp(activity, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 8);
        box.addView(input, lp);
        TextView tip = Ui.text(activity, "只填一个你愿意执行的数。填 0 表示暂时不设预算。", 11.5f, Ui.INK3, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(activity, 6);
        box.addView(tip, tl);

        AlertDialog d = new AlertDialog.Builder(activity)
                .setTitle("月预算")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String s = input.getText().toString().trim();
                        long cents = s.length() == 0 ? 0 : Money.parse(s);
                        if (s.length() > 0 && cents < 0) {
                            Ui.toast(activity, "请输入正确的金额");
                            return;
                        }
                        App.get(activity).prefs().setBudget(cents);
                        d.dismiss();
                        host.refreshAll();
                    }
                });
            }
        });
        d.show();
    }

    private void importSms() {
        if (!PermissionUtils.readSmsPermission(activity)) {
            PermissionUtils.requestSms(activity, 501);
            Ui.toast(activity, "请允许短信权限，授权后再点一次“补录”");
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("补录近 90 天短信")
                .setMessage("只读取银行发件人（955xx 等）的消费短信，在本机解析；不会上传。已有相同金额和时间的账单会自动跳过。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始补录", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Ui.toast(activity, "正在补录，请稍候…");
                        App.get(activity).io().execute(new Runnable() {
                            @Override
                            public void run() {
                                final int n = SmsImporter.importRecent(activity, 90);
                                App.get(activity).main().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (n == SmsImporter.NO_PERMISSION) {
                                            Ui.toast(activity, "没有短信权限，无法补录");
                                        } else {
                                            Ui.toast(activity, "补录完成：新增 " + n + " 笔");
                                            host.refreshAll();
                                        }
                                    }
                                });
                            }
                        });
                    }
                }).show();
    }

    private void loadDemo() {
        if (App.get(activity).db().countDemo() > 0) {
            Ui.toast(activity, "示例数据已经载入了，可在“清空全部数据”里删除");
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("载入示例数据")
                .setMessage("会生成约 100 条示例账单和冷静清单，方便先看图表效果。示例数据可在设置里一键清空，不影响真实账单。")
                .setNegativeButton("取消", null)
                .setPositiveButton("载入", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        App.get(activity).io().execute(new Runnable() {
                            @Override
                            public void run() {
                                final int n = DemoData.insert(App.get(activity).db());
                                App.get(activity).main().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Ui.toast(activity, n > 0 ? "已载入 " + n + " 条示例" : "示例数据已存在");
                                        host.refreshAll();
                                    }
                                });
                            }
                        });
                    }
                }).show();
    }

    private void clearAll() {
        new AlertDialog.Builder(activity)
                .setTitle("清空全部数据？")
                .setMessage("会删除所有账单、分类规则、冷静清单和通知记录，无法恢复。建议先导出 CSV。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        App.get(activity).db().clearAll();
                        Ui.toast(activity, "已清空");
                        host.refreshAll();
                    }
                }).show();
    }

    private void showParseTest(String titlePrefill, String textPrefill) {
        LinearLayout box = Ui.col(activity);
        box.setPadding(Ui.dp(activity, 18), Ui.dp(activity, 10),
                Ui.dp(activity, 18), Ui.dp(activity, 4));
        EditText title = new EditText(activity);
        title.setHint("通知标题（可留空）");
        title.setText(titlePrefill == null ? "" : titlePrefill);
        title.setTextSize(12);
        title.setBackgroundResource(com.zhihua.ledger.R.drawable.bg_input);
        title.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 7),
                Ui.dp(activity, 10), Ui.dp(activity, 7));
        box.addView(title);

        EditText body = new EditText(activity);
        body.setHint("把银行 App 的消费通知原文粘贴到这里，例如：\n您尾号1234的储蓄卡于10月18日17:09在拼多多消费人民币35.50元，余额……");
        body.setText(textPrefill == null ? "" : textPrefill);
        body.setTextSize(12);
        body.setGravity(Gravity.TOP);
        body.setMinLines(5);
        body.setBackgroundResource(com.zhihua.ledger.R.drawable.bg_input);
        body.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 8),
                Ui.dp(activity, 10), Ui.dp(activity, 8));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 130));
        bl.topMargin = Ui.dp(activity, 8);
        box.addView(body, bl);

        TextView result = Ui.text(activity, "", 11.5f, Ui.INK2, false);
        result.setLineSpacing(Ui.dp(activity, 2), 1f);
        result.setPadding(0, Ui.dp(activity, 8), 0, 0);
        box.addView(result);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(box);

        AlertDialog d = new AlertDialog.Builder(activity)
                .setTitle("解析测试")
                .setView(scroll)
                .setNegativeButton("关闭", null)
                .setNeutralButton("解析", null)
                .create();
        final NotificationParser.Parsed[] parsed = new NotificationParser.Parsed[1];
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String t = title.getText().toString();
                        String b = body.getText().toString();
                        if (b.trim().length() == 0) {
                            result.setText("先粘贴一条通知原文。");
                            result.setTextColor(Ui.ORANGE);
                            return;
                        }
                        NotificationParser.Parsed p = NotificationParser.parse(t, b,
                                "com.chinamworld.main", System.currentTimeMillis());
                        parsed[0] = p;
                        if (p == null) {
                            result.setText("未识别。请确认原文里有“消费/支付/支出”和金额；"
                                    + "也可以点“最近通知记录”看建行实际推送的格式。");
                            result.setTextColor(Ui.ORANGE);
                            return;
                        }
                        Categorizer.Result c = Categorizer.categorize(p.merchant, p.platform,
                                p.rawText, null);
                        String msg = "识别成功\n金额：¥" + Money.format(p.amountCents)
                                + "\n商户：" + p.merchant
                                + (p.platform.length() > 0 ? " · 平台：" + p.platform : "")
                                + "\n分类：" + Category.name(c.category)
                                + "\n时间：" + DateX.dateTime(p.occurredAt)
                                + (p.cardTail.length() > 0 ? "\n卡尾号：" + p.cardTail : "");
                        result.setText(msg);
                        result.setTextColor(Ui.GREEN);
                        d.getButton(AlertDialog.BUTTON_NEGATIVE).setText("关闭");
                        d.getButton(AlertDialog.BUTTON_NEUTRAL).setText("记入账单");
                        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v2) {
                                saveParsed(parsed[0], c.category);
                                d.dismiss();
                            }
                        });
                    }
                });
            }
        });
        d.show();
    }

    private void saveParsed(NotificationParser.Parsed p, String category) {
        if (p == null) return;
        App.get(activity).io().execute(new Runnable() {
            @Override
            public void run() {
                Transaction t = new Transaction();
                t.amountCents = p.amountCents;
                t.merchant = p.merchant;
                t.platform = p.platform;
                t.category = category;
                t.occurredAt = p.occurredAt;
                t.cardTail = p.cardTail;
                t.balanceCents = p.balanceCents;
                t.source = "notification";
                t.rawText = p.rawText;
                t.fingerprint = p.fingerprint;
                long id = App.get(activity).db().insertTransaction(t);
                App.get(activity).main().post(new Runnable() {
                    @Override
                    public void run() {
                        Ui.toast(activity, id > 0 ? "已记入账单" : "这笔已经在账单里了");
                        host.refreshAll();
                    }
                });
            }
        });
    }

    private void showCaptures() {
        App.get(activity).io().execute(new Runnable() {
            @Override
            public void run() {
                final List<RawCapture> list = App.get(activity).db().listCaptures(20);
                App.get(activity).main().post(new Runnable() {
                    @Override
                    public void run() {
                        LinearLayout box = Ui.col(activity);
                        box.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 8),
                                Ui.dp(activity, 14), Ui.dp(activity, 8));
                        if (list.isEmpty()) {
                            box.addView(Ui.text(activity, "还没有记录。收到银行通知后，这里会保留最近 20 条的原文（仅本机）。",
                                    12f, Ui.INK2, false));
                        }
                        for (RawCapture r : list) {
                            LinearLayout item = Ui.col(activity);
                            item.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 9),
                                    Ui.dp(activity, 10), Ui.dp(activity, 9));
                            item.setBackground(Ui.round(activity, 0xFFF6F7F4, 10));
                            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            il.bottomMargin = Ui.dp(activity, 7);
                            item.setLayoutParams(il);
                            item.addView(Ui.text(activity, DateX.dateTime(r.at) + " · "
                                    + (r.parsed ? "已识别" : "未识别"), 11f,
                                    r.parsed ? Ui.GREEN : Ui.ORANGE, true));
                            item.addView(Ui.text(activity, Ui.ellipsize(r.title, 60), 12f, Ui.INK, true));
                            TextView body = Ui.text(activity, Ui.ellipsize(r.text.replace('\n', ' '), 160),
                                    11f, Ui.INK2, false);
                            body.setLineSpacing(Ui.dp(activity, 2), 1f);
                            item.addView(body);
                            item.setClickable(true);
                            item.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    showParseTest(r.title, r.text);
                                }
                            });
                            box.addView(item);
                        }
                        ScrollView scroll = new ScrollView(activity);
                        scroll.addView(box);
                        new AlertDialog.Builder(activity)
                                .setTitle("最近通知（仅本机）")
                                .setView(scroll)
                                .setPositiveButton("关闭", null)
                                .show();
                    }
                });
            }
        });
    }

    private void showMiuiGuide() {
        String brand = PermissionUtils.brandName();
        String msg = "当前设备：" + brand + "。不同品牌路径略有差异，核心是三件事："
                + "通知使用权、无障碍、后台不被清理。\n\n"
                + "① 通知使用权\n"
                + "设置 → 应用设置 → 应用管理 → 知花 → 权限 → 通知使用权。\n"
                + "部分品牌在：设置 → 特殊应用权限 → 通知使用权。\n\n"
                + "② 无障碍\n"
                + "设置 → 无障碍 / 更多设置 → 无障碍 → 已下载的应用 → 知花。\n"
                + "如果提示“受限制的设置”：应用详情右上角 ⋮ → 允许受限制的设置，再回来打开。\n\n"
                + "③ 省电与自启动\n"
                + "• 小米/HyperOS：应用管理 → 省电策略 → 无限制；自启动 → 打开。\n"
                + "• 华为/荣耀：应用启动管理 → 手动管理，允许自启动/关联启动/后台活动。\n"
                + "• OPPO/一加/realme：电池 → 应用耗电管理 → 允许后台活动；自启动 → 打开。\n"
                + "• vivo/iQOO：电池 → 后台高耗电 → 允许；自启动 → 打开。\n"
                + "• 三星：电池 → 后台使用限制 → 把知花加入“从不休眠的应用”。\n\n"
                + "④ 最近任务\n"
                + "把知花卡片下拉锁定，减少被一键清理。\n\n"
                + "⑤ 后台守护\n"
                + "知花设置里打开“后台守护”，会常驻一条低优先级通知，降低被系统杀掉后收不到提醒的概率。\n\n"
                + "如果系统更新后通知断了，重新打开一次“通知使用权”；无障碍被关闭时，后台守护会提醒你。";
        new AlertDialog.Builder(activity)
                .setTitle("手机适配与后台保护")
                .setMessage(msg)
                .setNegativeButton("关闭", null)
                .setNeutralButton("应用详情", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        PermissionUtils.openAppDetails(activity);
                    }
                })
                .setPositiveButton("无障碍", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        PermissionUtils.openAccessibilitySettings(activity);
                    }
                }).show();
    }

    private void showPrivacy() {
        String msg = "知花没有申请网络权限，账单、通知原文、分类规则都只写在这台手机的本地数据库里。\n\n"
                + "• 通知读取：解析银行 App / 短信的动账通知，以及微信/支付宝的“支付成功”通知；\n"
                + "• 无障碍：只在你选择监控的消费 App 的收银台/扫码付款/先用后付页面读取当前屏幕文字，用来识别按钮、金额和收款方；不保存页面内容，不上传；\n"
                + "• 短信（可选）：只读取发件人为银行（955xx 等）的消费短信；\n"
                + "• 导出：由你选择保存位置，知花不会自动外传。\n\n"
                + "卸载 App 会删除全部本地数据。";
        new AlertDialog.Builder(activity)
                .setTitle("隐私说明")
                .setMessage(msg)
                .setPositiveButton("知道了", null)
                .show();
    }

    private static class Data {
        boolean notify, access, sms, battery;
        long budget;
        int captures, demo;
        List<AppCatalog.Entry> apps = new ArrayList<>();
    }
}


