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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
    private ZoomableImageView image;
    private TextView status,resultText;
    private Spinner model;
    private SeekBar opacity;
    private Button overlayButton,watchButton,referenceButton,perspectiveButton,rectifiedButton;

    @Override public void onCreate(Bundle state){super.onCreate(state);if(!OpenCVLoader.initLocal())Toast.makeText(this,"OpenCV could not start",Toast.LENGTH_LONG).show();setContentView(buildUi());}

    private View buildUi(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(8,17,31));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-1));
        root.addView(text("WATCH ALIGN · STANDALONE",12,Color.rgb(50,213,242)));TextView h1=text("Watch Align Android",28,Color.WHITE);h1.setPadding(0,dp(4),0,0);root.addView(h1);
        root.addView(text("V1.3.0-alpha23 · perspective GMT overlay",14,Color.rgb(158,176,201)));
        root.addView(text("Alpha23 keeps the visual-first perspective template and fixes image inspection gestures so pinch zoom, pan and double-tap zoom work reliably inside the scrolling results screen.",13,Color.rgb(158,176,201)));
        model=new Spinner(this);model.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ModelCatalog.labels()));root.addView(model,lp(-1,dp(54),10));
        Button pick=button("Choose watch photo");pick.setOnClickListener(v->pickWatch());root.addView(pick,lp(-1,dp(52),6));
        Button pickRef=button("Choose genuine reference photos (optional, multi-select)");pickRef.setOnClickListener(v->pickReferences());root.addView(pickRef,lp(-1,dp(52),6));
        Button analyse=button("Build perspective template + diagnostics");analyse.setBackgroundColor(Color.rgb(50,213,242));analyse.setTextColor(Color.rgb(4,32,42));analyse.setOnClickListener(v->analyse());root.addView(analyse,lp(-1,dp(54),12));
        status=text("Choose a watch photo to begin.",14,Color.rgb(158,176,201));root.addView(status);image=new ZoomableImageView(this);image.setAdjustViewBounds(true);root.addView(image,lp(-1,-2,12));
        root.addView(text("Tip: pinch to zoom up to 8×; drag to pan; double-tap toggles fit/2.5×.",12,Color.rgb(158,176,201)));

        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);
        watchButton=smallButton("QC view");perspectiveButton=smallButton("Native template");rectifiedButton=smallButton("Rectified");
        perspectiveButton.setEnabled(false);rectifiedButton.setEnabled(false);
        watchButton.setOnClickListener(v->showAnnotated());perspectiveButton.setOnClickListener(v->showPerspective());rectifiedButton.setOnClickListener(v->showRectified());
        row1.addView(watchButton,new LinearLayout.LayoutParams(0,dp(48),1));row1.addView(perspectiveButton,new LinearLayout.LayoutParams(0,dp(48),1));row1.addView(rectifiedButton,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(row1,lp(-1,dp(48),8));

        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);referenceButton=smallButton("Reference");overlayButton=smallButton("Gen overlay");referenceButton.setEnabled(false);overlayButton.setEnabled(false);referenceButton.setOnClickListener(v->showReference());overlayButton.setOnClickListener(v->showOverlay());row2.addView(referenceButton,new LinearLayout.LayoutParams(0,dp(48),1));row2.addView(overlayButton,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(row2,lp(-1,dp(48),6));

        opacity=new SeekBar(this);opacity.setMax(100);opacity.setProgress(50);opacity.setVisibility(View.GONE);opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){if(lastResult!=null&&lastResult.aligned!=null)image.setImageBitmap(lastResult.overlay(p/100f));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});root.addView(opacity);
        resultText=text("",15,Color.WHITE);resultText.setPadding(0,dp(12),0,dp(32));root.addView(resultText);return scroll;
    }

    private void pickWatch(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_WATCH);}
    private void pickReferences(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK_REFERENCE);}

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
        try{
            if(request==PICK_WATCH&&data.getData()!=null){watchBitmap=readBitmap(data.getData());lastResult=null;image.setImageBitmap(watchBitmap);setResultButtons(false);status.setText("Watch photo ready. Tap Build perspective template.");return;}
            if(request==PICK_REFERENCE){referenceBitmaps.clear();ClipData clip=data.getClipData();if(clip!=null){for(int i=0;i<clip.getItemCount()&&referenceBitmaps.size()<20;i++){Uri u=clip.getItemAt(i).getUri();if(u!=null)referenceBitmaps.add(readBitmap(u));}}else if(data.getData()!=null)referenceBitmaps.add(readBitmap(data.getData()));lastResult=null;setResultButtons(false);status.setText(referenceBitmaps.size()+" genuine reference photo"+(referenceBitmaps.size()==1?"":"s")+" ready.");}
        }catch(Exception e){status.setText("Could not read image: "+e.getMessage());}
    }

    private Bitmap readBitmap(Uri uri)throws Exception{try(InputStream in=getContentResolver().openInputStream(uri)){Bitmap b=BitmapFactory.decodeStream(in);if(b==null)throw new IllegalArgumentException("Not a readable image");int max=Math.max(b.getWidth(),b.getHeight());if(max<=1600)return b.copy(Bitmap.Config.ARGB_8888,false);float s=1600f/max;return Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*s),Math.round(b.getHeight()*s),true).copy(Bitmap.Config.ARGB_8888,false);}}

    private void analyse(){
        if(watchBitmap==null){status.setText("Choose your watch photo first.");return;}ModelCatalog.Profile profile=ModelCatalog.at(model.getSelectedItemPosition());resultText.setText("");opacity.setVisibility(View.GONE);setResultButtons(false);Bitmap watch=watchBitmap;List<Bitmap>manualRefs=new ArrayList<>(referenceBitmaps);
        if(profile.geometryMode==ModelCatalog.GeometryMode.VISUAL_ONLY){status.setText("Running model-specific visual QC checklist…");worker.submit(()->{try{VisualOnlyQc.Result r=VisualOnlyQc.analyse(watch,profile);runOnUiThread(()->{lastResult=null;image.setImageBitmap(r.annotated);resultText.setText(r.report);status.setText("Visual QC checklist ready.");});}catch(Throwable t){runOnUiThread(()->status.setText("QC error: "+t.getMessage()));}});return;}
        status.setText("Solving dial perspective and building canonical template…");
        worker.submit(()->{try{
            List<Bitmap> refs=manualRefs;String sourceNote;
            if(!refs.isEmpty())sourceNote="Genuine diagnostics: "+refs.size()+" manually selected reference photo"+(refs.size()==1?"":"s")+".";
            else if(profile.supportsAutoReference()){OnlineReferenceFinder.PoolResult found=OnlineReferenceFinder.findPool(this,watch,profile.code,8);refs=new ArrayList<>(found.bitmaps);sourceNote="Genuine diagnostics: "+refs.size()+" exact-model official-source image"+(refs.size()==1?"":"s")+(found.fromCacheOnly?" from cache.":" downloaded/cached.");}
            else sourceNote="Genuine diagnostics unavailable automatically for this model.";
            WatchAlignCoreV13.AnalysisResult r=WatchAlignCoreV13.analyse(watch,refs,profile.code);final String note=sourceNote;runOnUiThread(()->{lastResult=r;image.setImageBitmap(r.perspectiveOverlay!=null?r.perspectiveOverlay:r.annotated);resultText.setText(r.report+"\n\n"+note);status.setText(r.perspectiveOverlay!=null?"Perspective template ready. Inspect visually before using diagnostics.":"Analysis complete; perspective template unavailable for this photo.");referenceButton.setEnabled(r.reference!=null);overlayButton.setEnabled(r.aligned!=null);perspectiveButton.setEnabled(r.perspectiveOverlay!=null);rectifiedButton.setEnabled(r.rectified!=null);});
        }catch(Throwable t){runOnUiThread(()->{lastResult=null;setResultButtons(false);status.setText("Reference/analysis error: "+t.getMessage());resultText.setText("Watch Align could not establish reliable geometry from this photo. Try a clearer, more front-on image.");});}});
    }

    private void setResultButtons(boolean enabled){referenceButton.setEnabled(enabled);overlayButton.setEnabled(enabled);perspectiveButton.setEnabled(enabled);rectifiedButton.setEnabled(enabled);opacity.setVisibility(View.GONE);}
    private void showAnnotated(){opacity.setVisibility(View.GONE);if(lastResult!=null)image.setImageBitmap(lastResult.annotated);else if(watchBitmap!=null)image.setImageBitmap(watchBitmap);}
    private void showPerspective(){opacity.setVisibility(View.GONE);if(lastResult!=null&&lastResult.perspectiveOverlay!=null)image.setImageBitmap(lastResult.perspectiveOverlay);}
    private void showRectified(){opacity.setVisibility(View.GONE);if(lastResult!=null&&lastResult.rectified!=null)image.setImageBitmap(lastResult.rectified);}
    private void showReference(){opacity.setVisibility(View.GONE);if(lastResult!=null&&lastResult.reference!=null)image.setImageBitmap(lastResult.reference);}
    private void showOverlay(){if(lastResult!=null&&lastResult.aligned!=null){opacity.setVisibility(View.VISIBLE);image.setImageBitmap(lastResult.overlay(opacity.getProgress()/100f));}}
    private TextView text(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private Button smallButton(String s){Button b=button(s);b.setTextSize(12);return b;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
