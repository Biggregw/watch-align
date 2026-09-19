package com.watchalign.mobile;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;

/** Dedicated non-scrolling image inspector with visual overlay comparison tools. */
public class FullscreenInspectActivity extends Activity {
    private ZoomableImageView image;
    private Bitmap base,overlay;
    private float alpha=1f;
    private float nudgeDx=0f,nudgeDy=0f,nudgeRot=0f,nudgeScale=1f;
    private float overlayPivotX,overlayPivotY;
    private SeekBar xSeek,ySeek,rotSeek,scaleSeek;
    private TextView xValue,yValue,rotValue,scaleValue;
    private boolean syncingControls=false;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(8,17,31));getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        overlay=InspectionImageStore.bitmap;base=InspectionImageStore.baseBitmap;if(overlay==null){finish();return;}
        findOverlayPivot();
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(8,17,31));
        image=new ZoomableImageView(this);image.setBackgroundColor(Color.rgb(8,17,31));image.setImageBitmap(overlay);root.addView(image,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),dp(8),dp(10),dp(8));top.setBackgroundColor(0xAA08111F);
        Button back=new Button(this);back.setText("Back");back.setAllCaps(false);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(78),dp(46)));
        TextView title=new TextView(this);title.setText(InspectionImageStore.title==null?"Inspection":InspectionImageStore.title);title.setTextColor(Color.WHITE);title.setTextSize(17);title.setPadding(dp(10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        Button reset=new Button(this);reset.setText("Reset");reset.setAllCaps(false);reset.setOnClickListener(v->{image.resetZoom();resetNudge();});top.addView(reset,new LinearLayout.LayoutParams(dp(78),dp(46)));root.addView(top,new FrameLayout.LayoutParams(-1,dp(62),Gravity.TOP));

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(10),dp(4),dp(10),dp(6));bottom.setBackgroundColor(0xD008111F);
        boolean hasDiagnostics=InspectionImageStore.diagnosticsText!=null&&!InspectionImageStore.diagnosticsText.isEmpty();
        if(InspectionImageStore.overlayMode&&base!=null){
            LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);
            TextView label=new TextView(this);label.setText("Overlay");label.setTextColor(Color.WHITE);label.setTextSize(12);controls.addView(label,new LinearLayout.LayoutParams(dp(54),dp(38)));
            SeekBar seek=new SeekBar(this);seek.setMax(100);seek.setProgress(100);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){alpha=p/100f;refresh();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});controls.addView(seek,new LinearLayout.LayoutParams(0,dp(38),1));
            Button blink=new Button(this);blink.setText("Hold to blink");blink.setAllCaps(false);blink.setTextSize(11);blink.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){image.setImageBitmapPreserveZoom(base);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){refresh();return true;}return false;});controls.addView(blink,new LinearLayout.LayoutParams(dp(118),dp(38)));bottom.addView(controls);

            TextView manualTitle=new TextView(this);manualTitle.setText("MANUAL MASTER ALIGNMENT");manualTitle.setTextColor(Color.rgb(50,213,242));manualTitle.setTextSize(11);manualTitle.setPadding(0,dp(2),0,0);bottom.addView(manualTitle,new LinearLayout.LayoutParams(-1,dp(24)));

            xValue=valueText();yValue=valueText();rotValue=valueText();scaleValue=valueText();
            xSeek=makeSeek(400,200,p->{nudgeDx=p-200f;updateValueLabels();refresh();});
            ySeek=makeSeek(400,200,p->{nudgeDy=p-200f;updateValueLabels();refresh();});
            rotSeek=makeSeek(600,300,p->{nudgeRot=(p-300f)*0.05f;updateValueLabels();refresh();});
            scaleSeek=makeSeek(600,300,p->{nudgeScale=0.70f+p/1000f;updateValueLabels();refresh();});
            bottom.addView(adjustRow("Move X",xSeek,xValue));
            bottom.addView(adjustRow("Move Y",ySeek,yValue));
            bottom.addView(adjustRow("Rotate",rotSeek,rotValue));
            bottom.addView(adjustRow("Size",scaleSeek,scaleValue));

            LinearLayout fineRow=new LinearLayout(this);fineRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView fineLabel=new TextView(this);fineLabel.setText("Fine");fineLabel.setTextColor(Color.rgb(158,176,201));fineLabel.setTextSize(11);fineRow.addView(fineLabel,new LinearLayout.LayoutParams(dp(44),dp(34)));
            Button left=smallBtn("◄");left.setOnClickListener(v->{nudgeDx-=1f;syncAdjustmentControls();refresh();});
            Button right=smallBtn("►");right.setOnClickListener(v->{nudgeDx+=1f;syncAdjustmentControls();refresh();});
            Button up=smallBtn("▲");up.setOnClickListener(v->{nudgeDy-=1f;syncAdjustmentControls();refresh();});
            Button down=smallBtn("▼");down.setOnClickListener(v->{nudgeDy+=1f;syncAdjustmentControls();refresh();});
            Button rotCcw=smallBtn("↺");rotCcw.setOnClickListener(v->{nudgeRot-=0.2f;syncAdjustmentControls();refresh();});
            Button rotCw=smallBtn("↻");rotCw.setOnClickListener(v->{nudgeRot+=0.2f;syncAdjustmentControls();refresh();});
            Button scaleDown=smallBtn("−");scaleDown.setOnClickListener(v->{nudgeScale=Math.max(0.70f,nudgeScale-0.002f);syncAdjustmentControls();refresh();});
            Button scaleUp=smallBtn("+");scaleUp.setOnClickListener(v->{nudgeScale=Math.min(1.30f,nudgeScale+0.002f);syncAdjustmentControls();refresh();});
            fineRow.addView(left,lpSmall());fineRow.addView(right,lpSmall());fineRow.addView(up,lpSmall());fineRow.addView(down,lpSmall());fineRow.addView(rotCcw,lpSmall());fineRow.addView(rotCw,lpSmall());fineRow.addView(scaleDown,lpSmall());fineRow.addView(scaleUp,lpSmall());bottom.addView(fineRow);
            updateValueLabels();
        }
        if(hasDiagnostics){
            TextView diagnostics=new TextView(this);diagnostics.setText(InspectionImageStore.diagnosticsText);diagnostics.setTextColor(Color.WHITE);diagnostics.setTextSize(11);diagnostics.setTextIsSelectable(true);diagnostics.setPadding(dp(6),dp(4),dp(6),dp(4));
            ScrollView diagnosticScroll=new ScrollView(this);diagnosticScroll.addView(diagnostics);bottom.addView(diagnosticScroll,new LinearLayout.LayoutParams(-1,dp(142)));
        }
        TextView hint=new TextView(this);hint.setText(InspectionImageStore.overlayMode?"Automatic alignment is the starting point. Move, resize and rotate only the red/white master until it sits on the real markers. These manual edits are visual only and do not change automated QC values.":"Pinch to zoom · drag to pan · double-tap zoom");hint.setTextColor(Color.WHITE);hint.setTextSize(11);hint.setGravity(Gravity.CENTER);hint.setPadding(dp(4),0,dp(4),0);bottom.addView(hint,new LinearLayout.LayoutParams(-1,dp(42)));
        int bottomHeight=InspectionImageStore.overlayMode?dp(286):hasDiagnostics?dp(184):dp(42);FrameLayout.LayoutParams bottomLp=new FrameLayout.LayoutParams(-1,bottomHeight,Gravity.BOTTOM);root.addView(bottom,bottomLp);setContentView(root);
        if(InspectionImageStore.overlayMode&&base!=null)refresh();
    }

    private interface ProgressHandler{void onProgress(int progress);}
    private SeekBar makeSeek(int max,int initial,ProgressHandler handler){
        SeekBar s=new SeekBar(this);s.setMax(max);s.setProgress(initial);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar seekBar,int progress,boolean fromUser){if(!syncingControls)handler.onProgress(progress);}
            @Override public void onStartTrackingTouch(SeekBar seekBar){}
            @Override public void onStopTrackingTouch(SeekBar seekBar){}
        });return s;
    }
    private LinearLayout adjustRow(String label,SeekBar seek,TextView value){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=new TextView(this);name.setText(label);name.setTextColor(Color.WHITE);name.setTextSize(11);row.addView(name,new LinearLayout.LayoutParams(dp(62),dp(32)));
        row.addView(seek,new LinearLayout.LayoutParams(0,dp(32),1));row.addView(value,new LinearLayout.LayoutParams(dp(72),dp(32)));return row;
    }
    private TextView valueText(){TextView v=new TextView(this);v.setTextColor(Color.rgb(158,176,201));v.setTextSize(11);v.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);return v;}
    private Button smallBtn(String txt){Button b=new Button(this);b.setText(txt);b.setAllCaps(false);b.setTextSize(10);b.setPadding(0,0,0,0);return b;}
    private LinearLayout.LayoutParams lpSmall(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(36),dp(34));p.leftMargin=dp(2);return p;}

    private void resetNudge(){nudgeDx=0f;nudgeDy=0f;nudgeRot=0f;nudgeScale=1f;syncAdjustmentControls();refresh();}
    private void syncAdjustmentControls(){
        if(xSeek==null)return;syncingControls=true;
        xSeek.setProgress(Math.max(0,Math.min(400,Math.round(nudgeDx+200f))));
        ySeek.setProgress(Math.max(0,Math.min(400,Math.round(nudgeDy+200f))));
        rotSeek.setProgress(Math.max(0,Math.min(600,Math.round(nudgeRot/0.05f+300f))));
        scaleSeek.setProgress(Math.max(0,Math.min(600,Math.round((nudgeScale-0.70f)*1000f))));
        syncingControls=false;updateValueLabels();
    }
    private void updateValueLabels(){
        if(xValue==null)return;
        xValue.setText(String.format(java.util.Locale.US,"%+.0f px",nudgeDx));
        yValue.setText(String.format(java.util.Locale.US,"%+.0f px",nudgeDy));
        rotValue.setText(String.format(java.util.Locale.US,"%+.2f°",nudgeRot));
        scaleValue.setText(String.format(java.util.Locale.US,"%.1f%%",nudgeScale*100f));
    }

    private void findOverlayPivot(){
        overlayPivotX=overlay.getWidth()/2f;overlayPivotY=overlay.getHeight()/2f;
        int w=overlay.getWidth(),h=overlay.getHeight();if(w<=0||h<=0)return;
        int[] row=new int[w];int minX=w,minY=h,maxX=-1,maxY=-1;
        try{
            for(int y=0;y<h;y+=2){
                overlay.getPixels(row,0,w,0,y,w,1);
                for(int x=0;x<w;x+=2){if(Color.alpha(row[x])>20){if(x<minX)minX=x;if(x>maxX)maxX=x;if(y<minY)minY=y;if(y>maxY)maxY=y;}}
            }
            if(maxX>=minX&&maxY>=minY){overlayPivotX=(minX+maxX)*0.5f;overlayPivotY=(minY+maxY)*0.5f;}
        }catch(Throwable ignored){}
    }

    private void refresh(){
        if(base==null){image.setImageBitmapPreserveZoom(overlay);return;}
        Bitmap out=Bitmap.createBitmap(base.getWidth(),base.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);c.drawBitmap(base,0,0,p);p.setAlpha(Math.max(0,Math.min(255,Math.round(alpha*255))));
        if(overlay!=null){
            if(nudgeDx!=0f||nudgeDy!=0f||nudgeRot!=0f||nudgeScale!=1f){
                Matrix m=new Matrix();m.postScale(nudgeScale,nudgeScale,overlayPivotX,overlayPivotY);m.postRotate(nudgeRot,overlayPivotX,overlayPivotY);m.postTranslate(nudgeDx,nudgeDy);c.drawBitmap(overlay,m,p);
            }else{c.drawBitmap(overlay,0,0,p);}
        }
        image.setImageBitmapPreserveZoom(out);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
