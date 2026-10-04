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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One-photo automatic bright-outline perspective proof. */
public class PerspectiveOverlayPocActivity extends Activity {
    private static final int PICK_CANDIDATE=2301;
    private static final int BG=Color.rgb(8,17,31),ACCENT=Color.rgb(50,213,242),MUTED=Color.rgb(158,176,201);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Bitmap candidateBitmap,lastOverlay;
    private ImageView preview;
    private TextView status;
    private Button buildButton,inspectButton;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!OpenCVLoader.initLocal())Toast.makeText(this,"OpenCV could not start",Toast.LENGTH_LONG).show();
        setContentView(buildUi());
    }

    private View buildUi(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-1));
        root.addView(text("Opposing-minute Perspective Overlay",28,Color.WHITE));
        root.addView(text("Candidate photo only · explicit minute-pair fit · no nudge",14,ACCENT));
        root.addView(text("The dial edge provides only an initial search pose. The app then detects the inner ends of the 48 minor minute ticks individually, admits them only as complete opposing pairs, and robustly fits the projective transform from those measured correspondences. Hour markers and the 12 triangle do not participate in the fit.",13,MUTED),lp(-1,-2,10));

        Button pick=button("Choose candidate GMT photo");pick.setOnClickListener(v->pickPhoto());root.addView(pick,lp(-1,dp(52),8));
        buildButton=button("Build opposing-minute outline");buildButton.setBackgroundColor(ACCENT);buildButton.setTextColor(Color.rgb(4,32,42));buildButton.setEnabled(false);buildButton.setOnClickListener(v->buildOverlay());root.addView(buildButton,lp(-1,dp(54),6));
        inspectButton=button("Inspect last outline");inspectButton.setEnabled(false);inspectButton.setOnClickListener(v->openInspector());root.addView(inspectButton,lp(-1,dp(48),6));
        status=text("Choose a sharp GMT photo with the complete black dial visible.",14,MUTED);root.addView(status,lp(-1,-2,12));
        preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(BG);root.addView(preview,lp(-1,-2,8));
        root.addView(text("The inspector has opacity and hold-to-blink only. There is no manual movement of the overlay. If the independent hour-marker/triangle outlines do not align, the minute-pair perspective fit has failed.",12,MUTED),lp(-1,-2,10));
        return scroll;
    }

    private void pickPhoto(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_CANDIDATE);}

    private void buildOverlay(){
        if(candidateBitmap==null)return;final Bitmap photo=candidateBitmap;
        buildButton.setEnabled(false);inspectButton.setEnabled(false);status.setText("Detecting minor minute ticks and fitting opposing pairs…");
        worker.submit(()->{
            AutomaticDialOverlay.Result q=AutomaticDialOverlay.build(photo);
            runOnUiThread(()->{
                buildButton.setEnabled(true);
                if(!q.valid){lastOverlay=null;inspectButton.setEnabled(false);status.setText("Automatic fit failed: "+q.reason);return;}
                lastOverlay=q.overlay;inspectButton.setEnabled(true);
                String projective=q.projectiveAccepted?"opposing-minute homography accepted":"ellipse seed used; opposing-minute fit rejected";
                String phase=q.twelvePhaseUsed?"automatic local-12 phase":"upright-photo phase fallback";
                String residual=(Double.isFinite(q.fitBefore)&&Double.isFinite(q.fitAfter))?String.format(Locale.US," · RMS %.2f→%.2f px",q.fitBefore,q.fitAfter):"";
                status.setText(String.format(Locale.US,"Outline ready · %d minor ticks · %d complete opposite pairs · %d robust inliers%s · %s · %s.",q.detectedTicks,q.completePairs,q.inliers,residual,projective,phase));
                openInspector();
            });
        });
    }

    private void openInspector(){
        if(candidateBitmap==null||lastOverlay==null)return;
        InspectionImageStore.setOverlay(candidateBitmap,lastOverlay,"Opposing-minute perspective outline");
        startActivity(new Intent(this,PhotographicOverlayInspectActivity.class));
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=PICK_CANDIDATE||result!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            candidateBitmap=readBitmap(data.getData());lastOverlay=null;preview.setImageBitmap(candidateBitmap);buildButton.setEnabled(true);inspectButton.setEnabled(false);status.setText("Photo ready. Tap Build opposing-minute outline.");
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
