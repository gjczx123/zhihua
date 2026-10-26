package com.zhihua.ledger.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.zhihua.ledger.core.StatsEngine;
import com.zhihua.ledger.data.Money;
import com.zhihua.ledger.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** 周期趋势柱状图，当前周期高亮。 */
public class BarChartView extends View {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final List<StatsEngine.Bucket> data = new ArrayList<>();
    private float progress = 1f;
    private ValueAnimator animator;
    private long max;

    public BarChartView(Context context) {
        super(context);
        init();
    }

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BarChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Ui.INK3);
        textPaint.setTextSize(Ui.dp(getContext(), 11));
        gridPaint.setColor(0xFFE7EAE4);
        gridPaint.setStrokeWidth(Math.max(1, Ui.dp(getContext(), 0.7f)));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setColor(Ui.INK);
        valuePaint.setTextSize(Ui.dp(getContext(), 11));
        valuePaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
    }

    public void setData(List<StatsEngine.Bucket> buckets) {
        data.clear();
        if (buckets != null) data.addAll(buckets);
        max = 1;
        for (StatsEngine.Bucket b : data) max = Math.max(max, b.total);
        if (animator != null) animator.cancel();
        progress = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(560);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = Ui.dp(getContext(), 178);
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            h = MeasureSpec.getSize(heightMeasureSpec);
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data.isEmpty()) return;
        int w = getWidth();
        int h = getHeight();
        float left = Ui.dp(getContext(), 42);
        float right = w - Ui.dp(getContext(), 4);
        float top = Ui.dp(getContext(), 24);
        float bottom = h - Ui.dp(getContext(), 26);
        float chartH = bottom - top;

        for (int i = 0; i <= 2; i++) {
            float y = top + chartH * i / 2f;
            canvas.drawLine(left, y, right, y, gridPaint);
        }
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(Ui.dp(getContext(), 11f));
        textPaint.setColor(Ui.INK3);
        canvas.drawText(Money.compact(max), left - Ui.dp(getContext(), 6),
                top + Ui.dp(getContext(), 3), textPaint);
        canvas.drawText(Money.compact(max / 2), left - Ui.dp(getContext(), 6),
                top + chartH / 2f + Ui.dp(getContext(), 3), textPaint);
        canvas.drawText("0", left - Ui.dp(getContext(), 6), bottom, textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);

        boolean allZero = true;
        for (StatsEngine.Bucket b : data) if (b.total > 0) allZero = false;
        if (allZero) {
            textPaint.setTextSize(Ui.dp(getContext(), 12));
            textPaint.setColor(Ui.INK3);
            canvas.drawText("这个周期还没有支出", w / 2f, h / 2f, textPaint);
            return;
        }

        float slot = (right - left) / data.size();
        float barW = Math.min(Ui.dp(getContext(), 26), slot * 0.44f);
        float maxBarH = chartH * progress;
        for (int i = 0; i < data.size(); i++) {
            StatsEngine.Bucket b = data.get(i);
            float cx = left + slot * i + slot / 2f;
            float bh = b.total <= 0 ? 0 : Math.max(Ui.dp(getContext(), 3),
                    (float) b.total / max * maxBarH);
            float barLeft = cx - barW / 2f;
            float barTop = bottom - bh;
            rect.set(barLeft, barTop, cx + barW / 2f, bottom);
            if (b.selected) {
                barPaint.setColor(Ui.ACCENT);
            } else {
                barPaint.setColor(0xFFDDE1DB);
            }
            canvas.drawRoundRect(rect, Ui.dp(getContext(), 3), Ui.dp(getContext(), 3), barPaint);

            textPaint.setColor(b.selected ? Ui.INK : Ui.INK3);
            textPaint.setTextSize(Ui.dp(getContext(), 11f));
            textPaint.setTypeface(android.graphics.Typeface.create(
                    b.selected ? "sans-serif-medium" : "sans-serif",
                    android.graphics.Typeface.NORMAL));
            canvas.drawText(b.label, cx, h - Ui.dp(getContext(), 8), textPaint);

            if (b.selected && b.total > 0) {
                canvas.drawText(Money.compact(b.total), cx, Math.max(top - 4,
                        barTop - Ui.dp(getContext(), 6)), valuePaint);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}

