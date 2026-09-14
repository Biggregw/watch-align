package com.watchalign.mobile;

import android.content.Context;import android.graphics.Canvas;import android.graphics.Color;import android.graphics.Paint;import android.view.View;

/** Ghost dial and centre target drawn over the live preview. */
final class CaptureGuideView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private boolean ready;
    CaptureGuideView(Context c){super(c);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5f);}
    void setReady(boolean value){ready=value;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float r=Math.min(getWidth(),getHeight())*.36f,cx=getWidth()/2f,cy=getHeight()/2f;paint.setColor(ready?Color.rgb(60,255,120):Color.WHITE);paint.setAlpha(210);c.drawCircle(cx,cy,r,paint);c.drawLine(cx-24,cy,cx+24,cy,paint);c.drawLine(cx,cy-24,cx,cy+24,paint);c.drawLine(cx,cy-r-15,cx,cy-r+22,paint);}
}
