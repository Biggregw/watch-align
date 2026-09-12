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

/** Alpha48: auto-seeded 12 marker relation check with editable five-point measurement. */
public class Triangle12InspectActivity extends Activity {
    private Bitmap base; private PerspectiveMasterRenderer.Pose pose; private MeasureView measureView;
    private TextView result; private Button stepButton; private boolean fine=true;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(8,17,31));getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        base=InspectionImageStore.baseBitmap;pose=InspectionImageStore.alignedPose==null?null:InspectionImageStore.alignedPose.copy();if(base==null||pose==null){finish();return;}
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);measureView=new MeasureView();root.addView(measureView,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(6),dp(8),dp(6));top.setBackgroundColor(0xE008111F);
        Button back=btn("Back");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(72),dp(46)));
        TextView title=txt("12 relation auto-seed · α48",18);title.setPadding(dp(10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        Button reset=btn("Auto seed");reset.setOnClickListener(v->{measureView.autoSeed();updateResult();});top.addView(reset,new LinearLayout.LayoutParams(dp(88),dp(46)));root.addView(top,new FrameLayout.LayoutParams(-1,dp(60),Gravity.TOP));

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(8),dp(3),dp(8),dp(5));bottom.setBackgroundColor(0xEE08111F);
        TextView hint=txt("Five points are pre-positioned from the aligned gen geometry. Check and adjust: 1 LEFT BASE, 2 RIGHT BASE, 3 APEX, 4 INNER TIP OF 60, 5 CROWN TOP.",10);hint.setGravity(Gravity.CENTER);bottom.addView(hint,new LinearLayout.LayoutParams(-1,dp(40)));
        result=txt("",11);result.setGravity(Gravity.CENTER);bottom.addView(result,new LinearLayout.LayoutParams(-1,dp(72)));

        LinearLayout row1=new LinearLayout(this);row1.setGravity(Gravity.CENTER);
        String[] names={"1 Left","2 Right","3 Apex"};for(int i=0;i<3;i++){final int k=i;Button b=btn(names[i]);b.setOnClickListener(v->measureView.select(k));row1.addView(b,new LinearLayout.LayoutParams(0,dp(38),1));}bottom.addView(row1);
        LinearLayout row2=new LinearLayout(this);row2.setGravity(Gravity.CENTER);
        Button p4=btn("4 Minute 60");p4.setOnClickListener(v->measureView.select(3));Button p5=btn("5 Crown top");p5.setOnClickListener(v->measureView.select(4));row2.addView(p4,new LinearLayout.LayoutParams(0,dp(38),1));row2.addView(p5,new LinearLayout.LayoutParams(0,dp(38),1));bottom.addView(row2);

        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);stepButton=btn("Fine 0.25 px");stepButton.setOnClickListener(v->{fine=!fine;stepButton.setText(fine?"Fine 0.25 px":"Coarse 1 px");});controls.addView(stepButton,new LinearLayout.LayoutParams(dp(118),dp(42)));
        Button left=btn("◀"),up=btn("▲"),down=btn("▼"),right=btn("▶");setupRepeat(left,-1,0);setupRepeat(up,0,-1);setupRepeat(down,0,1);setupRepeat(right,1,0);controls.addView(left,new LinearLayout.LayoutParams(0,dp(42),1));controls.addView(up,new LinearLayout.LayoutParams(0,dp(42),1));controls.addView(down,new LinearLayout.LayoutParams(0,dp(42),1));controls.addView(right,new LinearLayout.LayoutParams(0,dp(42),1));bottom.addView(controls);
        TextView caveat=txt("Auto seed is only a starting suggestion. Final QC uses your corrected points against the supplied front-on genuine reference, not Rolex factory tolerances.",9);caveat.setGravity(Gravity.CENTER);bottom.addView(caveat,new LinearLayout.LayoutParams(-1,dp(48)));
        root.addView(bottom,new FrameLayout.LayoutParams(-1,dp(284),Gravity.BOTTOM));setContentView(root);measureView.autoSeed();updateResult();
    }

    private void setupRepeat(Button b,float dx,float dy){Handler h=new Handler(Looper.getMainLooper());Runnable r=new Runnable(){@Override public void run(){nudge(dx,dy);h.postDelayed(this,80);}};b.setOnTouchListener((v,e)->{int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN){nudge(dx,dy);h.postDelayed(r,330);return true;}if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){h.removeCallbacks(r);return true;}return true;});}
    private void nudge(float dx,float dy){if(!measureView.hasSelectedPoint())return;float s=fine?.25f:1f;measureView.nudge(dx*s,dy*s);updateResult();}

    private void updateResult(){
        if(result==null||measureView==null)return;if(!measureView.complete()){result.setText("Set all 5 points. Selected: "+measureView.selectedName());return;}
        Triangle12RelationalMetric.Result m=measureView.metric();
        float bg=(m.baseGapRatio-GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE)*100f;
        float ag=(m.apexGapRatio-GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE)*100f;
        String baseText=Math.abs(bg)<0.5f?"base-to-60 gap ≈ gen":bg>0?String.format("base-to-60 gap %.1f%% BW LARGER",bg):String.format("base-to-60 gap %.1f%% BW SMALLER",-bg);
        String crownText=Math.abs(ag)<0.5f?"apex-to-crown gap ≈ gen":ag>0?String.format("apex-to-crown gap %.1f%% BW LARGER",ag):String.format("apex-to-crown gap %.1f%% BW SMALLER",-ag);
        String radial;if(bg < -0.5f && ag > 0.5f) radial="POSITION: OUTWARD toward minute track vs gen";else if(bg > 0.5f && ag < -0.5f) radial="POSITION: INWARD toward crown vs gen";else if(Math.abs(bg)<0.5f && Math.abs(ag)<0.5f) radial="POSITION: essentially matches gen reference";else radial="POSITION: mixed local relationship, inspect taps/shape";
        String rot=Math.abs(m.rotationDeg)<.03f?"rotation 0.00°":String.format("rotation %.2f° %s",Math.abs(m.rotationDeg),m.rotationDeg>0?"clockwise":"counter-clockwise");
        result.setText(radial+"\n"+baseText+" · "+crownText+"\n"+rot+String.format(" · lateral %+.2f px",m.lateralPx));
    }

    private final class MeasureView extends View {
        final PointF[] actual=new PointF[5];final boolean[] set={false,false,false,false,false};int selected=0;
        final PointF centre,p12,p3,p6,p9;final float dialR,cropLeft,cropTop,cropRight,cropBottom;float drawScale,drawOx,drawOy;
        final Paint axis=stroke(Color.rgb(255,35,35),2),actualPaint=stroke(Color.rgb(60,255,120),2),minute=stroke(Color.rgb(255,220,40),2),target=stroke(Color.CYAN,2),label=fill(Color.WHITE);
        MeasureView(){super(Triangle12InspectActivity.this);setBackgroundColor(Color.BLACK);
            centre=PerspectiveMasterRenderer.projectPoint(pose,0,0);p12=PerspectiveMasterRenderer.projectPoint(pose,0,-1);p3=PerspectiveMasterRenderer.projectPoint(pose,1,0);p6=PerspectiveMasterRenderer.projectPoint(pose,0,1);p9=PerspectiveMasterRenderer.projectPoint(pose,-1,0);dialR=(dist(centre,p12)+dist(centre,p3)+dist(centre,p6)+dist(centre,p9))/4f;
            float half=Math.max(64f,dialR*.43f);cropLeft=Math.max(0,p12.x-half);cropRight=Math.min(base.getWidth(),p12.x+half);cropTop=Math.max(0,p12.y-half*.45f);cropBottom=Math.min(base.getHeight(),p12.y+half*1.30f);label.setTextSize(dp(9));label.setFakeBoldText(true);
            setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){PointF q=screenToImage(e.getX(),e.getY());if(q!=null){int near=nearestSet(q);if(near>=0&&dist(q,actual[near])<screenPxToImage(dp(32))){selected=near;}else{actual[selected]=q;set[selected]=true;if(selected<4)selected++;}invalidate();updateResult();}return true;}return true;});
        }
        void autoSeed(){
            actual[0]=PerspectiveMasterRenderer.projectPoint(pose,Gmt126710BlnrTriangleReference.LEFT_X,Gmt126710BlnrTriangleReference.LEFT_Y);
            actual[1]=PerspectiveMasterRenderer.projectPoint(pose,Gmt126710BlnrTriangleReference.RIGHT_X,Gmt126710BlnrTriangleReference.RIGHT_Y);
            actual[2]=PerspectiveMasterRenderer.projectPoint(pose,Gmt126710BlnrTriangleReference.APEX_X,Gmt126710BlnrTriangleReference.APEX_Y);
            actual[3]=PerspectiveMasterRenderer.projectPoint(pose,0,-Gmt126710BlnrTriangleReference.MINUTE_INNER_R);
            float crownY=Gmt126710BlnrTriangleReference.APEX_Y + Gmt126710BlnrTriangleReference.WIDTH_R*GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE;
            actual[4]=PerspectiveMasterRenderer.projectPoint(pose,0,crownY);
            for(int i=0;i<5;i++)set[i]=true;selected=0;invalidate();
        }
        boolean complete(){for(boolean b:set)if(!b)return false;return true;}boolean hasSelectedPoint(){return set[selected];}
        String selectedName(){return selected==0?"1 Left base":selected==1?"2 Right base":selected==2?"3 Apex":selected==3?"4 Minute 60 inner tip":"5 Crown top";}
        void select(int i){selected=i;invalidate();updateResult();}void resetActual(){for(int i=0;i<5;i++){set[i]=false;actual[i]=null;}selected=0;invalidate();}
        void nudge(float dx,float dy){if(!set[selected])return;actual[selected].x+=dx;actual[selected].y+=dy;invalidate();}
        Triangle12RelationalMetric.Result metric(){return Triangle12RelationalMetric.measure(actual[0],actual[1],actual[2],actual[3],actual[4],centre,p12);}

        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=cropRight-cropLeft,h=cropBottom-cropTop;if(w<=1||h<=1)return;float availTop=dp(62),availBottom=getHeight()-dp(288),availH=Math.max(1,availBottom-availTop);drawScale=Math.min(getWidth()/w,availH/h);float dw=w*drawScale,dh=h*drawScale;drawOx=(getWidth()-dw)/2f;drawOy=availTop+(availH-dh)/2f;Rect src=new Rect(Math.round(cropLeft),Math.round(cropTop),Math.round(cropRight),Math.round(cropBottom));RectF dst=new RectF(drawOx,drawOy,drawOx+dw,drawOy+dh);c.drawBitmap(base,src,dst,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
            drawMinuteAxis(c,-1,"59");drawMinuteAxis(c,0,"60");drawMinuteAxis(c,1,"01");PointF aa=s(PerspectiveMasterRenderer.projectPoint(pose,0,-.52)),bb=s(p12);c.drawLine(aa.x,aa.y,bb.x,bb.y,axis);
            for(int i=0;i<5;i++)if(set[i]){PointF q=s(actual[i]);float r=dp(i==selected?8:5);c.drawCircle(q.x,q.y,r,actualPaint);c.drawText(String.valueOf(i+1),q.x+dp(8),q.y-dp(7),label);}
            if(set[0]&&set[1]){PointF l=s(actual[0]),r=s(actual[1]);c.drawLine(l.x,l.y,r.x,r.y,actualPaint);}if(set[0]&&set[1]&&set[2]){PointF l=s(actual[0]),r=s(actual[1]),ap=s(actual[2]);c.drawLine(l.x,l.y,ap.x,ap.y,actualPaint);c.drawLine(r.x,r.y,ap.x,ap.y,actualPaint);}if(complete())drawGenuineRelationTargets(c);
        }
        private void drawGenuineRelationTargets(Canvas c){PointF lm=mid(actual[0],actual[1]);float bw=dist(actual[0],actual[1]);float ux=p12.x-centre.x,uy=p12.y-centre.y,un=(float)Math.hypot(ux,uy);ux/=un;uy/=un;float tx=-uy,ty=ux;float targetBaseX=actual[3].x-ux*bw*GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE,targetBaseY=actual[3].y-uy*bw*GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE;float targetApexX=actual[4].x+ux*bw*GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE,targetApexY=actual[4].y+uy*bw*GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE;PointF b0=s(new PointF(targetBaseX-tx*bw*.5f,targetBaseY-ty*bw*.5f)),b1=s(new PointF(targetBaseX+tx*bw*.5f,targetBaseY+ty*bw*.5f)),ap=s(new PointF(targetApexX,targetApexY));c.drawLine(b0.x,b0.y,b1.x,b1.y,target);c.drawCircle(ap.x,ap.y,dp(5),target);c.drawText("GEN BASE GAP",b1.x+dp(5),b1.y,label);c.drawText("GEN APEX/CROWN GAP",ap.x+dp(6),ap.y,label);PointF m=s(lm);c.drawCircle(m.x,m.y,dp(3),target);}
        private void drawMinuteAxis(Canvas c,int minuteOffset,String text){double ang=Math.toRadians(minuteOffset*6.0-90.0),ux=Math.cos(ang),uy=Math.sin(ang);PointF a=s(PerspectiveMasterRenderer.projectPoint(pose,ux*.91,uy*.91)),b=s(PerspectiveMasterRenderer.projectPoint(pose,ux,uy));c.drawLine(a.x,a.y,b.x,b.y,minute);c.drawText(text,b.x+dp(3),b.y+dp(10),label);}
        private int nearestSet(PointF q){int best=-1;float bd=Float.MAX_VALUE;for(int i=0;i<5;i++)if(set[i]){float d=dist(q,actual[i]);if(d<bd){bd=d;best=i;}}return best;}
        private float screenPxToImage(float px){return drawScale>0?px/drawScale:px;}private PointF s(PointF q){return new PointF(drawOx+(q.x-cropLeft)*drawScale,drawOy+(q.y-cropTop)*drawScale);}private PointF screenToImage(float sx,float sy){if(drawScale<=0)return null;float x=cropLeft+(sx-drawOx)/drawScale,y=cropTop+(sy-drawOy)/drawScale;if(x<cropLeft||x>cropRight||y<cropTop||y>cropBottom)return null;return new PointF(x,y);}
    }
    private static PointF mid(PointF a,PointF b){return new PointF((a.x+b.x)/2f,(a.y+b.y)/2f);}private static float dist(PointF a,PointF b){if(a==null||b==null)return Float.MAX_VALUE;return (float)Math.hypot(a.x-b.x,a.y-b.y);}
    private Paint stroke(int color,float widthDp){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(Math.round(widthDp)));return p;}private Paint fill(int color){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStyle(Paint.Style.FILL);return p;}private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(11);return b;}private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
