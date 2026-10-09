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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Alpha99 results: headline by evidence strength, the disclaimer, the whole-dial overview with status badges (tap for
 * the full overlay), a two-column grid of close-ups for clear findings, then worth-a-look findings, then not-assessed
 * markers whose close-up shows why (tap a tile for the detail view), one line for everything within range, and the
 * full overlay / technical details as secondary, collapsed items.
 */
public class Alpha99ResultsActivity extends Activity {
    static final int BG=Color.rgb(8,17,31),CARD=Color.rgb(17,30,50),MUTED=Color.rgb(158,176,201),
            RED=Color.rgb(255,99,88),AMBER=Color.rgb(255,176,50),GREEN=Color.rgb(72,209,108),GREY=Color.rgb(160,160,168),
            SLATE=Color.rgb(120,170,220);

    /** Process-local handoff from the measuring activity. */
    static final class Store {
        static Alpha99Findings.Summary summary;
        static Bitmap overview;
        static final Map<String,Bitmap> closeups=new HashMap<>();
        static String technical;
        /** the model the photo was checked as, and an optional hint that a different model may fit the photo. */
        static String modelLabel,modelHint;
        static Bitmap photo,overlay;
        private Store(){}
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(Store.summary==null){finish();return;}
        setContentView(build());
    }

    static int colour(Alpha99Findings.Status s){
        switch(s){case CLEAR:return RED;case WORTH:return AMBER;case MINOR:return SLATE;case WITHIN:return GREEN;default:return GREY;}
    }

    private View build(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-2));
        Alpha99Findings.Summary s=Store.summary;
        root.addView(text("Results",28,Color.WHITE,true));
        if(Store.modelLabel!=null)root.addView(text("Checked as: "+Store.modelLabel,14,MUTED,false),lp(-1,-2,2));
        if(Store.modelHint!=null)root.addView(text(Store.modelHint,14,Color.rgb(255,176,50),false),lp(-1,-2,4));
        List<String> head=s.headlineLines();
        for(int i=0;i<head.size();i++)root.addView(text(head.get(i),i==0?19:16,Color.WHITE,i==0),lp(-1,-2,i==0?8:2));
        root.addView(text(Alpha99Findings.DISCLAIMER,13,MUTED,false),lp(-1,-2,6));

        // overview hero
        if(Store.overview!=null){
            ImageView hero=new ImageView(this);hero.setImageBitmap(Store.overview);hero.setAdjustViewBounds(true);hero.setScaleType(ImageView.ScaleType.FIT_CENTER);
            hero.setOnClickListener(v->openOverlay());
            root.addView(hero,lp(-1,-2,12));
            root.addView(text("✓ within   • too small to see   ! worth a look   !! clear finding   – not assessed   ·   tap for the full overlay",12,MUTED,false),lp(-1,-2,4));
        }

        // evidence grid
        List<Alpha99Findings.Finding> tiles=s.tiles();
        if(!tiles.isEmpty()){
            root.addView(text(Alpha99Findings.INTERFERENCE_NOTE,13,MUTED,false),lp(-1,-2,14));
            LinearLayout row=null;
            for(int i=0;i<tiles.size();i++){
                if(i%2==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);root.addView(row,lp(-1,-2,10));}
                LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1f);if(i%2==1)tp.leftMargin=dp(10);
                row.addView(tile(s.all.indexOf(tiles.get(i)),tiles.get(i)),tp);
            }
            if(tiles.size()%2==1){LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1f);tp.leftMargin=dp(10);row.addView(new View(this),tp);}
        }

        String within=s.withinLine();
        if(!within.isEmpty())root.addView(text(within,14,MUTED,false),lp(-1,-2,16));
        String na=s.notAssessedLine();
        if(!na.isEmpty())root.addView(text(na,14,MUTED,false),lp(-1,-2,6));

        // secondary
        if(Store.photo!=null&&Store.overlay!=null){
            Button ov=new Button(this);ov.setText("Open full overlay");ov.setAllCaps(false);ov.setOnClickListener(v->openOverlay());
            root.addView(ov,lp(-1,dp(48),18));
        }
        TextView tech=text(Store.technical==null?"":Store.technical,12,MUTED,false);tech.setTextIsSelectable(true);tech.setVisibility(View.GONE);
        Button details=new Button(this);details.setText("Technical details  ▸");details.setAllCaps(false);
        details.setOnClickListener(v->{boolean show=tech.getVisibility()!=View.VISIBLE;tech.setVisibility(show?View.VISIBLE:View.GONE);details.setText(show?"Technical details  ▾":"Technical details  ▸");});
        root.addView(details,lp(-1,dp(48),8));root.addView(tech,lp(-1,-2,6));
        return scroll;
    }

    private View tile(int index,Alpha99Findings.Finding f){
        LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.setBackgroundColor(CARD);t.setPadding(dp(8),dp(8),dp(8),dp(10));
        Bitmap c=Store.closeups.get(f.key);
        if(c!=null){ImageView iv=new ImageView(this);iv.setImageBitmap(c);iv.setAdjustViewBounds(true);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);t.addView(iv,new LinearLayout.LayoutParams(-1,-2));}
        t.addView(text(f.title,15,Color.WHITE,true),lp(-1,-2,6));
        t.addView(text(f.statusLabel(),12,colour(f.status),true),lp(-1,-2,2));
        t.addView(text(f.shortLine(),13,Color.WHITE,false),lp(-1,-2,2));
        t.setOnClickListener(v->{Intent i=new Intent(this,Alpha99DetailActivity.class);i.putExtra(Alpha99DetailActivity.EXTRA_INDEX,index);startActivity(i);});
        return t;
    }

    private void openOverlay(){
        if(Store.photo==null||Store.overlay==null)return;
        InspectionImageStore.setOverlay(Store.photo,Store.overlay,"Full overlay",null);startActivity(new Intent(this,PhotographicOverlayInspectActivity.class));
    }

    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
