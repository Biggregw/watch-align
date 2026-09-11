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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_WATCH = 1001;
    private static final int PICK_REFERENCE = 1002;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Bitmap watchBitmap;
    private Bitmap referenceBitmap;
    private WatchAlignCoreV13.AnalysisResult lastResult;
    private ImageView image;
    private TextView status;
    private TextView resultText;
    private Spinner model;
    private SeekBar opacity;
    private Button overlayButton;
    private Button watchButton;
    private Button referenceButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!OpenCVLoader.initLocal()) Toast.makeText(this, "OpenCV could not start", Toast.LENGTH_LONG).show();
        setContentView(buildUi());
    }

    private View buildUi() {
        int pad = dp(16);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.rgb(8,17,31));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(pad,pad,pad,pad);
        scroll.addView(root, new ViewGroup.LayoutParams(-1,-1));

        root.addView(text("WATCH ALIGN · STANDALONE", 12, Color.rgb(50,213,242)));
        TextView h1 = text("Watch Align Android", 28, Color.WHITE); h1.setPadding(0,dp(4),0,0); root.addView(h1);
        root.addView(text("V1.3.0-alpha14 · analysis runs on this device", 14, Color.rgb(158,176,201)));
        root.addView(text("No hosted backend. Exact-model reference discovery is online; watch analysis and geometry registration run locally.", 13, Color.rgb(158,176,201)));

        model = new Spinner(this);
        String[] models = {"126710BLNR · GMT-Master II", "124060 · Submariner No-Date"};
        model.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, models));
        root.addView(model, lp(-1,dp(54),10));

        Button pick = button("Choose watch photo"); pick.setOnClickListener(v -> pickImage(PICK_WATCH)); root.addView(pick, lp(-1,dp(52),6));
        Button pickRef = button("Choose your own genuine/reference photo (optional)"); pickRef.setOnClickListener(v -> pickImage(PICK_REFERENCE)); root.addView(pickRef, lp(-1,dp(52),6));
        Button analyse = button("Analyse + find best reference"); analyse.setBackgroundColor(Color.rgb(50,213,242)); analyse.setTextColor(Color.rgb(4,32,42)); analyse.setOnClickListener(v -> analyse()); root.addView(analyse, lp(-1,dp(54),12));

        status = text("Choose a watch photo to begin.", 14, Color.rgb(158,176,201)); root.addView(status);
        image = new ImageView(this); image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.FIT_CENTER); root.addView(image, lp(-1,-2,12));

        LinearLayout viewButtons = new LinearLayout(this); viewButtons.setOrientation(LinearLayout.HORIZONTAL);
        watchButton = smallButton("QC view"); referenceButton = smallButton("Reference"); overlayButton = smallButton("Overlay");
        referenceButton.setEnabled(false); overlayButton.setEnabled(false);
        watchButton.setOnClickListener(v -> showAnnotated()); referenceButton.setOnClickListener(v -> showReference()); overlayButton.setOnClickListener(v -> showOverlay());
        viewButtons.addView(watchButton,new LinearLayout.LayoutParams(0,dp(48),1)); viewButtons.addView(referenceButton,new LinearLayout.LayoutParams(0,dp(48),1)); viewButtons.addView(overlayButton,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(viewButtons, lp(-1,dp(48),8));

        opacity = new SeekBar(this); opacity.setMax(100); opacity.setProgress(50); opacity.setVisibility(View.GONE);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){if(lastResult!=null&&lastResult.overlayReady)showOverlay();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        root.addView(opacity, lp(-1,dp(42),0));

        resultText = text("", 14, Color.WHITE); resultText.setPadding(0,dp(8),0,dp(24)); root.addView(resultText);
        return scroll;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        Bitmap b=load(data.getData());
        if(b==null){Toast.makeText(this,"Could not read image",Toast.LENGTH_LONG).show();return;}
        if(requestCode==PICK_WATCH){watchBitmap=b;image.setImageBitmap(b);status.setText("Watch photo selected.");}
        else if(requestCode==PICK_REFERENCE){referenceBitmap=b;status.setText("Reference photo selected.");}
        lastResult=null;referenceButton.setEnabled(false);overlayButton.setEnabled(false);opacity.setVisibility(View.GONE);resultText.setText("");
    }

    private void analyse() {
        if(watchBitmap==null){Toast.makeText(this,"Choose a watch photo first",Toast.LENGTH_SHORT).show();return;}
        status.setText("Analysing…");
        final Bitmap watch=watchBitmap, supplied=referenceBitmap;
        final String modelRef=model.getSelectedItemPosition()==0?"126710BLNR":"124060";
        worker.submit(() -> {
            try {
                WatchAlignCoreV13.AnalysisResult r=WatchAlignCoreV13.analyse(getApplicationContext(),watch,supplied,modelRef);
                runOnUiThread(() -> {
                    lastResult=r; image.setImageBitmap(r.annotated); resultText.setText(r.report); status.setText("Analysis complete.");
                    referenceButton.setEnabled(r.reference!=null); overlayButton.setEnabled(r.overlayReady); opacity.setVisibility(r.overlayReady?View.VISIBLE:View.GONE);
                });
            } catch(Throwable e) {
                runOnUiThread(() -> {status.setText("Reference/analysis error: "+e.getMessage()); resultText.setText("");});
            }
        });
    }

    private void showAnnotated(){if(lastResult!=null)image.setImageBitmap(lastResult.annotated);else if(watchBitmap!=null)image.setImageBitmap(watchBitmap);opacity.setVisibility(View.GONE);}
    private void showReference(){if(lastResult!=null&&lastResult.reference!=null)image.setImageBitmap(lastResult.reference);opacity.setVisibility(View.GONE);}
    private void showOverlay(){if(lastResult!=null&&lastResult.overlayReady&&lastResult.reference!=null){image.setImageBitmap(WatchAlignCoreV13.blend(lastResult.watchAligned,lastResult.reference,opacity.getProgress()/100f));opacity.setVisibility(View.VISIBLE);}}

    private void pickImage(int code){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,code);}
    private Bitmap load(Uri u){try(InputStream in=getContentResolver().openInputStream(u)){return BitmapFactory.decodeStream(in);}catch(Exception e){return null;}}
    private TextView text(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);return b;}
    private Button smallButton(String s){Button b=button(s);b.setTextSize(14);return b;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
