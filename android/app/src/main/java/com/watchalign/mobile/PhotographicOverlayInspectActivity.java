package com.watchalign.mobile;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

/** Inspector for the proof build: outline opacity and blink only, deliberately no nudge. */
public class PhotographicOverlayInspectActivity extends Activity {
    private ZoomableImageView image;
    private Bitmap base,overlay,fixedMaster,scoring;
    private TextView outlineTitle,outlineHint;
    private float alpha=.75f;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(8,17,31));getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        base=InspectionImageStore.baseBitmap;fixedMaster=InspectionImageStore.bitmap;scoring=InspectionImageStore.scoringOverlay;
        overlay=scoring!=null?scoring:fixedMaster;if(base==null||overlay==null){finish();return;}
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(8,17,31));
        image=new ZoomableImageView(this);image.setBackgroundColor(Color.rgb(8,17,31));root.addView(image,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),dp(8),dp(10),dp(8));top.setBackgroundColor(0xAA08111F);
        Button back=new Button(this);back.setText("Back");back.setAllCaps(false);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(78),dp(46)));
        outlineTitle=new TextView(this);outlineTitle.setTextColor(Color.WHITE);outlineTitle.setTextSize(17);outlineTitle.setPadding(dp(10),0,0,0);top.addView(outlineTitle,new LinearLayout.LayoutParams(0,dp(46),1));
        Button reset=new Button(this);reset.setText("Reset");reset.setAllCaps(false);reset.setOnClickListener(v->{image.resetZoom();alpha=.75f;refresh();});top.addView(reset,new LinearLayout.LayoutParams(dp(78),dp(46)));root.addView(top,new FrameLayout.LayoutParams(-1,dp(62),Gravity.TOP));

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(10),dp(5),dp(10),dp(8));bottom.setBackgroundColor(0xC008111F);
        RadioGroup modes=new RadioGroup(this);modes.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton scoreMode=new RadioButton(this);scoreMode.setId(View.generateViewId());scoreMode.setText(scoring==null?"Scoring unavailable":"Scoring (ring adjusted)");scoreMode.setTextColor(Color.WHITE);scoreMode.setTextSize(12);scoreMode.setEnabled(scoring!=null);
        RadioButton fixedMode=new RadioButton(this);fixedMode.setId(View.generateViewId());fixedMode.setText("Fixed master");fixedMode.setTextColor(Color.WHITE);fixedMode.setTextSize(12);
        modes.addView(scoreMode,new RadioGroup.LayoutParams(0,dp(44),1));modes.addView(fixedMode,new RadioGroup.LayoutParams(0,dp(44),1));bottom.addView(modes);
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=new TextView(this);label.setText("Outline");label.setTextColor(Color.WHITE);label.setTextSize(12);controls.addView(label,new LinearLayout.LayoutParams(dp(72),dp(42)));
        SeekBar seek=new SeekBar(this);seek.setMax(100);seek.setProgress(75);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){alpha=p/100f;refresh();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});controls.addView(seek,new LinearLayout.LayoutParams(0,dp(42),1));
        Button blink=new Button(this);blink.setText("Hold to blink");blink.setAllCaps(false);blink.setTextSize(11);blink.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){image.setImageBitmapPreserveZoom(base);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){refresh();return true;}return false;});controls.addView(blink,new LinearLayout.LayoutParams(dp(118),dp(42)));bottom.addView(controls);
        outlineHint=new TextView(this);outlineHint.setTextColor(Color.WHITE);outlineHint.setTextSize(11);outlineHint.setGravity(Gravity.CENTER);bottom.addView(outlineHint,new LinearLayout.LayoutParams(-1,dp(40)));
        String diagnostics=InspectionImageStore.diagnosticsText;
        if(diagnostics!=null&&!diagnostics.isEmpty()){
            TextView metrics=new TextView(this);metrics.setText(diagnostics);metrics.setTextColor(Color.WHITE);metrics.setTextSize(10);metrics.setPadding(dp(4),0,dp(4),0);
            metrics.setMovementMethod(new android.text.method.ScrollingMovementMethod());metrics.setVerticalScrollBarEnabled(true);
            bottom.addView(metrics,new LinearLayout.LayoutParams(-1,dp(140)));
        }
        int bottomH=(diagnostics!=null&&!diagnostics.isEmpty())?dp(280):dp(140);
        modes.setOnCheckedChangeListener((group,id)->selectOverlay(id==scoreMode.getId()));
        root.addView(bottom,new FrameLayout.LayoutParams(-1,bottomH,Gravity.BOTTOM));setContentView(root);
        modes.check(scoring!=null?scoreMode.getId():fixedMode.getId());
    }

    private void selectOverlay(boolean scoreMode){
        overlay=scoreMode&&scoring!=null?scoring:fixedMaster;
        outlineTitle.setText(scoreMode?"Ring-adjusted scoring outline":"Fixed-master perspective outline");
        outlineHint.setText(scoreMode?"Yellow: exact ring-adjusted reference used for mismatch. Cyan: detected outlines where assessed. Unreliable area comparisons remain withheld.":
                "Yellow: fixed model master; separate from the mismatch reference. Cyan: fitted baton edges."+(scoring==null?" Scoring reference unavailable.":""));
        refresh();
    }

    private void refresh(){
        Bitmap out=Bitmap.createBitmap(base.getWidth(),base.getHeight(),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);c.drawBitmap(base,0,0,p);p.setAlpha(Math.max(0,Math.min(255,Math.round(alpha*255))));c.drawBitmap(overlay,0,0,p);image.setImageBitmapPreserveZoom(out);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
