package com.watchalign.mobile;

import android.app.Activity;
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

/** Alpha30 first-pass manual perspective workbench. */
public class ManualAlignActivity extends Activity {
    private ZoomableImageView image;
    private Bitmap base;
    private final PerspectiveMasterRenderer.Pose pose=new PerspectiveMasterRenderer.Pose();
    private PerspectiveJoystickView joystick;
    private String modelRef="126710BLNR";
    private long lastRender=0;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(8,17,31));getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        base=InspectionImageStore.baseBitmap!=null?InspectionImageStore.baseBitmap:InspectionImageStore.bitmap;
        if(base==null){finish();return;} if(InspectionImageStore.modelRef!=null)modelRef=InspectionImageStore.modelRef;
        resetPose();
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(8,17,31));
        image=new ZoomableImageView(this);image.setBackgroundColor(Color.BLACK);root.addView(image,new FrameLayout.LayoutParams(-1,-1));
        image.setImageBitmap(PerspectiveMasterRenderer.render(base,modelRef,pose));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(6),dp(8),dp(6));top.setBackgroundColor(0xCC08111F);
        Button back=btn("Back");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(72),dp(44)));
        TextView title=txt("Perspective align",17);title.setPadding(dp(8),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));
        Button reset=btn("Reset");reset.setOnClickListener(v->{resetPose();syncControls();renderNow();image.resetZoom();});top.addView(reset,new LinearLayout.LayoutParams(dp(76),dp(44)));root.addView(top,new FrameLayout.LayoutParams(-1,dp(58),Gravity.TOP));

        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(8),dp(5),dp(8),dp(8));panel.setBackgroundColor(0xDD08111F);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        joystick=new PerspectiveJoystickView(this);joystick.setListener((yaw,pitch)->{pose.yawDeg=yaw;pose.pitchDeg=pitch;renderThrottled();});row.addView(joystick,new LinearLayout.LayoutParams(dp(128),dp(128)));
        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);right.setPadding(dp(8),0,0,0);
        right.addView(label("Scale"));SeekBar scale=new SeekBar(this);scale.setMax(100);scale.setProgress(45);scale.setOnSeekBarChangeListener(listener(p->{pose.scalePx=Math.min(base.getWidth(),base.getHeight())*(0.24f+0.0038f*p);renderThrottled();}));right.addView(scale,new LinearLayout.LayoutParams(-1,dp(34)));
        right.addView(label("Roll"));SeekBar roll=new SeekBar(this);roll.setMax(120);roll.setProgress(60);roll.setOnSeekBarChangeListener(listener(p->{pose.rollDeg=(p-60)*0.5f;renderThrottled();}));right.addView(roll,new LinearLayout.LayoutParams(-1,dp(34)));
        right.addView(label("Overlay"));SeekBar alpha=new SeekBar(this);alpha.setMax(100);alpha.setProgress(100);alpha.setOnSeekBarChangeListener(listener(p->{pose.alpha=Math.max(0.05f,p/100f);renderThrottled();}));right.addView(alpha,new LinearLayout.LayoutParams(-1,dp(34)));
        row.addView(right,new LinearLayout.LayoutParams(0,dp(128),1));panel.addView(row);

        LinearLayout nudge=new LinearLayout(this);nudge.setGravity(Gravity.CENTER);Button l=btn("◀");Button u=btn("▲");Button d=btn("▼");Button r=btn("▶");Button fine=btn("Fine 2px");
        l.setOnClickListener(v->{pose.centerX-=2;renderNow();});r.setOnClickListener(v->{pose.centerX+=2;renderNow();});u.setOnClickListener(v->{pose.centerY-=2;renderNow();});d.setOnClickListener(v->{pose.centerY+=2;renderNow();});
        nudge.addView(l,new LinearLayout.LayoutParams(0,dp(40),1));nudge.addView(u,new LinearLayout.LayoutParams(0,dp(40),1));nudge.addView(d,new LinearLayout.LayoutParams(0,dp(40),1));nudge.addView(r,new LinearLayout.LayoutParams(0,dp(40),1));nudge.addView(fine,new LinearLayout.LayoutParams(0,dp(40),1));panel.addView(nudge);
        LinearLayout actions=new LinearLayout(this);Button blink=btn("Hold to blink");blink.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){image.setImageBitmapPreserveZoom(base);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){renderNow();return true;}return false;});actions.addView(blink,new LinearLayout.LayoutParams(0,dp(42),1));TextView hint=txt("Joystick = tilt • sliders = size/roll • arrows = centre",11);hint.setGravity(Gravity.CENTER);actions.addView(hint,new LinearLayout.LayoutParams(0,dp(42),2));panel.addView(actions);
        root.addView(panel,new FrameLayout.LayoutParams(-1,dp(222),Gravity.BOTTOM));setContentView(root);
        scale.setProgress(Math.round((pose.scalePx/Math.min(base.getWidth(),base.getHeight())-0.24f)/0.0038f));
    }

    private void resetPose(){pose.centerX=base.getWidth()/2f;pose.centerY=base.getHeight()/2f;pose.scalePx=Math.min(base.getWidth(),base.getHeight())*0.41f;pose.pitchDeg=0;pose.yawDeg=0;pose.rollDeg=0;pose.alpha=1f;}
    private void syncControls(){if(joystick!=null)joystick.setTilt(pose.yawDeg,pose.pitchDeg);}
    private void renderThrottled(){long now=System.currentTimeMillis();if(now-lastRender<33)return;lastRender=now;renderNow();}
    private void renderNow(){image.setImageBitmapPreserveZoom(PerspectiveMasterRenderer.render(base,modelRef,pose));}
    private SeekBar.OnSeekBarChangeListener listener(java.util.function.IntConsumer f){return new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){f.accept(p);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){renderNow();}};}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(11);return b;}
    private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}
    private TextView label(String s){return txt(s,11);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
