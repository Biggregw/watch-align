package com.watchalign.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Magnifier;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Alpha91 research prototype: user labels the OUTER BLACK DIAL edge at 12, 3, 6 and 9.
 * Each rough tap is snapped only inward/outward across the local dial boundary; its cardinal
 * identity and along-edge position remain user controlled.
 */
public class CardinalAnchorActivity extends Activity {
    private static final int BG=Color.rgb(8,17,31), ACCENT=Color.rgb(50,213,242), MUTED=Color.rgb(158,176,201);
    private static final String[] LABELS={"12","3","6","9"};
    private Bitmap watchBitmap;
    private final List<PointF> rawPoints=new ArrayList<>();
    private final List<PointF> snappedPoints=new ArrayList<>();
    private TextView promptText,statsText;
    private Button applyButton,resetButton,undoButton;
    private TouchOverlayView imageView;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        watchBitmap=InspectionImageStore.baseBitmap;
        if(watchBitmap==null){Toast.makeText(this,"No watch image available",Toast.LENGTH_SHORT).show();finish();return;}

        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);
        imageView=new TouchOverlayView(this);root.addView(imageView,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),dp(8),dp(10),dp(8));top.setBackgroundColor(0xDD08111F);
        Button back=button("Back");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(70),dp(46)));
        TextView title=text("Alpha91 · 4 Dial-Edge Anchors",16,Color.WHITE);title.setPadding(dp(10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        undoButton=button("Undo");undoButton.setOnClickListener(v->undo());top.addView(undoButton,new LinearLayout.LayoutParams(dp(76),dp(46)));
        resetButton=button("Reset");resetButton.setOnClickListener(v->reset());top.addView(resetButton,new LinearLayout.LayoutParams(dp(76),dp(46)));
        root.addView(top,new FrameLayout.LayoutParams(-1,dp(62),Gravity.TOP));

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(14),dp(7),dp(14),dp(10));bottom.setBackgroundColor(0xE008111F);
        promptText=text("",15,Color.WHITE);promptText.setGravity(Gravity.CENTER);bottom.addView(promptText,new LinearLayout.LayoutParams(-1,dp(31)));
        statsText=text("",12,MUTED);statsText.setGravity(Gravity.CENTER);bottom.addView(statsText,new LinearLayout.LayoutParams(-1,dp(46)));
        applyButton=button("Apply 4-point perspective & inspect");applyButton.setBackgroundColor(ACCENT);applyButton.setTextColor(Color.rgb(4,32,42));applyButton.setEnabled(false);applyButton.setOnClickListener(v->apply());bottom.addView(applyButton,new LinearLayout.LayoutParams(-1,dp(50)));
        root.addView(bottom,new FrameLayout.LayoutParams(-1,dp(145),Gravity.BOTTOM));
        setContentView(root);updateUi();
    }

    private void reset(){rawPoints.clear();snappedPoints.clear();imageView.clearPending();updateUi();imageView.invalidate();}
    private void undo(){if(!rawPoints.isEmpty())rawPoints.remove(rawPoints.size()-1);snappedPoints.clear();imageView.clearPending();updateUi();imageView.invalidate();}

    private void updateUi(){
        int n=rawPoints.size();undoButton.setEnabled(n>0);resetButton.setEnabled(n>0);applyButton.setEnabled(false);
        if(n<4){
            promptText.setText(String.format(Locale.US,"Step %d/4: set OUTER BLACK DIAL EDGE at %s",n+1,LABELS[n]));
            promptText.setTextColor(n==0?Color.rgb(255,90,90):n==1?Color.rgb(255,205,70):n==2?Color.rgb(100,180,255):Color.rgb(170,120,255));
            statsText.setText("Press near the black dial edge, drag under the magnifier, then release. Do not use the hour marker.");
            return;
        }
        snapAll();
        if(snappedPoints.size()==4){
            promptText.setText("All four anchors set");promptText.setTextColor(Color.rgb(127,225,170));
            double shift=0;for(int i=0;i<4;i++)shift+=distance(rawPoints.get(i),snappedPoints.get(i));shift/=4.0;
            statsText.setText(String.format(Locale.US,"Local edge snap complete · mean in/out correction %.1f px. Inspect the green crosshairs, then apply.",shift));
            applyButton.setEnabled(true);
        }else{
            promptText.setText("Could not snap all four points");promptText.setTextColor(Color.rgb(255,90,90));
            statsText.setText("Undo the last point or Reset and place each point closer to the black dial edge.");
        }
    }

    private void snapAll(){
        snappedPoints.clear();if(rawPoints.size()!=4)return;
        PointF c=new PointF(0,0);for(PointF p:rawPoints){c.x+=p.x;c.y+=p.y;}c.x/=4f;c.y/=4f;
        for(PointF p:rawPoints){PointF q=snapNormalToDialEdge(p,c);if(q==null){snappedPoints.clear();return;}snappedPoints.add(q);}
    }

    /**
     * Search only along the approximate outward normal. This is intentionally local: the user has
     * already told us WHICH dial-edge location this is, so reflections elsewhere cannot steal it.
     */
    private PointF snapNormalToDialEdge(PointF tap,PointF centre){
        double nx=tap.x-centre.x,ny=tap.y-centre.y,norm=Math.hypot(nx,ny);if(norm<20)return null;nx/=norm;ny/=norm;
        double tx=-ny,ty=nx;
        double bestScore=-1e9,bestOffset=0;
        final double search=22.0,step=0.5,probe=2.25;
        for(double off=-search;off<=search;off+=step){
            double contrast=0,absContrast=0;int count=0;
            for(int k=-4;k<=4;k+=2){
                double bx=tap.x+nx*off+tx*k,by=tap.y+ny*off+ty*k;
                double inside=luma(bx-nx*probe,by-ny*probe),outside=luma(bx+nx*probe,by+ny*probe);
                if(!Double.isFinite(inside)||!Double.isFinite(outside))continue;
                double d=outside-inside;contrast+=d;absContrast+=Math.abs(d);count++;
            }
            if(count<3)continue;
            // Black dial -> lighter rehaut should be positive. Keep an absolute fallback for unusual glare,
            // and mildly prefer the boundary nearest the user's indicated edge.
            double mean=contrast/count,meanAbs=absContrast/count;
            double edgeStrength=Math.max(mean,0.55*meanAbs);
            double score=edgeStrength-0.22*Math.abs(off);
            if(score>bestScore){bestScore=score;bestOffset=off;}
        }
        if(bestScore<4.0)return new PointF(tap.x,tap.y); // safe fallback: preserve the user's precise placement
        return new PointF((float)(tap.x+nx*bestOffset),(float)(tap.y+ny*bestOffset));
    }

    private double luma(double x,double y){
        if(x<1||y<1||x>=watchBitmap.getWidth()-2||y>=watchBitmap.getHeight()-2)return Double.NaN;
        int x0=(int)Math.floor(x),y0=(int)Math.floor(y),x1=x0+1,y1=y0+1;double fx=x-x0,fy=y-y0;
        double a=lum(watchBitmap.getPixel(x0,y0)),b=lum(watchBitmap.getPixel(x1,y0)),c=lum(watchBitmap.getPixel(x0,y1)),d=lum(watchBitmap.getPixel(x1,y1));
        return (1-fy)*((1-fx)*a+fx*b)+fy*((1-fx)*c+fx*d);
    }
    private static double lum(int c){return 0.2126*Color.red(c)+0.7152*Color.green(c)+0.0722*Color.blue(c);}
    private static double distance(PointF a,PointF b){return Math.hypot(a.x-b.x,a.y-b.y);}

    private void apply(){
        if(snappedPoints.size()!=4)return;
        Bitmap overlay=AssistedCardinalOverlay.build(watchBitmap,snappedPoints);
        if(overlay==null){Toast.makeText(this,"Those four anchors could not form a stable perspective transform",Toast.LENGTH_LONG).show();return;}
        InspectionImageStore.setOverlay(watchBitmap,overlay,"Alpha91 · 4-point outer-dial homography");
        setResult(RESULT_OK,new Intent().putExtra("alpha91_cardinal",true));finish();
    }

    private TextView text(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}

    private class TouchOverlayView extends View {
        private final Paint[] anchorPaint=new Paint[4];
        private final Paint snapPaint=new Paint(Paint.ANTI_ALIAS_FLAG),pendingPaint=new Paint(Paint.ANTI_ALIAS_FLAG),linePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Magnifier magnifier;private PointF pending;
        TouchOverlayView(Context c){super(c);int[] colors={Color.rgb(255,90,90),Color.rgb(255,205,70),Color.rgb(100,180,255),Color.rgb(170,120,255)};for(int i=0;i<4;i++){anchorPaint[i]=new Paint(Paint.ANTI_ALIAS_FLAG);anchorPaint[i].setColor(colors[i]);anchorPaint[i].setStyle(Paint.Style.STROKE);anchorPaint[i].setStrokeWidth(4f);}snapPaint.setColor(Color.rgb(80,255,145));snapPaint.setStyle(Paint.Style.STROKE);snapPaint.setStrokeWidth(4f);pendingPaint.setColor(Color.YELLOW);pendingPaint.setStyle(Paint.Style.STROKE);pendingPaint.setStrokeWidth(3f);linePaint.setColor(0x88FFFFFF);linePaint.setStrokeWidth(2f);magnifier=new Magnifier(this);}
        void clearPending(){pending=null;try{magnifier.dismiss();}catch(Throwable ignored){}}

        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(watchBitmap==null)return;Transform t=transform();if(t==null)return;canvas.drawBitmap(watchBitmap,null,new android.graphics.RectF(t.dx,t.dy,t.dx+watchBitmap.getWidth()*t.scale,t.dy+watchBitmap.getHeight()*t.scale),null);
            for(int i=0;i<rawPoints.size();i++)drawCross(canvas,rawPoints.get(i),anchorPaint[i],t,18f,LABELS[i]);
            if(snappedPoints.size()==4){for(int i=0;i<4;i++){PointF a=rawPoints.get(i),b=snappedPoints.get(i);canvas.drawLine(t.dx+a.x*t.scale,t.dy+a.y*t.scale,t.dx+b.x*t.scale,t.dy+b.y*t.scale,linePaint);drawCross(canvas,b,snapPaint,t,12f,LABELS[i]);}}
            if(pending!=null)drawCross(canvas,pending,pendingPaint,t,15f,"");
        }
        private void drawCross(Canvas c,PointF p,Paint paint,Transform t,float r,String label){float x=t.dx+p.x*t.scale,y=t.dy+p.y*t.scale;c.drawCircle(x,y,r,paint);c.drawLine(x-r*.7f,y,x+r*.7f,y,paint);c.drawLine(x,y-r*.7f,x,y+r*.7f,paint);if(!label.isEmpty()){Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setColor(paint.getColor());text.setTextSize(28f);text.setFakeBoldText(true);c.drawText(label,x+r+5,y-r-2,text);}}
        private Transform transform(){int vw=getWidth(),vh=getHeight(),bw=watchBitmap.getWidth(),bh=watchBitmap.getHeight();if(vw<=0||vh<=0||bw<=0||bh<=0)return null;float scale=Math.min((float)vw/bw,(float)vh/bh),dx=(vw-bw*scale)/2f,dy=(vh-bh*scale)/2f;return new Transform(scale,dx,dy);}
        private PointF imagePoint(float x,float y){Transform t=transform();if(t==null)return null;float ix=(x-t.dx)/t.scale,iy=(y-t.dy)/t.scale;if(ix<0||iy<0||ix>=watchBitmap.getWidth()||iy>=watchBitmap.getHeight())return null;return new PointF(ix,iy);}
        @Override public boolean onTouchEvent(MotionEvent e){if(rawPoints.size()>=4)return true;int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_MOVE){pending=imagePoint(e.getX(),e.getY());if(pending!=null){try{magnifier.show(e.getX(),e.getY());}catch(Throwable ignored){}invalidate();}return true;}if(a==MotionEvent.ACTION_UP){PointF p=imagePoint(e.getX(),e.getY());try{magnifier.dismiss();}catch(Throwable ignored){}pending=null;if(p!=null){rawPoints.add(p);updateUi();invalidate();performClick();}return true;}if(a==MotionEvent.ACTION_CANCEL){clearPending();invalidate();return true;}return super.onTouchEvent(e);}
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private static final class Transform{final float scale,dx,dy;Transform(float s,float x,float y){scale=s;dx=x;dy=y;}}
}
