package com.example.inspireface_example.liveness;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.inspireface_example.R;

/**
 * Draws face bounding brackets on top of the camera preview.
 *
 * Face rectangles are supplied in the upright image coordinate space (the space the
 * detector worked in, after rotation). The view maps them to screen space assuming the
 * preview uses FILL_CENTER (center-crop), and mirrors horizontally for the front camera.
 */
public class FaceOverlayView extends View {

    /** Immutable per-frame snapshot handed over from the analysis thread. */
    static final class Frame {
        final int imageWidth;
        final int imageHeight;
        final boolean mirrored;
        final RectF[] rects;
        final int color;

        Frame(int imageWidth, int imageHeight, boolean mirrored, RectF[] rects, int color) {
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.mirrored = mirrored;
            this.rects = rects;
            this.color = color;
        }
    }

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mapped = new RectF();
    private volatile Frame frame;

    public FaceOverlayView(Context context) {
        this(context, null);
    }

    public FaceOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(dp(3));
        boxPaint.setStrokeCap(Paint.Cap.ROUND);
        boxPaint.setColor(ContextCompat.getColor(context, R.color.liveness_accent));
    }

    /** Safe to call from any thread. Pass {@code null} rects to clear. */
    void submit(@Nullable Frame f) {
        frame = f;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Frame f = frame;
        if (f == null || f.rects == null || f.rects.length == 0
                || f.imageWidth <= 0 || f.imageHeight <= 0) {
            return;
        }
        float vw = getWidth();
        float vh = getHeight();
        // FILL_CENTER: uniform scale that covers the view, centered.
        float scale = Math.max(vw / f.imageWidth, vh / f.imageHeight);
        float dx = (vw - f.imageWidth * scale) / 2f;
        float dy = (vh - f.imageHeight * scale) / 2f;

        boxPaint.setColor(f.color);
        for (RectF r : f.rects) {
            float left = r.left;
            float right = r.right;
            if (f.mirrored) {
                float l = f.imageWidth - right;
                right = f.imageWidth - left;
                left = l;
            }
            mapped.set(left * scale + dx, r.top * scale + dy,
                    right * scale + dx, r.bottom * scale + dy);
            drawBrackets(canvas, mapped);
        }
    }

    /** Corner brackets read better over video than a full box. */
    private void drawBrackets(Canvas canvas, RectF r) {
        float len = Math.min(r.width(), r.height()) * 0.22f;
        float radius = dp(6);
        // Top-left
        canvas.drawLine(r.left, r.top + len, r.left, r.top + radius, boxPaint);
        canvas.drawLine(r.left + radius, r.top, r.left + len, r.top, boxPaint);
        canvas.drawArc(r.left, r.top, r.left + 2 * radius, r.top + 2 * radius, 180, 90, false, boxPaint);
        // Top-right
        canvas.drawLine(r.right - len, r.top, r.right - radius, r.top, boxPaint);
        canvas.drawLine(r.right, r.top + radius, r.right, r.top + len, boxPaint);
        canvas.drawArc(r.right - 2 * radius, r.top, r.right, r.top + 2 * radius, 270, 90, false, boxPaint);
        // Bottom-left
        canvas.drawLine(r.left, r.bottom - len, r.left, r.bottom - radius, boxPaint);
        canvas.drawLine(r.left + radius, r.bottom, r.left + len, r.bottom, boxPaint);
        canvas.drawArc(r.left, r.bottom - 2 * radius, r.left + 2 * radius, r.bottom, 90, 90, false, boxPaint);
        // Bottom-right
        canvas.drawLine(r.right - len, r.bottom, r.right - radius, r.bottom, boxPaint);
        canvas.drawLine(r.right, r.bottom - radius, r.right, r.bottom - len, boxPaint);
        canvas.drawArc(r.right - 2 * radius, r.bottom - 2 * radius, r.right, r.bottom, 0, 90, false, boxPaint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
