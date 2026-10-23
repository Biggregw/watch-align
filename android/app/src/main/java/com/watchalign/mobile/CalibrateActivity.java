package com.watchalign.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Watch Align 1.6.2: calibrate one marker TYPE from a tapped marker (owner, 18 Oct 2026). The marker's close-up shows the
 * allowed zone, drawn from the limits being edited:
 *   round / baton  a circle around where the centre should be (hour line x ring); the cross is the measured centre
 *   triangle       two lines either side of the hour line (sideways) and the line its top edge must stay below (gap)
 *   batons, 12     a wedge either side of the hour line (rotation)
 * Each slider changes the zone, the answer, the count of verified genuine markers the setting would flag, and the
 * answers on every saved photo, live. The owner's Pass / Fail / Unsure for this marker is saved with the photo.
 * Saving applies to every marker of the type, on this and later photos; Reset brings back the tested limits.
 */
public class CalibrateActivity extends Activity {
    static final String EXTRA_HOUR="hour";
    static final int BG=AlignmentResultsActivity.BG,CARD=AlignmentResultsActivity.CARD,MUTED=AlignmentResultsActivity.MUTED,
            RED=AlignmentResultsActivity.RED,GREEN=AlignmentResultsActivity.GREEN,GREY=AlignmentResultsActivity.GREY,AMBER=AlignmentResultsActivity.AMBER;
    static final int GUIDE=Color.rgb(80,200,255);

