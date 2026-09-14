package com.watchalign.mobile;

import android.app.Activity;
import android.content.Intent;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.watchalign.mobile.profile.Triangle12Calibration;
import com.watchalign.mobile.profile.Triangle12CalibrationLoader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Final combined GMT QC view: manual 12 relation plus perspective-gated per-index geometry. */
public class FinalQcActivity extends Activity {
    private static final String TRIANGLE_CALIBRATION_ASSET =
            "calibrations/rolex/gmt-master-ii/126710/triangle12-corrected-pilot-v2.json";

    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ZoomableImageView image;
    private Bitmap combined, base;
    private LinearLayout root;
    private PerspectiveMasterRenderer.Pose pose;
    private PointF[] tri;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(8,17,31));getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        base=InspectionImageStore.baseBitmap;pose=InspectionImageStore.alignedPose;tri=InspectionImageStore.trianglePoints;
        if(base==null||pose==null||tri==null||tri.length<5){finish();return;}
        combined=buildCombined(base,pose,tri);

        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(8,17,31));
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        ProgressBar spinner=new ProgressBar(this);TextView status=txt("Running GMT QC…",11);status.setGravity(Gravity.CENTER);
        LinearLayout loading=new LinearLayout(this);loading.setGravity(Gravity.CENTER);loading.setOrientation(LinearLayout.VERTICAL);loading.addView(spinner,new LinearLayout.LayoutParams(dp(56),dp(56)));loading.addView(status,new LinearLayout.LayoutParams(-1,dp(44)));root.addView(loading,new LinearLayout.LayoutParams(-1,-1));setContentView(root);

        worker.submit(()->{
            GmtQcPipeline.Result result=null;String failure=null;
            try{result=GmtQcPipeline.analyse(base,InspectionImageStore.alignedModelRef,pose);if(result==null)failure=GmtQcPipeline.lastFailureReason();}
            catch(Throwable t){failure=t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage());}
            final GmtQcPipeline.Result ready=result;final String reason=failure;
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;InspectionImageStore.gmtQc=ready;GmtIndexAutoAnalyzer.Result indexResult=ready!=null?ready.indices:GmtIndexAutoAnalyzer.unavailableForKnownDialFailure(base,reason);buildAndShowUi(indexResult);});
        });
    }

    private void buildAndShowUi(final GmtIndexAutoAnalyzer.Result indexResult){
        final String qcSummary=buildSummary(indexResult);
        if(InspectionImageStore.historyRecordId==null)try{InspectionHistory.Record saved=InspectionHistory.save(this,base,InspectionImageStore.alignedModelRef,pose,tri,InspectionImageStore.triangleMetric,qcSummary);InspectionImageStore.historyRecordId=saved.id;}catch(Exception ignored){}
        root.removeAllViews();

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(6),dp(8),dp(6));top.setBackgroundColor(0xE008111F);
        Button back=btn("Back");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(72),dp(46)));
        TextView title=txt("Final GMT QC",18);title.setPadding(dp(10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        Button reset=btn("Fit watch");reset.setOnClickListener(v->{if(image!=null)image.resetZoom();});top.addView(reset,new LinearLayout.LayoutParams(dp(86),dp(46)));root.addView(top,new LinearLayout.LayoutParams(-1,dp(60)));

        image=new ZoomableImageView(this);image.setBackgroundColor(Color.BLACK);image.setImageBitmap(combined);root.addView(image,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(10),dp(5),dp(10),dp(7));bottom.setBackgroundColor(0xEE08111F);
        TextView summary=txt(qcSummary,9);summary.setGravity(Gravity.CENTER);bottom.addView(summary,new LinearLayout.LayoutParams(-1,dp(175)));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
        Button blink=btn("Hold: original");blink.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){image.setImageBitmapPreserveZoom(base);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){image.setImageBitmapPreserveZoom(combined);return true;}return false;});row.addView(blink,new LinearLayout.LayoutParams(0,dp(46),1));
        Button indices=btn("Index evidence");boolean hasIndexEvidence=indexResult!=null&&indexResult.geometry!=null&&indexResult.annotated!=null;indices.setEnabled(hasIndexEvidence);indices.setOnClickListener(v->{if(hasIndexEvidence)image.setImageBitmapPreserveZoom(indexResult.annotated);else Toast.makeText(this,"No reliable index evidence is available for this photo.",Toast.LENGTH_SHORT).show();});row.addView(indices,new LinearLayout.LayoutParams(0,dp(46),1));
        Button both=btn("12 overlay");both.setOnClickListener(v->image.setImageBitmapPreserveZoom(combined));row.addView(both,new LinearLayout.LayoutParams(0,dp(46),1));
        Button share=btn("Share report");share.setOnClickListener(v->{try{QcExport.share(this,combined,InspectionImageStore.alignedModelRef,qcSummary);}catch(Exception e){Toast.makeText(this,"Could not export: "+e.getMessage(),Toast.LENGTH_LONG).show();}});row.addView(share,new LinearLayout.LayoutParams(0,dp(46),1));bottom.addView(row);
        Button extended=btn("Detailed GMT checks: date, cyclops, rehaut and SEL");extended.setEnabled(InspectionImageStore.gmtQc!=null);extended.setOnClickListener(v->{if(InspectionImageStore.gmtQc!=null)startActivity(new Intent(this,GmtExtendedQcActivity.class));else Toast.makeText(this,"Detailed GMT checks are unavailable because canonical dial creation failed.",Toast.LENGTH_LONG).show();});bottom.addView(extended,new LinearLayout.LayoutParams(-1,dp(42)));
        TextView hint=txt("Pinch or double-tap to inspect. Green/cyan shows the corrected 12 relation. Evidence is image-derived, not a Rolex factory tolerance.",9);hint.setGravity(Gravity.CENTER);bottom.addView(hint,new LinearLayout.LayoutParams(-1,dp(45)));
        root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(318)));
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

    private String buildSummary(GmtIndexAutoAnalyzer.Result indexResult){
        Triangle12RelationalMetric.Result m=InspectionImageStore.triangleMetric;
        String triangle;
        try {
            Triangle12Calibration calibration = Triangle12CalibrationLoader.load(getAssets(), TRIANGLE_CALIBRATION_ASSET);
            triangle=GmtTriangle12Assessment.summary(m, calibration);
        } catch (Exception e) {
            triangle="QC SUMMARY\n12 triangle calibration unavailable · "+e.getClass().getSimpleName();
        }
        String indices=indexResult==null?"INDEX GEOMETRY\nUnavailable.":indexResult.summary;
        return triangle+"\n\n"+indices;
    }

    private void path(Canvas c,PointF a,PointF b,PointF d,Paint p){Path q=new Path();q.moveTo(a.x,a.y);q.lineTo(b.x,b.y);q.lineTo(d.x,d.y);q.close();c.drawPath(q,p);}private Paint stroke(int color,float width,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);p.setAlpha(alpha);return p;}private Paint fill(int color,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStyle(Paint.Style.FILL);p.setAlpha(alpha);return p;}private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(10);return b;}private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
