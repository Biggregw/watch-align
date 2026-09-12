package com.watchalign.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** Alpha32 direct-manipulation perspective workbench with high-contrast alignment guides. */
public class ManualAlignActivity extends Activity {
    private ZoomableImageView image;
    private Bitmap base;
    private final PerspectiveMasterRenderer.Pose pose=new PerspectiveMasterRenderer.Pose();
    private PerspectiveJoystickView joystick;
    private String modelRef="126710BLNR";
    private long lastRender=0;
    private SeekBar scaleBar,rollBar,alphaBar;
    private boolean centreLocked=false,scaleLocked=false,guidesOnly=false;
    private Button centreLock,scaleLock,guideToggle;
    private float lastX,lastY,startPinchDistance,startPinchScale;
    private boolean pinching=false;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(8,17,31));getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        base=InspectionImageStore.baseBitmap!=null?InspectionImageStore.baseBitmap:InspectionImageStore.bitmap;
        if(base==null){finish();return;} if(InspectionImageStore.modelRef!=null)modelRef=InspectionImageStore.modelRef;
        resetPose();
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(8,17,31));
        image=new ZoomableImageView(this);image.setBackgroundColor(Color.BLACK);root.addView(image,new FrameLayout.LayoutParams(-1,-1));
        image.setImageBitmap(PerspectiveMasterRenderer.render(base,modelRef,pose));
        image.setOnTouchListener((v,e)->handleDirectManipulation(e));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(6),dp(8),dp(6));top.setBackgroundColor(0xCC08111F);
        Button back=btn("Back");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(72),dp(44)));
        TextView title=txt("Perspective align · α32",17);title.setPadding(dp(8),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));
        Button reset=btn("Reset");reset.setOnClickListener(v->{resetPose();centreLocked=false;scaleLocked=false;guidesOnly=false;syncControls();renderNow();image.resetZoom();});top.addView(reset,new LinearLayout.LayoutParams(dp(76),dp(44)));root.addView(top,new FrameLayout.LayoutParams(-1,dp(58),Gravity.TOP));

        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(8),dp(5),dp(8),dp(8));panel.setBackgroundColor(0xDD08111F);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        joystick=new PerspectiveJoystickView(this);joystick.setListener((yaw,pitch)->{pose.yawDeg=yaw;pose.pitchDeg=pitch;renderThrottled();});row.addView(joystick,new LinearLayout.LayoutParams(dp(112),dp(112)));
        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);right.setPadding(dp(8),0,0,0);
        right.addView(label("Scale (or pinch)"));scaleBar=new SeekBar(this);scaleBar.setMax(100);scaleBar.setOnSeekBarChangeListener(listener(p->{if(!scaleLocked){pose.scalePx=Math.min(base.getWidth(),base.getHeight())*(0.24f+0.0038f*p);renderThrottled();}}));right.addView(scaleBar,new LinearLayout.LayoutParams(-1,dp(32)));
        right.addView(label("Roll"));rollBar=new SeekBar(this);rollBar.setMax(120);rollBar.setOnSeekBarChangeListener(listener(p->{pose.rollDeg=(p-60)*0.5f;renderThrottled();}));right.addView(rollBar,new LinearLayout.LayoutParams(-1,dp(32)));
        right.addView(label("Overlay"));alphaBar=new SeekBar(this);alphaBar.setMax(100);alphaBar.setProgress(100);alphaBar.setOnSeekBarChangeListener(listener(p->{pose.alpha=Math.max(0.15f,p/100f);renderThrottled();}));right.addView(alphaBar,new LinearLayout.LayoutParams(-1,dp(32)));
        row.addView(right,new LinearLayout.LayoutParams(0,dp(112),1));panel.addView(row);

        LinearLayout locks=new LinearLayout(this);locks.setGravity(Gravity.CENTER);
        centreLock=btn("Lock centre");centreLock.setOnClickListener(v->{centreLocked=!centreLocked;updateLockLabels();});
        scaleLock=btn("Lock scale");scaleLock.setOnClickListener(v->{scaleLocked=!scaleLocked;updateLockLabels();});
        guideToggle=btn("Guides only");guideToggle.setOnClickListener(v->{guidesOnly=!guidesOnly;guideToggle.setText(guidesOnly?"Show markers":"Guides only");renderNow();});
        locks.addView(centreLock,new LinearLayout.LayoutParams(0,dp(38),1));locks.addView(scaleLock,new LinearLayout.LayoutParams(0,dp(38),1));locks.addView(guideToggle,new LinearLayout.LayoutParams(0,dp(38),1));panel.addView(locks);

        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        Button blink=btn("Hold to blink");blink.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){image.setImageBitmapPreserveZoom(base);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){renderNow();return true;}return false;});actions.addView(blink,new LinearLayout.LayoutParams(0,dp(42),1));
        Button set=btn("Set alignment");set.setOnClickListener(v->openLockedInspection());set.setBackgroundColor(Color.rgb(50,213,242));set.setTextColor(Color.rgb(4,32,42));actions.addView(set,new LinearLayout.LayoutParams(0,dp(42),1));panel.addView(actions);
        TextView hint=txt("Drag = centre • pinch = scale • joystick = perspective • roll = rotation",11);hint.setGravity(Gravity.CENTER);panel.addView(hint,new LinearLayout.LayoutParams(-1,dp(32)));
        root.addView(panel,new FrameLayout.LayoutParams(-1,dp(224),Gravity.BOTTOM));setContentView(root);
        syncControls();
    }

    private boolean handleDirectManipulation(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();pinching=false;return true;}
        if(action==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()>=2){pinching=true;startPinchDistance=distance(e);startPinchScale=pose.scalePx;return true;}
        if(action==MotionEvent.ACTION_MOVE){
            if(e.getPointerCount()>=2&&pinching){
                if(!scaleLocked){float d=distance(e);if(startPinchDistance>10f&&d>0){pose.scalePx=Math.max(40f,Math.min(Math.min(base.getWidth(),base.getHeight())*0.70f,startPinchScale*(d/startPinchDistance)));syncScaleBar();renderThrottled();}}
            }else if(!centreLocked){
                float dx=e.getX()-lastX,dy=e.getY()-lastY;float fit=fitScale();if(fit>0){pose.centerX+=dx/fit;pose.centerY+=dy/fit;}lastX=e.getX();lastY=e.getY();renderThrottled();
            }
            return true;
        }
        if(action==MotionEvent.ACTION_POINTER_UP){pinching=false;if(e.getPointerCount()>1){int keep=e.getActionIndex()==0?1:0;lastX=e.getX(keep);lastY=e.getY(keep);}return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){pinching=false;renderNow();return true;}
        return true;
    }

    private float fitScale(){float vw=image.getWidth(),vh=image.getHeight();if(vw<=0||vh<=0)return 1f;return Math.min(vw/base.getWidth(),vh/base.getHeight());}
    private float distance(MotionEvent e){if(e.getPointerCount()<2)return 0;float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);return (float)Math.sqrt(dx*dx+dy*dy);}

    private void openLockedInspection(){
        Bitmap overlay=PerspectiveMasterRenderer.renderOverlay(base.getWidth(),base.getHeight(),modelRef,pose);
        InspectionImageStore.setOverlay(base,overlay,"Locked QC master");
        Toast.makeText(this,"Alignment locked · opening inspector",Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this,FullscreenInspectActivity.class));
    }

    private void resetPose(){pose.centerX=base.getWidth()/2f;pose.centerY=base.getHeight()/2f;pose.scalePx=Math.min(base.getWidth(),base.getHeight())*0.41f;pose.pitchDeg=0;pose.yawDeg=0;pose.rollDeg=0;pose.alpha=1f;}
    private void syncControls(){if(joystick!=null)joystick.setTilt(pose.yawDeg,pose.pitchDeg);syncScaleBar();if(rollBar!=null)rollBar.setProgress(Math.round(pose.rollDeg/0.5f)+60);if(alphaBar!=null)alphaBar.setProgress(Math.round(pose.alpha*100));updateLockLabels();}
    private void syncScaleBar(){if(scaleBar!=null){int p=Math.round((pose.scalePx/Math.min(base.getWidth(),base.getHeight())-0.24f)/0.0038f);scaleBar.setProgress(Math.max(0,Math.min(100,p)));}}
    private void updateLockLabels(){if(centreLock!=null)centreLock.setText(centreLocked?"Centre locked":"Lock centre");if(scaleLock!=null)scaleLock.setText(scaleLocked?"Scale locked":"Lock scale");}
    private void renderThrottled(){long now=System.currentTimeMillis();if(now-lastRender<24)return;lastRender=now;renderNow();}
    private void renderNow(){Bitmap b=guidesOnly?PerspectiveMasterRenderer.renderGuides(base,modelRef,pose):PerspectiveMasterRenderer.render(base,modelRef,pose);image.setImageBitmapPreserveZoom(b);}
    private SeekBar.OnSeekBarChangeListener listener(java.util.function.IntConsumer f){return new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){if(from)f.accept(p);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){renderNow();}};}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(11);return b;}
    private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}
    private TextView label(String s){return txt(s,11);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
