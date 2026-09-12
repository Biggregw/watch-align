package com.watchalign.mobile;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Alpha41: precision radial/lateral measurement for the 12 o'clock triangle. */
public class Triangle12InspectActivity extends Activity {
    private Bitmap base;
    private PerspectiveMasterRenderer.Pose pose;
    private TriangleView triangleView;
    private TextView result;
    private Button stepButton;
    private boolean fine=true;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(8,17,31));getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        base=InspectionImageStore.baseBitmap;pose=InspectionImageStore.alignedPose==null?null:InspectionImageStore.alignedPose.copy();
        if(base==null||pose==null){finish();return;}

        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        triangleView=new TriangleView();root.addView(triangleView,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(6),dp(8),dp(6));top.setBackgroundColor(0xE008111F);
        Button back=btn("Back");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(72),dp(46)));
        TextView title=txt("12 triangle precision · α41",18);title.setPadding(dp(10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        Button reset=btn("Reset");reset.setOnClickListener(v->{triangleView.resetActual();updateResult();});top.addView(reset,new LinearLayout.LayoutParams(dp(76),dp(46)));root.addView(top,new FrameLayout.LayoutParams(-1,dp(60),Gravity.TOP));

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(10),dp(5),dp(10),dp(7));bottom.setBackgroundColor(0xEE08111F);
        TextView hint=txt("Tap the visual centre of the 12 triangle, then fine-nudge the GREEN cross onto it.",12);hint.setGravity(Gravity.CENTER);bottom.addView(hint,new LinearLayout.LayoutParams(-1,dp(34)));
        result=txt("",14);result.setGravity(Gravity.CENTER);bottom.addView(result,new LinearLayout.LayoutParams(-1,dp(42)));

        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);
        stepButton=btn("Fine 0.25 px");stepButton.setOnClickListener(v->{fine=!fine;stepButton.setText(fine?"Fine 0.25 px":"Coarse 1 px");});controls.addView(stepButton,new LinearLayout.LayoutParams(dp(118),dp(44)));
        Button left=btn("◀"),up=btn("▲"),down=btn("▼"),right=btn("▶");setupRepeat(left,-1,0);setupRepeat(up,0,-1);setupRepeat(down,0,1);setupRepeat(right,1,0);
        controls.addView(left,new LinearLayout.LayoutParams(0,dp(44),1));controls.addView(up,new LinearLayout.LayoutParams(0,dp(44),1));controls.addView(down,new LinearLayout.LayoutParams(0,dp(44),1));controls.addView(right,new LinearLayout.LayoutParams(0,dp(44),1));bottom.addView(controls);
        TextView caveat=txt("HIGH means outward toward the rehaut. Measurement is relative to the QC ruler target at 0.79 dial radius, not a Rolex factory tolerance.",10);caveat.setGravity(Gravity.CENTER);bottom.addView(caveat,new LinearLayout.LayoutParams(-1,dp(44)));
        root.addView(bottom,new FrameLayout.LayoutParams(-1,dp(174),Gravity.BOTTOM));
        setContentView(root);updateResult();
    }

    private void setupRepeat(Button b,float dx,float dy){
        Handler h=new Handler(Looper.getMainLooper());
        Runnable r=new Runnable(){@Override public void run(){nudge(dx,dy);h.postDelayed(this,80);}};
        b.setOnTouchListener((v,e)->{int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN){nudge(dx,dy);h.postDelayed(r,330);return true;}if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){h.removeCallbacks(r);return true;}return true;});
    }

    private void nudge(float dx,float dy){float s=fine?0.25f:1f;triangleView.actualX+=dx*s;triangleView.actualY+=dy*s;triangleView.invalidate();updateResult();}

    private void updateResult(){
        if(result==null||triangleView==null)return;
        Triangle12Metric.Result m=triangleView.metric();
        String radial=Math.abs(m.radialPx)<0.05f?"ON TARGET":(m.radialPx>0?String.format("HIGH +%.2f px",m.radialPx):String.format("LOW %.2f px",m.radialPx));
        String lateral=Math.abs(m.lateralPx)<0.05f?"centred":(m.lateralPx>0?String.format("%.2f px right",m.lateralPx):String.format("%.2f px left",-m.lateralPx));
        result.setText(radial+String.format(" · %+.3f%% dial radius · ",m.radialPct)+lateral);
    }

    private final class TriangleView extends View {
        final PointF expected,p77,p81,left79,right79,centre,p12,p3,p6,p9;
        final float dialR,cropLeft,cropTop,cropRight,cropBottom;
        float actualX,actualY;
        float drawScale,drawOx,drawOy;
        final Paint axis=new Paint(Paint.ANTI_ALIAS_FLAG),target=new Paint(Paint.ANTI_ALIAS_FLAG),actualPaint=new Paint(Paint.ANTI_ALIAS_FLAG),dim=new Paint(Paint.ANTI_ALIAS_FLAG),label=new Paint(Paint.ANTI_ALIAS_FLAG);

        TriangleView(){
            super(Triangle12InspectActivity.this);setBackgroundColor(Color.BLACK);
            expected=PerspectiveMasterRenderer.projectPoint(pose,0,-.79);p77=PerspectiveMasterRenderer.projectPoint(pose,0,-.77);p81=PerspectiveMasterRenderer.projectPoint(pose,0,-.81);
            left79=PerspectiveMasterRenderer.projectPoint(pose,-.07,-.79);right79=PerspectiveMasterRenderer.projectPoint(pose,.07,-.79);
            centre=PerspectiveMasterRenderer.projectPoint(pose,0,0);p12=PerspectiveMasterRenderer.projectPoint(pose,0,-1);p3=PerspectiveMasterRenderer.projectPoint(pose,1,0);p6=PerspectiveMasterRenderer.projectPoint(pose,0,1);p9=PerspectiveMasterRenderer.projectPoint(pose,-1,0);
            dialR=(dist(centre,p12)+dist(centre,p3)+dist(centre,p6)+dist(centre,p9))/4f;
            float half=Math.max(45f,dialR*.27f);cropLeft=Math.max(0,expected.x-half);cropRight=Math.min(base.getWidth(),expected.x+half);cropTop=Math.max(0,expected.y-half);cropBottom=Math.min(base.getHeight(),expected.y+half);
            actualX=expected.x;actualY=expected.y;
            axis.setColor(Color.rgb(255,35,35));axis.setStrokeWidth(dp(2));axis.setStyle(Paint.Style.STROKE);
            target.setColor(Color.CYAN);target.setStrokeWidth(dp(2));target.setStyle(Paint.Style.STROKE);
            actualPaint.setColor(Color.rgb(60,255,120));actualPaint.setStrokeWidth(dp(2));actualPaint.setStyle(Paint.Style.STROKE);
            dim.setColor(Color.WHITE);dim.setAlpha(150);dim.setStrokeWidth(dp(1));dim.setStyle(Paint.Style.STROKE);
            label.setColor(Color.WHITE);label.setTextSize(dp(11));label.setFakeBoldText(true);
            setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_MOVE){PointF q=screenToImage(e.getX(),e.getY());if(q!=null){actualX=q.x;actualY=q.y;invalidate();updateResult();}return true;}return true;});
        }

        void resetActual(){actualX=expected.x;actualY=expected.y;invalidate();}
        Triangle12Metric.Result metric(){
            float ox=p81.x-p77.x,oy=p81.y-p77.y,rx=right79.x-left79.x,ry=right79.y-left79.y;
            return Triangle12Metric.measure(actualX,actualY,expected.x,expected.y,ox,oy,rx,ry,dialR);
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=cropRight-cropLeft,h=cropBottom-cropTop;if(w<=1||h<=1)return;
            float availTop=dp(66),availBottom=getHeight()-dp(180);float availH=Math.max(1,availBottom-availTop);drawScale=Math.min(getWidth()/w,availH/h);float dw=w*drawScale,dh=h*drawScale;drawOx=(getWidth()-dw)/2f;drawOy=availTop+(availH-dh)/2f;
            Rect src=new Rect(Math.round(cropLeft),Math.round(cropTop),Math.round(cropRight),Math.round(cropBottom));RectF dst=new RectF(drawOx,drawOy,drawOx+dw,drawOy+dh);Paint photo=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);c.drawBitmap(base,src,dst,photo);
            PointF a=s(PerspectiveMasterRenderer.projectPoint(pose,0,-.60)),b=s(PerspectiveMasterRenderer.projectPoint(pose,0,-1.0));c.drawLine(a.x,a.y,b.x,b.y,axis);
            PointF tl=s(left79),tr=s(right79);c.drawLine(tl.x,tl.y,tr.x,tr.y,target);
            drawReferenceTick(c,.78);drawReferenceTick(c,.80);
            PointF ep=s(expected);c.drawCircle(ep.x,ep.y,dp(7),target);c.drawText("TARGET",ep.x+dp(10),ep.y-dp(8),label);
            PointF ap=s(new PointF(actualX,actualY));float r=dp(9);c.drawCircle(ap.x,ap.y,r,actualPaint);c.drawLine(ap.x-r-dp(5),ap.y,ap.x+r+dp(5),ap.y,actualPaint);c.drawLine(ap.x,ap.y-r-dp(5),ap.x,ap.y+r+dp(5),actualPaint);c.drawText("ACTUAL",ap.x+dp(12),ap.y+dp(18),label);
        }

        private void drawReferenceTick(Canvas c,double rr){PointF l=s(PerspectiveMasterRenderer.projectPoint(pose,-.045,-rr)),r=s(PerspectiveMasterRenderer.projectPoint(pose,.045,-rr));c.drawLine(l.x,l.y,r.x,r.y,dim);}
        private PointF s(PointF q){return new PointF(drawOx+(q.x-cropLeft)*drawScale,drawOy+(q.y-cropTop)*drawScale);}
        private PointF screenToImage(float sx,float sy){if(drawScale<=0)return null;float x=cropLeft+(sx-drawOx)/drawScale,y=cropTop+(sy-drawOy)/drawScale;if(x<cropLeft||x>cropRight||y<cropTop||y>cropBottom)return null;return new PointF(x,y);}
        private float dist(PointF a,PointF b){float dx=a.x-b.x,dy=a.y-b.y;return (float)Math.hypot(dx,dy);}
    }

    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);return b;}
    private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
