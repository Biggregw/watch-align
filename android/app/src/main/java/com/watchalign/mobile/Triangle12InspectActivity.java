package com.watchalign.mobile;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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

/** Alpha42: three-vertex position, rotation and shape comparison for the 12 o'clock triangle. */
public class Triangle12InspectActivity extends Activity {
    private Bitmap base; private PerspectiveMasterRenderer.Pose pose; private TriangleView triangleView;
    private TextView result,hint; private Button stepButton; private boolean fine=true;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(8,17,31));getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        base=InspectionImageStore.baseBitmap;pose=InspectionImageStore.alignedPose==null?null:InspectionImageStore.alignedPose.copy();if(base==null||pose==null){finish();return;}
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);triangleView=new TriangleView();root.addView(triangleView,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(6),dp(8),dp(6));top.setBackgroundColor(0xE008111F);
        Button back=btn("Back");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(72),dp(46)));
        TextView title=txt("12 triangle 3-point fit · α42",18);title.setPadding(dp(10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        Button reset=btn("Reset");reset.setOnClickListener(v->{triangleView.resetActual();updateResult();});top.addView(reset,new LinearLayout.LayoutParams(dp(76),dp(46)));root.addView(top,new FrameLayout.LayoutParams(-1,dp(60),Gravity.TOP));

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(10),dp(4),dp(10),dp(7));bottom.setBackgroundColor(0xEE08111F);
        hint=txt("Tap 1 LEFT BASE, 2 RIGHT BASE, 3 APEX. Tap a numbered point again to select it for nudging.",11);hint.setGravity(Gravity.CENTER);bottom.addView(hint,new LinearLayout.LayoutParams(-1,dp(36)));
        result=txt("",12);result.setGravity(Gravity.CENTER);bottom.addView(result,new LinearLayout.LayoutParams(-1,dp(58)));
        LinearLayout select=new LinearLayout(this);select.setGravity(Gravity.CENTER);Button p1=btn("1 Left"),p2=btn("2 Right"),p3=btn("3 Apex");p1.setOnClickListener(v->triangleView.select(0));p2.setOnClickListener(v->triangleView.select(1));p3.setOnClickListener(v->triangleView.select(2));select.addView(p1,new LinearLayout.LayoutParams(0,dp(40),1));select.addView(p2,new LinearLayout.LayoutParams(0,dp(40),1));select.addView(p3,new LinearLayout.LayoutParams(0,dp(40),1));bottom.addView(select);
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);stepButton=btn("Fine 0.25 px");stepButton.setOnClickListener(v->{fine=!fine;stepButton.setText(fine?"Fine 0.25 px":"Coarse 1 px");});controls.addView(stepButton,new LinearLayout.LayoutParams(dp(118),dp(44)));
        Button left=btn("◀"),up=btn("▲"),down=btn("▼"),right=btn("▶");setupRepeat(left,-1,0);setupRepeat(up,0,-1);setupRepeat(down,0,1);setupRepeat(right,1,0);controls.addView(left,new LinearLayout.LayoutParams(0,dp(44),1));controls.addView(up,new LinearLayout.LayoutParams(0,dp(44),1));controls.addView(down,new LinearLayout.LayoutParams(0,dp(44),1));controls.addView(right,new LinearLayout.LayoutParams(0,dp(44),1));bottom.addView(controls);
        TextView caveat=txt("HIGH = outward toward rehaut. Position, angle, width, height and apex centring are measured after the four-point perspective correction. QC reference, not Rolex factory tolerance.",9);caveat.setGravity(Gravity.CENTER);bottom.addView(caveat,new LinearLayout.LayoutParams(-1,dp(44)));
        root.addView(bottom,new FrameLayout.LayoutParams(-1,dp(224),Gravity.BOTTOM));setContentView(root);updateResult();
    }

    private void setupRepeat(Button b,float dx,float dy){Handler h=new Handler(Looper.getMainLooper());Runnable r=new Runnable(){@Override public void run(){nudge(dx,dy);h.postDelayed(this,80);}};b.setOnTouchListener((v,e)->{int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN){nudge(dx,dy);h.postDelayed(r,330);return true;}if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){h.removeCallbacks(r);return true;}return true;});}
    private void nudge(float dx,float dy){if(!triangleView.hasSelectedPoint())return;float s=fine?.25f:1f;triangleView.nudge(dx*s,dy*s);updateResult();}

    private void updateResult(){
        if(result==null||triangleView==null)return;if(!triangleView.complete()){result.setText("Set all three vertices. Selected: "+triangleView.selectedName());return;}
        Triangle12ShapeMetric.Result m=triangleView.metric();String radial=Math.abs(m.radialPx)<.05f?"ON TARGET":(m.radialPx>0?String.format("HIGH +%.2f px",m.radialPx):String.format("LOW %.2f px",m.radialPx));
        String lateral=Math.abs(m.lateralPx)<.05f?"centred":(m.lateralPx>0?String.format("%.2f px right",m.lateralPx):String.format("%.2f px left",-m.lateralPx));
        String rot=Math.abs(m.rotationDeg)<.03f?"rotation 0.00°":String.format("rotation %.2f° %s",Math.abs(m.rotationDeg),m.rotationDeg>0?"clockwise":"counter-clockwise");
        result.setText(radial+String.format(" (%+.3f%% R) · ",m.radialPct)+lateral+"\n"+rot+String.format(" · width %+.1f%% · height %+.1f%% · apex %+.2f px",m.widthPct,m.heightPct,m.apexCentrePx));
    }

    private final class TriangleView extends View {
        final PointF[] expected=new PointF[3],actual=new PointF[3];final boolean[] set={false,false,false};int selected=0;
        final PointF centre,p12,p3,p6,p9;final float dialR,cropLeft,cropTop,cropRight,cropBottom;float drawScale,drawOx,drawOy;
        final Paint axis=stroke(Color.rgb(255,35,35),2),target=stroke(Color.CYAN,2),actualPaint=stroke(Color.rgb(60,255,120),2),label=fill(Color.WHITE);
        TriangleView(){super(Triangle12InspectActivity.this);setBackgroundColor(Color.BLACK);
            // Canonical triangle centroid is exactly radius .79: base at .84, apex at .69.
            expected[0]=PerspectiveMasterRenderer.projectPoint(pose,-.07,-.84);expected[1]=PerspectiveMasterRenderer.projectPoint(pose,.07,-.84);expected[2]=PerspectiveMasterRenderer.projectPoint(pose,0,-.69);
            centre=PerspectiveMasterRenderer.projectPoint(pose,0,0);p12=PerspectiveMasterRenderer.projectPoint(pose,0,-1);p3=PerspectiveMasterRenderer.projectPoint(pose,1,0);p6=PerspectiveMasterRenderer.projectPoint(pose,0,1);p9=PerspectiveMasterRenderer.projectPoint(pose,-1,0);dialR=(dist(centre,p12)+dist(centre,p3)+dist(centre,p6)+dist(centre,p9))/4f;
            PointF ec=centroid(expected);float half=Math.max(52f,dialR*.30f);cropLeft=Math.max(0,ec.x-half);cropRight=Math.min(base.getWidth(),ec.x+half);cropTop=Math.max(0,ec.y-half);cropBottom=Math.min(base.getHeight(),ec.y+half);label.setTextSize(dp(10));label.setFakeBoldText(true);
            setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){PointF q=screenToImage(e.getX(),e.getY());if(q!=null){int near=nearestSet(q);if(near>=0&&dist(q,actual[near])<screenPxToImage(dp(34))){selected=near;}else{actual[selected]=q;set[selected]=true;if(selected<2)selected++;}invalidate();updateResult();}return true;}return true;});
        }
        boolean complete(){return set[0]&&set[1]&&set[2];}boolean hasSelectedPoint(){return set[selected];}String selectedName(){return selected==0?"1 Left base":selected==1?"2 Right base":"3 Apex";}void select(int i){selected=i;invalidate();updateResult();}
        void resetActual(){for(int i=0;i<3;i++){set[i]=false;actual[i]=null;}selected=0;invalidate();}
        void nudge(float dx,float dy){if(!set[selected])return;actual[selected].x+=dx;actual[selected].y+=dy;invalidate();}
        Triangle12ShapeMetric.Result metric(){float[] a=pack(actual),e=pack(expected);PointF ec=centroid(expected);float ox=ec.x-centre.x,oy=ec.y-centre.y;PointF er=new PointF(expected[1].x-expected[0].x,expected[1].y-expected[0].y);return Triangle12ShapeMetric.measure(a,e,ox,oy,er.x,er.y,dialR);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=cropRight-cropLeft,h=cropBottom-cropTop;if(w<=1||h<=1)return;float availTop=dp(66),availBottom=getHeight()-dp(230),availH=Math.max(1,availBottom-availTop);drawScale=Math.min(getWidth()/w,availH/h);float dw=w*drawScale,dh=h*drawScale;drawOx=(getWidth()-dw)/2f;drawOy=availTop+(availH-dh)/2f;Rect src=new Rect(Math.round(cropLeft),Math.round(cropTop),Math.round(cropRight),Math.round(cropBottom));RectF dst=new RectF(drawOx,drawOy,drawOx+dw,drawOy+dh);c.drawBitmap(base,src,dst,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));PointF a=s(PerspectiveMasterRenderer.projectPoint(pose,0,-.60)),b=s(p12);c.drawLine(a.x,a.y,b.x,b.y,axis);drawTriangle(c,expected,target);for(int i=0;i<3;i++){if(set[i]){PointF q=s(actual[i]);float r=dp(i==selected?9:6);c.drawCircle(q.x,q.y,r,actualPaint);c.drawText(String.valueOf(i+1),q.x+dp(9),q.y-dp(8),label);}}if(set[0]&&set[1]){PointF l=s(actual[0]),r=s(actual[1]);c.drawLine(l.x,l.y,r.x,r.y,actualPaint);}if(complete()){PointF l=s(actual[0]),r=s(actual[1]),ap=s(actual[2]);c.drawLine(l.x,l.y,ap.x,ap.y,actualPaint);c.drawLine(r.x,r.y,ap.x,ap.y,actualPaint);}}
        private void drawTriangle(Canvas c,PointF[] p,Paint paint){Path path=new Path();PointF a=s(p[0]),b=s(p[1]),d=s(p[2]);path.moveTo(a.x,a.y);path.lineTo(b.x,b.y);path.lineTo(d.x,d.y);path.close();c.drawPath(path,paint);}
        private int nearestSet(PointF q){int best=-1;float bd=Float.MAX_VALUE;for(int i=0;i<3;i++)if(set[i]){float d=dist(q,actual[i]);if(d<bd){bd=d;best=i;}}return best;}
        private float screenPxToImage(float px){return drawScale>0?px/drawScale:px;}private PointF s(PointF q){return new PointF(drawOx+(q.x-cropLeft)*drawScale,drawOy+(q.y-cropTop)*drawScale);}private PointF screenToImage(float sx,float sy){if(drawScale<=0)return null;float x=cropLeft+(sx-drawOx)/drawScale,y=cropTop+(sy-drawOy)/drawScale;if(x<cropLeft||x>cropRight||y<cropTop||y>cropBottom)return null;return new PointF(x,y);}
    }
    private static float[] pack(PointF[] p){return new float[]{p[0].x,p[0].y,p[1].x,p[1].y,p[2].x,p[2].y};}private static PointF centroid(PointF[] p){return new PointF((p[0].x+p[1].x+p[2].x)/3f,(p[0].y+p[1].y+p[2].y)/3f);}private static float dist(PointF a,PointF b){if(a==null||b==null)return Float.MAX_VALUE;return (float)Math.hypot(a.x-b.x,a.y-b.y);}
    private Paint stroke(int color,float widthDp){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(Math.round(widthDp)));return p;}private Paint fill(int color){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStyle(Paint.Style.FILL);return p;}private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);return b;}private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
