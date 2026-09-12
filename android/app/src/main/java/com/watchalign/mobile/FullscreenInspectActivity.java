package com.watchalign.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Dedicated non-scrolling image inspector with visual overlay comparison tools. */
public class FullscreenInspectActivity extends Activity {
    private ZoomableImageView image;
    private Bitmap base,overlay;
    private float alpha=1f;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(8,17,31));getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        overlay=InspectionImageStore.bitmap;base=InspectionImageStore.baseBitmap;if(overlay==null){finish();return;}
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(8,17,31));
        image=new ZoomableImageView(this);image.setBackgroundColor(Color.rgb(8,17,31));root.addView(image,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),dp(8),dp(10),dp(8));top.setBackgroundColor(0xAA08111F);
        Button back=new Button(this);back.setText("Back");back.setAllCaps(false);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(78),dp(46)));
        TextView title=new TextView(this);title.setText(InspectionImageStore.title==null?"Inspection":InspectionImageStore.title);title.setTextColor(Color.WHITE);title.setTextSize(17);title.setPadding(dp(10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        Button reset=new Button(this);reset.setText("Reset");reset.setAllCaps(false);reset.setOnClickListener(v->image.resetZoom());top.addView(reset,new LinearLayout.LayoutParams(dp(78),dp(46)));root.addView(top,new FrameLayout.LayoutParams(-1,dp(62),Gravity.TOP));

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(10),dp(4),dp(10),dp(6));bottom.setBackgroundColor(0xB008111F);
        boolean hasTriangleCheck=InspectionImageStore.alignedPose!=null&&"126710BLNR".equals(InspectionImageStore.alignedModelRef);
        if(InspectionImageStore.overlayMode&&base!=null){
            LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);
            TextView label=new TextView(this);label.setText("Overlay");label.setTextColor(Color.WHITE);label.setTextSize(12);controls.addView(label,new LinearLayout.LayoutParams(dp(54),dp(40)));
            SeekBar seek=new SeekBar(this);seek.setMax(100);seek.setProgress(100);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){alpha=p/100f;refresh();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});controls.addView(seek,new LinearLayout.LayoutParams(0,dp(40),1));
            Button blink=new Button(this);blink.setText("Hold to blink");blink.setAllCaps(false);blink.setTextSize(11);blink.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){image.setImageBitmapPreserveZoom(base);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){refresh();return true;}return false;});controls.addView(blink,new LinearLayout.LayoutParams(dp(118),dp(40)));bottom.addView(controls);
            if(hasTriangleCheck){Button tri=new Button(this);tri.setText("12 triangle precision check");tri.setAllCaps(false);tri.setOnClickListener(v->startActivity(new Intent(this,Triangle12InspectActivity.class)));bottom.addView(tri,new LinearLayout.LayoutParams(-1,dp(44)));}
        }
        TextView hint=new TextView(this);hint.setText(InspectionImageStore.overlayMode?"Pinch/drag to inspect · slider changes overlay · hold Blink for watch-only":"Pinch to zoom · drag to pan · double-tap zoom");hint.setTextColor(Color.WHITE);hint.setTextSize(12);hint.setGravity(Gravity.CENTER);bottom.addView(hint,new LinearLayout.LayoutParams(-1,dp(34)));
        int bottomHeight=InspectionImageStore.overlayMode?(hasTriangleCheck?dp(126):dp(82)):dp(42);FrameLayout.LayoutParams bottomLp=new FrameLayout.LayoutParams(-1,bottomHeight,Gravity.BOTTOM);root.addView(bottom,bottomLp);setContentView(root);

        if(InspectionImageStore.overlayMode&&base!=null)refresh();else image.setImageBitmap(overlay);
    }

    private void refresh(){if(base==null){image.setImageBitmapPreserveZoom(overlay);return;}Bitmap out=Bitmap.createBitmap(base.getWidth(),base.getHeight(),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);c.drawBitmap(base,0,0,p);p.setAlpha(Math.max(0,Math.min(255,Math.round(alpha*255))));c.drawBitmap(overlay,0,0,p);image.setImageBitmapPreserveZoom(out);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
