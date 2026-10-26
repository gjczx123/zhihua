package com.zhihua.ledger.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.DateX;
import com.zhihua.ledger.data.Money;

import java.util.LinkedHashMap;
import java.util.Map;

/** 付款前全屏“减速带”。由无障碍服务直接添加到屏幕上。 */
public class ImpulseOverlayView extends FrameLayout {

    public interface Listener {
        void onAbandon(ImpulseOverlayView v, long amountCents, String category);

        void onContinue(ImpulseOverlayView v, long amountCents, String category);

        void onDismiss(ImpulseOverlayView v, long amountCents, String category);
    }

    private static final String[] CHIP_KEYS = Category.KEYS;

    private final Listener listener;
    private final String appLabel;
    private final int pauseSeconds;
    private final boolean scanMode;
    private final Map<String, TextView> chips = new LinkedHashMap<>();
    private EditText amountInput;
    private TextView contextView;
    private CheckBox confirmBox;
    private TextView continueButton;
    private TextView pauseButton;
    private String selectedCategory;
    private boolean categoryChosen;
    private int remaining;
    private boolean finished;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public ImpulseOverlayView(Context context, String appLabel, long amountCents,
                              String category, int pauseSeconds, Listener listener) {
        this(context, appLabel, amountCents, category, pauseSeconds, false, listener);
    }

    public ImpulseOverlayView(Context context, String appLabel, long amountCents,
                              String category, int pauseSeconds, boolean scanMode, Listener listener) {
        super(context);
        this.listener = listener;
        this.appLabel = appLabel == null ? "购物 App" : appLabel;
        this.pauseSeconds = Math.max(3, pauseSeconds);
        this.scanMode = scanMode;
        this.remaining = this.pauseSeconds;
        this.selectedCategory = Category.valid(category) ? category : Category.SHOPPING;
        this.categoryChosen = !scanMode
                || (Category.valid(category) && !Category.OTHER.equals(category));
        setClickable(true);
        setFocusable(true);
        setBackgroundColor(0xCC08131B);
        build(amountCents);
        startCountdown();
    }

