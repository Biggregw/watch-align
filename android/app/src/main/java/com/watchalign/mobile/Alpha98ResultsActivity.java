package com.watchalign.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Alpha98 results screen: plain-language findings, one close-up per feature outside the measured genuine range (tap to
 * zoom), what is within range, what could not be assessed and why, and the technical numbers behind a collapsed
 * "Technical details" section. No pass / fail, no colour-coded judgement.
 */
public class Alpha98ResultsActivity extends Activity {
    private static final int BG=Color.rgb(8,17,31),CARD=Color.rgb(17,30,50),ACCENT=Color.rgb(50,213,242),MUTED=Color.rgb(158,176,201);

    /** Process-local handoff from the measuring activity. */
    static final class Store {
        static Alpha98Findings.Summary summary;
        static final List<Bitmap> closeups=new ArrayList<>();
        static String technical;
        static Bitmap photo,overlay;
        private Store(){}
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(Store.summary==null){finish();return;}
        setContentView(build());
    }

    private View build(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-2));
        Alpha98Findings.Summary s=Store.summary;
        root.addView(text("Results",28,Color.WHITE,true));
        root.addView(text(s.headline(),18,Color.WHITE,true),lp(-1,-2,8));
        root.addView(text(Alpha98Findings.DISCLAIMER,13,MUTED,false),lp(-1,-2,6));

        List<Alpha98Findings.Finding> out=s.outside();
        for(int i=0;i<out.size();i++){
            Alpha98Findings.Finding f=out.get(i);
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setBackgroundColor(CARD);card.setPadding(dp(12),dp(12),dp(12),dp(12));
            card.addView(text(f.title,18,Color.WHITE,true));
            Bitmap c=i<Store.closeups.size()?Store.closeups.get(i):null;
            if(c!=null){
                ImageView iv=new ImageView(this);iv.setImageBitmap(c);iv.setAdjustViewBounds(true);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                final String title=f.title+" - close-up";
                iv.setOnClickListener(v->{InspectionImageStore.set(c,title);startActivity(new Intent(this,FullscreenInspectActivity.class));});
                card.addView(iv,lp(-1,-2,8));
                card.addView(text(caption(f)+" Tap the picture to zoom.",12,MUTED,false),lp(-1,-2,4));
            }
            for(String line:f.lines)card.addView(text(line,15,Color.WHITE,false),lp(-1,-2,6));
            root.addView(card,lp(-1,-2,14));
        }

        List<Alpha98Findings.Finding> within=s.within();
        if(!within.isEmpty()){
            root.addView(text("Within the measured genuine range",17,Color.WHITE,true),lp(-1,-2,18));
            StringBuilder b=new StringBuilder();for(Alpha98Findings.Finding f:within){if(b.length()>0)b.append(" · ");b.append(f.title);}
            root.addView(text(b.toString(),15,MUTED,false),lp(-1,-2,4));
        }
        List<Alpha98Findings.Finding> na=s.notAssessed();
        if(!na.isEmpty()){
            root.addView(text("Not assessed in this photo",17,Color.WHITE,true),lp(-1,-2,18));
            for(Alpha98Findings.Finding f:na)root.addView(text(f.title+": "+f.reason+".",15,MUTED,false),lp(-1,-2,4));
            root.addView(text("A sharper, straight-on photo with the hands away from the markers and no glare on the date magnifier can let these be assessed.",13,MUTED,false),lp(-1,-2,6));
        }

        if(Store.photo!=null&&Store.overlay!=null){
            Button ov=new Button(this);ov.setText("Open full overlay");ov.setAllCaps(false);
            ov.setOnClickListener(v->{InspectionImageStore.setOverlay(Store.photo,Store.overlay,"Full overlay",null);startActivity(new Intent(this,PhotographicOverlayInspectActivity.class));});
            root.addView(ov,lp(-1,dp(50),18));
        }
        TextView tech=text(Store.technical==null?"":Store.technical,12,MUTED,false);tech.setTextIsSelectable(true);tech.setVisibility(View.GONE);
        Button details=new Button(this);details.setText("Technical details  ▸");details.setAllCaps(false);
        details.setOnClickListener(v->{boolean show=tech.getVisibility()!=View.VISIBLE;tech.setVisibility(show?View.VISIBLE:View.GONE);details.setText(show?"Technical details  ▾":"Technical details  ▸");});
        root.addView(details,lp(-1,dp(48),8));root.addView(tech,lp(-1,-2,6));
        return scroll;
    }

    static String caption(Alpha98Findings.Finding f){
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
