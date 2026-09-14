package com.watchalign.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/** Finger-safe precision loupe shown while dragging alignment handles. */
final class PrecisionLoupeView extends View {
    private Bitmap bitmap;
    private PointF focus;
    private boolean visible;
    private final Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cross=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow=new Paint(Paint.ANTI_ALIAS_FLAG);

    PrecisionLoupeView(Context context){
        super(context);
        border.setStyle(Paint.Style.STROKE);border.setStrokeWidth(dp(4));border.setColor(Color.WHITE);
        cross.setStyle(Paint.Style.STROKE);cross.setStrokeWidth(dp(2));cross.setColor(Color.rgb(255,235,0));
        shadow.setStyle(Paint.Style.STROKE);shadow.setStrokeWidth(dp(9));shadow.setColor(Color.BLACK);shadow.setAlpha(210);
        setVisibility(INVISIBLE);
    }

    void show(Bitmap source, PointF bitmapFocus){bitmap=source;focus=new PointF(bitmapFocus.x,bitmapFocus.y);visible=true;setVisibility(VISIBLE);invalidate();}
    void move(PointF bitmapFocus){if(!visible)return;focus.set(bitmapFocus.x,bitmapFocus.y);invalidate();}
    void hide(){visible=false;setVisibility(INVISIBLE);}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(!visible||bitmap==null||focus==null)return;
        float w=getWidth(),h=getHeight(),cx=w/2f,cy=h/2f,r=Math.min(w,h)*0.43f;
        float srcRadius=Math.max(24f,Math.min(bitmap.getWidth(),bitmap.getHeight())*0.038f);
        int left=Math.max(0,Math.round(focus.x-srcRadius));int top=Math.max(0,Math.round(focus.y-srcRadius));
        int right=Math.min(bitmap.getWidth(),Math.round(focus.x+srcRadius));int bottom=Math.min(bitmap.getHeight(),Math.round(focus.y+srcRadius));
        Rect src=new Rect(left,top,right,bottom);RectF dst=new RectF(cx-r,cy-r,cx+r,cy+r);
        Path clip=new Path();clip.addCircle(cx,cy,r,Path.Direction.CW);c.save();c.clipPath(clip);c.drawBitmap(bitmap,src,dst,null);c.restore();
        c.drawCircle(cx,cy,r,shadow);c.drawCircle(cx,cy,r,border);
        float arm=r*0.26f;c.drawLine(cx-arm,cy,cx+arm,cy,cross);c.drawLine(cx,cy-arm,cx,cy+arm,cross);c.drawCircle(cx,cy,dp(3),cross);
    }

    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
}
