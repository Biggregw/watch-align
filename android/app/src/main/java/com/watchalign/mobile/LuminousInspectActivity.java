package com.watchalign.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import java.util.concurrent.*;

/** Explicitly opened experimental inspection, independent of production QC. */
public class LuminousInspectActivity extends Activity {
    private static java.lang.ref.WeakReference<Alpha99Pipeline.Output> cachedOuter=new java.lang.ref.WeakReference<>(null);
    private static LuminousMeasurement cached;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout root;private Bitmap photo;private Alpha99Pipeline.Output outer;private int hour;
    @Override public void onCreate(Bundle state){super.onCreate(state);
        photo=Alpha99ResultsActivity.Store.photo;outer=Alpha99ResultsActivity.Store.outer;
        if(photo==null||outer==null||!outer.ok()){finish();return;}
        hour=getIntent().getIntExtra("hour",0);buildRoot();
        if(cachedOuter.get()==outer&&cached!=null){show(cached);return;}
        text("Measuring inner boundaries…",16);worker.submit(()->{
            try{LuminousReference ref=LuminousReference.load(getAssets()::open,outer.model.id);LuminousMeasurement result=LuminousMeasurement.run(photo,outer,ref);
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;cachedOuter=new java.lang.ref.WeakReference<>(outer);cached=result;show(result);});
            }catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())text("Not assessed: experimental inner references or image measurement unavailable. Existing outer-metal results are unchanged.",16);});}
        });
    }
    private void buildRoot(){ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Alpha99ResultsActivity.BG);
        scroll.setOnApplyWindowInsetsListener((v,ins)->{v.setPadding(0,ins.getSystemWindowInsetTop(),0,ins.getSystemWindowInsetBottom());return ins;});root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(16),dp(16),dp(16));scroll.addView(root);setContentView(scroll);}
    private void show(LuminousMeasurement result){root.removeAllViews();text(hour==0?"Luminous markers · experimental":hour+" o’clock luminous marker",24);
        text("Provisional genuine inner references. No replica pass/fail classifications. Outer-metal inspection remains separate. Yellow expected · cyan detected · orange extra · magenta missing.",14);
        if(hour==0){
            Bitmap base=crop(0,0,1.06);for(LuminousMeasurement.Marker m:result.markers.values())MarkerMismatchOverlay.closeup(base,0,0,1.06,m.displayPolygon());
            ImageView dial=image(base);text("Tap a marker to inspect it, or use its button below.",14);
            dial.setOnTouchListener((v,event)->{if(event.getAction()!=MotionEvent.ACTION_UP)return true;
                float scale=Math.min(dial.getWidth()/480f,dial.getHeight()/480f);if(scale<=0)return true;double x=(event.getX()-(dial.getWidth()-480*scale)/2)/scale,y=(event.getY()-(dial.getHeight()-480*scale)/2)/scale;
                double cx=(x+.5)/480*2.12-1.06,cy=(y+.5)/480*2.12-1.06;LuminousMeasurement.Marker best=null;double distance=.16;
                for(LuminousMeasurement.Marker m:result.markers.values()){double[] c=ExpectedMarkerOutline.moved(new double[][]{m.spec.masterPoint()},ring())[0];double r=Math.hypot(cx-c[0],cy-c[1]);if(r<distance){distance=r;best=m;}}
                if(best!=null)open(best.spec.hour);return true;
            });
            for(LuminousMeasurement.Marker m:result.markers.values())button(m.spec.hour+" o’clock · "+(m.assessed()?String.format(java.util.Locale.US,"%.3f%% mismatch",m.score.mismatchPct):"not assessed"),()->open(m.spec.hour));
        }else{
            LuminousMeasurement.Marker m=result.markers.get(hour);if(m==null){text("No luminous marker at this hour for the selected model.",16);return;}
            double[] center=ExpectedMarkerOutline.moved(new double[][]{m.spec.masterPoint()},ring())[0];double half=.19;
            Bitmap plain=crop(center[0],center[1],half),marked=plain.copy(Bitmap.Config.ARGB_8888,true);MarkerMismatchOverlay.closeup(marked,center[0],center[1],half,m.displayPolygon());ImageView close=image(marked);
            CheckBox outline=new CheckBox(this);outline.setText("Show reference and measured regions");outline.setTextColor(Color.WHITE);outline.setChecked(true);outline.setOnCheckedChangeListener((v,on)->close.setImageBitmap(on?marked:plain));root.addView(outline);
            text(m.description(),16);text("Displacement uses dial radius R, right/down positive. Rotation uses area moments of the visible inner footprint; rounds have no measurable orientation. Filled-template area compares the entire inner footprint, including size and position differences.",13);
            button("Back to all luminous markers",()->finish());
        }
        text("Single-photo measurements can depend on lighting and edge localisation. Experimental references are not Rolex manufacturing tolerances.",13);
    }
    private Bitmap crop(double x,double y,double half){Mat rgba=new Mat();Utils.bitmapToMat(photo,rgba);Mat c=Alpha98Closeups.render(rgba,outer.pose.homography,x,y,half);Bitmap b=Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888);Utils.matToBitmap(c,b);c.release();rgba.release();return b;}
    private double[] ring(){return outer.measurement.ring!=null&&outer.measurement.ring.usable?outer.measurement.ring.model:null;}
    private void open(int h){Intent i=new Intent(this,LuminousInspectActivity.class);i.putExtra("hour",h);startActivity(i);}
    private ImageView image(Bitmap b){ImageView iv=new ImageView(this);iv.setContentDescription("Luminous marker comparison image");iv.setImageBitmap(b);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);root.addView(iv,new LinearLayout.LayoutParams(-1,dp(340)));return iv;}
    private void text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setPadding(0,dp(8),0,dp(8));root.addView(t);}
    private void button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->action.run());root.addView(b,new LinearLayout.LayoutParams(-1,-2));}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
