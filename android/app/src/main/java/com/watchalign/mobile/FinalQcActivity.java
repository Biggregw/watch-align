package com.watchalign.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Final GMT QC: exact angular geometry plus image-calibrated model dimensions and measured residuals. */
public class FinalQcActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ZoomableImageView image;
    private Bitmap combined,base;
    private LinearLayout root;
    private PerspectiveMasterRenderer.Pose pose;
    private PointF[] legacyTrianglePoints;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(8,17,31));getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        base=InspectionImageStore.baseBitmap;pose=InspectionImageStore.alignedPose;legacyTrianglePoints=InspectionImageStore.trianglePoints;
        if(base==null||pose==null){finish();return;}

        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(8,17,31));
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        ProgressBar spinner=new ProgressBar(this);TextView status=txt("Running automatic GMT geometry QC…",11);status.setGravity(Gravity.CENTER);
        LinearLayout loading=new LinearLayout(this);loading.setGravity(Gravity.CENTER);loading.setOrientation(LinearLayout.VERTICAL);loading.addView(spinner,new LinearLayout.LayoutParams(dp(56),dp(56)));loading.addView(status,new LinearLayout.LayoutParams(-1,dp(44)));root.addView(loading,new LinearLayout.LayoutParams(-1,-1));setContentView(root);

        worker.submit(()->{
            GmtQcPipeline.Result result=null;String failure=null;
            try{result=GmtQcPipeline.analyse(base,InspectionImageStore.alignedModelRef,pose);if(result==null)failure=GmtQcPipeline.lastFailureReason();}
            catch(Throwable t){failure=t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage());}
            final GmtQcPipeline.Result ready=result;final String reason=failure;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                InspectionImageStore.gmtQc=ready;
                GmtIndexAutoAnalyzer.Result indices=ready!=null?ready.indices:GmtIndexAutoAnalyzer.unavailableForKnownDialFailure(base,reason);
                combined=buildUnified(base,pose,ready,indices);
                buildAndShowUi(ready,indices,reason);
            });
        });
    }

    private void buildAndShowUi(final GmtQcPipeline.Result result,final GmtIndexAutoAnalyzer.Result indexResult,String failure){
        final String qcSummary=buildSummary(result,indexResult,failure);
        // Keep the old history format compatible when a legacy manual triangle result exists.
        if(InspectionImageStore.historyRecordId==null&&legacyTrianglePoints!=null&&legacyTrianglePoints.length>=5&&InspectionImageStore.triangleMetric!=null){
            try{InspectionHistory.Record saved=InspectionHistory.save(this,base,InspectionImageStore.alignedModelRef,pose,legacyTrianglePoints,InspectionImageStore.triangleMetric,qcSummary);InspectionImageStore.historyRecordId=saved.id;}catch(Exception ignored){}
        }
        root.removeAllViews();

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(6),dp(8),dp(6));top.setBackgroundColor(0xE008111F);
        Button back=btn("Back");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(72),dp(46)));
        TextView title=txt("Final GMT QC",18);title.setPadding(dp(10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        Button reset=btn("Fit watch");reset.setOnClickListener(v->{if(image!=null)image.resetZoom();});top.addView(reset,new LinearLayout.LayoutParams(dp(86),dp(46)));root.addView(top,new LinearLayout.LayoutParams(-1,dp(60)));

        image=new ZoomableImageView(this);image.setBackgroundColor(Color.BLACK);image.setImageBitmap(combined);root.addView(image,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(10),dp(5),dp(10),dp(7));bottom.setBackgroundColor(0xEE08111F);
        TextView summary=txt(qcSummary,9);summary.setGravity(Gravity.CENTER);bottom.addView(summary,new LinearLayout.LayoutParams(-1,dp(205)));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
        Button blink=btn("Hold: original");blink.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){image.setImageBitmapPreserveZoom(base);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){image.setImageBitmapPreserveZoom(combined);return true;}return false;});row.addView(blink,new LinearLayout.LayoutParams(0,dp(46),1));
        Button unified=btn("Reference + measured");unified.setOnClickListener(v->image.setImageBitmapPreserveZoom(combined));row.addView(unified,new LinearLayout.LayoutParams(0,dp(46),1));
        Button indices=btn("Index evidence");boolean hasIndexEvidence=indexResult!=null&&indexResult.geometry!=null&&indexResult.annotated!=null;indices.setEnabled(hasIndexEvidence);indices.setOnClickListener(v->{if(hasIndexEvidence)image.setImageBitmapPreserveZoom(indexResult.annotated);else Toast.makeText(this,"No reliable index evidence is available for this photo.",Toast.LENGTH_SHORT).show();});row.addView(indices,new LinearLayout.LayoutParams(0,dp(46),1));
        Button share=btn("Share report");share.setOnClickListener(v->{try{QcExport.share(this,combined,InspectionImageStore.alignedModelRef,qcSummary);}catch(Exception e){Toast.makeText(this,"Could not export: "+e.getMessage(),Toast.LENGTH_LONG).show();}});row.addView(share,new LinearLayout.LayoutParams(0,dp(46),1));bottom.addView(row);
        Button extended=btn("Detailed GMT checks: date, cyclops, rehaut and SEL");extended.setEnabled(result!=null);extended.setOnClickListener(v->{if(result!=null)startActivity(new Intent(this,GmtExtendedQcActivity.class));else Toast.makeText(this,"Detailed GMT checks are unavailable because canonical dial creation failed.",Toast.LENGTH_LONG).show();});bottom.addView(extended,new LinearLayout.LayoutParams(-1,dp(42)));
        TextView hint=txt("CYAN = mathematically exact GMT angular axes. YELLOW = image-calibrated 126710 marker dimensions/radii projected with the verified inner dial edge. MAGENTA = measured watch geometry. These dimensions are reference-derived, not Rolex factory CAD.",9);hint.setGravity(Gravity.CENTER);bottom.addView(hint,new LinearLayout.LayoutParams(-1,dp(62)));
        root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(369)));
    }

    private Bitmap buildUnified(Bitmap src,PerspectiveMasterRenderer.Pose pose,GmtQcPipeline.Result result,GmtIndexAutoAnalyzer.Result indices){
        Bitmap out;
        if(indices!=null&&indices.annotated!=null)out=indices.annotated.copy(Bitmap.Config.ARGB_8888,true);
        else{out=src.copy(Bitmap.Config.ARGB_8888,true);Gmt126710IdealOverlay.draw(out,pose,Gmt126710IdealOverlay.markerCenterRadius());}
        if(result!=null&&result.triangle!=null)out=GmtTriangleAutoAnalyzer.drawMeasured(out,result.triangle);
        return out;
    }

    private String buildSummary(GmtQcPipeline.Result result,GmtIndexAutoAnalyzer.Result indexResult,String failure){
        String triangle=result!=null&&result.triangle!=null?result.triangle.summary:GmtTriangleAutoAnalyzer.unavailable(failure).summary;
        String indices=indexResult==null?"REFERENCE GMT GEOMETRY\nUnavailable.":indexResult.summary;
        return triangle+"\n\n"+indices;
    }

    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(10);return b;}private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