    private void build(long amountCents) {
        Context c = getContext();
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout wrap = Ui.col(c);
        wrap.setGravity(Gravity.CENTER);
        wrap.setMinimumHeight(getResources().getDisplayMetrics().heightPixels);
        wrap.setPadding(Ui.dp(c, 16), Ui.dp(c, 30), Ui.dp(c, 16), Ui.dp(c, 30));
        scroll.addView(wrap, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = Ui.col(c);
        card.setPadding(Ui.dp(c, 22), Ui.dp(c, 18), Ui.dp(c, 22), Ui.dp(c, 18));
        card.setBackground(Ui.round(c, Color.WHITE, 20));
        card.setElevation(Ui.dp(c, 12));
        wrap.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 顶部
        LinearLayout top = Ui.row(c);
        TextView badge = Ui.text(c, scanMode ? "扫码付款确认" : "付款前停一下",
                11.5f, Ui.ORANGE, true);
        badge.setPadding(Ui.dp(c, 9), Ui.dp(c, 5), Ui.dp(c, 9), Ui.dp(c, 5));
        badge.setBackground(Ui.round(c, Ui.ORANGE_SOFT, 999));        top.addView(badge);
        TextView app = Ui.text(c, "  " + appLabel, 12.5f, Ui.INK2, true);
        top.addView(app);
        top.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView close = Ui.text(c, "×", 24f, Ui.INK3, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭提醒");
        close.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 4), Ui.dp(c, 4));
        close.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finishDismiss();
            }
        });
        top.addView(close);
        card.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = Ui.title(c, scanMode ? "扫码付款，先确认一下" : "这笔，真的要买吗？", 26f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Ui.dp(c, 14);
        card.addView(title, titleLp);

        TextView sub = Ui.text(c, scanMode
                ? "先选分类，核对收款方和金额，再决定要不要付。"
                : "先停 " + pauseSeconds + " 秒，给冲动一个缓冲。", 13f, Ui.INK2, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(c, 6);
        card.addView(sub, subLp);

        // 金额
        LinearLayout amountBox = Ui.col(c);
        amountBox.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 10));
        amountBox.setBackground(Ui.round(c, 0xFFF6F7F4, 12));
        LinearLayout.LayoutParams amountLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        amountLp.topMargin = Ui.dp(c, 16);
        card.addView(amountBox, amountLp);

        TextView amountLabel = Ui.text(c, "识别金额（可修改）", 11.5f, Ui.INK3, false);
        amountBox.addView(amountLabel);
        LinearLayout amountRow = Ui.row(c);
        TextView yuan = Ui.text(c, "¥", 22f, Ui.INK2, true);
        amountRow.addView(yuan);
        amountInput = new EditText(c);
        amountInput.setText(amountCents > 0 ? Money.formatPlain(amountCents) : "");
        amountInput.setHint("0.00");
        amountInput.setHintTextColor(Ui.INK3);
        amountInput.setTextSize(34);
        amountInput.setTextColor(Ui.INK);
        amountInput.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        amountInput.setBackground(null);
        amountInput.setPadding(Ui.dp(c, 8), 0, 0, 0);
        amountInput.setSingleLine(true);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setGravity(Gravity.CENTER_VERTICAL);
        amountInput.setContentDescription("消费金额");
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0,
                Ui.dp(c, 52), 1f);
        amountRow.addView(amountInput, inputLp);
        amountBox.addView(amountRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        contextView = Ui.text(c, "正在读取本月消费…", 12f, Ui.INK2, false);
        contextView.setLineSpacing(Ui.dp(c, 2), 1f);
        LinearLayout.LayoutParams ctxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ctxLp.topMargin = Ui.dp(c, 12);
        card.addView(contextView, ctxLp);

        // 分类
        TextView catTitle = Ui.text(c, "这笔算什么？", 13f, Ui.INK, true);
        LinearLayout.LayoutParams catTitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        catTitleLp.topMargin = Ui.dp(c, 18);
        card.addView(catTitle, catTitleLp);

        LinearLayout grid = Ui.col(c);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridLp.topMargin = Ui.dp(c, 8);
        card.addView(grid, gridLp);
        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            LinearLayout row = Ui.row(c);
            for (int i = 0; i < 4; i++) {
                int idx = rowIndex * 4 + i;
                if (idx >= CHIP_KEYS.length) break;
                TextView chip = makeChip(c, CHIP_KEYS[idx]);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        Ui.dp(c, 48), 1f);
                lp.setMargins(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
                row.addView(chip, lp);
                chips.put(CHIP_KEYS[idx], chip);
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        refreshChips();

        // 确认
        confirmBox = new CheckBox(c);
        confirmBox.setText(scanMode ? "我已核对收款方和金额" : "我已想清楚，不是冲动消费");
        confirmBox.setTextSize(13);
        confirmBox.setTextColor(Ui.INK2);
        confirmBox.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
        confirmBox.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbLp.topMargin = Ui.dp(c, 12);
        card.addView(confirmBox, cbLp);

        // 按钮
        pauseButton = Ui.primaryButton(c, scanMode ? "先不付了" : "再想想，先不买");
        pauseButton.setBackground(Ui.ripple(c, Ui.PRIMARY, 0x33FFFFFF, 16));
        LinearLayout.LayoutParams pauseLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 52));
        pauseLp.topMargin = Ui.dp(c, 12);
        card.addView(pauseButton, pauseLp);
        pauseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finishAbandon();
            }
        });

        continueButton = Ui.secondaryButton(c, "仍然支付（" + pauseSeconds + "s）");
        LinearLayout.LayoutParams contLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 50));
        contLp.topMargin = Ui.dp(c, 9);
        card.addView(continueButton, contLp);
        continueButton.setEnabled(false);
        continueButton.setAlpha(0.45f);
        continueButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (scanMode && !categoryChosen) {
                    Ui.toast(getContext(), "先选一个分类；不知道就选“其他”");
                    return;
                }
                if (!confirmBox.isChecked()) {
                    Ui.toast(getContext(), scanMode ? "先勾选“我已核对收款方和金额”" : "先勾选“我已想清楚”，再继续支付");
                    return;
                }
                finishContinue();
            }
        });

        TextView footer = Ui.text(c, scanMode
                ? "知花不会替你付款，只帮你核对这笔花在哪。"
                : "知花不会替你付款，只帮你按下暂停键。", 11f, Ui.INK3, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = Ui.dp(c, 12);
        card.addView(footer, footerLp);

        setAlpha(0f);
        setTranslationY(Ui.dp(c, 24));
        animate().alpha(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private TextView makeChip(Context c, String key) {
        TextView chip = Ui.text(c, Category.name(key), 12.5f, Ui.INK2, true);
        chip.setGravity(Gravity.CENTER);
        chip.setTag(key);
        chip.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedCategory = (String) v.getTag();
                categoryChosen = true;
                refreshChips();
            }
        });
        return chip;
    }

    private void refreshChips() {
        for (Map.Entry<String, TextView> e : chips.entrySet()) {
            boolean selected = e.getKey().equals(selectedCategory);
            TextView v = e.getValue();
            if (selected) {
                v.setBackground(Ui.roundStroke(getContext(), 0xFFE7EAE4, Ui.INK, 10));
                v.setTextColor(Ui.INK);
            } else {
                v.setBackground(Ui.round(getContext(), 0xFFF6F7F4, 10));
                v.setTextColor(Ui.INK2);
            }
        }
    }

    private void startCountdown() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (finished) return;
                remaining--;
                if (remaining <= 0) {
                    continueButton.setText(scanMode ? "确认付款" : "仍然支付");
                    continueButton.setEnabled(true);
                    continueButton.setAlpha(1f);
                } else {
                    continueButton.setText((scanMode ? "确认付款" : "仍然支付") + "（" + remaining + "s）");
                    handler.postDelayed(this, 1000);
                }
            }
        }, 1000);
    }

    public void setStats(long monthSpent, int sameCategoryCount, long monthlyBudget) {
        if (contextView == null) return;
        long amount = currentAmount();
        StringBuilder sb = new StringBuilder();
        sb.append("本月已花 ").append(Money.yuan(monthSpent));
        if (monthlyBudget > 0) {
            int pct = (int) Math.round(monthSpent * 100.0 / monthlyBudget);
            sb.append(" · 预算已用 ").append(pct).append("%");
        }
        sb.append("\n同类消费：本月第 ").append(Math.max(1, sameCategoryCount)).append(" 笔");
        if (amount > 0 && monthSpent > 0) {
            double pct = amount * 100.0 / monthSpent;
            sb.append(" · 这件约占 ").append(String.format(java.util.Locale.CHINA, "%.1f", pct)).append("%");
        }
        contextView.setText(sb.toString());
    }

    public long currentAmount() {
        if (amountInput == null) return 0;
        String s = amountInput.getText() == null ? "" : amountInput.getText().toString().trim();
        if (s.length() == 0) return 0;
        long v = Money.parse(s);
        return Math.max(0, v);
    }

    public String currentCategory() {
        return Category.valid(selectedCategory) ? selectedCategory : Category.SHOPPING;
    }

    private void finishAbandon() {
        if (finished) return;
        finished = true;
        long amount = currentAmount();
        String cat = currentCategory();
        hide(new Runnable() {
            @Override
            public void run() {
                listener.onAbandon(ImpulseOverlayView.this, amount, cat);
            }
        });
    }

    private void finishContinue() {
        if (finished) return;
        finished = true;
        long amount = currentAmount();
        String cat = currentCategory();
        hide(new Runnable() {
            @Override
            public void run() {
                listener.onContinue(ImpulseOverlayView.this, amount, cat);
            }
        });
    }

    private void finishDismiss() {
        if (finished) return;
        finished = true;
        long amount = currentAmount();
        String cat = currentCategory();
        hide(new Runnable() {
            @Override
            public void run() {
                listener.onDismiss(ImpulseOverlayView.this, amount, cat);
            }
        });
    }

    private void hide(final Runnable end) {
        handler.removeCallbacksAndMessages(null);
        animate().alpha(0f).translationY(Ui.dp(getContext(), 20)).setDuration(160)
                .withEndAction(end).start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                Ui.toast(getContext(), "再想想，或点右上角关闭");
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}

