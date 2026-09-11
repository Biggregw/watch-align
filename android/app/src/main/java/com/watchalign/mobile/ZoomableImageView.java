package com.watchalign.mobile;

import android.content.Context;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/** Lightweight pinch-zoom/pan ImageView for QC, reference and overlay inspection. */
final class ZoomableImageView extends ImageView {
    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private float scale = 1f;
    private float lastX, lastY;
    private boolean dragging;

    ZoomableImageView(Context context) { this(context, null); }
    ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float next = Math.max(1f, Math.min(6f, scale * factor));
                factor = next / scale;
                scale = next;
                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                setImageMatrix(matrix);
                getParent().requestDisallowInterceptTouchEvent(scale > 1.01f);
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) { resetZoom(); return true; }
        });
    }

    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);fitToView();}

    @Override public void setImageBitmap(android.graphics.Bitmap bm) {
        super.setImageBitmap(bm);
        post(this::fitToView);
    }

    private void fitToView() {
        if (getDrawable()==null || getWidth()==0 || getHeight()==0) return;
        float dw=getDrawable().getIntrinsicWidth(), dh=getDrawable().getIntrinsicHeight();
        if(dw<=0||dh<=0)return;
        float base=Math.min(getWidth()/dw,getHeight()/dh);
        float dx=(getWidth()-dw*base)/2f,dy=(getHeight()-dh*base)/2f;
        matrix.reset();matrix.postScale(base,base);matrix.postTranslate(dx,dy);scale=1f;setImageMatrix(matrix);
    }

    void resetZoom(){fitToView();}

    @Override public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX=event.getX();lastY=event.getY();dragging=true;
                getParent().requestDisallowInterceptTouchEvent(scale>1.01f);
                break;
            case MotionEvent.ACTION_MOVE:
                if(!scaleDetector.isInProgress()&&dragging&&scale>1.01f){
                    float dx=event.getX()-lastX,dy=event.getY()-lastY;
                    matrix.postTranslate(dx,dy);setImageMatrix(matrix);
                    lastX=event.getX();lastY=event.getY();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging=false;getParent().requestDisallowInterceptTouchEvent(false);break;
        }
        return true;
    }
}
