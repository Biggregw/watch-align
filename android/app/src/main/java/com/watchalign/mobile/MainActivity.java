package com.watchalign.mobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Bitmap watchBitmap;
    private final List<Bitmap> referenceBitmaps=new ArrayList<>();
    private WatchAlignCoreV13.AnalysisResult lastResult;
    private ImageView preview;
    private TextView status,resultText;
    private Spinner model;
    private Button overlayButton,watchButton,referenceButton,perspectiveButton,rectifiedButton;

    @Override public void onCreate(Bundle state){super.onCreate(state);if(!OpenCVLoader.initLocal())Toast.makeText(this,"OpenCV could not start",Toast.LENGTH_LONG).show();setContentView(buildUi());}

    private View buildUi(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(8,17,31));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-1));
        root.addView(text("WATCH ALIGN · STANDALONE",12,Color.rgb(50,213,242)));TextView h1=text("Watch Align Android",28,Color.WHITE);h1.setPadding(0,dp(4),0,0);root.addView(h1);
        root.addView(text("V1.3.0-alpha41 · perspective ruler + 12 triangle precision",14,Color.rgb(158,176,201)));
        root.addView(text("Alpha41 keeps the four-edge perspective ruler and adds a dedicated 12 o'clock triangle check. After alignment you can magnify the 12 marker, place a green measurement cross on its visual centre and read radial HIGH/LOW offset plus lateral offset relative to the QC ruler target.",13,Color.rgb(158,176,201)));
        model=new Spinner(this);model.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ModelCatalog.labels()));root.addView(model,lp(-1,dp(54),10));
        Button pick=button("Choose watch photo");pick.setOnClickListener(v->pickWatch());root.addView(pick,lp(-1,dp(52),6));
        Button pickRef=button("Choose genuine reference photos (optional)");pickRef.setOnClickListener(v->pickReferences());root.addView(pickRef,lp(-1,dp(52),6));
        Button manual=button("Align ruler");manual.setBackgroundColor(Color.rgb(255,60,60));manual.setTextColor(Color.WHITE);manual.setOnClickListener(v->openManualWorkbench());root.addView(manual,lp(-1,dp(54),12));
        Button analyse=button("Build automatic QC overlay");analyse.setOnClickListener(v->analyse());root.addView(analyse,lp(-1,dp(52),6));
        status=text("Choose a watch photo to begin.",14,Color.rgb(158,176,201));root.addView(status);
        preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(Color.rgb(8,17,31));root.addView(preview,lp(-1,-2,12));
        root.addView(text("Recommended route: Align ruler. For perspective photos place 12, 3, 6 and 9 independently on the same dial edge, fine-nudge and lock them, then use the 12 triangle precision check from the inspection screen.",12,Color.rgb(158,176,201)));

        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);
        watchButton=smallButton("Diagnostics");perspectiveButton=smallButton("Auto template");rectifiedButton=smallButton("Rectified");
        perspectiveButton.setEnabled(false);rectifiedButton.setEnabled(false);watchButton.setEnabled(false);
        watchButton.setOnClickListener(v->{if(lastResult!=null)openInspector("Diagnostics",lastResult.annotated);});
        perspectiveButton.setOnClickListener(v->{if(lastResult!=null&&lastResult.perspectiveOverlay!=null)openOverlayInspector("Automatic QC master",watchBitmap,lastResult.perspectiveOverlay);});
        rectifiedButton.setOnClickListener(v->{if(lastResult!=null)openInspector("Rectified",lastResult.rectified);});
        row1.addView(watchButton,new LinearLayout.LayoutParams(0,dp(48),1));row1.addView(perspectiveButton,new LinearLayout.LayoutParams(0,dp(48),1));row1.addView(rectifiedButton,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(row1,lp(-1,dp(48),8));

        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);
        referenceButton=smallButton("Reference");overlayButton=smallButton("Gen overlay");referenceButton.setEnabled(false);overlayButton.setEnabled(false);
        referenceButton.setOnClickListener(v->{if(lastResult!=null)openInspector("Genuine reference",lastResult.reference);});
        overlayButton.setOnClickListener(v->{if(lastResult!=null&&lastResult.aligned!=null)openInspector("Genuine overlay",lastResult.overlay(0.5f));});
        row2.addView(referenceButton,new LinearLayout.LayoutParams(0,dp(48),1));row2.addView(overlayButton,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(row2,lp(-1,dp(48),6));

        resultText=text("",15,Color.WHITE);resultText.setPadding(0,dp(12),0,dp(32));root.addView(resultText);return scroll;
    }

    private void openManualWorkbench(){if(watchBitmap==null){status.setText("Choose a watch photo first.");return;}ModelCatalog.Profile profile=ModelCatalog.at(model.getSelectedItemPosition());InspectionImageStore.setManual(watchBitmap,profile.code,"Align ruler");startActivity(new Intent(this,ManualAlignActivity.class));}
    private void openInspector(String title,Bitmap bitmap){if(bitmap==null)return;InspectionImageStore.set(bitmap,title);startActivity(new Intent(this,FullscreenInspectActivity.class));}
    private void openOverlayInspector(String title,Bitmap base,Bitmap overlay){if(base==null||overlay==null)return;InspectionImageStore.setOverlay(base,overlay,title);startActivity(new Intent(this,FullscreenInspectActivity.class));}

    private void pickWatch(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_WATCH);}
    private void pickReferences(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK_REFERENCE);}

    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;try{if(request==PICK_WATCH&&data.getData()!=null){watchBitmap=readBitmap(data.getData());lastResult=null;preview.setImageBitmap(watchBitmap);setResultButtons(false);status.setText("Watch photo ready. Open Align ruler.");return;}if(request==PICK_REFERENCE){referenceBitmaps.clear();ClipData clip=data.getClipData();if(clip!=null){for(int i=0;i<clip.getItemCount()&&referenceBitmaps.size()<20;i++){Uri u=clip.getItemAt(i).getUri();if(u!=null)referenceBitmaps.add(readBitmap(u));}}else if(data.getData()!=null)referenceBitmaps.add(readBitmap(data.getData()));lastResult=null;setResultButtons(false);status.setText(referenceBitmaps.size()+" genuine reference photo"+(referenceBitmaps.size()==1?"":"s")+" ready.");}}catch(Exception e){status.setText("Could not read image: "+e.getMessage());}}

    private Bitmap readBitmap(Uri uri)throws Exception{try(InputStream in=getContentResolver().openInputStream(uri)){Bitmap b=BitmapFactory.decodeStream(in);if(b==null)throw new IllegalArgumentException("Not a readable image");int max=Math.max(b.getWidth(),b.getHeight());if(max<=1600)return b.copy(Bitmap.Config.ARGB_8888,false);float s=1600f/max;return Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*s),Math.round(b.getHeight()*s),true).copy(Bitmap.Config.ARGB_8888,false);}}

    private void analyse(){if(watchBitmap==null){status.setText("Choose your watch photo first.");return;}ModelCatalog.Profile profile=ModelCatalog.at(model.getSelectedItemPosition());resultText.setText("");setResultButtons(false);Bitmap watch=watchBitmap;List<Bitmap>refs=new ArrayList<>(referenceBitmaps);if(profile.geometryMode==ModelCatalog.GeometryMode.VISUAL_ONLY){status.setText("Running model-specific visual QC checklist…");worker.submit(()->{try{VisualOnlyQc.Result r=VisualOnlyQc.analyse(watch,profile);runOnUiThread(()->{lastResult=null;preview.setImageBitmap(r.annotated);resultText.setText(r.report);status.setText("Visual QC checklist ready.");});}catch(Throwable t){runOnUiThread(()->status.setText("QC error: "+t.getMessage()));}});return;}status.setText("Trying automatic pose solve…");worker.submit(()->{try{String sourceNote=refs.isEmpty()?"No genuine reference selected.":"Genuine comparison: "+refs.size()+" manually selected reference photo"+(refs.size()==1?"":"s")+".";WatchAlignCoreV13.AnalysisResult r=WatchAlignCoreV13.analyse(watch,refs,profile.code);final String note=sourceNote;runOnUiThread(()->{lastResult=r;preview.setImageBitmap(r.perspectiveOverlay!=null?r.perspectiveOverlay:r.annotated);resultText.setText(r.report+"\n\n"+note);status.setText(r.perspectiveOverlay!=null?"Automatic template ready. Manual alignment remains recommended.":"Automatic pose unavailable. Use Align ruler instead.");watchButton.setEnabled(r.annotated!=null);referenceButton.setEnabled(r.reference!=null);overlayButton.setEnabled(r.aligned!=null);perspectiveButton.setEnabled(r.perspectiveOverlay!=null);rectifiedButton.setEnabled(r.rectified!=null);});}catch(Throwable t){runOnUiThread(()->{lastResult=null;setResultButtons(false);status.setText("Automatic analysis failed. Use Align ruler.");resultText.setText("Manual alignment is independent of automatic dial detection.");});}});}

    private void setResultButtons(boolean enabled){watchButton.setEnabled(enabled);referenceButton.setEnabled(enabled);overlayButton.setEnabled(enabled);perspectiveButton.setEnabled(enabled);rectifiedButton.setEnabled(enabled);}
    private TextView text(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private Button smallButton(String s){Button b=button(s);b.setTextSize(12);return b;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
