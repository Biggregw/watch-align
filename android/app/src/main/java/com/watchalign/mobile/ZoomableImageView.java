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

/**
 * Stable matrix zoom view. Alpha24 deliberately avoids manual pointer-index bookkeeping:
 * ScaleGestureDetector owns pinch state and GestureDetector owns pan/double-tap.
 */
final class ZoomableImageView extends ImageView {
    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private float userScale = 1f;

    ZoomableImageView(Context context) { this(context, null); }

    ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        setClickable(true);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                setParentIntercept(false);
                return true;
            }

            @Override public boolean onScale(ScaleGestureDetector detector) {
                float sf = detector.getScaleFactor();
                if (!Float.isFinite(sf) || sf <= 0f) return false;
                float next = Math.max(1f, Math.min(8f, userScale * sf));
                float applied = next / userScale;
                userScale = next;
                matrix.postScale(applied, applied, detector.getFocusX(), detector.getFocusY());
                clampToBounds();
                setImageMatrix(matrix);
                return true;
            }

            @Override public void onScaleEnd(ScaleGestureDetector detector) {
                clampToBounds();
                setImageMatrix(matrix);
                setParentIntercept(userScale <= 1.01f);
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (userScale <= 1.01f || scaleDetector.isInProgress()) return false;
                setParentIntercept(false);
                matrix.postTranslate(-distanceX, -distanceY);
                clampToBounds();
                setImageMatrix(matrix);
                return true;
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                if (userScale > 1.15f) {
                    fitToView();
                } else {
                    float target = 2.5f;
                    float applied = target / userScale;
                    userScale = target;
                    matrix.postScale(applied, applied, e.getX(), e.getY());
                    clampToBounds();
                    setImageMatrix(matrix);
                    setParentIntercept(false);
                }
                return true;
            }
        });
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        post(this::fitToView);
    }

    @Override public void setImageBitmap(android.graphics.Bitmap bm) {
        super.setImageBitmap(bm);
        post(this::fitToView);
    }

    private void fitToView() {
        if (getDrawable() == null || getWidth() <= 0 || getHeight() <= 0) return;
        float dw = getDrawable().getIntrinsicWidth();
        float dh = getDrawable().getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        float base = Math.min(getWidth() / dw, getHeight() / dh);
        float dx = (getWidth() - dw * base) / 2f;
        float dy = (getHeight() - dh * base) / 2f;
        matrix.reset();
        matrix.postScale(base, base);
        matrix.postTranslate(dx, dy);
        userScale = 1f;
        setImageMatrix(matrix);
        setParentIntercept(true);
    }

    void resetZoom() { fitToView(); }

    private void clampToBounds() {
        if (getDrawable() == null || getWidth() <= 0 || getHeight() <= 0) return;
        RectF r = new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
        matrix.mapRect(r);
        float dx = 0f, dy = 0f;
        if (r.width() <= getWidth()) dx = getWidth() * 0.5f - r.centerX();
        else if (r.left > 0f) dx = -r.left;
        else if (r.right < getWidth()) dx = getWidth() - r.right;
        if (r.height() <= getHeight()) dy = getHeight() * 0.5f - r.centerY();
        else if (r.top > 0f) dy = -r.top;
        else if (r.bottom < getHeight()) dy = getHeight() - r.bottom;
        if (dx != 0f || dy != 0f) matrix.postTranslate(dx, dy);
    }

    /** allowParent=true means the surrounding ScrollView may intercept single-finger scrolling. */
    private void setParentIntercept(boolean allowParent) {
        ViewParent p = getParent();
        if (p != null) p.requestDisallowInterceptTouchEvent(!allowParent);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        try {
            if (event.getPointerCount() >= 2 || userScale > 1.01f) setParentIntercept(false);
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            int a = event.getActionMasked();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                clampToBounds();
                setImageMatrix(matrix);
                setParentIntercept(userScale <= 1.01f);
                performClick();
            }
            return true;
        } catch (RuntimeException ex) {
            // Gesture streams can be interrupted by Android parent views. Reset safely instead of crashing.
            fitToView();
            return true;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
