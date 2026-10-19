package com.watchalign.mobile;

import android.app.Activity;
import android.app.AlertDialog;
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

import java.util.List;

/**
 * Watch Align 1.6.2: the photos checked on this phone (numbers, verdicts and a small picture, kept on the phone only),
 * each judged against the owner's current saved limits. Export shares the limits, the numbers and the verdicts as
 * text (no photos).
 */
public class SavedPhotosActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);setTitle("Watch Align");}
    @Override protected void onResume(){super.onResume();setContentView(build());}

    private View build(){
        int pad=dp(16);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(AlignmentResultsActivity.BG);
        scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad,pad,pad,pad);scroll.addView(root,new ViewGroup.LayoutParams(-1,-2));
        List<Calibration.Record> all=CalibrationStore.all(this);
        root.addView(text("Saved photos",24,Color.WHITE,true));
        root.addView(text(all.size()+" photo"+(all.size()==1?"":"s")+" checked on this phone, judged with your current limits"
                +(ClearlyOff.USER.isEmpty()?" (the defaults)":" (your saved limits)")+". Only the numbers, your verdicts and a small picture are kept, on this phone.",13,AlignmentResultsActivity.MUTED,false),lp(-1,-2,4));
        Button export=new Button(this);export.setAllCaps(false);export.setText("Export limits, numbers and verdicts");
        export.setOnClickListener(v->exportAll(all));root.addView(export,lp(-1,dp(48),12));
        for(Calibration.Record r:all)root.addView(row(r),lp(-1,-2,8));
        if(!all.isEmpty()){
            Button wipe=new Button(this);wipe.setAllCaps(false);wipe.setText("Delete all saved photos");
            wipe.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("Delete all "+all.size()+" saved photos and their verdicts from this phone?")
                    .setPositiveButton("Delete",(d,w)->{for(Calibration.Record x:all)CalibrationStore.delete(this,x.id);setContentView(build());})
                    .setNegativeButton("Cancel",null).show());
            root.addView(wipe,lp(-1,dp(48),16));
        }
        return scroll;
    }

    private View row(Calibration.Record r){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setBackgroundColor(AlignmentResultsActivity.CARD);row.setPadding(dp(10),dp(10),dp(10),dp(10));
        Bitmap th=CalibrationStore.thumb(this,r.id);
        if(th!=null){ImageView iv=new ImageView(this);iv.setImageBitmap(th);iv.setAdjustViewBounds(true);row.addView(iv,new LinearLayout.LayoutParams(dp(84),dp(84)));}
        LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setPadding(dp(10),0,0,0);
        ClearlyOff.Limits L=ClearlyOff.forModel(r.model);
        int off=0,ok=0,cant=0,verd=0;StringBuilder offHours=new StringBuilder();
        for(Calibration.Mark k:r.marks){ClearlyOff.State s=Calibration.state(r,k,L);
            if(Calibration.flagged(s)){off++;offHours.append(offHours.length()>0?", ":"").append(k.hour);}else if(s==ClearlyOff.State.NO_VISIBLE_ISSUE)ok++;else cant++;
            if(k.verdict!=null)verd++;}
        col.addView(text(r.label,15,Color.WHITE,true));
        col.addView(text(off==0?"No markers flagged for review":off+" flagged for review ("+offHours+")",14,off>0?AlignmentResultsActivity.RED:AlignmentResultsActivity.GREEN,true),lp(-1,-2,2));
        col.addView(text(ok+" no visible issue · "+cant+" can't check"+(verd>0?" · "+verd+" verdict"+(verd==1?"":"s"):""),13,AlignmentResultsActivity.MUTED,false),lp(-1,-2,2));
        Button del=new Button(this);del.setAllCaps(false);del.setText("Delete");
        del.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("Delete this saved photo and its verdicts?")
                .setPositiveButton("Delete",(d,w)->{CalibrationStore.delete(this,r.id);setContentView(build());}).setNegativeButton("Cancel",null).show());
        col.addView(del,lp(-2,dp(40),4));
        row.addView(col,new LinearLayout.LayoutParams(0,-2,1f));
        return row;
    }

    private void exportAll(List<Calibration.Record> all){
        String text=Calibration.export(CalibrationStore.limitsForExport(),all,version());
        Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,"Watch Align calibration");i.putExtra(Intent.EXTRA_TEXT,text);
        startActivity(Intent.createChooser(i,"Export calibration"));
    }

    private String version(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "";}}
    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
