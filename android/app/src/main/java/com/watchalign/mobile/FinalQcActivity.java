package com.watchalign.mobile;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Alpha50 final combined QC view: main alignment + corrected 12 triangle + concise summary. */
public class FinalQcActivity extends Activity {
    private ZoomableImageView image;
    private Bitmap combined, base;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(8,17,31));getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        base=InspectionImageStore.baseBitmap;
        PerspectiveMasterRenderer.Pose pose=InspectionImageStore.alignedPose;
        PointF[] tri=InspectionImageStore.trianglePoints;
        if(base==null||pose==null||tri==null||tri.length<5){finish();return;}
        combined=buildCombined(base,pose,tri);

        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(8,17,31));
        image=new ZoomableImageView(this);image.setBackgroundColor(Color.BLACK);image.setImageBitmap(combined);root.addView(image,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(6),dp(8),dp(6));top.setBackgroundColor(0xE008111F);
        Button back=btn("Back");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(72),dp(46)));
        TextView title=txt("Final QC result · α50",18);title.setPadding(dp(10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        Button reset=btn("Reset");reset.setOnClickListener(v->image.resetZoom());top.addView(reset,new LinearLayout.LayoutParams(dp(76),dp(46)));root.addView(top,new FrameLayout.LayoutParams(-1,dp(60),Gravity.TOP));

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(10),dp(5),dp(10),dp(7));bottom.setBackgroundColor(0xEE08111F);
        TextView summary=txt(buildSummary(),12);summary.setGravity(Gravity.CENTER);bottom.addView(summary,new LinearLayout.LayoutParams(-1,dp(90)));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
        Button blink=btn("Hold watch only");blink.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){image.setImageBitmapPreserveZoom(base);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){image.setImageBitmapPreserveZoom(combined);return true;}return false;});row.addView(blink,new LinearLayout.LayoutParams(0,dp(46),1));
        Button main=btn("Main overlay");main.setOnClickListener(v->image.setImageBitmapPreserveZoom(mainOnly(base,InspectionImageStore.alignedPose)));row.addView(main,new LinearLayout.LayoutParams(0,dp(46),1));
        Button both=btn("Combined");both.setOnClickListener(v->image.setImageBitmapPreserveZoom(combined));row.addView(both,new LinearLayout.LayoutParams(0,dp(46),1));bottom.addView(row);
        TextView hint=txt("Combined view uses your final corrected triangle points. Green = measured triangle, cyan = genuine local relationship targets, red/yellow = main dial ruler.",10);hint.setGravity(Gravity.CENTER);bottom.addView(hint,new LinearLayout.LayoutParams(-1,dp(50)));
        root.addView(bottom,new FrameLayout.LayoutParams(-1,dp(198),Gravity.BOTTOM));setContentView(root);
    }

    private Bitmap mainOnly(Bitmap src,PerspectiveMasterRenderer.Pose pose){
        Bitmap overlay=PerspectiveMasterRenderer.renderOverlay(src.getWidth(),src.getHeight(),InspectionImageStore.alignedModelRef,pose);
        Bitmap out=src.copy(Bitmap.Config.ARGB_8888,true);new Canvas(out).drawBitmap(overlay,0,0,new Paint(Paint.ANTI_ALIAS_FLAG));return out;
    }

    private Bitmap buildCombined(Bitmap src,PerspectiveMasterRenderer.Pose pose,PointF[] p){
        Bitmap out=mainOnly(src,pose);Canvas c=new Canvas(out);float u=Math.max(1f,Math.min(out.getWidth(),out.getHeight())/900f);
        Paint halo=stroke(Color.BLACK,7*u,215),green=stroke(Color.rgb(60,255,120),3.2f*u,255),cyan=stroke(Color.CYAN,2.5f*u,245),white=fill(Color.WHITE,245);white.setTextSize(17*u);white.setFakeBoldText(true);
        path(c,p[0],p[1],p[2],halo);path(c,p[0],p[1],p[2],green);
        float bw=dist(p[0],p[1]);PointF centre=PerspectiveMasterRenderer.projectPoint(pose,0,0),p12=PerspectiveMasterRenderer.projectPoint(pose,0,-1);float ux=p12.x-centre.x,uy=p12.y-centre.y,n=(float)Math.hypot(ux,uy);if(n<1)n=1;ux/=n;uy/=n;float tx=-uy,ty=ux;
        float bx=p[3].x-ux*bw*GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE,by=p[3].y-uy*bw*GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE;
        PointF b0=new PointF(bx-tx*bw*.5f,by-ty*bw*.5f),b1=new PointF(bx+tx*bw*.5f,by+ty*bw*.5f);
        float ax=p[4].x+ux*bw*GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE,ay=p[4].y+uy*bw*GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE;
        c.drawLine(b0.x,b0.y,b1.x,b1.y,cyan);c.drawCircle(ax,ay,6*u,cyan);c.drawText("12 QC",p[1].x+10*u,p[1].y-12*u,white);
        return out;
    }

    private String buildSummary(){
        Triangle12RelationalMetric.Result m=InspectionImageStore.triangleMetric;
        if(m==null)return "QC SUMMARY\n12 triangle measurements unavailable";
        float bg=(m.baseGapRatio-GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE)*100f;
        float ag=(m.apexGapRatio-GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE)*100f;
        String pos=(bg<-.5f&&ag>.5f)?"slightly outward toward minute track":(bg>.5f&&ag<-.5f)?"slightly inward toward crown":(Math.abs(bg)<.5f&&Math.abs(ag)<.5f)?"matches genuine reference":"mixed local relationship";
        String rot=Math.abs(m.rotationDeg)<.10f?"essentially straight":String.format("%.2f° %s",Math.abs(m.rotationDeg),m.rotationDeg>0?"clockwise":"counter-clockwise");
        String centre=Math.abs(m.lateralPx)<.5f?"centred":String.format("%+.2f px lateral",m.lateralPx);
        return "QC SUMMARY\n12 triangle: "+pos+" · "+centre+" · "+rot+"\nBase-to-60 "+String.format("%+.1f%% BW",bg)+" · apex-to-crown "+String.format("%+.1f%% BW",ag);
    }

    private void path(Canvas c,PointF a,PointF b,PointF d,Paint p){Path q=new Path();q.moveTo(a.x,a.y);q.lineTo(b.x,b.y);q.lineTo(d.x,d.y);q.close();c.drawPath(q,p);}private float dist(PointF a,PointF b){return (float)Math.hypot(a.x-b.x,a.y-b.y);}private Paint stroke(int color,float width,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);p.setAlpha(alpha);return p;}private Paint fill(int color,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStyle(Paint.Style.FILL);p.setAlpha(alpha);return p;}private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(11);return b;}private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
