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
    private WatchAlignCore.AnalysisResult lastResult;
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
        root.addView(text("V1.3.0-alpha6 · analysis runs on this device", 14, Color.rgb(158,176,201)));
        root.addView(text("No hosted backend. Exact-model reference discovery is online; watch analysis and registration run locally.", 13, Color.rgb(158,176,201)));

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
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){if(lastResult!=null&&lastResult.aligned!=null) image.setImageBitmap(lastResult.overlay(p/100f));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}}); root.addView(opacity);
        resultText = text("", 15, Color.WHITE); resultText.setPadding(0,dp(12),0,dp(32)); root.addView(resultText);
        return scroll;
    }

    private void pickImage(int request) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, request);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Bitmap b = readBitmap(data.getData());
            if (request == PICK_WATCH) {
                watchBitmap = b; lastResult=null; image.setImageBitmap(b); referenceButton.setEnabled(false); overlayButton.setEnabled(false);
                status.setText("Watch photo ready. Tap Analyse + find best reference.");
            } else {
                referenceBitmap = b; lastResult=null; referenceButton.setEnabled(false); overlayButton.setEnabled(false);
                status.setText("Your reference photo is ready and will be used instead of online discovery.");
            }
        } catch (Exception e) { status.setText("Could not read image: " + e.getMessage()); }
    }

    private Bitmap readBitmap(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Bitmap b = BitmapFactory.decodeStream(in); if (b == null) throw new IllegalArgumentException("Not a readable image");
            int max = Math.max(b.getWidth(), b.getHeight()); if (max <= 1600) return b.copy(Bitmap.Config.ARGB_8888, false);
            float s = 1600f / max; return Bitmap.createScaledBitmap(b, Math.round(b.getWidth()*s), Math.round(b.getHeight()*s), true).copy(Bitmap.Config.ARGB_8888,false);
        }
    }

    private void analyse() {
        if (watchBitmap == null) { status.setText("Choose your watch photo first."); return; }
        status.setText(referenceBitmap == null ? "Detecting dial locally and searching exact-model references…" : "Detecting dial locally with your chosen reference…");
        resultText.setText(""); opacity.setVisibility(View.GONE); referenceButton.setEnabled(false); overlayButton.setEnabled(false);
        Bitmap watch = watchBitmap; Bitmap manualRef = referenceBitmap; String refCode = model.getSelectedItemPosition()==0 ? "126710BLNR" : "124060";
        worker.submit(() -> {
            try {
                Bitmap ref = manualRef; String sourceNote = "Manual reference selected on device.";
                if (ref == null) {
                    OnlineReferenceFinder.Result found = OnlineReferenceFinder.find(this, watch, refCode);
                    ref = found.bitmap;
                    sourceNote = (found.fromCache ? "Reference: cached exact-model official-source image.\n" : "Reference: downloaded from exact-model official manufacturer source and cached locally.\n") + "Source: " + found.source;
                }
                WatchAlignCore.AnalysisResult r = WatchAlignCore.analyse(watch, ref, refCode);
                final String note = sourceNote;
                runOnUiThread(() -> {
                    lastResult=r; image.setImageBitmap(r.annotated); resultText.setText(r.report + "\n\n" + note); status.setText("Analysis complete.");
                    referenceButton.setEnabled(r.reference!=null); overlayButton.setEnabled(r.aligned!=null);
                    if(r.aligned==null) opacity.setVisibility(View.GONE);
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    lastResult=null; referenceButton.setEnabled(false); overlayButton.setEnabled(false); opacity.setVisibility(View.GONE);
                    status.setText("Reference/analysis error: " + t.getMessage());
                    resultText.setText("Watch Align refused to produce QC/overlay output because the dial or reference geometry could not be verified. Try a clearer photo with the full dial visible.");
                });
            }
        });
    }

    private void showAnnotated(){opacity.setVisibility(View.GONE); if(lastResult!=null) image.setImageBitmap(lastResult.annotated);}
    private void showReference(){opacity.setVisibility(View.GONE); if(lastResult!=null&&lastResult.reference!=null) image.setImageBitmap(lastResult.reference);}
    private void showOverlay(){if(lastResult!=null&&lastResult.aligned!=null){opacity.setVisibility(View.VISIBLE); image.setImageBitmap(lastResult.overlay(opacity.getProgress()/100f));}}

    private TextView text(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private Button smallButton(String s){Button b=button(s);b.setTextSize(12);return b;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
