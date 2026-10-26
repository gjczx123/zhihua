package com.zhihua.ledger.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.zhihua.ledger.ui.Ui;

/** 预算进度条，超支变红。 */
public class BudgetBarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float ratio;
    private float animated;
    private boolean over;
    private int trackColor = 0xFFE7EAE4;

    public void setTrackColor(int color) {
        trackColor = color;
        invalidate();
    }

    public BudgetBarView(Context context) {
        super(context);
        init();
    }

    public BudgetBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BudgetBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setProgress(long spent, long budget) {
        over = budget > 0 && spent > budget;
        ratio = budget <= 0 ? 0 : Math.min(1f, (float) spent / budget);
        ValueAnimator a = ValueAnimator.ofFloat(0f, ratio);
        a.setDuration(420);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                animated = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        a.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float r = h / 2f;
        paint.setShader(null);
        paint.setColor(trackColor);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, paint);

        float fill = Math.max(0, Math.min(w, w * animated));
        if (fill <= 0) return;
        paint.setColor(over ? Ui.RED : Ui.ACCENT);
        rect.set(0, 0, fill, h);
        canvas.drawRoundRect(rect, r, r, paint);
    }
}
