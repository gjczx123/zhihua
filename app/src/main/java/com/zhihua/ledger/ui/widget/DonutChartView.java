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

/** 分类占比环形图。 */
public class DonutChartView extends View {
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final List<StatsEngine.CatStat> data = new ArrayList<>();
    private long total;
    private float progress = 1f;
    private ValueAnimator animator;

    public DonutChartView(Context context) {
        super(context);
        init();
    }

    public DonutChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DonutChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(Ui.dp(getContext(), 24));
        arcPaint.setStrokeCap(Paint.Cap.BUTT);
        centerPaint.setTextAlign(Paint.Align.CENTER);
        centerPaint.setColor(Ui.INK);
        centerPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        subPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setColor(Ui.INK3);
        subPaint.setTextSize(Ui.dp(getContext(), 11));
    }

    public void setData(long total, List<StatsEngine.CatStat> stats) {
        this.total = total;
        this.data.clear();
        if (stats != null) this.data.addAll(stats);
        if (animator != null) animator.cancel();
        progress = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(650);
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
        int w = squareSize(Ui.dp(getContext(), 160), widthMeasureSpec);
        setMeasuredDimension(w, w);
    }

    private int squareSize(int desired, int spec) {
        int mode = MeasureSpec.getMode(spec);
        int size = MeasureSpec.getSize(spec);
        if (mode == MeasureSpec.UNSPECIFIED) return desired;
        if (mode == MeasureSpec.AT_MOST) return Math.min(desired, size);
        return size;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float stroke = Ui.dp(getContext(), 24);
        float pad = stroke / 2f + Ui.dp(getContext(), 4);
        oval.set(pad, pad, w - pad, h - pad);

        arcPaint.setColor(0xFFEDF2F6);
        canvas.drawArc(oval, 0, 360, false, arcPaint);

        if (total > 0 && !data.isEmpty()) {
            float start = -90f;
            float full = 360f * progress;
            for (StatsEngine.CatStat s : data) {
                float sweep = (float) (s.total * 1.0 / total) * full;
                if (sweep <= 0.2f) continue;
                arcPaint.setColor(s.color);
                float gap = Math.min(2.2f, sweep * 0.15f);
                canvas.drawArc(oval, start + gap / 2f, Math.max(0.4f, sweep - gap), false, arcPaint);
                start += sweep;
            }
        }

        centerPaint.setTextSize(Ui.dp(getContext(), total >= 100000 ? 22 : 25));
        String amount = total > 0 ? Money.compact(total) : "—";
        canvas.drawText(amount, w / 2f, h / 2f + Ui.dp(getContext(), 3), centerPaint);
        subPaint.setTextSize(Ui.dp(getContext(), 11));
        canvas.drawText(total > 0 ? "总支出" : "暂无支出", w / 2f, h / 2f + Ui.dp(getContext(), 22), subPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
