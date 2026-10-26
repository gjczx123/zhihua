package com.zhihua.ledger;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zhihua.ledger.core.PermissionUtils;
import com.zhihua.ledger.data.Transaction;
import com.zhihua.ledger.export.CsvExporter;
import com.zhihua.ledger.service.GuardService;
import com.zhihua.ledger.ui.Host;
import com.zhihua.ledger.ui.TransactionDialog;
import com.zhihua.ledger.ui.Ui;
import com.zhihua.ledger.ui.screen.HomeScreen;
import com.zhihua.ledger.ui.screen.LedgerScreen;
import com.zhihua.ledger.ui.screen.SettingsScreen;
import com.zhihua.ledger.ui.screen.StatsScreen;
import com.zhihua.ledger.ui.widget.BottomNavView;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements Host, App.DataListener {
    private FrameLayout content;
    private BottomNavView bottomNav;
    private HomeScreen home;
    private StatsScreen stats;
    private LedgerScreen ledger;
    private SettingsScreen settings;
    private int tab = 0;
    private boolean refreshScheduled;
    private boolean pendingOnboarding;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshScheduled = false;
            refreshAll();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyBars();
        setContentView(R.layout.activity_main);
        content = findViewById(R.id.content);
        bottomNav = findViewById(R.id.bottom_nav);

        home = new HomeScreen(this, this);
        stats = new StatsScreen(this, this);
        ledger = new LedgerScreen(this, this);
        settings = new SettingsScreen(this, this);
        addScreen(home);
        addScreen(stats);
        addScreen(ledger);
        addScreen(settings);

        bottomNav.setListener(new BottomNavView.Listener() {
            @Override
            public void onTabSelected(int index) {
                selectTab(index);
            }
        });

        App.get(this).addDataListener(this);
        selectTab(0);
        boolean notificationsGranted = android.os.Build.VERSION.SDK_INT < 33
                || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (!notificationsGranted) {
            pendingOnboarding = true;
            PermissionUtils.requestPostNotifications(this, 601);
        } else {
            showOnboardingDelayed();
        }
        long pendingId = getIntent() == null ? 0 : getIntent().getLongExtra("pending_tx_id", 0);
        if (pendingId > 0) {
            content.postDelayed(new Runnable() {
                @Override
                public void run() {
                    openPendingCategory(pendingId);
                }
            }, 500);
        }
    }

    private void openPendingCategory(final long id) {
        App.get(this).io().execute(new Runnable() {
            @Override
            public void run() {
                final Transaction t = App.get(MainActivity.this).db().findById(id);
                if (t == null) return;
                App.get(MainActivity.this).main().post(new Runnable() {
                    @Override
                    public void run() {
                        selectTab(2);
                        new TransactionDialog(MainActivity.this, MainActivity.this, t);
                    }
                });
            }
        });
    }

    private void showOnboardingDelayed() {
        if (App.get(this).prefs().onboardingDone()) return;
        content.postDelayed(new Runnable() {
            @Override
            public void run() {
                showOnboarding();
            }
        }, 450);
    }

    private void addScreen(View v) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        v.setVisibility(View.GONE);
        content.addView(v, lp);
    }

    private void applyBars() {
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        w.getDecorView().setSystemUiVisibility(flags);
    }

    public void selectTab(int index) {
        if (index < 0 || index > 3) return;
        tab = index;
        home.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        stats.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        ledger.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        settings.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        bottomNav.setSelected(index);
        if (index == 0) home.refresh();
        else if (index == 1) stats.refresh();
        else if (index == 2) ledger.refresh();
        else settings.refresh();
    }

    public void refreshAll() {
        if (home == null) return;
        home.refresh();
        stats.refresh();
        ledger.refresh();
        settings.refresh();
    }

    @Override
    public void onDataChanged() {
        if (refreshScheduled) return;
        refreshScheduled = true;
        content.postDelayed(refreshRunnable, 280);
    }

    @Override
    public void openAdd() {
        new TransactionDialog(this, this, null);
    }

    @Override
    public void openEdit(Transaction t) {
        if (t != null) new TransactionDialog(this, this, t);
    }

    @Override
    public void openSettings() {
        selectTab(3);
    }

    @Override
    public void exportCsv() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(new Date());
        i.putExtra(Intent.EXTRA_TITLE, "知花账单_" + stamp + ".csv");
        try {
            startActivityForResult(i, 900);
        } catch (Exception e) {
            Ui.toast(this, "没有找到可用的文件选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 900 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            final Uri uri = data.getData();
            final List<Transaction> list = App.get(this).db().listAll();
            App.get(this).io().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        OutputStream out = getContentResolver().openOutputStream(uri);
                        if (out == null) throw new Exception("no stream");
                        CsvExporter.write(out, list);
                        out.close();
                        App.get(MainActivity.this).main().post(new Runnable() {
                            @Override
                            public void run() {
                                Ui.toast(MainActivity.this, "已导出 " + list.size() + " 笔账单");
                            }
                        });
                    } catch (Exception e) {
                        App.get(MainActivity.this).main().post(new Runnable() {
                            @Override
                            public void run() {
                                Ui.toast(MainActivity.this, "导出失败，请换个位置再试");
                            }
                        });
                    }
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 501) {
            if (PermissionUtils.smsPermission(this)) {
                Ui.toast(this, "短信权限已开启，可以点“补录近90天短信”了");
            } else {
                Ui.toast(this, "未获得短信权限，可稍后在系统设置里开启");
            }
            settings.refresh();
        } else if (requestCode == 601 && pendingOnboarding) {
            pendingOnboarding = false;
            showOnboardingDelayed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (App.get(this).prefs().backgroundGuard()) {
            GuardService.start(this);
        }
        if (settings != null) {
            if (tab == 0) home.refresh();
            else if (tab == 1) stats.refresh();
            else if (tab == 2) ledger.refresh();
            else settings.refresh();
        }
    }

    @Override
    public void onBackPressed() {
        if (tab != 0) {
            selectTab(0);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasExtra("tab")) {
            selectTab(intent.getIntExtra("tab", 0));
        }
        if (intent != null && intent.getLongExtra("pending_tx_id", 0) > 0) {
            openPendingCategory(intent.getLongExtra("pending_tx_id", 0));
        }
    }

    @Override
    protected void onDestroy() {
        App.get(this).removeDataListener(this);
        content.removeCallbacks(refreshRunnable);
        super.onDestroy();
    }

    private void showOnboarding() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = Ui.col(this);
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 20));
        box.setBackground(Ui.round(this, Color.WHITE, 20));

        box.addView(Ui.text(this, "知花", 12f, Ui.INK2, true));
        TextView title = Ui.title(this, "每一笔，都知道去向", 24f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = Ui.dp(this, 8);
        box.addView(title, tl);
        TextView sub = Ui.text(this, "把银行、微信、支付宝的消费自动变成看得懂的账单。", 12.5f,
                Ui.INK2, false);
        sub.setLineSpacing(Ui.dp(this, 2), 1f);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = Ui.dp(this, 6);
        box.addView(sub, sl);

        String[][] steps = {
                {"1", "通知读取", "银行 App 的消费通知自动入账，不用手动记。"},
                {"2", "付款前提醒", "收银台、扫码或先用后付时，先核对金额和分类。"},
                {"3", "月预算与统计", "日 / 周 / 月 / 年统计、习惯总结和建议会慢慢长出来。"}
        };
        for (String[] step : steps) {
            LinearLayout row = Ui.row(this);
            row.setGravity(Gravity.TOP);
            TextView num = Ui.text(this, step[0], 11f, Ui.INK3, true);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(
                    Ui.dp(this, 22), ViewGroup.LayoutParams.WRAP_CONTENT);
            nl.topMargin = Ui.dp(this, 2);
            row.addView(num, nl);
            LinearLayout col = Ui.col(this);
            col.addView(Ui.text(this, step[1], 13.5f, Ui.INK, true));
            TextView body = Ui.text(this, step[2], 12f, Ui.INK2, false);
            body.setLineSpacing(Ui.dp(this, 2), 1f);
            col.addView(body);
            row.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.topMargin = Ui.dp(this, 14);
            box.addView(row, rl);
        }

        TextView privacy = Ui.text(this, "所有数据只保存在这台手机上，没有网络权限。",
                11.5f, Ui.INK3, false);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pl.topMargin = Ui.dp(this, 16);
        box.addView(privacy, pl);

        TextView go = Ui.primaryButton(this, "去开启");
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        gl.topMargin = Ui.dp(this, 18);
        box.addView(go, gl);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                App.get(MainActivity.this).prefs().setOnboardingDone();
                dialog.dismiss();
                selectTab(3);
            }
        });
        TextView skip = Ui.text(this, "先逛逛", 12.5f, Ui.INK2, true);
        skip.setGravity(Gravity.CENTER);
        skip.setPadding(0, Ui.dp(this, 12), 0, 0);
        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                App.get(MainActivity.this).prefs().setOnboardingDone();
                dialog.dismiss();
            }
        });
        box.addView(skip);

        dialog.setContentView(box);
        dialog.setCancelable(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.getDecorView().setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        }
        dialog.show();
    }
}
