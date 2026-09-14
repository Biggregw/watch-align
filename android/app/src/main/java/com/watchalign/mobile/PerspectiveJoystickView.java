package com.watchalign.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

final class PerspectiveJoystickView extends View {
    interface Listener { void onTilt(float yawDeg,float pitchDeg); }
    private final Paint ring=new Paint(Paint.ANTI_ALIAS_FLAG),knob=new Paint(Paint.ANTI_ALIAS_FLAG),cross=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Listener listener; private float nx=0f,ny=0f;
    PerspectiveJoystickView(Context c){super(c);ring.setStyle(Paint.Style.STROKE);ring.setStrokeWidth(3f);ring.setColor(Color.LTGRAY);knob.setColor(Color.rgb(255,55,55));cross.setColor(0x66FFFFFF);cross.setStrokeWidth(2f);setBackgroundColor(0x22000000);}
    void setListener(Listener l){listener=l;}
    void setTilt(float yaw,float pitch){nx=Math.max(-1,Math.min(1,yaw/40f));ny=Math.max(-1,Math.min(1,pitch/40f));invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*0.42f;c.drawCircle(cx,cy,r,ring);c.drawLine(cx-r,cy,cx+r,cy,cross);c.drawLine(cx,cy-r,cx,cy+r,cross);c.drawCircle(cx+nx*r,cy+ny*r,Math.max(14f,r*0.18f),knob);}
    @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()!=MotionEvent.ACTION_DOWN&&e.getActionMasked()!=MotionEvent.ACTION_MOVE)return true;float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.max(1f,Math.min(getWidth(),getHeight())*0.42f);float x=(e.getX()-cx)/r,y=(e.getY()-cy)/r;float len=(float)Math.hypot(x,y);if(len>1f){x/=len;y/=len;}nx=x;ny=y;invalidate();if(listener!=null)listener.onTilt(nx*40f,ny*40f);return true;}
}
