package com.watchalign.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Alpha99 evidence detail for one tile: the large close-up with the genuine reference outline (tap to zoom), the
 * evidence state, and the full genuine-reference comparison in plain words (the Alpha98 detail, plus how the excess
 * compares with the measured photo-to-photo uncertainty).
 */
public class Alpha99DetailActivity extends Activity {
    static final String EXTRA_INDEX="index";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Alpha99Findings.Summary s=Alpha99ResultsActivity.Store.summary;
        int i=getIntent().getIntExtra(EXTRA_INDEX,-1);
        if(s==null||i<0||i>=s.all.size()){finish();return;}
        setContentView(build(s.all.get(i)));
    }

    private View build(Alpha99Findings.Finding f){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Alpha99ResultsActivity.BG);
        scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-2));
        root.addView(text(f.title,24,Color.WHITE,true));
        root.addView(text(f.statusLabel(),14,Alpha99ResultsActivity.colour(f.status),true),lp(-1,-2,4));
        Bitmap c=Alpha99ResultsActivity.Store.closeups.get(f.key);
        if(c!=null){
            ImageView iv=new ImageView(this);iv.setImageBitmap(c);iv.setAdjustViewBounds(true);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            final String title=f.title+" - close-up";
            iv.setOnClickListener(v->{InspectionImageStore.set(c,title);startActivity(new Intent(this,FullscreenInspectActivity.class));});
            root.addView(iv,lp(-1,-2,10));
            root.addView(text(caption(f)+" Tap the picture to zoom.",12,Alpha99ResultsActivity.MUTED,false),lp(-1,-2,4));
        }
        for(String line:f.detail())root.addView(text(line,15,Color.WHITE,false),lp(-1,-2,10));
        root.addView(text(Alpha99Findings.INTERFERENCE_NOTE,13,Alpha99ResultsActivity.MUTED,false),lp(-1,-2,14));
        root.addView(text(Alpha99Findings.DISCLAIMER,13,Alpha99ResultsActivity.MUTED,false),lp(-1,-2,6));
        return scroll;
    }

    static String caption(Alpha99Findings.Finding f){
        switch(f.shape){
            case DATE:return "Yellow: the date window's edges as measured. Blue: a level line along the dial.";
            case RING:return "Yellow: where genuine markers sit on this dial's printed minute track.";
            default:return "Yellow outline: where a genuine marker would sit, judged from the other markers on this dial.";
        }
    }

    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
