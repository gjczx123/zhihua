package com.zhihua.ledger.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.zhihua.ledger.R;

public final class Ui {
    public static final int INK = 0xFF171A17;
    public static final int INK2 = 0xFF5C625D;
    public static final int INK3 = 0xFF6C726D;
    public static final int LINE = 0xFFE2E6E0;
    public static final int BG = 0xFFF3F5F1;
    public static final int SURFACE = 0xFFFFFFFF;
    public static final int PRIMARY = 0xFF171A17;
    public static final int PRIMARY_SOFT = 0xFFECEFEA;
    public static final int TEAL = 0xFF1F6B57;
    public static final int ACCENT = 0xFF1F6B57;
    public static final int GREEN = 0xFF2E7D32;
    public static final int GREEN_SOFT = 0xFFE8F1EA;
    public static final int ORANGE = 0xFF8A5A14;
    public static final int ORANGE_SOFT = 0xFFFBF3E4;
    public static final int RED = 0xFFB3261E;
    public static final int RED_SOFT = 0xFFFBEDEC;

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context c, String s, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setText(s == null ? "" : s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setFontFeatureSettings("tnum");
        if (medium) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView title(Context c, String s, float sp) {
        TextView t = text(c, s, sp, INK, true);
        t.setLetterSpacing(-0.01f);
        return t;
    }

    public static TextView label(Context c, String s, float sp, int color) {
        return text(c, s, sp, color, false);
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static View spacer(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dp)));
        return v;
    }

    public static View hSpacer(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, dp), 1));
        return v;
    }

    public static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable roundStroke(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable d = round(c, fill, radiusDp);
        d.setStroke(dp(c, 1), stroke);
        return d;
    }

    public static GradientDrawable gradient(Context c, int[] colors, float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static RippleDrawable ripple(Context c, int fill, int rippleColor, float radiusDp) {
        GradientDrawable content = round(c, fill, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        l.setBackgroundResource(R.drawable.bg_card);
        return l;
    }

    public static LinearLayout.LayoutParams cardLp(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 12);
        return lp;
    }

    public static TextView pill(Context c, String s, int color, int bgRes) {
        TextView t = text(c, s, 11.5f, color, true);
        t.setPadding(dp(c, 9), dp(c, 4), dp(c, 9), dp(c, 4));
        t.setBackgroundResource(bgRes);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    public static TextView pill(Context c, String s, int color, int bgColor, boolean solid) {
        TextView t = text(c, s, 11.5f, color, true);
        t.setPadding(dp(c, 9), dp(c, 4), dp(c, 9), dp(c, 4));
        t.setBackground(round(c, bgColor, 999));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    public static TextView primaryButton(Context c, String s) {
        TextView t = text(c, s, 15f, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_button_primary);
        t.setMinHeight(dp(c, 48));
        t.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    public static TextView secondaryButton(Context c, String s) {
        TextView t = text(c, s, 14f, INK, true);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_button_secondary);
        t.setMinHeight(dp(c, 48));
        t.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    public static ImageView icon(Context c, int res, int sizeDp, int tint) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setColorFilter(tint);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        v.setLayoutParams(lp);
        return v;
    }

    public static View dot(Context c, int color, float sizeDp) {
        View v = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        v.setBackground(d);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f)));
        lp.topMargin = dp(c, 12);
        lp.bottomMargin = dp(c, 12);
        v.setLayoutParams(lp);
        return v;
    }

    public static void addFull(LinearLayout parent, View child) {
        parent.addView(child, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public static void fillWidth(View v) {
        if (v.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            v.setLayoutParams(lp);
        } else {
            v.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    public static void margin(View v, int l, int t, int r, int b) {
        if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.setMargins(dp(v.getContext(), l), dp(v.getContext(), t),
                    dp(v.getContext(), r), dp(v.getContext(), b));
            v.setLayoutParams(lp);
        }
    }

    public static void weight(View v, float w) {
        if (v.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) v.getLayoutParams()).weight = w;
        }
    }

    public static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }

    public static void hideKeyboard(Activity a) {
        View v = a.getCurrentFocus();
        if (v != null) {
            InputMethodManager im = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    public static TextView sectionTitle(Context c, String s) {
        TextView t = text(c, s, 13f, INK2, true);
        t.setLetterSpacing(0f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(c, 2);
        lp.topMargin = dp(c, 12);
        lp.bottomMargin = dp(c, 8);
        t.setLayoutParams(lp);
        return t;
    }

    public static String ellipsize(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    public static boolean empty(String s) {
        return TextUtils.isEmpty(s);
    }
}
