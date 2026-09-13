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

/** Final combined QC view: perspective-rectified 12 check against image-derived genuine controls. */
public class FinalQcActivity extends Activity {
    private ZoomableImageView image;
    private Bitmap combined, base;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(8,17,31));getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        base=InspectionImageStore.baseBitmap;
        PerspectiveMasterRenderer.Pose pose=InspectionImageStore.alignedPose;
        PointF[] tri=InspectionImageStore.trianglePoints;
        if(base==null||pose==null||tri==null||tri.length<5){finish();return;}
        combined=buildCombined(base,pose,tri);final String qcSummary=buildSummary();
        if(InspectionImageStore.historyRecordId==null)try{InspectionHistory.Record saved=InspectionHistory.save(this,base,InspectionImageStore.alignedModelRef,pose,tri,InspectionImageStore.triangleMetric,qcSummary);InspectionImageStore.historyRecordId=saved.id;}catch(Exception ignored){}

        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(8,17,31));
        image=new ZoomableImageView(this);image.setBackgroundColor(Color.BLACK);image.setImageBitmap(combined);root.addView(image,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(6),dp(8),dp(6));top.setBackgroundColor(0xE008111F);
        Button back=btn("Back");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(72),dp(46)));
        TextView title=txt("Final QC result",18);title.setPadding(dp(10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        Button reset=btn("Reset");reset.setOnClickListener(v->image.resetZoom());top.addView(reset,new LinearLayout.LayoutParams(dp(76),dp(46)));root.addView(top,new FrameLayout.LayoutParams(-1,dp(60),Gravity.TOP));

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(10),dp(5),dp(10),dp(7));bottom.setBackgroundColor(0xEE08111F);
        TextView summary=txt(qcSummary,10);summary.setGravity(Gravity.CENTER);bottom.addView(summary,new LinearLayout.LayoutParams(-1,dp(190)));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
        Button blink=btn("Hold watch only");blink.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){image.setImageBitmapPreserveZoom(base);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){image.setImageBitmapPreserveZoom(combined);return true;}return false;});row.addView(blink,new LinearLayout.LayoutParams(0,dp(46),1));
        Button main=btn("Main overlay");main.setOnClickListener(v->image.setImageBitmapPreserveZoom(mainOnly(base,InspectionImageStore.alignedPose)));row.addView(main,new LinearLayout.LayoutParams(0,dp(46),1));
        Button both=btn("Combined");both.setOnClickListener(v->image.setImageBitmapPreserveZoom(combined));row.addView(both,new LinearLayout.LayoutParams(0,dp(46),1));
        Button share=btn("Share QC");share.setOnClickListener(v->{try{QcExport.share(this,combined,InspectionImageStore.alignedModelRef,qcSummary);}catch(Exception e){android.widget.Toast.makeText(this,"Could not export: "+e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}});row.addView(share,new LinearLayout.LayoutParams(0,dp(46),1));bottom.addView(row);
        TextView hint=txt("Green = measured triangle. Cyan = fixed projected genuine-reference outline. Results near a control boundary are labelled borderline. Image-derived controls only, not Rolex factory tolerances.",9);hint.setGravity(Gravity.CENTER);bottom.addView(hint,new LinearLayout.LayoutParams(-1,dp(58)));
        root.addView(bottom,new FrameLayout.LayoutParams(-1,dp(300),Gravity.BOTTOM));setContentView(root);
    }

    private Bitmap mainOnly(Bitmap src,PerspectiveMasterRenderer.Pose pose){
        Bitmap overlay=PerspectiveMasterRenderer.renderOverlay(src.getWidth(),src.getHeight(),InspectionImageStore.alignedModelRef,pose);
        Bitmap out=src.copy(Bitmap.Config.ARGB_8888,true);new Canvas(out).drawBitmap(overlay,0,0,new Paint(Paint.ANTI_ALIAS_FLAG));return out;
    }

    private Bitmap buildCombined(Bitmap src,PerspectiveMasterRenderer.Pose pose,PointF[] p){
        Bitmap out=mainOnly(src,pose);Canvas c=new Canvas(out);float u=Math.max(1f,Math.min(out.getWidth(),out.getHeight())/900f);
        Paint halo=stroke(Color.BLACK,7*u,215),green=stroke(Color.rgb(60,255,120),3.2f*u,255),cyan=stroke(Color.CYAN,2.8f*u,245),white=fill(Color.WHITE,245);white.setTextSize(16*u);white.setFakeBoldText(true);
        path(c,p[0],p[1],p[2],halo);path(c,p[0],p[1],p[2],green);

        PointF rl=PerspectiveMasterRenderer.projectPoint(pose,Gmt126710BlnrTriangleReference.LEFT_X,Gmt126710BlnrTriangleReference.LEFT_Y);
        PointF rr=PerspectiveMasterRenderer.projectPoint(pose,Gmt126710BlnrTriangleReference.RIGHT_X,Gmt126710BlnrTriangleReference.RIGHT_Y);
        PointF ra=PerspectiveMasterRenderer.projectPoint(pose,Gmt126710BlnrTriangleReference.APEX_X,Gmt126710BlnrTriangleReference.APEX_Y);
        path(c,rl,rr,ra,cyan);
        c.drawCircle(rl.x,rl.y,5*u,cyan);c.drawCircle(rr.x,rr.y,5*u,cyan);c.drawCircle(ra.x,ra.y,5*u,cyan);
        PointF rm=PerspectiveMasterRenderer.projectPoint(pose,0,-Gmt126710BlnrTriangleReference.MINUTE_INNER_R);
        c.drawCircle(rm.x,rm.y,5*u,cyan);c.drawText("GEN REF",rr.x+9*u,rr.y-10*u,white);
        return out;
    }

    private String buildSummary(){
        Triangle12RelationalMetric.Result m=InspectionImageStore.triangleMetric;
        if(m==null)return "QC SUMMARY\n12 triangle measurements unavailable";

        float baseMin=GenTriangle12RelationalReference.BASE_TO_60_MIN,baseMax=GenTriangle12RelationalReference.BASE_TO_60_MAX;
        float crownMin=GenTriangle12RelationalReference.APEX_TO_CROWN_MIN,crownMax=GenTriangle12RelationalReference.APEX_TO_CROWN_MAX;
        float rotMax=GenTriangle12RelationalReference.ROTATION_OBSERVED_GEN_MAX_DEG;
        boolean baseOk=m.baseGapRatio>=baseMin&&m.baseGapRatio<=baseMax;
        boolean crownOk=m.apexGapRatio>=crownMin&&m.apexGapRatio<=crownMax;
        boolean rotOk=Math.abs(m.rotationDeg)<=rotMax;
        float baseMargin=baseOk?Math.min(m.baseGapRatio-baseMin,baseMax-m.baseGapRatio):Math.min(Math.abs(m.baseGapRatio-baseMin),Math.abs(m.baseGapRatio-baseMax));
        boolean borderlineBase=baseOk&&baseMargin<0.010f;

        String headline;
        if(!rotOk)headline=borderlineBase?"position borderline low · rotation outside observed controls":"rotation outside observed controls";
        else if(!baseOk)headline=m.baseGapRatio<baseMin?"triangle too close to minute track vs observed controls":"triangle too far from minute track vs observed controls";
        else if(!crownOk)headline="apex/crown relationship outside observed controls";
        else if(borderlineBase)headline="position borderline near genuine-control boundary";
        else headline="measured relationships inside current observed controls";

        float trackDelta=m.baseGapRatio-GenTriangle12RelationalReference.BASE_TO_60_MEDIAN;
        String trackDir=trackDelta<0?"closer to minute track":"farther from minute track";
        String centre=Math.abs(m.lateralPx)<.5f?"centred":String.format("%+.2f px lateral",m.lateralPx);
        String rot=rotOk?String.format("%+.2f° (inside ±%.2f°)",m.rotationDeg,rotMax):String.format("%+.2f° · %.2f° beyond observed limit",m.rotationDeg,Math.abs(m.rotationDeg)-rotMax);

        String baseText=String.format("Track gap: %.3f BW · %.3f %s than genuine median %.3f · lower-bound margin %.3f",
                m.baseGapRatio,Math.abs(trackDelta),trackDir,GenTriangle12RelationalReference.BASE_TO_60_MEDIAN,m.baseGapRatio-baseMin);
        String crownText=String.format("Apex-to-crown: %.3f BW · genuine median %.3f · %s current observed %.3f–%.3f",
                m.apexGapRatio,GenTriangle12RelationalReference.APEX_TO_CROWN_MEDIAN,crownOk?"inside":"outside",crownMin,crownMax);
        String confidence=borderlineBase?"Confidence: BORDERLINE because track-gap is within 0.010 BW of the control boundary.":"Confidence: not boundary-limited on track gap.";

        return "QC SUMMARY\n12 triangle: "+headline+" · "+centre+"\n"+baseText+"\n"+crownText+"\nRotation: "+rot+"\n"+confidence;
    }

    private void path(Canvas c,PointF a,PointF b,PointF d,Paint p){Path q=new Path();q.moveTo(a.x,a.y);q.lineTo(b.x,b.y);q.lineTo(d.x,d.y);q.close();c.drawPath(q,p);}private Paint stroke(int color,float width,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);p.setAlpha(alpha);return p;}private Paint fill(int color,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStyle(Paint.Style.FILL);p.setAlpha(alpha);return p;}private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(11);return b;}private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
