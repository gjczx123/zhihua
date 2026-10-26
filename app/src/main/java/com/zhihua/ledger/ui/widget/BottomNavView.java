package com.zhihua.ledger.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.zhihua.ledger.ui.Ui;

/** 自绘底部导航，避免引入 AndroidX。 */
public class BottomNavView extends View {
    public interface Listener {
        void onTabSelected(int index);
    }

    private static final String[] LABELS = {"首页", "分析", "账单", "设置"};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private int selected;
    private Listener listener;
    private int downIndex = -1;

    public BottomNavView(Context context) {
        super(context);
        init();
    }

    public BottomNavView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BottomNavView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClickable(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setSelected(int index) {
        if (index < 0 || index > 3) return;
        selected = index;
        invalidate();
    }

    public int getSelected() {
        return selected;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.BG);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setColor(Ui.LINE);
        canvas.drawRect(0, 0, w, Math.max(1, Ui.dp(getContext(), 0.7f)), paint);

        float itemW = w / 4f;
        for (int i = 0; i < 4; i++) {
            float cx = itemW * i + itemW / 2f;
            float iconY = h * 0.38f;
            boolean sel = i == selected;
            int color = sel ? Ui.INK : Ui.INK3;

            if (sel) {
                paint.setColor(0xFFE7EAE4);
                rect.set(cx - Ui.dp(getContext(), 24), iconY - Ui.dp(getContext(), 17),
                        cx + Ui.dp(getContext(), 24), iconY + Ui.dp(getContext(), 13));
                canvas.drawRoundRect(rect, Ui.dp(getContext(), 14), Ui.dp(getContext(), 14), paint);
            }

            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(getContext(), 1.6f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            path.reset();
            drawIcon(canvas, i, cx, iconY);
            paint.setStyle(Paint.Style.FILL);

            textPaint.setColor(color);
            textPaint.setTextSize(Ui.dp(getContext(), 11f));
            canvas.drawText(LABELS[i], cx, h - Ui.dp(getContext(), 8), textPaint);
        }
    }

    private void drawIcon(Canvas canvas, int index, float cx, float cy) {
        float s = Ui.dp(getContext(), 1f);
        if (index == 0) {
            path.moveTo(cx - 8 * s, cy - 1 * s);
            path.lineTo(cx, cy - 8 * s);
            path.lineTo(cx + 8 * s, cy - 1 * s);
            path.moveTo(cx - 6 * s, cy - 1 * s);
            path.lineTo(cx - 6 * s, cy + 8 * s);
            path.lineTo(cx + 6 * s, cy + 8 * s);
            path.lineTo(cx + 6 * s, cy - 1 * s);
            path.moveTo(cx - 2 * s, cy + 8 * s);
            path.lineTo(cx - 2 * s, cy + 3 * s);
            path.lineTo(cx + 2 * s, cy + 3 * s);
            path.lineTo(cx + 2 * s, cy + 8 * s);
            canvas.drawPath(path, paint);
        } else if (index == 1) {
            canvas.drawLine(cx - 7 * s, cy + 7 * s, cx - 7 * s, cy + 1 * s, paint);
            canvas.drawLine(cx, cy + 7 * s, cx, cy - 5 * s, paint);
            canvas.drawLine(cx + 7 * s, cy + 7 * s, cx + 7 * s, cy - 2 * s, paint);
        } else if (index == 2) {
            path.reset();
            path.addRoundRect(new RectF(cx - 8 * s, cy - 7 * s, cx + 8 * s, cy + 7 * s),
                    2 * s, 2 * s, Path.Direction.CW);
            canvas.drawPath(path, paint);
            canvas.drawLine(cx - 5 * s, cy - 3 * s, cx + 5 * s, cy - 3 * s, paint);
            canvas.drawLine(cx - 5 * s, cy, cx + 3 * s, cy, paint);
            canvas.drawLine(cx - 5 * s, cy + 3 * s, cx + 2 * s, cy + 3 * s, paint);
        } else {
            canvas.drawLine(cx - 8 * s, cy - 5 * s, cx + 8 * s, cy - 5 * s, paint);
            canvas.drawCircle(cx - 2 * s, cy - 5 * s, 2 * s, paint);
            canvas.drawLine(cx - 8 * s, cy + 1 * s, cx + 8 * s, cy + 1 * s, paint);
            canvas.drawCircle(cx + 4 * s, cy + 1 * s, 2 * s, paint);
            canvas.drawLine(cx - 8 * s, cy + 7 * s, cx + 8 * s, cy + 7 * s, paint);
            canvas.drawCircle(cx, cy + 7 * s, 2 * s, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float itemW = getWidth() / 4f;
        int idx = (int) (event.getX() / itemW);
        if (idx < 0) idx = 0;
        if (idx > 3) idx = 3;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downIndex = idx;
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (downIndex == idx) {
                if (listener != null) listener.onTabSelected(idx);
            }
            downIndex = -1;
            return true;
        }
        return super.onTouchEvent(event);
    }
}
