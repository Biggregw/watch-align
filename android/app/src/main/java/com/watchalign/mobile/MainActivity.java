package com.watchalign.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One screen: pick a photo, check it, read the summary, inspect the overlay.
 *
 * Only models with the verified GMT pipeline (CanonicalGmtGeometryAnalyzer) are
 * offered. The older reference-photo comparison, diagnostics and rectified views
 * are not exposed; their code paths remain for research use.
 */
public class MainActivity extends Activity {
    private static final int PICK_WATCH=1001;
    private static final int PICK_SEED=1003;
    private static final int BG=Color.rgb(8,17,31), ACCENT=Color.rgb(50,213,242), MUTED=Color.rgb(158,176,201);

    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<ModelCatalog.Profile> models=new ArrayList<>();
    private Bitmap watchBitmap;
    private WatchAlignCoreV13.AnalysisResult lastResult;
    private ImageView preview;
    private TextView status,summaryText;
    private Spinner model;
    private Button checkButton,resultsButton,inspectButton,exportButton,manualButton;

    @Override public void onCreate(Bundle state){super.onCreate(state);if(!OpenCVLoader.initLocal())Toast.makeText(this,"OpenCV could not start",Toast.LENGTH_LONG).show();setContentView(buildUi());}

    private View buildUi(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-1));
        TextView h1=text("Watch Align",28,Color.WHITE);root.addView(h1);
        root.addView(text("GMT-Master II dial check · "+WatchAlignCoreV13.CORE_VERSION,13,MUTED));
        root.addView(text("Use a sharp, straight-on photo with the whole dial in frame and the hands away from 12.",13,MUTED));

        for(ModelCatalog.Profile p:ModelCatalog.all())if(CanonicalGmtGeometryAnalyzer.supports(p.code))models.add(p);
        List<String> labels=new ArrayList<>();for(ModelCatalog.Profile p:models)labels.add(p.label);
        model=new Spinner(this);model.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));root.addView(model,lp(-1,dp(54),10));

        Button pick=button("Choose watch photo");pick.setOnClickListener(v->pickWatch());root.addView(pick,lp(-1,dp(52),6));
        checkButton=button("Check watch");checkButton.setBackgroundColor(ACCENT);checkButton.setTextColor(Color.rgb(4,32,42));
        checkButton.setOnClickListener(v->analyse());checkButton.setEnabled(false);root.addView(checkButton,lp(-1,dp(54),10));

        status=text("Choose a watch photo to begin.",14,MUTED);root.addView(status,lp(-1,-2,10));
        preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(BG);root.addView(preview,lp(-1,-2,10));

        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        inspectButton=smallButton("Inspect overlay");resultsButton=smallButton("Full results");exportButton=smallButton("Export card");
        inspectButton.setOnClickListener(v->{if(lastResult!=null&&lastResult.perspectiveOverlay!=null)openOverlayInspector("Overlay · hold to blink",watchBitmap,lastResult.perspectiveOverlay);});
        resultsButton.setOnClickListener(v->showQcReport());
        exportButton.setOnClickListener(v->exportQcCard());
        row.addView(inspectButton,new LinearLayout.LayoutParams(0,dp(48),1));row.addView(resultsButton,new LinearLayout.LayoutParams(0,dp(48),1));row.addView(exportButton,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(row,lp(-1,dp(48),8));

        summaryText=text("",15,Color.WHITE);summaryText.setTextIsSelectable(true);root.addView(summaryText,lp(-1,-2,12));

        // Manual fallback, offered only when the automatic dial fit did not work.
        manualButton=button("Align dial edge by hand");manualButton.setOnClickListener(v->openManualSeedPicker());manualButton.setVisibility(View.GONE);
        root.addView(manualButton,lp(-1,dp(50),10));

        View spacer=new View(this);root.addView(spacer,lp(-1,dp(32),0));
        setResultButtons(false);
        return scroll;
    }

    /** The plain-English block between SUMMARY and DETAILS, or null. */
    static String summaryOf(String report){
        if(report==null)return null;
        int a=report.indexOf("SUMMARY\n");if(a<0)return null;
        int b=report.indexOf("\n\nDETAILS",a);
        return (b<0?report.substring(a+8):report.substring(a+8,b)).trim();
    }

    private void exportQcCard(){
        if(watchBitmap==null||lastResult==null)return;
        Bitmap card=renderQcCard(watchBitmap,lastResult);
        if(card!=null){
            InspectionImageStore.set(card,"QC card");
            startActivity(new Intent(this,FullscreenInspectActivity.class));
            Toast.makeText(this,"QC card ready. Screenshot or share it from here.",Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap renderQcCard(Bitmap watch,WatchAlignCoreV13.AnalysisResult result){
        Bitmap display=result.perspectiveOverlay!=null?composeOverlay(watch,result.perspectiveOverlay):result.annotated;
        int w=1080,margin=40,imgH=0;
        if(display!=null)imgH=Math.round(w*(display.getHeight()/(float)display.getWidth()));
        imgH=Math.min(imgH,1440);
        Paint pText=new Paint(Paint.ANTI_ALIAS_FLAG);pText.setColor(Color.WHITE);pText.setTextSize(30f);
        Paint pHeader=new Paint(Paint.ANTI_ALIAS_FLAG);pHeader.setColor(ACCENT);pHeader.setTextSize(36f);pHeader.setFakeBoldText(true);
        Paint pMuted=new Paint(Paint.ANTI_ALIAS_FLAG);pMuted.setColor(MUTED);pMuted.setTextSize(24f);
        String summary=summaryOf(result.report);
        if(summary==null)summary="Summary unavailable for this photo.";
        List<String> lines=new ArrayList<>();
        for(String para:summary.split("\n")){if(para.trim().isEmpty()){lines.add("");continue;}lines.addAll(wrap(para,pText,w-2*margin));}
        int lineH=40,textH=90+lines.size()*lineH+70;
        Bitmap card=Bitmap.createBitmap(w,imgH+textH,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(card);c.drawColor(BG);
        if(display!=null){
            android.graphics.Rect src=new android.graphics.Rect(0,0,display.getWidth(),Math.min(display.getHeight(),Math.round(display.getWidth()*(imgH/(float)w))));
            android.graphics.Rect dst=new android.graphics.Rect(0,0,w,imgH);
            c.drawBitmap(display,src,dst,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        }
        int y=imgH+60;
        c.drawText("WATCH ALIGN · QC CARD",margin,y,pHeader);y+=50;
        for(String l:lines){if(!l.isEmpty())c.drawText(l,margin,y,pText);y+=lineH;}
        c.drawText(WatchAlignCoreV13.CORE_VERSION,margin,y+20,pMuted);
        return card;
    }

    /** Greedy word wrap to a pixel width. */
    static List<String> wrap(String s,Paint p,float width){
        List<String> out=new ArrayList<>();StringBuilder line=new StringBuilder();
        for(String word:s.split(" ")){
            String next=line.length()==0?word:line+" "+word;
            if(p.measureText(next)<=width||line.length()==0){line.setLength(0);line.append(next);}
            else{out.add(line.toString());line.setLength(0);line.append(word);}
        }
        if(line.length()>0)out.add(line.toString());
        return out;
    }

    private Bitmap composeOverlay(Bitmap base,Bitmap overlay){
        if(base==null)return overlay;
        if(overlay==null)return base;
        Bitmap out=Bitmap.createBitmap(base.getWidth(),base.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(base,0,0,p);c.drawBitmap(overlay,0,0,p);return out;
    }

    private void showQcReport(){
        if(lastResult==null)return;
        ScrollView scroller=new ScrollView(this);
        TextView body=text(lastResult.report,14,Color.WHITE);body.setTextIsSelectable(true);body.setPadding(dp(18),dp(12),dp(18),dp(18));
        scroller.setBackgroundColor(BG);scroller.addView(body);
        new AlertDialog.Builder(this).setTitle("Full results").setView(scroller).setPositiveButton("Close",null).show();
    }

    private void openOverlayInspector(String title,Bitmap base,Bitmap overlay){if(base==null||overlay==null)return;InspectionImageStore.setOverlay(base,overlay,title);startActivity(new Intent(this,FullscreenInspectActivity.class));}

    private void pickWatch(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_WATCH);}
    private void openManualSeedPicker(){if(watchBitmap==null){status.setText("Choose a watch photo first.");return;}InspectionImageStore.baseBitmap=watchBitmap;startActivityForResult(new Intent(this,ManualSeedActivity.class),PICK_SEED);}

    @Override protected void onActivityResult(int request,int result,Intent data){
        if(request==PICK_SEED&&result==RESULT_OK&&InspectionImageStore.hasManualSeed){status.setText("Hand alignment set. Checking again…");analyse();return;}
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
        try{
            if(request==PICK_WATCH&&data.getData()!=null){
                InspectionImageStore.clearManualSeed();watchBitmap=readBitmap(data.getData());lastResult=null;
                preview.setImageBitmap(watchBitmap);summaryText.setText("");setResultButtons(false);manualButton.setVisibility(View.GONE);
                checkButton.setEnabled(true);status.setText("Photo ready. Tap Check watch.");
            }
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
        if(watchBitmap==null){status.setText("Choose your watch photo first.");return;}
        if(models.isEmpty()){status.setText("No supported model is available.");return;}
        ModelCatalog.Profile profile=models.get(Math.max(0,model.getSelectedItemPosition()));
        summaryText.setText("");setResultButtons(false);checkButton.setEnabled(false);Bitmap watch=watchBitmap;
        status.setText("Checking… fitting the dial, drawing the template and measuring the 12 marker.");
        worker.submit(()->{try{
            WatchAlignCoreV13.AnalysisResult r=WatchAlignCoreV13.analyse(watch,Collections.<Bitmap>emptyList(),profile.code);
            runOnUiThread(()->{
                lastResult=r;checkButton.setEnabled(true);
                preview.setImageBitmap(r.perspectiveOverlay!=null?composeOverlay(watchBitmap,r.perspectiveOverlay):watchBitmap);
                String sum=summaryOf(r.report);
                summaryText.setText(sum!=null?sum:"Summary unavailable. Open Full results.");
                boolean autoFailed=r.perspectiveOverlay==null||r.report.contains("Dial centre: UNREFINED");
                boolean manual=InspectionImageStore.hasManualSeed;
                manualButton.setVisibility(autoFailed||manual?View.VISIBLE:View.GONE);
                status.setText(r.perspectiveOverlay!=null
                        ?(autoFailed&&!manual?"Done, but the dial edge could not be fitted automatically. Try Align dial edge by hand below.":"Done. Red outlines show where a genuine dial's markers should be.")
                        :"Done, but no template could be drawn for this photo. Try Align dial edge by hand below, or a clearer photo.");
                resultsButton.setEnabled(true);exportButton.setEnabled(true);inspectButton.setEnabled(r.perspectiveOverlay!=null);
            });
        }catch(Throwable t){runOnUiThread(()->{lastResult=null;setResultButtons(false);checkButton.setEnabled(true);manualButton.setVisibility(View.VISIBLE);
            status.setText("Could not check this photo: "+t.getMessage());
            summaryText.setText("Watch Align could not find the dial reliably. Try a clearer, straight-on photo with the full dial visible, or align the dial edge by hand.");});}});
    }

    private void setResultButtons(boolean enabled){inspectButton.setEnabled(enabled);resultsButton.setEnabled(enabled);exportButton.setEnabled(enabled);}
    private TextView text(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private Button smallButton(String s){Button b=button(s);b.setTextSize(13);return b;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