    private int hour;private ModelSpec.Shape shape;private String model;
    private ClearlyOff.Limits saved,draft;
    private ClearlyOff.Marker live;
    private Calibration.Genuine genuine;private List<Calibration.Record> records=new ArrayList<>();
    private Bitmap base;private double cx,cy,half;
    private ImageView picture;private TextView answer,genuineLine,photosLine,agreeLine,verdictLine;
    private final List<Runnable> sliderRefresh=new ArrayList<>();
    private Button[] verdictButtons;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        hour=getIntent().getIntExtra(EXTRA_HOUR,-1);
        ClearlyOff.Result r=AlignmentResultsActivity.Store.clearlyOff;ModelSpec ms=AlignmentResultsActivity.Store.model;
        ClearlyOff.Marker shown=r==null?null:r.at(hour);live=shown==null?null:shown.copy();
        if(live==null||ms==null){finish();return;}
        shape=live.shape;model=ms.id;
        saved=ClearlyOff.forModel(model);draft=saved.copy();
        genuine=CalibrationStore.genuine(this,model);
        records=CalibrationStore.all(this);
        setTitle("Watch Align");
        prepareBase(ms,r);
        setContentView(build());
        refresh();
    }

    // ------------------------------------------------------------------------------------------------ picture

    private void prepareBase(ModelSpec ms,ClearlyOff.Result r){
        ModelSpec.Marker mk=ms.atHour(hour);
        double[] c=mk!=null?mk.masterPoint():ClearlyOff.cpt(0.75,Math.toRadians(30*(hour%12)));
        cx=c[0];cy=c[1];half=shape==ModelSpec.Shape.ROUND?0.13:shape==ModelSpec.Shape.BATON?0.17:0.21;
        if(shape==ModelSpec.Shape.TRIANGLE){double[] q=ClearlyOff.cpt(0.80,Math.toRadians(30*(hour%12)));cx=q[0];cy=q[1];}
        try{
            Mat rgba=new Mat();Utils.bitmapToMat(AlignmentResultsActivity.Store.photo,rgba);
            Mat m=Alpha98Closeups.render(rgba,AlignmentResultsActivity.Store.H,cx,cy,half);rgba.release();
            base=Bitmap.createBitmap(m.cols(),m.rows(),Bitmap.Config.ARGB_8888);Utils.matToBitmap(m,base);m.release();
        }catch(Throwable t){base=null;}
    }

    /** canonical -> picture px */
    private float px(double x){return (float)((x-(cx-half))/(2*half)*Alpha98Closeups.SIZE-0.5);}
    private float py(double y){return (float)((y-(cy-half))/(2*half)*Alpha98Closeups.SIZE-0.5);}
    private float pr(double r){return (float)(r/(2*half)*Alpha98Closeups.SIZE);}

    private Bitmap drawZone(){
        if(base==null)return null;
        Bitmap b=base.copy(Bitmap.Config.ARGB_8888,true);Canvas cv=new Canvas(b);
        int col=live.state==ClearlyOff.State.CLEARLY_OFF?RED:live.state==ClearlyOff.State.POSSIBLE?AMBER:live.state==ClearlyOff.State.NO_VISIBLE_ISSUE?GREEN:GREY;
        Paint guide=paint(GUIDE,3,false),zone=paint(AMBER,4,false),meas=paint(col,4,false),fill=paint(Color.argb(60,255,190,60),0,true);
        Paint dashed=paint(AMBER,4,false);dashed.setPathEffect(new DashPathEffect(new float[]{14,10},0));
        Double line=AlignmentResultsActivity.Store.clearlyOff.lines.get(hour%12);
        double a=line!=null?line:Math.toRadians(30*(hour%12));
        double[] p0=ClearlyOff.cpt(0.40,a),p1=ClearlyOff.cpt(1.05,a);
        cv.drawLine(px(p0[0]),py(p0[1]),px(p1[0]),py(p1[1]),guide);
        double[] c=live.cObs;
        if(shape==ModelSpec.Shape.TRIANGLE){
            // sideways: two lines parallel to the hour line, the limit either side
            double off=live.sideLimitPct/100,nx=Math.cos(a),ny=Math.sin(a);
            for(int s=-1;s<=1;s+=2){double[] q0=ClearlyOff.cpt(0.55,a),q1=ClearlyOff.cpt(1.0,a);
                cv.drawLine(px(q0[0]+s*off*nx),py(q0[1]+s*off*ny),px(q1[0]+s*off*nx),py(q1[1]+s*off*ny),zone);}
            // gap: the tick ends (blue), the line the top edge must stay below (amber, dashed), the measured edge
            if(Double.isFinite(live.gapTrackR)){
                arc(cv,live.gapTrackR,a,guide);
                if(Double.isFinite(live.gapLimitPct))arc(cv,live.gapTrackR-live.gapLimitPct/100,a,dashed);
                if(Double.isFinite(live.gapEdgeR))arc(cv,live.gapEdgeR,a,meas);
            }
        }else if(c!=null&&!live.relativePositionUsed&&Double.isFinite(live.posLimitPct)){
            // position: a circle around where the centre should be
            double re=Double.isFinite(live.scale)&&live.cGen!=null?live.scale*ClearlyOff.norm(live.cGen):ClearlyOff.norm(c);
            double[] e=ClearlyOff.cpt(re,a);
            cv.drawCircle(px(e[0]),py(e[1]),pr(live.posLimitPct/100),fill);
            cv.drawCircle(px(e[0]),py(e[1]),pr(live.posLimitPct/100),zone);
        }
        if(c!=null&&shape!=ModelSpec.Shape.ROUND&&Double.isFinite(live.tiltLimitDeg)){
            // rotation: a wedge either side of the hour line through the marker
            double L=shape==ModelSpec.Shape.BATON?0.14:0.16;
            for(int s=-1;s<=1;s+=2){double t=a+s*Math.toRadians(live.tiltLimitDeg);
                cv.drawLine(px(c[0]-L*Math.sin(t)),py(c[1]+L*Math.cos(t)),px(c[0]+L*Math.sin(t)),py(c[1]-L*Math.cos(t)),dashed);}
        }
        if(c!=null&&shape!=ModelSpec.Shape.ROUND&&Double.isFinite(live.tiltDeg)){
            double t=a+Math.toRadians(live.tiltDeg),L=0.12;
            cv.drawLine(px(c[0]-L*Math.sin(t)),py(c[1]+L*Math.cos(t)),px(c[0]+L*Math.sin(t)),py(c[1]-L*Math.cos(t)),meas);
        }
        if(c!=null){float x=px(c[0]),y=py(c[1]),u=14;
            Paint k=paint(Color.BLACK,9,false);cv.drawLine(x-u,y,x+u,y,k);cv.drawLine(x,y-u,x,y+u,k);
            cv.drawLine(x-u,y,x+u,y,meas);cv.drawLine(x,y-u,x,y+u,meas);}
        return b;
    }
    private void arc(Canvas cv,double r,double a,Paint p){
        RectF o=new RectF(px(-r),py(-r),px(r),py(r));
        float mid=(float)(Math.toDegrees(a)-90);cv.drawArc(o,mid-10,20,false,p);
    }
    private static Paint paint(int c,float w,boolean fill){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(c);p.setStrokeWidth(w);
        p.setStyle(fill?Paint.Style.FILL:Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);return p;}

    // ------------------------------------------------------------------------------------------------ layout

    private String typeName(){return shape==ModelSpec.Shape.ROUND?"round markers":shape==ModelSpec.Shape.BATON?"batons":"12 o'clock triangle";}

    private View build(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-2));
        root.addView(text("Calibrate",14,MUTED,true));
        root.addView(text(hour+" o'clock — "+typeName(),24,Color.WHITE,true),lp(-1,-2,2));
        root.addView(text(shape==ModelSpec.Shape.TRIANGLE?"Settings for the 12 o'clock triangle.":"Settings apply to all "+typeName()+" on this and later photos.",13,MUTED,false),lp(-1,-2,4));
        answer=text("",18,Color.WHITE,true);root.addView(answer,lp(-1,-2,10));
        picture=new ImageView(this);picture.setAdjustViewBounds(true);picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(picture,lp(-1,-2,8));
        root.addView(text(legend(),12,MUTED,false),lp(-1,-2,4));

        // the owner's verdict on this marker
        root.addView(text("Your verdict on this marker",16,Color.WHITE,true),lp(-1,-2,16));
        LinearLayout vr=new LinearLayout(this);vr.setOrientation(LinearLayout.HORIZONTAL);
        Calibration.Verdict[] vs=Calibration.Verdict.values();String[] names={"Pass","Fail","Unsure"};verdictButtons=new Button[vs.length];
        for(int i=0;i<vs.length;i++){Button b=new Button(this);b.setAllCaps(false);b.setText(names[i]);final Calibration.Verdict v=vs[i];
            b.setOnClickListener(x->setVerdict(v));verdictButtons[i]=b;vr.addView(b,new LinearLayout.LayoutParams(0,dp(48),1f));}
        root.addView(vr,lp(-1,-2,4));
        verdictLine=text("",12,MUTED,false);root.addView(verdictLine,lp(-1,-2,2));

        // sliders
        // 1.6.6: the two standards, kept apart: the fixed strict genuine standard and these adjustable replica-QC limits
        root.addView(text("Strict genuine standard (fixed)",16,Color.WHITE,true),lp(-1,-2,16));
        root.addView(text(WatchClassification.standardText(),12,MUTED,false),lp(-1,-2,4));
        root.addView(text("Replica QC limits (adjustable)",16,Color.WHITE,true),lp(-1,-2,16));
        for(Calibration.Param p:Calibration.params(shape))root.addView(slider(p),lp(-1,-2,8));
        genuineLine=text("",14,Color.WHITE,true);root.addView(genuineLine,lp(-1,-2,12));
        photosLine=text("",13,MUTED,false);root.addView(photosLine,lp(-1,-2,8));
        agreeLine=text("",13,MUTED,false);root.addView(agreeLine,lp(-1,-2,8));

        Button suggest=button("Match my verdicts",v->suggest());root.addView(suggest,lp(-1,dp(48),12));
        Button save=button("Save for "+(shape==ModelSpec.Shape.TRIANGLE?"the 12 o'clock triangle":"all "+typeName()),v->save());root.addView(save,lp(-1,dp(48),6));
        Button reset=button("Reset to defaults",v->{ClearlyOff.Limits d=ClearlyOff.Limits.defaults();for(Calibration.Param p:Calibration.params(shape))p.set(draft,p.get(d));refreshSliders();refresh();});
        root.addView(reset,lp(-1,dp(48),6));
        Button photos=button("Saved photos",v->startActivity(new Intent(this,SavedPhotosActivity.class)));root.addView(photos,lp(-1,dp(48),6));
        root.addView(text("Your saved photos, verdicts and limits stay on this phone. Export shares the numbers only, never the photos.",12,MUTED,false),lp(-1,-2,12));
        return scroll;
    }

    private String legend(){
        if(shape==ModelSpec.Shape.TRIANGLE)return "Blue: the hour line and the minute-track tick ends. Amber: the sideways limits either side of the line, and (dashed) the line the triangle's top edge must stay below. Coloured arc: the measured top edge. Cross: the measured centre.";
        String s=live.relativePositionUsed
                ?"Blue: the printed minute-track hour line. The position limit is scored against the other credible markers and that absolute hour line; a legacy centre-circle would be misleading, so none is drawn. Cross: observed centre."
                :"Blue: the hour line. Amber circle: the legacy centre limit (used when a relative fit is unavailable). Cross: measured centre.";
        if(shape==ModelSpec.Shape.BATON)s+=" Dashed amber: the rotation limit either side of the line; coloured line: the measured baton axis.";
        return s;
    }

    private View slider(Calibration.Param p){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setBackgroundColor(CARD);box.setPadding(dp(12),dp(8),dp(12),dp(8));
        TextView label=text("",14,Color.WHITE,false);box.addView(label);
        SeekBar bar=new SeekBar(this);int steps=(int)Math.round((p.max-p.min)/p.step)+(p.canDisable?1:0);bar.setMax(steps);
        box.addView(bar,lp(-1,-2,4));
        ClearlyOff.Limits def=ClearlyOff.Limits.defaults();
        Runnable show=()->{double v=p.get(draft);
            String val=Double.isNaN(v)?"not judged":String.format(Locale.US,"%.2f%s",v,p.unit);
            double dv=p.get(def);String d=Double.isNaN(dv)?"not judged":String.format(Locale.US,"%.2f%s",dv,p.unit);
            label.setText(p.name+": "+val+"   (default "+d+")");
            int pos=Double.isNaN(v)?steps:(int)Math.round((Math.max(p.min,Math.min(p.max,v))-p.min)/p.step);
            if(bar.getProgress()!=pos)bar.setProgress(pos);};
        sliderRefresh.add(show);show.run();
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int pr,boolean user){if(!user)return;
                double v=p.canDisable&&pr==steps?Double.NaN:Math.round((p.min+pr*p.step)*100)/100.0;p.set(draft,v);show.run();refresh();}
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){refreshSlow();}
        });
        return box;
    }
    private void refreshSliders(){for(Runnable r:sliderRefresh)r.run();}

    // ------------------------------------------------------------------------------------------------ live update

    /** fast: this marker's answer and picture, and the genuine count */
    private void refresh(){
        ClearlyOff.Result r=AlignmentResultsActivity.Store.clearlyOff;
        ClearlyOff.decide(live,r.rpx,null,draft);
        if(AlignmentResultsActivity.Store.result!=null&&AlignmentResultsActivity.Store.result.photoReason!=null){live.state=ClearlyOff.State.CANT_CHECK;live.reason=AlignmentResultsActivity.Store.result.photoReason;}
        String word=Calibration.word(live.state);
        answer.setText("With these limits: "+word+(Calibration.flagged(live.state)?" — "+ClearlyOff.headline(live).toLowerCase(Locale.UK):
                live.state==ClearlyOff.State.CANT_CHECK?" — "+live.reason:""));
        answer.setTextColor(live.state==ClearlyOff.State.CLEARLY_OFF?RED:live.state==ClearlyOff.State.POSSIBLE?AMBER:live.state==ClearlyOff.State.NO_VISIBLE_ISSUE?GREEN:GREY);
        Bitmap z=drawZone();if(z!=null)picture.setImageBitmap(z);
        if(genuine==null||genuine.total(shape)==0){genuineLine.setText("No verified genuine numbers for this model yet: be careful not to set limits that real watches would fail.");genuineLine.setTextColor(AMBER);}
        else if(shape!=ModelSpec.Shape.TRIANGLE&&genuine.totalComparable(shape)==0){
            genuineLine.setText("Verified genuine reference numbers use the old positional metric. These cannot safely validate the new 1.6.8 position limit; automatic suggestions are unavailable until matching measurements exist.");
            genuineLine.setTextColor(AMBER);
        }
        else{int n=genuine.flaggedComparable(shape,draft),t=genuine.totalComparable(shape);
            genuineLine.setText(String.format(Locale.UK,"Genuine watches: %d of %,d measured %s would be flagged for review at these limits.",n,t,shape==ModelSpec.Shape.TRIANGLE?"12 o'clock triangles":typeName()));
            genuineLine.setTextColor(n>0?RED:GREEN);}
        refreshVerdict();
        if(photosLine.getText().length()==0)refreshSlow();
    }
    /** the saved photos and the verdict agreement (on release of a slider) */
    private void refreshSlow(){
        List<String> ch=Calibration.changes(records,model,saved,draft);
        int n=0;for(Calibration.Record x:records)if(model.equals(x.model))n++;
        StringBuilder s=new StringBuilder();
        if(n==0)s.append("No saved photos of this model yet.");
        else if(ch.isEmpty())s.append("Your ").append(n).append(" saved photo").append(n==1?"":"s").append(" of this model: no answer changes against your saved limits.");
        else{s.append("Your ").append(n).append(" saved photo").append(n==1?"":"s").append(" of this model: ").append(ch.size()).append(ch.size()==1?" answer changes":" answers change").append(" against your saved limits:");
            for(int i=0;i<Math.min(8,ch.size());i++)s.append("\n• ").append(ch.get(i));
            if(ch.size()>8)s.append("\n• and ").append(ch.size()-8).append(" more");}
        photosLine.setText(s);
        Calibration.Agreement a=Calibration.agreement(records,model,shape,draft);
        if(a.total==0)agreeLine.setText("Record Pass or Fail on "+typeName()+" (above) to see how these limits agree with you.");
        else{StringBuilder t=new StringBuilder(String.format(Locale.UK,"Your verdicts on %s: the app agrees on %d of %d.",typeName(),a.agree,a.total));
            for(int i=0;i<Math.min(6,a.disagree.size());i++)t.append("\n• ").append(a.disagree.get(i));
            if(a.disagree.size()>6)t.append("\n• and ").append(a.disagree.size()-6).append(" more");
            agreeLine.setText(t);}
    }

    private Calibration.Record current(){
        String id=AlignmentResultsActivity.Store.recordId;if(id==null)return null;
        for(Calibration.Record r:records)if(id.equals(r.id))return r;
        Calibration.Record r=CalibrationStore.get(this,id);if(r!=null)records.add(0,r);return r;
    }
    private void setVerdict(Calibration.Verdict v){
        Calibration.Record r=current();Calibration.Mark k=r==null?null:r.at(hour);
        if(k==null){Toast.makeText(this,"This photo could not be saved for calibration.",Toast.LENGTH_SHORT).show();return;}
        k.verdict=k.verdict==v?null:v;
        CalibrationStore.save(this,r,null);
        refreshVerdict();refreshSlow();
    }
    private void refreshVerdict(){
        Calibration.Record r=current();Calibration.Mark k=r==null?null:r.at(hour);Calibration.Verdict v=k==null?null:k.verdict;
        for(int i=0;i<verdictButtons.length;i++){boolean on=v==Calibration.Verdict.values()[i];
            verdictButtons[i].setTextColor(on?Color.BLACK:Color.WHITE);
            verdictButtons[i].getBackground().mutate().setTint(on?(i==0?GREEN:i==1?RED:AMBER):Color.rgb(45,60,85));}
        verdictLine.setText(v==null?"Not recorded. Tap again to clear.":"Saved with this photo. Tap again to clear.");
    }

    private void suggest(){
        if(shape!=ModelSpec.Shape.TRIANGLE&&(genuine==null||genuine.totalComparable(shape)==0)){
            Toast.makeText(this,"Position suggestions require genuine references measured by the same 1.6.8 engine.",Toast.LENGTH_LONG).show();
            return;
        }
        ClearlyOff.Limits s=Calibration.suggest(records,model,shape,draft,genuine);
        if(s==null){Toast.makeText(this,"Record Pass or Fail on some "+typeName()+" first.",Toast.LENGTH_LONG).show();return;}
        for(Calibration.Param p:Calibration.params(shape))p.set(draft,p.get(s));
        refreshSliders();refresh();refreshSlow();
        Toast.makeText(this,"Set to the limits that agree most with your verdicts without failing a genuine watch. Save to keep them.",Toast.LENGTH_LONG).show();
    }

    private void save(){
        ClearlyOff.Limits all=ClearlyOff.forModel(model);
        for(Calibration.Param p:Calibration.params(shape))p.set(all,p.get(draft));
        CalibrationStore.saveLimits(this,model,all);
        saved=all.copy();
        Toast.makeText(this,all.isDefault()?"Back to the default limits.":"Saved. Re-checking this photo…",Toast.LENGTH_SHORT).show();
        new Thread(()->{Rejudge.current(all);runOnUiThread(()->{refreshSlow();Toast.makeText(this,"Done: this photo's answers use the new limits.",Toast.LENGTH_SHORT).show();});}).start();
    }

    private Button button(String s,View.OnClickListener l){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setOnClickListener(l);return b;}
    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
