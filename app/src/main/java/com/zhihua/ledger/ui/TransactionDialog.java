package com.zhihua.ledger.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.DatePicker;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;

import com.zhihua.ledger.App;
import com.zhihua.ledger.R;
import com.zhihua.ledger.core.Categorizer;
import com.zhihua.ledger.data.Category;
import com.zhihua.ledger.data.DateX;
import com.zhihua.ledger.data.Money;
import com.zhihua.ledger.data.PayLater;
import com.zhihua.ledger.data.Transaction;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

/** 新增/编辑账单的底部弹层。 */
public class TransactionDialog {
    private final Activity activity;
    private final Host host;
    private final Transaction existing;
    private Dialog dialog;
    private EditText amountInput;
    private EditText merchantInput;
    private EditText noteInput;
    private CheckBox payLaterBox;
    private TextView dateButton;
    private TextView timeButton;
    private long occurredAt;
    private String category;
    private boolean manualCategory;
    private final Map<String, TextView> chips = new LinkedHashMap<>();

    public TransactionDialog(Activity activity, Host host, Transaction existing) {
        this.activity = activity;
        this.host = host;
        this.existing = existing;
        this.occurredAt = existing == null ? System.currentTimeMillis() : existing.occurredAt;
        this.category = existing == null ? Category.OTHER : existing.category;
        this.manualCategory = existing != null;
        show();
    }

