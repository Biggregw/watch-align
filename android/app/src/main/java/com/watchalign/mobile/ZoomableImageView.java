package com.watchalign.mobile;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewParent;
import android.widget.ImageView;

/** Robust pinch-zoom/pan ImageView for QC, reference and overlay inspection. */
final class ZoomableImageView extends ImageView {
    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private float userScale = 1f;
    private float baseScale = 1f;
    private float lastX, lastY;
    private boolean dragging;
    private boolean multiTouch;

    ZoomableImageView(Context context) { this(context, null); }

    ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        setClickable(true);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                disallowParentIntercept(true);
                return true;
            }

            @Override public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                if (!Float.isFinite(factor) || factor <= 0f) return false;
                float next = clamp(userScale * factor, 1f, 8f);
                factor = next / userScale;
                userScale = next;
                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                constrainTranslation();
                setImageMatrix(matrix);
                return true;
            }

            @Override public void onScaleEnd(ScaleGestureDetector detector) {
                constrainTranslation();
                setImageMatrix(matrix);
                disallowParentIntercept(userScale > 1.01f);
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onDoubleTap(MotionEvent e) {
                if (userScale > 1.15f) {
                    fitToView();
                } else {
                    float target = 2.5f;
                    float factor = target / userScale;
                    userScale = target;
                    matrix.postScale(factor, factor, e.getX(), e.getY());
                    constrainTranslation();
                    setImageMatrix(matrix);
                    disallowParentIntercept(true);
                }
                return true;
            }
        });
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fitToView();
    }

    @Override public void setImageBitmap(android.graphics.Bitmap bm) {
        super.setImageBitmap(bm);
        post(this::fitToView);
    }

    private void fitToView() {
        if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) return;
        float dw = getDrawable().getIntrinsicWidth();
        float dh = getDrawable().getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        baseScale = Math.min(getWidth() / dw, getHeight() / dh);
        float dx = (getWidth() - dw * baseScale) / 2f;
        float dy = (getHeight() - dh * baseScale) / 2f;
        matrix.reset();
        matrix.postScale(baseScale, baseScale);
        matrix.postTranslate(dx, dy);
        userScale = 1f;
        setImageMatrix(matrix);
        disallowParentIntercept(false);
    }

    void resetZoom() { fitToView(); }

    private void constrainTranslation() {
        if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) return;
        RectF r = new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
        matrix.mapRect(r);
        float dx = 0f, dy = 0f;

        if (r.width() <= getWidth()) dx = getWidth() / 2f - r.centerX();
        else if (r.left > 0) dx = -r.left;
        else if (r.right < getWidth()) dx = getWidth() - r.right;

        if (r.height() <= getHeight()) dy = getHeight() / 2f - r.centerY();
        else if (r.top > 0) dy = -r.top;
        else if (r.bottom < getHeight()) dy = getHeight() - r.bottom;

        if (dx != 0f || dy != 0f) matrix.postTranslate(dx, dy);
    }

    private void disallowParentIntercept(boolean disallow) {
        ViewParent p = getParent();
        while (p != null) {
            p.requestDisallowInterceptTouchEvent(disallow);
            p = p.getParent();
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            lastX = event.getX();
            lastY = event.getY();
            dragging = true;
            multiTouch = false;
            if (userScale > 1.01f) disallowParentIntercept(true);
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            multiTouch = true;
            dragging = false;
            disallowParentIntercept(true);
        }

        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);

        if (action == MotionEvent.ACTION_MOVE && !scaleDetector.isInProgress() && !multiTouch && dragging && userScale > 1.01f) {
            float dx = event.getX() - lastX;
            float dy = event.getY() - lastY;
            matrix.postTranslate(dx, dy);
            constrainTranslation();
            setImageMatrix(matrix);
            lastX = event.getX();
            lastY = event.getY();
            disallowParentIntercept(true);
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            if (event.getPointerCount() <= 2) {
                multiTouch = false;
                int remaining = event.getActionIndex() == 0 ? 1 : 0;
                if (remaining < event.getPointerCount()) {
                    lastX = event.getX(remaining);
                    lastY = event.getY(remaining);
                    dragging = userScale > 1.01f;
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            multiTouch = false;
            constrainTranslation();
            setImageMatrix(matrix);
            disallowParentIntercept(false);
            performClick();
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
