package com.watchalign.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Strict photographic overlay proof. A real front-on dial photo is warped directly onto
 * a candidate watch photo using matching minor minute ticks only. No measurements or QC.
 */
public class PerspectiveOverlayPocActivity extends Activity {
    private static final int PICK_REFERENCE=2201, ALIGN_REFERENCE=2202, PICK_CANDIDATE=2203, ALIGN_CANDIDATE=2204;
    private static final int BG=Color.rgb(8,17,31),ACCENT=Color.rgb(50,213,242),MUTED=Color.rgb(158,176,201);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();

    private Bitmap referenceBitmap,candidateBitmap,lastOverlay;
    private double rcx,rcy,rr,rroll,ccx,ccy,cr,croll;
    private boolean referenceAligned,candidateAligned;
    private ImageView referencePreview,candidatePreview;
    private TextView status;
    private Button alignReferenceButton,pickCandidateButton,alignCandidateButton,inspectButton;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(!OpenCVLoader.initLocal())Toast.makeText(this,"OpenCV could not start",Toast.LENGTH_LONG).show();setContentView(buildUi());
    }

    private View buildUi(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-1));
        root.addView(text("Photographic Perspective Overlay",27,Color.WHITE));
        root.addView(text("Reference photo → minor minute ticks → candidate perspective",14,ACCENT));
        root.addView(text("This proof uses no canonical marker sizes and no QC measurements. Choose a clean front-on loose-dial photo as the reference, then a candidate GMT photo. The same minor minute ticks are matched in both photos and the actual reference photograph is warped onto the candidate. Hour markers, triangle, text and date window do not drive the fit.",13,MUTED),lp(-1,-2,10));

        Button pickReference=button("1. Choose front-on reference dial");pickReference.setOnClickListener(v->pick(PICK_REFERENCE));root.addView(pickReference,lp(-1,dp(52),8));
        alignReferenceButton=button("2. Set reference dial edge at 12 & 6");alignReferenceButton.setEnabled(false);alignReferenceButton.setOnClickListener(v->alignReference());root.addView(alignReferenceButton,lp(-1,dp(50),5));
        referencePreview=image();root.addView(referencePreview,lp(-1,-2,6));

        pickCandidateButton=button("3. Choose candidate GMT photo");pickCandidateButton.setEnabled(false);pickCandidateButton.setOnClickListener(v->pick(PICK_CANDIDATE));root.addView(pickCandidateButton,lp(-1,dp(52),12));
        alignCandidateButton=button("4. Set candidate dial edge at 12 & 6, then build");alignCandidateButton.setBackgroundColor(ACCENT);alignCandidateButton.setTextColor(Color.rgb(4,32,42));alignCandidateButton.setEnabled(false);alignCandidateButton.setOnClickListener(v->alignCandidate());root.addView(alignCandidateButton,lp(-1,dp(54),5));
        candidatePreview=image();root.addView(candidatePreview,lp(-1,-2,6));

        inspectButton=button("Inspect photographic overlay");inspectButton.setEnabled(false);inspectButton.setOnClickListener(v->openInspector());root.addView(inspectButton,lp(-1,dp(48),10));
        status=text("Choose the clean front-on loose-dial reference photo first.",14,MUTED);root.addView(status,lp(-1,-2,10));
        root.addView(text("Inspector has opacity and hold-to-blink only. There is deliberately no nudge. If it does not line up, the fit has failed.",12,MUTED),lp(-1,-2,10));
        return scroll;
    }

    private ImageView image(){ImageView v=new ImageView(this);v.setAdjustViewBounds(true);v.setScaleType(ImageView.ScaleType.FIT_CENTER);v.setBackgroundColor(BG);return v;}
    private void pick(int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,request);}

    private void alignReference(){
        if(referenceBitmap==null)return;InspectionImageStore.clearManualSeed();InspectionImageStore.baseBitmap=referenceBitmap;
        status.setText("Reference: set the physical dial/rehaut boundary at 12, then the opposite boundary at 6.");
        startActivityForResult(new Intent(this,ManualSeedActivity.class),ALIGN_REFERENCE);
    }
    private void alignCandidate(){
        if(candidateBitmap==null||!referenceAligned)return;InspectionImageStore.clearManualSeed();InspectionImageStore.baseBitmap=candidateBitmap;
        status.setText("Candidate: set the physical dial/rehaut boundary at 12, then the opposite boundary at 6.");
        startActivityForResult(new Intent(this,ManualSeedActivity.class),ALIGN_CANDIDATE);
    }

    private void buildOverlay(){
        if(!referenceAligned||!candidateAligned||referenceBitmap==null||candidateBitmap==null)return;
        alignCandidateButton.setEnabled(false);inspectButton.setEnabled(false);status.setText("Matching minor minute ticks and warping the actual reference dial photograph…");
        final Bitmap ref=referenceBitmap,cand=candidateBitmap;
        worker.submit(()->{
            PhotographicMinuteTrackOverlay.Result q=PhotographicMinuteTrackOverlay.build(ref,rcx,rcy,rr,rroll,cand,ccx,ccy,cr,croll);
            runOnUiThread(()->{
                alignCandidateButton.setEnabled(true);
                if(!q.valid){lastOverlay=null;inspectButton.setEnabled(false);status.setText("Could not build a stable photographic overlay: "+q.reason+". Reference ticks "+q.referenceTicks+", candidate ticks "+q.candidateTicks+", matched "+q.matchedTicks+".");return;}
                lastOverlay=q.overlay;inspectButton.setEnabled(true);
                status.setText(String.format(java.util.Locale.US,"Photographic overlay ready. Reference ticks %d · candidate ticks %d · matched %d · inliers %d · mean inlier error %.2f px. No hour marker, triangle or text was used to fit it.",q.referenceTicks,q.candidateTicks,q.matchedTicks,q.inlierTicks,q.meanInlierErrorPx));
                openInspector();
            });
        });
    }

    private void openInspector(){
        if(candidateBitmap==null||lastOverlay==null)return;InspectionImageStore.setOverlay(candidateBitmap,lastOverlay,"Photographic perspective overlay");startActivity(new Intent(this,PhotographicOverlayInspectActivity.class));
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        if(request==ALIGN_REFERENCE){
            if(result==RESULT_OK&&InspectionImageStore.hasManualSeed){rcx=InspectionImageStore.manualCx;rcy=InspectionImageStore.manualCy;rr=InspectionImageStore.manualR;rroll=InspectionImageStore.manualRoll;referenceAligned=true;pickCandidateButton.setEnabled(true);status.setText("Reference aligned. Now choose the candidate GMT photo.");}
            else if(referenceBitmap!=null)status.setText("Reference alignment cancelled.");return;
        }
        if(request==ALIGN_CANDIDATE){
            if(result==RESULT_OK&&InspectionImageStore.hasManualSeed){ccx=InspectionImageStore.manualCx;ccy=InspectionImageStore.manualCy;cr=InspectionImageStore.manualR;croll=InspectionImageStore.manualRoll;candidateAligned=true;buildOverlay();}
            else if(candidateBitmap!=null)status.setText("Candidate alignment cancelled.");return;
        }
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            if(request==PICK_REFERENCE){
                referenceBitmap=readBitmap(data.getData());referenceAligned=false;lastOverlay=null;referencePreview.setImageBitmap(referenceBitmap);alignReferenceButton.setEnabled(true);pickCandidateButton.setEnabled(false);alignCandidateButton.setEnabled(false);inspectButton.setEnabled(false);status.setText("Reference photo ready. Set its 12 and 6 dial edges.");
            }else if(request==PICK_CANDIDATE){
                candidateBitmap=readBitmap(data.getData());candidateAligned=false;lastOverlay=null;candidatePreview.setImageBitmap(candidateBitmap);alignCandidateButton.setEnabled(referenceAligned);inspectButton.setEnabled(false);status.setText("Candidate ready. Set its 12 and 6 dial edges; the photographic overlay will then build automatically.");
            }
        }catch(Exception e){status.setText("Could not read image: "+e.getMessage());}
    }

    private Bitmap readBitmap(Uri uri)throws Exception{
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,opts);}if(opts.outWidth<=0||opts.outHeight<=0)throw new IllegalArgumentException("Not a readable image");
        int maxDim=Math.max(opts.outWidth,opts.outHeight),sample=1;while(maxDim/(sample*2)>=1600)sample*=2;opts.inJustDecodeBounds=false;opts.inSampleSize=sample;opts.inPreferredConfig=Bitmap.Config.ARGB_8888;Bitmap b;try(InputStream in=getContentResolver().openInputStream(uri)){b=BitmapFactory.decodeStream(in,null,opts);}if(b==null)throw new IllegalArgumentException("Not a readable image");int currentMax=Math.max(b.getWidth(),b.getHeight());if(currentMax<=1600)return b.copy(Bitmap.Config.ARGB_8888,false);float s=1600f/currentMax;return Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*s),Math.round(b.getHeight()*s),true).copy(Bitmap.Config.ARGB_8888,false);
    }

    private TextView text(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
