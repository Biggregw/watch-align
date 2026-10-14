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

/** One marker: its answer, its close-up (tap to zoom) and, only for a defect, the measured misalignment against the
 *  genuine range. Exact numbers for any marker stay under a collapsed "Technical details". Display only. */
public class AlignmentDetailActivity extends Activity {
    static final String EXTRA_HOUR="hour";
    static final String LEGEND="Yellow: genuine outer-metal reference (fixed). Pink dotted: where the genuine luminous fill is expected. Cyan: the luminous outline measured in this photo, dashed where an edge was not visible and was inferred from the marker's known shape. Silver: illustration of the metal surround around the measured lume. Red: direction of the misalignment.";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        AlignmentAnalysis.Output o=AlignmentResultsActivity.Store.result;int hour=getIntent().getIntExtra(EXTRA_HOUR,-1);
        AlignmentDecision.Marker m=null;if(o!=null)for(AlignmentDecision.Marker x:o.markers)if(x.hour==hour)m=x;
        if(m==null){finish();return;}
        setTitle("Watch Align");
        setContentView(build(m));
    }

    private View build(AlignmentDecision.Marker m){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(AlignmentResultsActivity.BG);
        scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-2));
        root.addView(text(m.title()+" — "+m.label(),24,AlignmentResultsActivity.colour(m),true));
        if(m.worthALook())root.addView(text("Luminous fill: within the genuine range. Outer metal: borderline, see below.",15,Color.WHITE,false),lp(-1,-2,6));
        if(m.outcome==AlignmentDecision.Outcome.DEFECT)root.addView(text("Primary issue: "+m.primary.headline()+".",18,Color.WHITE,true),lp(-1,-2,6));
        else if(m.outcome==AlignmentDecision.Outcome.CANNOT_ASSESS)root.addView(text(AlignmentDecision.capital(m.reason)+".",15,Color.WHITE,false),lp(-1,-2,6));
        Bitmap c=AlignmentResultsActivity.Store.closeups.get(m.hour);
        if(c!=null){
            ImageView iv=new ImageView(this);iv.setImageBitmap(c);iv.setAdjustViewBounds(true);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            final String title=m.title()+" - close-up";
            iv.setOnClickListener(v->{InspectionImageStore.set(c,title);startActivity(new Intent(this,FullscreenInspectActivity.class));});
            root.addView(iv,lp(-1,-2,12));
            root.addView(text(LEGEND+"  Tap to zoom.",12,AlignmentResultsActivity.MUTED,false),lp(-1,-2,4));
        }
        if(m.outcome==AlignmentDecision.Outcome.DEFECT){
            root.addView(text(m.primary.measured(),16,Color.WHITE,false),lp(-1,-2,12));
            root.addView(text(m.primary.genuine(),15,AlignmentResultsActivity.MUTED,false),lp(-1,-2,2));
            for(AlignmentDecision.Issue i:m.secondary){
                root.addView(text("Also: "+i.headline().toLowerCase(java.util.Locale.US)+".",15,Color.WHITE,false),lp(-1,-2,12));
                root.addView(text(i.measured()+" "+i.genuine(),13,AlignmentResultsActivity.MUTED,false),lp(-1,-2,2));
            }
        }
        for(String note:m.metalNotes)root.addView(text(note,14,Color.WHITE,false),lp(-1,-2,10));
        String sub=m.outcome==AlignmentDecision.Outcome.CANNOT_ASSESS?null:m.notAssessed.isEmpty()?null:"Not assessed: "+String.join(", ",m.notAssessed)+" (the visible edges do not fix it).";
        if(sub!=null)root.addView(text(sub,13,AlignmentResultsActivity.MUTED,false),lp(-1,-2,10));
        if(m.fit!=null&&m.fit.inferred&&m.outcome!=AlignmentDecision.Outcome.CANNOT_ASSESS)
            root.addView(text("Part of this marker's luminous edge was not visible; its position uses the marker's known genuine shape, with the extra uncertainty allowed for.",13,AlignmentResultsActivity.MUTED,false),lp(-1,-2,6));

        LinearLayout tech=new LinearLayout(this);tech.setOrientation(LinearLayout.VERTICAL);tech.setVisibility(View.GONE);
        StringBuilder s=new StringBuilder();
        for(AlignmentDecision.Issue i:AlignmentAnalysis.allIssues(m))
            s.append(AlignmentDecision.name(i.param)).append(": ").append(AlignmentAnalysis.fmt(i.param,i.deviation)).append(" from the genuine nominal; genuine envelope ")
             .append(AlignmentAnalysis.fmt(i.param,i.limit)).append("; photo uncertainty ").append(AlignmentAnalysis.fmt(i.param,i.uncertainty)).append('\n');
        LuminousAlignment.Result f=m.fit;
        if(f!=null)s.append(String.format(java.util.Locale.US,"edge samples %d · edge noise %.2f px · lume %.0f / dial %.0f · merged-bevel samples set aside %.0f%%%s",
                f.samples,f.noisePx,f.lume,f.dial,100*f.mergedShare,f.inferred?" · known-size inference":""));
        TextView exact=text(s.toString(),12,AlignmentResultsActivity.MUTED,false);exact.setTextIsSelectable(true);tech.addView(exact);
        Button more=new Button(this);more.setAllCaps(false);more.setText("Technical details  ▸");
        more.setOnClickListener(v->{boolean show=tech.getVisibility()!=View.VISIBLE;tech.setVisibility(show?View.VISIBLE:View.GONE);more.setText(show?"Technical details  ▾":"Technical details  ▸");});
        root.addView(more,lp(-1,dp(48),18));root.addView(tech,lp(-1,-2,4));
        root.addView(text(AlignmentResultsActivity.DISCLAIMER,12,AlignmentResultsActivity.MUTED,false),lp(-1,-2,14));
        return scroll;
    }

    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
