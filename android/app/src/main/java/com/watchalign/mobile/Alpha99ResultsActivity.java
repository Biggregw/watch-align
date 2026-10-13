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
 * Results, simple first: how many markers are flagged, the corrected dial with one badge per marker (tap a marker for
 * its close-up), close-ups of the flagged markers and of markers that could not be assessed, then collapsed lists:
 * every marker, and the technical details (overlays, exact measurements). Display only; findings are unchanged.
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
        static final Map<Integer,MarkerMismatchResearch.Result> mismatch=new HashMap<>();
        static String technical;
        /** the model the photo was checked as, and an optional hint that a different model may fit the photo. */
        static String modelLabel,modelHint;
        static Bitmap photo,overlay,scoringOverlay;
        /** the model the photo was measured with (marker positions for tapping the overview). */
        static ModelSpec model;
        private Store(){}
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(Store.summary==null){finish();return;}
        setContentView(build());
    }

    static int colour(Alpha99Findings.Status s){
        switch(s){case CLEAR:return RED;case WORTH:return AMBER;case MINOR:case WITHIN:return GREEN;default:return GREY;}
    }

    private View build(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-2));
        Alpha99Findings.Summary s=Store.summary;
        // 1. the answer: how many markers are flagged, and the other counts in one line
        root.addView(text(InspectionView.headline(s,Store.mismatch),26,Color.WHITE,true));
        root.addView(text(InspectionView.counts(s,Store.mismatch),14,MUTED,false),lp(-1,-2,4));
        if(Store.modelLabel!=null)root.addView(text(Store.modelLabel,13,MUTED,false),lp(-1,-2,2));
        if(Store.modelHint!=null)root.addView(text(Store.modelHint,13,AMBER,false),lp(-1,-2,4));

        // 2. the watch, each marker badged; tap a marker for its close-up
        if(Store.overview!=null){
            ImageView hero=new ImageView(this);hero.setImageBitmap(Store.overview);hero.setAdjustViewBounds(true);hero.setScaleType(ImageView.ScaleType.FIT_CENTER);
            hero.setOnTouchListener((v,e)->{
                if(e.getAction()!=android.view.MotionEvent.ACTION_UP)return true;
                double k=Alpha99Overview.SIZE/(double)Math.max(1,v.getWidth());
                Alpha99Findings.Finding hit=Store.model==null?null:Alpha99Overview.at(s,Store.model,e.getX()*k,e.getY()*k);
                if(hit!=null)openDetail(s.all.indexOf(hit));else openOverlay();
                return true;
            });
            root.addView(hero,lp(-1,-2,12));
            root.addView(text("!! clear   ! worth a look   – not assessed   ✓ within (grey: partly assessed)   ·   tap a marker",12,MUTED,false),lp(-1,-2,4));
        }

        // 3. flagged markers, then markers that could not be assessed (each with its close-up)
        List<Alpha99Findings.Finding> flagged=new java.util.ArrayList<>(),unassessed=new java.util.ArrayList<>();
        for(Alpha99Findings.Finding f:s.all){
            InspectionView.State st=InspectionView.state(f,Store.mismatch.get(f.hour));
            if(st==InspectionView.State.CLEAR||st==InspectionView.State.WORTH)flagged.add(f);
            else if(st==InspectionView.State.NOT_ASSESSED&&f.group==null&&Store.closeups.containsKey(f.key))unassessed.add(f);
        }
        if(!flagged.isEmpty()){root.addView(text("Flagged",18,Color.WHITE,true),lp(-1,-2,18));grid(root,s,flagged);}
        if(!unassessed.isEmpty()){root.addView(text("Not assessed",18,Color.WHITE,true),lp(-1,-2,18));grid(root,s,unassessed);}
        String grouped=s.notAssessedLine();
        if(!grouped.isEmpty()&&unassessed.isEmpty()&&flagged.isEmpty())root.addView(text(grouped,13,MUTED,false),lp(-1,-2,8));

        // 4. every marker, one line each (collapsed)
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setVisibility(View.GONE);
        for(Alpha99Findings.Finding f:s.all){
            if(f.group!=null&&f.status==Alpha99Findings.Status.NOT_ASSESSED&&!Store.closeups.containsKey(f.key))continue;
            MarkerMismatchResearch.Result q=Store.mismatch.get(f.hour);InspectionView.State st=InspectionView.state(f,q);
            String area=InspectionView.area(f,q);
            Button b=new Button(this);b.setAllCaps(false);b.setTextColor(stateColour(st));
            b.setText(f.title+"  ·  "+InspectionView.label(st)+(area==null?"":"  ·  "+area.replace("Area mismatch ","area ").replace("Area: not assessed","area not assessed")));
            final int index=s.all.indexOf(f);b.setOnClickListener(v->openDetail(index));
            list.addView(b,lp(-1,-2,4));
        }
        root.addView(toggle("All markers",list),lp(-1,dp(48),18));root.addView(list,lp(-1,-2,4));

        // 5. technical details (collapsed): overlays and every exact measurement, unchanged
        LinearLayout tech=new LinearLayout(this);tech.setOrientation(LinearLayout.VERTICAL);tech.setVisibility(View.GONE);
        if(Store.photo!=null&&Store.overlay!=null){
            Button ov=new Button(this);ov.setText("Open scoring / fixed-master overlays");ov.setAllCaps(false);ov.setOnClickListener(v->openOverlay());
            tech.addView(ov,lp(-1,dp(48),4));
        }
        for(String line:s.headlineLines())tech.addView(text(line,13,MUTED,false),lp(-1,-2,4));
        tech.addView(text(Alpha99Findings.DISCLAIMER,12,MUTED,false),lp(-1,-2,6));
        TextView exact=text(Store.technical==null?"":Store.technical,12,MUTED,false);exact.setTextIsSelectable(true);
        tech.addView(exact,lp(-1,-2,6));
        root.addView(toggle("Technical details",tech),lp(-1,dp(48),8));root.addView(tech,lp(-1,-2,4));
        root.addView(text(InspectionView.SHORT_DISCLAIMER,12,MUTED,false),lp(-1,-2,14));
        return scroll;
    }

    /** The existing status wording, kept for the technical details. */
    static String colourLabel(Alpha99Findings.Finding f){return "Existing status: "+f.statusLabel()+(f.partNotAssessed==null?"":" ("+f.partNotAssessed+" not assessed: "+f.partReason+")");}

    static int stateColour(InspectionView.State st){
        switch(st){case CLEAR:return RED;case WORTH:return AMBER;case WITHIN:return GREEN;case MINOR:return SLATE;default:return GREY;}
    }

    private void grid(LinearLayout root,Alpha99Findings.Summary s,List<Alpha99Findings.Finding> tiles){
        LinearLayout row=null;
        for(int i=0;i<tiles.size();i++){
            if(i%2==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);root.addView(row,lp(-1,-2,10));}
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1f);if(i%2==1)tp.leftMargin=dp(10);
            row.addView(tile(s.all.indexOf(tiles.get(i)),tiles.get(i)),tp);
        }
        if(tiles.size()%2==1){LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1f);tp.leftMargin=dp(10);row.addView(new View(this),tp);}
    }

    private Button toggle(String name,View target){
        Button b=new Button(this);b.setAllCaps(false);b.setText(name+"  ▸");
        b.setOnClickListener(v->{boolean show=target.getVisibility()!=View.VISIBLE;target.setVisibility(show?View.VISIBLE:View.GONE);b.setText(name+(show?"  ▾":"  ▸"));});
        return b;
    }

    private void openDetail(int index){Intent i=new Intent(this,Alpha99DetailActivity.class);i.putExtra(Alpha99DetailActivity.EXTRA_INDEX,index);startActivity(i);}

    private View tile(int index,Alpha99Findings.Finding f){
        MarkerMismatchResearch.Result q=Store.mismatch.get(f.hour);InspectionView.State st=InspectionView.state(f,q);
        LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.setBackgroundColor(CARD);t.setPadding(dp(8),dp(8),dp(8),dp(10));
        Bitmap c=Store.closeups.get(f.key);
        if(c!=null){ImageView iv=new ImageView(this);iv.setImageBitmap(c);iv.setAdjustViewBounds(true);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);t.addView(iv,new LinearLayout.LayoutParams(-1,-2));}
        t.addView(text(f.title,15,Color.WHITE,true),lp(-1,-2,6));
        t.addView(text(InspectionView.label(st),13,stateColour(st),true),lp(-1,-2,2));
        List<String> lines=InspectionView.findings(f);
        String one=!lines.isEmpty()?lines.get(0):InspectionView.explain(f,q);
        if(one!=null)t.addView(text(one,13,Color.WHITE,false),lp(-1,-2,2));
        t.setOnClickListener(v->openDetail(index));
        return t;
    }

    private void openOverlay(){
        if(Store.photo==null||Store.overlay==null)return;
        InspectionImageStore.setMarkerOverlays(Store.photo,Store.overlay,Store.scoringOverlay);startActivity(new Intent(this,PhotographicOverlayInspectActivity.class));
    }

    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
