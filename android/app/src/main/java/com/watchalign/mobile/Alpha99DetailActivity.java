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
 * One marker: its assessment in a word, the large close-up with exactly the scored outlines and extra/missing metal
 * (tap to zoom), the area mismatch when it was measured reliably, and any rotation or position finding. All the
 * existing explanations stay under a collapsed "Technical details". Display only.
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
        MarkerMismatchResearch.Result q=f.dialWide?null:Alpha99ResultsActivity.InspectionView.result(f,Store.mismatch);
        InspectionView.State st=InspectionView.state(f,q);
        // the answer
        root.addView(text(f.title,24,Color.WHITE,true));
        root.addView(text(InspectionView.label(st),20,Alpha99ResultsActivity.stateColour(st),true),lp(-1,-2,4));
        String why=InspectionView.explain(f,q);
        if(why!=null)root.addView(text(why,15,Color.WHITE,false),lp(-1,-2,4));
        // the picture: exactly the scored outlines and regions (tap to zoom)
        Bitmap c=Alpha99ResultsActivity.Store.closeups.get(f.key);
        if(c!=null){
            ImageView iv=new ImageView(this);iv.setImageBitmap(c);iv.setAdjustViewBounds(true);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            final String title=f.title+" - close-up";
            iv.setOnClickListener(v->{InspectionImageStore.set(c,title);startActivity(new Intent(this,FullscreenInspectActivity.class));});
            root.addView(iv,lp(-1,-2,12));
            String legend=!InspectionView.marker(f)?caption(f):q!=null&&q.trusted()?InspectionView.LEGEND_MEASURED:InspectionView.LEGEND_WITHHELD;
            root.addView(text(legend+"  Tap to zoom.",12,Alpha99ResultsActivity.MUTED,false),lp(-1,-2,4));
        }
        // the numbers that matter
        String area=InspectionView.area(f,q);
        if(area!=null){
            root.addView(text(area,18,Color.WHITE,true),lp(-1,-2,14));
            String parts=InspectionView.areaParts(q),reason=InspectionView.areaReason(f,q);
            if(parts!=null)root.addView(text(parts,14,Alpha99ResultsActivity.MUTED,false),lp(-1,-2,2));
            if(reason!=null)root.addView(text(reason,14,Alpha99ResultsActivity.MUTED,false),lp(-1,-2,2));
        }
        for(String line:InspectionView.findings(f))root.addView(text(line,16,Color.WHITE,false),lp(-1,-2,10));
        // everything else, unchanged, collapsed
        LinearLayout tech=new LinearLayout(this);tech.setOrientation(LinearLayout.VERTICAL);tech.setVisibility(View.GONE);
        tech.addView(text(Alpha99ResultsActivity.colourLabel(f),13,Alpha99ResultsActivity.MUTED,false));
        if(f.edgeDiagnostic!=null)tech.addView(text(f.edgeDiagnostic+" Diagnostic only: the reported rotation is unchanged.",13,Alpha99ResultsActivity.MUTED,false),lp(-1,-2,6));
        if(q!=null)tech.addView(text(q.display(),13,Alpha99ResultsActivity.MUTED,false),lp(-1,-2,6));
        for(String line:f.detail())tech.addView(text(line,13,Alpha99ResultsActivity.MUTED,false),lp(-1,-2,6));
        if(c!=null)tech.addView(text(caption(f),12,Alpha99ResultsActivity.MUTED,false),lp(-1,-2,6));
        tech.addView(text(Alpha99Findings.INTERFERENCE_NOTE,12,Alpha99ResultsActivity.MUTED,false),lp(-1,-2,6));
        tech.addView(text(Alpha99Findings.DISCLAIMER,12,Alpha99ResultsActivity.MUTED,false),lp(-1,-2,6));
        android.widget.Button more=new android.widget.Button(this);more.setAllCaps(false);more.setText("Technical details  ▸");
        more.setOnClickListener(v->{boolean show=tech.getVisibility()!=View.VISIBLE;tech.setVisibility(show?View.VISIBLE:View.GONE);more.setText(show?"Technical details  ▾":"Technical details  ▸");});
        root.addView(more,lp(-1,dp(48),18));root.addView(tech,lp(-1,-2,4));
        return scroll;
    }

    static String caption(Alpha99Findings.Finding f){
        switch(f.shape){
            case DATE:return "Yellow: the date window's edges as measured. Blue: a level line along the dial.";
            case RING:return "Yellow: where genuine markers sit on this dial's printed minute track.";
            case BATON:return "Yellow: ring-adjusted nominal outer-metal polygon used for outline mismatch. Cyan: actual detector-fitted edges. If the ring is unavailable, yellow shows the fixed master and mismatch is withheld.";
            default:return "Yellow: ring-adjusted nominal outer-metal polygon used for outline comparison. Uncertain edges have no reported mismatch percentage. If the ring is unavailable, yellow shows the fixed nominal.";
        }
    }

    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
