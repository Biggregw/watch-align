package com.watchalign.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * Watch Align results (alignment only): the answer, the dial with one badge per marker (tap a marker), a card for each
 * defect (its close-up and the dominant issue), one line per marker, and collapsed technical details. No scores,
 * no area percentages, no measurements for markers without a defect.
 */
public class AlignmentResultsActivity extends Activity {
    static final int BG=Color.rgb(8,17,31),CARD=Color.rgb(17,30,50),MUTED=Color.rgb(158,176,201),
            RED=Color.rgb(255,99,88),GREEN=Color.rgb(72,209,108),GREY=Color.rgb(160,160,168);

    /** Process-local handoff from the start screen. */
    static final class Store {
        static AlignmentAnalysis.Output result;static ModelSpec model;static String modelLabel;
        static Bitmap overview,photo;static final Map<Integer,Bitmap> closeups=new HashMap<>();
        static String technical,legacyTechnical;
        private Store(){}
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(Store.result==null||Store.model==null){finish();return;}
        setTitle("Watch Align");
        setContentView(build());
    }

    static int colour(AlignmentDecision.Outcome o){return o==AlignmentDecision.Outcome.DEFECT?RED:o==AlignmentDecision.Outcome.NO_DEFECT?GREEN:GREY;}

    private View build(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-2));
        AlignmentAnalysis.Output o=Store.result;
        root.addView(text("Watch Align",14,MUTED,true));
        root.addView(text(AlignmentAnalysis.headline(o),26,o.count(AlignmentDecision.Outcome.DEFECT)>0?RED:Color.WHITE,true),lp(-1,-2,2));
        root.addView(text(AlignmentAnalysis.counts(o),14,MUTED,false),lp(-1,-2,4));
        if(Store.modelLabel!=null)root.addView(text(Store.modelLabel,13,MUTED,false),lp(-1,-2,2));
        if(o.photoReason!=null)root.addView(text(AlignmentDecision.capital(o.photoReason)+". Upload a sharp photo taken straight on, with the whole dial visible.",15,Color.WHITE,false),lp(-1,-2,8));

        if(Store.overview!=null){
            ImageView hero=new ImageView(this);hero.setImageBitmap(Store.overview);hero.setAdjustViewBounds(true);hero.setScaleType(ImageView.ScaleType.FIT_CENTER);
            hero.setOnTouchListener((v,e)->{
                if(e.getAction()!=MotionEvent.ACTION_UP)return true;
                double k=AlignmentOverview.SIZE/(double)Math.max(1,v.getWidth());
                AlignmentDecision.Marker hit=AlignmentOverview.at(o,Store.model,e.getX()*k,e.getY()*k);
                if(hit!=null)openDetail(hit.hour);
                return true;
            });
            root.addView(hero,lp(-1,-2,12));
            root.addView(text("✓ no defect   ! defect detected   – cannot assess   ·   tap a marker",12,MUTED,false),lp(-1,-2,4));
        }

        // a card for every defect, the dominant issue first
        for(AlignmentDecision.Marker m:o.markers)if(m.outcome==AlignmentDecision.Outcome.DEFECT)root.addView(defectCard(m),lp(-1,-2,14));

        // every marker, one line each
        root.addView(text("All markers",18,Color.WHITE,true),lp(-1,-2,18));
        for(AlignmentDecision.Marker m:o.markers){
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setBackgroundColor(CARD);row.setPadding(dp(12),dp(10),dp(12),dp(10));
            LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);
            top.addView(text(m.title(),16,Color.WHITE,true),new LinearLayout.LayoutParams(0,-2,1f));
            top.addView(text(m.label(),16,colour(m.outcome),true));
            row.addView(top);
            String sub=AlignmentDecision.summary(m);
            if(sub!=null)row.addView(text(sub,13,MUTED,false),lp(-1,-2,2));
            final int hour=m.hour;row.setOnClickListener(v->openDetail(hour));
            root.addView(row,lp(-1,-2,6));
        }

        LinearLayout tech=new LinearLayout(this);tech.setOrientation(LinearLayout.VERTICAL);tech.setVisibility(View.GONE);
        tech.addView(text(NOTE,12,MUTED,false),lp(-1,-2,4));
        TextView exact=text((Store.technical==null?"":Store.technical)+(Store.legacyTechnical==null?"":"\n\nPrevious outer-metal analysis (Alpha107, kept for reference; not used for the results above):\n"+Store.legacyTechnical),11,MUTED,false);
        exact.setTextIsSelectable(true);tech.addView(exact,lp(-1,-2,6));
        root.addView(toggle("Technical details",tech),lp(-1,dp(48),18));root.addView(tech,lp(-1,-2,4));
        root.addView(text(DISCLAIMER,12,MUTED,false),lp(-1,-2,14));
        return scroll;
    }

    static final String DISCLAIMER="Watch Align checks the alignment of each hour marker against verified genuine Rolex watches. It is not an authenticity check or a buying recommendation.";
    static final String NOTE="Each marker's luminous shape is fitted by position and rotation only and compared with the range measured on verified genuine watches of this model, allowing for this photo's measurement uncertainty. A marker's genuine reference is placed by the other markers, never by the marker itself. The photo is treated as taken straight on.";

    private View defectCard(AlignmentDecision.Marker m){
        LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.setBackgroundColor(CARD);t.setPadding(dp(10),dp(10),dp(10),dp(12));
        t.addView(text(m.title()+" — Defect detected",18,RED,true));
        t.addView(text("Primary issue: "+m.primary.headline()+".",16,Color.WHITE,true),lp(-1,-2,4));
        Bitmap c=Store.closeups.get(m.hour);
        if(c!=null){ImageView iv=new ImageView(this);iv.setImageBitmap(c);iv.setAdjustViewBounds(true);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);t.addView(iv,lp(-1,-2,8));}
        t.addView(text(m.primary.measured(),14,Color.WHITE,false),lp(-1,-2,8));
        t.addView(text(m.primary.genuine(),14,MUTED,false),lp(-1,-2,2));
        final int hour=m.hour;t.setOnClickListener(v->openDetail(hour));
        return t;
    }

    private void openDetail(int hour){Intent i=new Intent(this,AlignmentDetailActivity.class);i.putExtra(AlignmentDetailActivity.EXTRA_HOUR,hour);startActivity(i);}

    private Button toggle(String name,View target){
        Button b=new Button(this);b.setAllCaps(false);b.setText(name+"  ▸");
        b.setOnClickListener(v->{boolean show=target.getVisibility()!=View.VISIBLE;target.setVisibility(show?View.VISIBLE:View.GONE);b.setText(name+(show?"  ▾":"  ▸"));});
        return b;
    }
    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