    private void show() {
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.TRANSPARENT);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout wrap = Ui.col(activity);
        wrap.setGravity(Gravity.BOTTOM);
        scroll.addView(wrap, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout sheet = Ui.col(activity);
        sheet.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 14),
                Ui.dp(activity, 20), Ui.dp(activity, 24));
        sheet.setBackgroundResource(R.drawable.bg_bottom_sheet);
        wrap.addView(sheet, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = Ui.row(activity);
        top.addView(Ui.title(activity, existing == null ? "记一笔"
                : existing.needsCategory == 1 ? "补分类" : "编辑账单", 20f));
        top.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView close = Ui.text(activity, "×", 24f, Ui.INK3, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭记账");
        close.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 2), Ui.dp(activity, 4));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        top.addView(close);
        sheet.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (existing != null && existing.rawText != null && existing.rawText.length() > 0) {
            TextView src = Ui.text(activity, "来源：" + existing.sourceLabel()
                    + (existing.cardTail != null && existing.cardTail.length() > 0
                    ? " · 尾号 " + existing.cardTail : ""), 11f, Ui.INK3, false);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sl.topMargin = Ui.dp(activity, 4);
            sheet.addView(src, sl);
        }
        if (existing != null && existing.needsCategory == 1) {
            TextView hint = Ui.text(activity, "自动分类没认出来，请选一个分类；知花会记住这个收款方。",
                    11f, Ui.ORANGE, false);
            hint.setBackground(Ui.round(activity, Ui.ORANGE_SOFT, 12));
            hint.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 7),
                    Ui.dp(activity, 10), Ui.dp(activity, 7));
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hl.topMargin = Ui.dp(activity, 8);
            sheet.addView(hint, hl);
        }

        // 金额
        LinearLayout amountBox = Ui.row(activity);
        amountBox.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 6),
                Ui.dp(activity, 14), Ui.dp(activity, 6));
        amountBox.setBackground(Ui.round(activity, 0xFFF6F7F4, 12));
        LinearLayout.LayoutParams abl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        abl.topMargin = Ui.dp(activity, 12);
        sheet.addView(amountBox, abl);
        amountBox.addView(Ui.text(activity, "¥", 22f, Ui.PRIMARY, true));
        amountInput = new EditText(activity);
        amountInput.setText(existing == null ? "" : Money.formatPlain(existing.amountCents));
        amountInput.setHint("0.00");
        amountInput.setHintTextColor(Ui.INK3);
        amountInput.setTextSize(32);
        amountInput.setTextColor(Ui.INK);
        amountInput.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        amountInput.setBackground(null);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setPadding(Ui.dp(activity, 8), 0, 0, 0);
        amountInput.setSingleLine(true);
        amountBox.addView(amountInput, new LinearLayout.LayoutParams(0, Ui.dp(activity, 54), 1f));

        merchantInput = new EditText(activity);
        merchantInput.setText(existing == null ? "" : safe(existing.merchant));
        merchantInput.setHint("商户 / 平台，例如：拼多多、美团外卖");
        merchantInput.setTextSize(14);
        merchantInput.setHintTextColor(Ui.INK3);
        merchantInput.setBackgroundResource(R.drawable.bg_input);
        merchantInput.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10),
                Ui.dp(activity, 12), Ui.dp(activity, 10));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ml.topMargin = Ui.dp(activity, 10);
        sheet.addView(merchantInput, ml);
        merchantInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (manualCategory) return;
                Categorizer.Result r = Categorizer.categorize(s.toString(), "", s.toString(), null);
                if (r.confidence >= 0.5) {
                    category = r.category;
                    refreshChips();
                }
            }
        });

        // 分类
        TextView catTitle = Ui.text(activity, "分类", 12f, Ui.INK2, true);
        LinearLayout.LayoutParams ctl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ctl.topMargin = Ui.dp(activity, 14);
        sheet.addView(catTitle, ctl);
        LinearLayout grid = Ui.col(activity);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.topMargin = Ui.dp(activity, 6);
        sheet.addView(grid, gl);
        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            LinearLayout row = Ui.row(activity);
            for (int i = 0; i < 4; i++) {
                int idx = rowIndex * 4 + i;
                if (idx >= Category.KEYS.length) break;
                String key = Category.KEYS[idx];
                TextView chip = Ui.text(activity, Category.name(key),
                        12f, Ui.INK2, true);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(0, Ui.dp(activity, 7), 0, Ui.dp(activity, 7));
                chip.setTag(key);
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        manualCategory = true;
                        category = (String) v.getTag();
                        refreshChips();
                    }
                });
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cl.setMargins(Ui.dp(activity, 3), Ui.dp(activity, 3),
                        Ui.dp(activity, 3), Ui.dp(activity, 3));
                row.addView(chip, cl);
                chips.put(key, chip);
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        refreshChips();

        // 日期时间
        LinearLayout dateRow = Ui.row(activity);
        LinearLayout.LayoutParams drl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        drl.topMargin = Ui.dp(activity, 12);
        sheet.addView(dateRow, drl);
        dateButton = pickerButton("");
        timeButton = pickerButton("");
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f);
        dl.rightMargin = Ui.dp(activity, 7);
        dateRow.addView(dateButton, dl);
        dateRow.addView(timeButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        refreshDateTime();
        dateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickDate();
            }
        });
        timeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime();
            }
        });

        noteInput = new EditText(activity);
        noteInput.setText(existing == null ? "" : safe(existing.note));
        noteInput.setHint("备注（可选）");
        noteInput.setTextSize(13);
        noteInput.setHintTextColor(Ui.INK3);
        noteInput.setBackgroundResource(R.drawable.bg_input);
        noteInput.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 9),
                Ui.dp(activity, 12), Ui.dp(activity, 9));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = Ui.dp(activity, 10);
        sheet.addView(noteInput, nl);

        if (existing == null) {
            payLaterBox = new CheckBox(activity);
            payLaterBox.setText("先用后付（暂不计入支出，实际付款后再入账）");
            payLaterBox.setTextSize(12.5f);
            payLaterBox.setTextColor(Ui.INK2);
            payLaterBox.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
            LinearLayout.LayoutParams plb = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            plb.topMargin = Ui.dp(activity, 8);
            sheet.addView(payLaterBox, plb);
        }

        TextView save = Ui.primaryButton(activity, existing == null ? "保存这一笔" : "保存修改");
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 52));
        sl.topMargin = Ui.dp(activity, 14);
        sheet.addView(save, sl);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });

        if (existing != null) {
            TextView delete = Ui.text(activity, "删除这笔账单", 12.5f, Ui.RED, true);
            delete.setGravity(Gravity.CENTER);
            delete.setPadding(0, Ui.dp(activity, 12), 0, Ui.dp(activity, 4));
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete();
                }
            });
            sheet.addView(delete, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.45f);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        amountInput.requestFocus();
        if (w != null) {
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private TextView pickerButton(String text) {
        TextView t = Ui.text(activity, text, 12.5f, Ui.INK, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(activity, 10), 0, Ui.dp(activity, 10));
        t.setBackgroundResource(R.drawable.bg_input);
        return t;
    }

    private void refreshChips() {
        for (Map.Entry<String, TextView> e : chips.entrySet()) {
            boolean sel = e.getKey().equals(category);
            TextView v = e.getValue();
            if (sel) {
                v.setBackground(Ui.roundStroke(activity, 0xFFE7EAE4, Ui.INK, 10));
                v.setTextColor(Ui.INK);
            } else {
                v.setBackground(Ui.round(activity, 0xFFF6F7F4, 10));
                v.setTextColor(Ui.INK2);
            }
        }
    }

    private void refreshDateTime() {
        dateButton.setText(DateX.monthDay(occurredAt) + " · " + DateX.weekday(occurredAt));
        timeButton.setText(DateX.time(occurredAt));
    }

    private void pickDate() {
        Calendar c = DateX.cal(occurredAt);
        DatePickerDialog d = new DatePickerDialog(activity,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        Calendar n = DateX.cal(occurredAt);
                        n.set(year, month, dayOfMonth);
                        occurredAt = n.getTimeInMillis();
                        refreshDateTime();
                    }
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        d.show();
    }

    private void pickTime() {
        Calendar c = DateX.cal(occurredAt);
        TimePickerDialog d = new TimePickerDialog(activity,
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        Calendar n = DateX.cal(occurredAt);
                        n.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        n.set(Calendar.MINUTE, minute);
                        n.set(Calendar.SECOND, 0);
                        occurredAt = n.getTimeInMillis();
                        refreshDateTime();
                    }
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true);
        d.show();
    }

    private void save() {
        long cents = Money.parse(amountInput.getText().toString());
        if (cents <= 0) {
            Ui.toast(activity, "请输入大于 0 的金额");
            return;
        }
        String merchant = merchantInput.getText().toString().trim();
        if (merchant.length() == 0) merchant = "手动记账";
        String note = noteInput.getText().toString().trim();
        final String finalMerchant = merchant;
        final String finalNote = note;
        final long finalCents = cents;
        final long finalTime = occurredAt;
        final String finalCategory = Category.valid(category) ? category : Category.OTHER;

        if (existing == null && payLaterBox != null && payLaterBox.isChecked()) {
            savePayLater(finalCents, finalMerchant, finalNote, finalTime, finalCategory);
            return;
        }

        App.get(activity).io().execute(new Runnable() {
            @Override
            public void run() {
                Transaction t;
                if (existing == null) {
                    t = new Transaction();
                    t.source = "manual";
                    t.fingerprint = "";
                } else {
                    t = existing.copy();
                }
                t.amountCents = finalCents;
                t.merchant = finalMerchant;
                t.category = finalCategory;
                t.needsCategory = 0;
                t.occurredAt = finalTime;
                t.note = finalNote;
                if (existing == null) {
                    App.get(activity).db().insertTransaction(t);
                } else {
                    App.get(activity).db().updateTransaction(t);
                }
                if (!Category.OTHER.equals(finalCategory) && finalMerchant.length() > 1) {
                    App.get(activity).db().learnRule(finalMerchant, finalCategory);
                }
                App.get(activity).main().post(new Runnable() {
                    @Override
                    public void run() {
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        host.refreshAll();
                    }
                });
            }
        });
    }

    private void savePayLater(final long cents, final String merchant, final String note,
                              final long time, final String category) {
        App.get(activity).io().execute(new Runnable() {
            @Override
            public void run() {
                PayLater p = new PayLater();
                p.amountCents = cents;
                p.merchant = merchant;
                p.category = category;
                p.createdAt = time;
                p.appLabel = "手动记账";
                p.note = note;
                App.get(activity).db().insertPayLater(p);
                App.get(activity).main().post(new Runnable() {
                    @Override
                    public void run() {
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        Ui.toast(activity, "已记下先用后付，实际付款后再入账");
                        host.refreshAll();
                    }
                });
            }
        });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(activity)
                .setTitle("删除这笔账单？")
                .setMessage("删除后无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        final long id = existing.id;
                        App.get(activity).io().execute(new Runnable() {
                            @Override
                            public void run() {
                                App.get(activity).db().deleteTransaction(id);
                                App.get(activity).main().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                                        host.refreshAll();
                                    }
                                });
                            }
                        });
                    }
                }).show();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}


