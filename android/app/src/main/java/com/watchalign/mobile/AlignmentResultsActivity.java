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
            RED=Color.rgb(255,99,88),GREEN=Color.rgb(72,209,108),GREY=Color.rgb(160,160,168),AMBER=Color.rgb(255,190,60);

    /** Process-local handoff from the start screen. */
    static final class Store {
        static AlignmentAnalysis.Output result;static ModelSpec model;static String modelLabel;
        static Bitmap overview,photo;static final Map<Integer,Bitmap> closeups=new HashMap<>();
        static String technical,legacyTechnical;
        /** 1.6.2 calibration: the pose, the clearly-off result (re-judged when the owner changes the limits) and this
         *  photo's saved record */
        static double[] H;static ClearlyOff.Result clearlyOff;static String recordId;
        private Store(){}
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(Store.result==null||Store.model==null){finish();return;}
        setTitle("Watch Align");
        setContentView(build());
    }
    @Override protected void onRestart(){super.onRestart();if(Store.result!=null)setContentView(build());}   // 1.6.2: after calibration

    static int colour(AlignmentDecision.Outcome o){return o==AlignmentDecision.Outcome.DEFECT?RED:o==AlignmentDecision.Outcome.NO_DEFECT?GREEN:GREY;}
    static int colour(AlignmentDecision.Marker m){return m.worthALook()?AMBER:colour(m.outcome);}

    private View build(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-2));
        AlignmentAnalysis.Output o=Store.result;
        root.addView(text("Watch Align",14,MUTED,true));
        int nOff=o.count(AlignmentDecision.Outcome.DEFECT)-AlignmentAnalysis.inspect(o);
        root.addView(text(AlignmentAnalysis.headline(o),26,nOff>0?RED:AlignmentAnalysis.inspect(o)>0?AMBER:Color.WHITE,true),lp(-1,-2,2));
        root.addView(text(AlignmentAnalysis.counts(o),14,MUTED,false),lp(-1,-2,4));
        if(Store.modelLabel!=null)root.addView(text(Store.modelLabel,13,MUTED,false),lp(-1,-2,2));
        if(Store.clearlyOff!=null&&!Store.clearlyOff.limits.isDefault())
            root.addView(text("Judged with your saved limits (tap a marker, then Calibrate, to change or reset them).",13,AMBER,false),lp(-1,-2,4));
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
            root.addView(text(AlignmentAnalysis.tracked(o)?"✓ no visible issue   ! flagged for review   ? inspect   – can't check   ·   tap a marker"
                    :"✓ no defect   ! defect detected   ? worth a look   – cannot assess   ·   tap a marker",12,MUTED,false),lp(-1,-2,4));
        }

        // a card for every defect, the dominant issue first
        for(AlignmentDecision.Marker m:o.markers)if(m.outcome==AlignmentDecision.Outcome.DEFECT)root.addView(defectCard(m),lp(-1,-2,14));

        // every marker, one line each
        root.addView(text("All markers",18,Color.WHITE,true),lp(-1,-2,18));
        for(AlignmentDecision.Marker m:o.markers){
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setBackgroundColor(CARD);row.setPadding(dp(12),dp(10),dp(12),dp(10));
            LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);
            top.addView(text(m.title(),16,Color.WHITE,true),new LinearLayout.LayoutParams(0,-2,1f));
            top.addView(text(m.label(),16,colour(m),true));
            row.addView(top);
            String sub=AlignmentDecision.summary(m);
            if(sub!=null)row.addView(text(sub,13,MUTED,false),lp(-1,-2,2));
            final int hour=m.hour;row.setOnClickListener(v->openDetail(hour));
            root.addView(row,lp(-1,-2,6));
        }

        LinearLayout tech=new LinearLayout(this);tech.setOrientation(LinearLayout.VERTICAL);tech.setVisibility(View.GONE);
        tech.addView(text(AlignmentAnalysis.tracked(o)?TRACK_NOTE:NOTE,12,MUTED,false),lp(-1,-2,4));
        TextView exact=text((Store.technical==null?"":Store.technical)+(Store.legacyTechnical==null?"":"\n\nPrevious outer-metal analysis (Alpha107, kept for reference; not used for the results above):\n"+Store.legacyTechnical),11,MUTED,false);
        exact.setTextIsSelectable(true);tech.addView(exact,lp(-1,-2,6));
        Button saved=new Button(this);saved.setAllCaps(false);saved.setText("Saved photos and calibration");
        saved.setOnClickListener(v->startActivity(new Intent(this,SavedPhotosActivity.class)));root.addView(saved,lp(-1,dp(48),18));
        root.addView(toggle("Technical details",tech),lp(-1,dp(48),8));root.addView(tech,lp(-1,-2,4));
        root.addView(text(DISCLAIMER,12,MUTED,false),lp(-1,-2,14));
        return scroll;
    }

    static final String DISCLAIMER="Watch Align checks whether each hour marker is clearly out of place on this photo. It is not an authenticity check or a buying recommendation.";
    static final String TRACK_NOTE="Each marker is measured against references found in this photo: its hour line (from the hour tick of the printed minute track, through the centre, to the opposite hour tick) and the ring through the other markers. With the default limits, a round marker or baton is flagged for review when its centre sits more than "+String.format(java.util.Locale.US,"%.2f",100*ClearlyOff.POS_LIMIT_R)+"% of the dial radius from where it should be, a baton when it is tilted more than "+String.format(java.util.Locale.US,"%.2f",ClearlyOff.TILT_LIMIT_DEG)+"°, and the 12 o'clock triangle when it sits more than 1.0% to the side of its line, more than 1.25% in or out, leaves less than 2.2% of the dial radius clear under the minute track, or its top edge is turned more than "+String.format(java.util.Locale.US,"%.1f",ClearlyOff.TRI_ROT_LIMIT_DEG)+"° against the line through the inner ends of minute ticks 59 and 1 (a small allowance is made on low-resolution photos). These are configurable review limits, not certified Rolex factory tolerances; stricter limits may flag genuine watches. Your own limits, if saved, replace them. Since 1.6.3 each marker is measured from its visible outer metal; a hand or glare only removes the part of the edge it covers, and the rest of the genuine outline (fixed shape and size) is fitted to what is seen. When a limit is exceeded but the visible edge is uncertain, the marker is shown as a possible misalignment to inspect. A marker is not checked when too little of its edge is visible to place it, or when a hand covers most of it.";
    static final String NOTE="Each marker's luminous shape is fitted by position and rotation only and compared with the range measured on verified genuine watches of this model, allowing for this photo's measurement uncertainty. A marker's genuine luminous reference is placed by the other markers, never by the marker itself (the separate outer-metal check uses the established Alpha107 ring, which includes the marker, as its genuine references do). Round markers are reported only when also displaced by more than 1.00% of the dial radius. The outer metal of the triangle and batons is checked separately. The photo is treated as taken straight on.";

    private View defectCard(AlignmentDecision.Marker m){
        LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.setBackgroundColor(CARD);t.setPadding(dp(10),dp(10),dp(10),dp(12));
        if(m.track!=null){
            t.addView(text(m.title()+" — "+m.label(),18,colour(m),true));
            t.addView(text(ClearlyOff.headline(m.track)+".",16,Color.WHITE,true),lp(-1,-2,4));
            if(m.inspect())t.addView(text("Measured from an uncertain edge: "+m.track.reason+". Check the close-up yourself.",14,AMBER,false),lp(-1,-2,4));
            Bitmap ev=Store.closeups.get(m.hour);
            if(ev!=null){ImageView iv=new ImageView(this);iv.setImageBitmap(ev);iv.setAdjustViewBounds(true);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);t.addView(iv,lp(-1,-2,8));}
            t.addView(text(ClearlyOff.numbers(m.track),13,MUTED,false),lp(-1,-2,8));
            final int hr=m.hour;t.setOnClickListener(v->openDetail(hr));
            return t;
        }
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
