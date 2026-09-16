package com.watchalign.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_WATCH=1001;
    private static final int PICK_REFERENCE=1002;
    private static final int PICK_SEED=1003;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Bitmap watchBitmap;
    private final List<Bitmap> referenceBitmaps=new ArrayList<>();
    private WatchAlignCoreV13.AnalysisResult lastResult;
    private ImageView preview;
    private TextView status,resultText;
    private Spinner model;
    private Button overlayButton,watchButton,referenceButton,perspectiveButton,rectifiedButton,exportButton,qcButton;

    @Override public void onCreate(Bundle state){super.onCreate(state);if(!OpenCVLoader.initLocal())Toast.makeText(this,"OpenCV could not start",Toast.LENGTH_LONG).show();setContentView(buildUi());}

    private View buildUi(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(8,17,31));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-1));
        root.addView(text("WATCH ALIGN · STANDALONE",12,Color.rgb(50,213,242)));TextView h1=text("Watch Align Android",28,Color.WHITE);h1.setPadding(0,dp(4),0,0);root.addView(h1);
        root.addView(text("V1.3.0-alpha30 · precision dial-edge alignment & visible QC results",14,Color.rgb(158,176,201)));
        root.addView(text("Start with automatic pose. If needed, use precision alignment on the dial edge at 12 and 6; the app calculates the centre and preserves the fitted perspective ellipse.",13,Color.rgb(158,176,201)));
        model=new Spinner(this);model.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ModelCatalog.labels()));root.addView(model,lp(-1,dp(54),10));
        Button pick=button("Choose watch photo");pick.setOnClickListener(v->pickWatch());root.addView(pick,lp(-1,dp(52),6));
        Button pickRef=button("Choose genuine reference photos (optional)");pickRef.setOnClickListener(v->pickReferences());root.addView(pickRef,lp(-1,dp(52),6));
        Button analyse=button("Build visual QC overlay");analyse.setBackgroundColor(Color.rgb(50,213,242));analyse.setTextColor(Color.rgb(4,32,42));analyse.setOnClickListener(v->analyse());root.addView(analyse,lp(-1,dp(54),12));
        Button seedBtn=button("Precision align dial edge (optional)");seedBtn.setOnClickListener(v->openManualSeedPicker());root.addView(seedBtn,lp(-1,dp(50),6));
        qcButton=button("View QC checks");qcButton.setEnabled(false);qcButton.setOnClickListener(v->showQcReport());root.addView(qcButton,lp(-1,dp(50),6));
        status=text("Choose a watch photo to begin.",14,Color.rgb(158,176,201));root.addView(status);
        preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(Color.rgb(8,17,31));root.addView(preview,lp(-1,-2,12));
        root.addView(text("Native template is the primary visual QC view. The QC checks button opens the automated findings separately so they cannot disappear below the image.",12,Color.rgb(158,176,201)));

        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);
        watchButton=smallButton("Diagnostics");perspectiveButton=smallButton("Native template");rectifiedButton=smallButton("Rectified");
        perspectiveButton.setEnabled(false);rectifiedButton.setEnabled(false);watchButton.setEnabled(false);
        watchButton.setOnClickListener(v->{if(lastResult!=null)openInspector("Diagnostics",lastResult.annotated);});
        perspectiveButton.setOnClickListener(v->{if(lastResult!=null&&lastResult.perspectiveOverlay!=null)openOverlayInspector("Visual QC master",watchBitmap,lastResult.perspectiveOverlay);});
        rectifiedButton.setOnClickListener(v->{if(lastResult!=null)openInspector("Rectified",lastResult.rectified);});
        row1.addView(watchButton,new LinearLayout.LayoutParams(0,dp(48),1));row1.addView(perspectiveButton,new LinearLayout.LayoutParams(0,dp(48),1));row1.addView(rectifiedButton,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(row1,lp(-1,dp(48),8));

        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);
        referenceButton=smallButton("Reference");overlayButton=smallButton("Gen overlay");exportButton=smallButton("Export QC card");
        referenceButton.setEnabled(false);overlayButton.setEnabled(false);exportButton.setEnabled(false);
        referenceButton.setOnClickListener(v->{if(lastResult!=null)openInspector("Genuine reference",lastResult.reference);});
        overlayButton.setOnClickListener(v->{if(lastResult!=null&&lastResult.aligned!=null)openInspector("Genuine overlay",lastResult.overlay(0.5f));});
        exportButton.setOnClickListener(v->exportQcCard());
        row2.addView(referenceButton,new LinearLayout.LayoutParams(0,dp(48),1));row2.addView(overlayButton,new LinearLayout.LayoutParams(0,dp(48),1));row2.addView(exportButton,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(row2,lp(-1,dp(48),6));

        resultText=text("",15,Color.WHITE);resultText.setPadding(0,dp(12),0,dp(32));root.addView(resultText);return scroll;
    }

    private void exportQcCard(){
        if(watchBitmap==null||lastResult==null)return;
        Bitmap card=renderQcCard(watchBitmap,lastResult);
        if(card!=null){
            InspectionImageStore.set(card,"QC Summary Card");
            startActivity(new Intent(this,FullscreenInspectActivity.class));
            Toast.makeText(this,"QC Card generated! Open to view and save.",Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap renderQcCard(Bitmap watch,WatchAlignCoreV13.AnalysisResult result){
        Bitmap display=result.perspectiveOverlay!=null?composeOverlay(watch,result.perspectiveOverlay):result.annotated;
        int w=1080,topH=1080,botH=600;
        Bitmap card=Bitmap.createBitmap(w,topH+botH,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(card);c.drawColor(Color.rgb(8,17,31));
        if(display!=null){
            android.graphics.Rect src=new android.graphics.Rect(0,0,display.getWidth(),display.getHeight());
            android.graphics.Rect dst=new android.graphics.Rect(0,0,w,topH);
            c.drawBitmap(display,src,dst,new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG|android.graphics.Paint.FILTER_BITMAP_FLAG));
        }
        android.graphics.Paint pText=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);pText.setColor(Color.WHITE);pText.setTextSize(26f);
        android.graphics.Paint pHeader=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);pHeader.setColor(Color.rgb(50,213,242));pHeader.setTextSize(32f);pHeader.setFakeBoldText(true);
        c.drawText("WATCH ALIGN · QC REPORT CARD",40,topH+50,pHeader);
        String[] lines=result.report.split("\n");int y=topH+100;
        for(String line:lines){if(line.trim().isEmpty())continue;if(y>topH+botH-30)break;c.drawText(line,40,y,pText);y+=34;}
        return card;
    }

    private Bitmap composeOverlay(Bitmap base,Bitmap overlay){
        if(base==null)return overlay;
        if(overlay==null)return base;
        Bitmap out=Bitmap.createBitmap(base.getWidth(),base.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG|android.graphics.Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(base,0,0,p);c.drawBitmap(overlay,0,0,p);return out;
    }

    private void showQcReport(){
        if(lastResult==null)return;
        ScrollView scroller=new ScrollView(this);
        TextView body=text(lastResult.report,14,Color.WHITE);body.setTextIsSelectable(true);body.setPadding(dp(18),dp(12),dp(18),dp(18));
        scroller.setBackgroundColor(Color.rgb(8,17,31));scroller.addView(body);
        new AlertDialog.Builder(this).setTitle("Watch Align QC checks").setView(scroller).setPositiveButton("Close",null).show();
    }

    private void openInspector(String title,Bitmap bitmap){if(bitmap==null)return;InspectionImageStore.set(bitmap,title);startActivity(new Intent(this,FullscreenInspectActivity.class));}
    private void openOverlayInspector(String title,Bitmap base,Bitmap overlay){if(base==null||overlay==null)return;InspectionImageStore.setOverlay(base,overlay,title);startActivity(new Intent(this,FullscreenInspectActivity.class));}

    private void pickWatch(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_WATCH);}
    private void pickReferences(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK_REFERENCE);}
    private void openManualSeedPicker(){if(watchBitmap==null){status.setText("Choose a watch photo first.");return;}InspectionImageStore.baseBitmap=watchBitmap;startActivityForResult(new Intent(this,ManualSeedActivity.class),PICK_SEED);}

    @Override protected void onActivityResult(int request,int result,Intent data){
        if(request==PICK_SEED&&result==RESULT_OK&&InspectionImageStore.hasManualSeed){status.setText("Precision alignment set. Running QC…");analyse();return;}
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
        try{
            if(request==PICK_WATCH&&data.getData()!=null){InspectionImageStore.clearManualSeed();watchBitmap=readBitmap(data.getData());lastResult=null;preview.setImageBitmap(watchBitmap);setResultButtons(false);status.setText("Watch photo ready. Tap Build visual QC overlay.");return;}
            if(request==PICK_REFERENCE){referenceBitmaps.clear();ClipData clip=data.getClipData();if(clip!=null){for(int i=0;i<clip.getItemCount()&&referenceBitmaps.size()<20;i++){Uri u=clip.getItemAt(i).getUri();if(u!=null)referenceBitmaps.add(readBitmap(u));}}else if(data.getData()!=null)referenceBitmaps.add(readBitmap(data.getData()));lastResult=null;setResultButtons(false);status.setText(referenceBitmaps.size()+" genuine reference photo"+(referenceBitmaps.size()==1?"":"s")+" ready.");}
        }catch(Exception e){status.setText("Could not read image: "+e.getMessage());}
    }

    private Bitmap readBitmap(Uri uri)throws Exception{
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,opts);}
        if(opts.outWidth<=0||opts.outHeight<=0)throw new IllegalArgumentException("Not a readable image");
        int maxDim=Math.max(opts.outWidth,opts.outHeight),sampleSize=1;while(maxDim/(sampleSize*2)>=1600)sampleSize*=2;
        opts.inJustDecodeBounds=false;opts.inSampleSize=sampleSize;opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap b;try(InputStream in=getContentResolver().openInputStream(uri)){b=BitmapFactory.decodeStream(in,null,opts);}
        if(b==null)throw new IllegalArgumentException("Not a readable image");int currentMax=Math.max(b.getWidth(),b.getHeight());if(currentMax<=1600)return b.copy(Bitmap.Config.ARGB_8888,false);
        float scale=1600f/currentMax;return Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*scale),Math.round(b.getHeight()*scale),true).copy(Bitmap.Config.ARGB_8888,false);
    }

    private void analyse(){
        if(watchBitmap==null){status.setText("Choose your watch photo first.");return;}ModelCatalog.Profile profile=ModelCatalog.at(model.getSelectedItemPosition());resultText.setText("");setResultButtons(false);Bitmap watch=watchBitmap;List<Bitmap>refs=new ArrayList<>(referenceBitmaps);
        if(profile.geometryMode==ModelCatalog.GeometryMode.VISUAL_ONLY){status.setText("Running model-specific visual QC checklist…");worker.submit(()->{try{VisualOnlyQc.Result r=VisualOnlyQc.analyse(watch,profile);runOnUiThread(()->{lastResult=null;preview.setImageBitmap(r.annotated);resultText.setText(r.report);status.setText("Visual QC checklist ready.");});}catch(Throwable t){runOnUiThread(()->status.setText("QC error: "+t.getMessage()));}});return;}
        status.setText("Solving photo pose, projecting master and running QC checks…");
        worker.submit(()->{try{
            String sourceNote=refs.isEmpty()?"No genuine reference selected. The fixed 126710BLNR visual master does not require one.":"Genuine comparison: "+refs.size()+" manually selected reference photo"+(refs.size()==1?"":"s")+".";
            WatchAlignCoreV13.AnalysisResult r=WatchAlignCoreV13.analyse(watch,refs,profile.code);final String note=sourceNote;runOnUiThread(()->{lastResult=r;preview.setImageBitmap(r.perspectiveOverlay!=null?composeOverlay(watchBitmap,r.perspectiveOverlay):r.annotated);resultText.setText(r.report+"\n\n"+note);qcButton.setEnabled(true);status.setText(r.perspectiveOverlay!=null?"Overlay ready and QC checks complete. Open Native template or View QC checks.":"QC checks complete, but the perspective template is unavailable for this photo.");watchButton.setEnabled(r.annotated!=null);referenceButton.setEnabled(r.reference!=null);overlayButton.setEnabled(r.aligned!=null);perspectiveButton.setEnabled(r.perspectiveOverlay!=null);rectifiedButton.setEnabled(r.rectified!=null);exportButton.setEnabled(true);});
        }catch(Throwable t){runOnUiThread(()->{lastResult=null;setResultButtons(false);status.setText("Analysis error: "+t.getMessage());resultText.setText("Watch Align could not establish reliable geometry from this photo. Try a clearer image with the full dial visible.");});}});
    }

    private void setResultButtons(boolean enabled){watchButton.setEnabled(enabled);referenceButton.setEnabled(enabled);overlayButton.setEnabled(enabled);perspectiveButton.setEnabled(enabled);rectifiedButton.setEnabled(enabled);exportButton.setEnabled(enabled);if(qcButton!=null)qcButton.setEnabled(enabled&&lastResult!=null);}
    private TextView text(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private Button smallButton(String s){Button b=button(s);b.setTextSize(12);return b;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
