package com.watchalign.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Alpha34: simple centre + 12 alignment, with optional 3/9 projective perspective handles. */
public class ManualAlignActivity extends Activity {
    private ZoomableImageView image;
    private Bitmap base;
    private final PerspectiveMasterRenderer.Pose pose=new PerspectiveMasterRenderer.Pose();
    private String modelRef="126710BLNR";
    private Button perspectiveButton;
    private TextView instruction;
    private int activeHandle=-1;
    private long lastRender=0;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(8,17,31));getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        base=InspectionImageStore.baseBitmap!=null?InspectionImageStore.baseBitmap:InspectionImageStore.bitmap;if(base==null){finish();return;}if(InspectionImageStore.modelRef!=null)modelRef=InspectionImageStore.modelRef;
        resetPose();
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        image=new ZoomableImageView(this);image.setBackgroundColor(Color.BLACK);image.setImageBitmap(PerspectiveMasterRenderer.renderAlignment(base,modelRef,pose));image.setOnTouchListener((v,e)->handleTouch(e));root.addView(image,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(6),dp(8),dp(6));top.setBackgroundColor(0xE008111F);
        Button back=btn("Back");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(72),dp(46)));
        TextView title=txt("Align master · α34",18);title.setPadding(dp(10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        Button reset=btn("Reset");reset.setOnClickListener(v->{resetPose();renderNow();});top.addView(reset,new LinearLayout.LayoutParams(dp(76),dp(46)));root.addView(top,new FrameLayout.LayoutParams(-1,dp(60),Gravity.TOP));

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(10),dp(8),dp(10),dp(10));bottom.setBackgroundColor(0xE608111F);
        instruction=txt("1 Drag YELLOW CENTER to the pinion   2 Drag CYAN 12 to 12 o'clock",14);instruction.setGravity(Gravity.CENTER);bottom.addView(instruction,new LinearLayout.LayoutParams(-1,dp(46)));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
        perspectiveButton=btn("Perspective: OFF");perspectiveButton.setOnClickListener(v->togglePerspective());row.addView(perspectiveButton,new LinearLayout.LayoutParams(0,dp(48),1));
        Button blink=btn("Hold to blink");blink.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){image.setImageBitmapPreserveZoom(base);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){renderNow();return true;}return false;});row.addView(blink,new LinearLayout.LayoutParams(0,dp(48),1));
        Button set=btn("Set alignment");set.setBackgroundColor(Color.rgb(50,213,242));set.setTextColor(Color.rgb(4,32,42));set.setOnClickListener(v->openLockedInspection());row.addView(set,new LinearLayout.LayoutParams(0,dp(48),1));bottom.addView(row);
        TextView hint=txt("Normal photos need only CENTER + 12. Turn Perspective on only for angled QC photos.",12);hint.setGravity(Gravity.CENTER);bottom.addView(hint,new LinearLayout.LayoutParams(-1,dp(42)));
        root.addView(bottom,new FrameLayout.LayoutParams(-1,dp(154),Gravity.BOTTOM));setContentView(root);
    }

    private boolean handleTouch(MotionEvent e){
        PointF q=screenToBitmap(e.getX(),e.getY());if(q==null)return true;int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){activeHandle=findHandle(q);return true;}
        if(action==MotionEvent.ACTION_MOVE&&activeHandle>=0){moveHandle(activeHandle,q);renderThrottled();return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){if(activeHandle>=0)renderNow();activeHandle=-1;return true;}
        return true;
    }

    private int findHandle(PointF q){
        PointF c=new PointF(pose.centerX,pose.centerY),p12=PerspectiveMasterRenderer.projectPoint(pose,0,-1);float threshold=screenPxToBitmap(dp(54));
        int best=-1;float bd=Float.MAX_VALUE;float d=dist(q,c);if(d<bd){bd=d;best=0;}d=dist(q,p12);if(d<bd){bd=d;best=1;}
        if(pose.perspectiveMode){PointF p3=PerspectiveMasterRenderer.projectPoint(pose,1,0),p9=PerspectiveMasterRenderer.projectPoint(pose,-1,0);d=dist(q,p3);if(d<bd){bd=d;best=2;}d=dist(q,p9);if(d<bd){bd=d;best=3;}}
        return bd<=threshold?best:-1;
    }

    private void moveHandle(int handle,PointF q){
        if(handle==0){float dx=q.x-pose.centerX,dy=q.y-pose.centerY;pose.centerX=q.x;pose.centerY=q.y;pose.anchor12X+=dx;pose.anchor12Y+=dy;if(pose.perspectiveMode){pose.anchor3X+=dx;pose.anchor3Y+=dy;pose.anchor9X+=dx;pose.anchor9Y+=dy;}return;}
        if(handle==1){if(dist(q,new PointF(pose.centerX,pose.centerY))>30f){pose.anchor12X=q.x;pose.anchor12Y=q.y;}return;}
        if(handle==2||handle==3){
            float vx=q.x-pose.centerX,vy=q.y-pose.centerY,len=(float)Math.sqrt(vx*vx+vy*vy);if(len<30f)return;float ux=vx/len,uy=vy/len;
            if(handle==2){float opposite=dist(new PointF(pose.anchor9X,pose.anchor9Y),new PointF(pose.centerX,pose.centerY));pose.anchor3X=q.x;pose.anchor3Y=q.y;pose.anchor9X=pose.centerX-ux*opposite;pose.anchor9Y=pose.centerY-uy*opposite;}
            else{float opposite=dist(new PointF(pose.anchor3X,pose.anchor3Y),new PointF(pose.centerX,pose.centerY));pose.anchor9X=q.x;pose.anchor9Y=q.y;pose.anchor3X=pose.centerX-ux*opposite;pose.anchor3Y=pose.centerY-uy*opposite;}
        }
    }

    private void togglePerspective(){
        if(!pose.perspectiveMode){PointF p3=PerspectiveMasterRenderer.projectPoint(pose,1,0),p9=PerspectiveMasterRenderer.projectPoint(pose,-1,0);pose.anchor3X=p3.x;pose.anchor3Y=p3.y;pose.anchor9X=p9.x;pose.anchor9Y=p9.y;pose.perspectiveMode=true;perspectiveButton.setText("Perspective: ON");instruction.setText("Fine tune MAGENTA 3 and 9 handles. They stay on one diameter through the centre.");}
        else{pose.perspectiveMode=false;perspectiveButton.setText("Perspective: OFF");instruction.setText("1 Drag YELLOW CENTER to the pinion   2 Drag CYAN 12 to 12 o'clock");}
        renderNow();
    }

    private void resetPose(){float s=Math.min(base.getWidth(),base.getHeight())*0.41f;pose.centerX=base.getWidth()/2f;pose.centerY=base.getHeight()/2f;pose.scalePx=s;pose.pitchDeg=pose.yawDeg=pose.rollDeg=0;pose.alpha=1f;pose.anchorMode=true;pose.perspectiveMode=false;pose.anchor12X=pose.centerX;pose.anchor12Y=pose.centerY-s;pose.anchor3X=pose.centerX+s;pose.anchor3Y=pose.centerY;pose.anchor9X=pose.centerX-s;pose.anchor9Y=pose.centerY;if(perspectiveButton!=null)perspectiveButton.setText("Perspective: OFF");if(instruction!=null)instruction.setText("1 Drag YELLOW CENTER to the pinion   2 Drag CYAN 12 to 12 o'clock");}

    private void openLockedInspection(){Bitmap overlay=PerspectiveMasterRenderer.renderOverlay(base.getWidth(),base.getHeight(),modelRef,pose);InspectionImageStore.setOverlay(base,overlay,"QC master · two-point aligned");Toast.makeText(this,"Alignment set",Toast.LENGTH_SHORT).show();startActivity(new Intent(this,FullscreenInspectActivity.class));}
    private void renderThrottled(){long now=System.currentTimeMillis();if(now-lastRender<22)return;lastRender=now;renderNow();}
    private void renderNow(){image.setImageBitmapPreserveZoom(PerspectiveMasterRenderer.renderAlignment(base,modelRef,pose));}

    private PointF screenToBitmap(float sx,float sy){float vw=image.getWidth(),vh=image.getHeight();if(vw<=0||vh<=0)return null;float fit=Math.min(vw/base.getWidth(),vh/base.getHeight());float ox=(vw-base.getWidth()*fit)/2f,oy=(vh-base.getHeight()*fit)/2f;return new PointF((sx-ox)/fit,(sy-oy)/fit);}
    private float screenPxToBitmap(float px){float fit=Math.min(image.getWidth()/(float)base.getWidth(),image.getHeight()/(float)base.getHeight());return fit>0?px/fit:px;}
    private float dist(PointF a,PointF b){float dx=a.x-b.x,dy=a.y-b.y;return (float)Math.sqrt(dx*dx+dy*dy);}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);return b;}
    private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
