package com.watchalign.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Alpha41: community ruler with true four-edge perspective, fine nudge controls and precision marker handoff. */
public class ManualAlignActivity extends Activity {
    private ZoomableImageView image;
    private Bitmap base;
    private final PerspectiveMasterRenderer.Pose pose=new PerspectiveMasterRenderer.Pose();
    private String modelRef="126710BLNR";
    private Button perspectiveButton,lockButton,stepButton;
    private TextView instruction,selectedLabel;
    private int activeHandle=-1,selectedHandle=-1;
    // 0=CENTER in flat mode, 1=12, 2=3, 3=6, 4=9 in perspective mode.
    private final boolean[] locked={false,false,false,false,false};
    private boolean fineStep=true;
    private long lastRender=0;
    private FrameLayout root;
    private PrecisionLoupeView loupe;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(8,17,31));getWindow().setNavigationBarColor(Color.rgb(8,17,31));
        base=InspectionImageStore.baseBitmap!=null?InspectionImageStore.baseBitmap:InspectionImageStore.bitmap;if(base==null){finish();return;}if(InspectionImageStore.modelRef!=null)modelRef=InspectionImageStore.modelRef;
        resetPose();
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        image=new ZoomableImageView(this);image.setBackgroundColor(Color.BLACK);image.setImageBitmap(PerspectiveMasterRenderer.renderAlignment(base,modelRef,pose));image.setOnTouchListener((v,e)->handleTouch(e));root.addView(image,new FrameLayout.LayoutParams(-1,-1));

        loupe=new PrecisionLoupeView(this);FrameLayout.LayoutParams lpLoupe=new FrameLayout.LayoutParams(dp(164),dp(164));lpLoupe.leftMargin=dp(12);lpLoupe.topMargin=dp(70);root.addView(loupe,lpLoupe);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(8),dp(6),dp(8),dp(6));top.setBackgroundColor(0xE008111F);
        Button back=btn("Back");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(72),dp(46)));
        TextView title=txt("Align ruler · α41",18);title.setPadding(dp(10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dp(46),1));
        Button reset=btn("Reset");reset.setOnClickListener(v->{resetPose();selectHandle(-1);renderNow();});top.addView(reset,new LinearLayout.LayoutParams(dp(76),dp(46)));root.addView(top,new FrameLayout.LayoutParams(-1,dp(60),Gravity.TOP));

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(10),dp(6),dp(10),dp(8));bottom.setBackgroundColor(0xEE08111F);
        instruction=txt("Set CENTER on pinion and 12 on the yellow dial edge",13);instruction.setGravity(Gravity.CENTER);bottom.addView(instruction,new LinearLayout.LayoutParams(-1,dp(34)));

        LinearLayout selectRow=new LinearLayout(this);selectRow.setGravity(Gravity.CENTER_VERTICAL);
        selectedLabel=txt("Selected: none",13);selectedLabel.setGravity(Gravity.CENTER_VERTICAL);selectRow.addView(selectedLabel,new LinearLayout.LayoutParams(0,dp(42),1));
        stepButton=btn("Fine 0.5 px");stepButton.setOnClickListener(v->{fineStep=!fineStep;stepButton.setText(fineStep?"Fine 0.5 px":"Coarse 2 px");});selectRow.addView(stepButton,new LinearLayout.LayoutParams(dp(110),dp(42)));
        lockButton=btn("Lock");lockButton.setEnabled(false);lockButton.setOnClickListener(v->toggleLock());selectRow.addView(lockButton,new LinearLayout.LayoutParams(dp(78),dp(42)));bottom.addView(selectRow);

        LinearLayout nudgeRow=new LinearLayout(this);nudgeRow.setGravity(Gravity.CENTER);
        Button left=btn("◀");Button up=btn("▲");Button down=btn("▼");Button right=btn("▶");
        setupRepeatNudge(left,-1,0);setupRepeatNudge(up,0,-1);setupRepeatNudge(down,0,1);setupRepeatNudge(right,1,0);
        nudgeRow.addView(left,new LinearLayout.LayoutParams(0,dp(46),1));nudgeRow.addView(up,new LinearLayout.LayoutParams(0,dp(46),1));nudgeRow.addView(down,new LinearLayout.LayoutParams(0,dp(46),1));nudgeRow.addView(right,new LinearLayout.LayoutParams(0,dp(46),1));bottom.addView(nudgeRow);

        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
        perspectiveButton=btn("Perspective: OFF");perspectiveButton.setOnClickListener(v->togglePerspective());row.addView(perspectiveButton,new LinearLayout.LayoutParams(0,dp(48),1));
        Button blink=btn("Hold to blink");blink.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){image.setImageBitmapPreserveZoom(base);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){renderNow();return true;}return false;});row.addView(blink,new LinearLayout.LayoutParams(0,dp(48),1));
        Button set=btn("Set alignment");set.setBackgroundColor(Color.rgb(50,213,242));set.setTextColor(Color.rgb(4,32,42));set.setOnClickListener(v->openLockedInspection());row.addView(set,new LinearLayout.LayoutParams(0,dp(48),1));bottom.addView(row);
        TextView hint=txt("Perspective ON: place 12, 3, 6 and 9 independently on the same yellow dial edge. CENTER CHECK should then land on the pinion.",11);hint.setGravity(Gravity.CENTER);bottom.addView(hint,new LinearLayout.LayoutParams(-1,dp(46)));
        root.addView(bottom,new FrameLayout.LayoutParams(-1,dp(222),Gravity.BOTTOM));setContentView(root);
    }

    private boolean handleTouch(MotionEvent e){
        PointF q=screenToBitmap(e.getX(),e.getY());if(q==null)return true;int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){int hit=findHandle(q);if(hit>=0){selectHandle(hit);placeLoupe(e.getX());loupe.show(base,handlePoint(hit));if(!locked[hit])activeHandle=hit;}return true;}
        if(action==MotionEvent.ACTION_MOVE&&activeHandle>=0){moveHandle(activeHandle,q);loupe.move(handlePoint(activeHandle));renderThrottled();return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){if(activeHandle>=0)renderNow();activeHandle=-1;if(selectedHandle>=0)loupe.show(base,handlePoint(selectedHandle));return true;}
        return true;
    }

    private void selectHandle(int handle){selectedHandle=handle;activeHandle=-1;if(handle<0){selectedLabel.setText("Selected: none");lockButton.setEnabled(false);lockButton.setText("Lock");loupe.hide();return;}selectedLabel.setText("Selected: "+handleName(handle)+(locked[handle]?" · LOCKED":""));lockButton.setEnabled(true);lockButton.setText(locked[handle]?"Unlock":"Lock");loupe.show(base,handlePoint(handle));}
    private String handleName(int h){return h==0?"CENTER":h==1?"12":h==2?"3":h==3?"6":"9";}
    private void toggleLock(){if(selectedHandle<0)return;locked[selectedHandle]=!locked[selectedHandle];selectHandle(selectedHandle);}

    private void setupRepeatNudge(Button b,float dx,float dy){
        Handler handler=new Handler(Looper.getMainLooper());
        Runnable repeat=new Runnable(){@Override public void run(){nudgeSelected(dx,dy);handler.postDelayed(this,85);}};
        b.setOnTouchListener((v,e)->{int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN){nudgeSelected(dx,dy);handler.postDelayed(repeat,330);return true;}if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){handler.removeCallbacks(repeat);return true;}return true;});
    }

    private void nudgeSelected(float dx,float dy){
        if(selectedHandle<0){Toast.makeText(this,"Tap a handle first",Toast.LENGTH_SHORT).show();return;}if(locked[selectedHandle])return;
        float step=fineStep?0.5f:2.0f;PointF p=handlePoint(selectedHandle);moveHandle(selectedHandle,new PointF(p.x+dx*step,p.y+dy*step));loupe.show(base,handlePoint(selectedHandle));renderNow();
    }

    private PointF handlePoint(int handle){
        if(handle==0)return pose.perspectiveMode?PerspectiveMasterRenderer.projectPoint(pose,0,0):new PointF(pose.centerX,pose.centerY);
        if(handle==1)return PerspectiveMasterRenderer.projectPoint(pose,0,-1);
        if(handle==2)return PerspectiveMasterRenderer.projectPoint(pose,1,0);
        if(handle==3)return PerspectiveMasterRenderer.projectPoint(pose,0,1);
        return PerspectiveMasterRenderer.projectPoint(pose,-1,0);
    }

    private void placeLoupe(float fingerX){
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)loupe.getLayoutParams();int size=dp(164),margin=dp(12);int w=root.getWidth();lp.leftMargin=(w>0&&fingerX<w/2f)?Math.max(margin,w-size-margin):margin;lp.topMargin=dp(70);loupe.setLayoutParams(lp);
    }

    private int findHandle(PointF q){
        float threshold=screenPxToBitmap(dp(58));int best=-1;float bd=Float.MAX_VALUE;
        if(!pose.perspectiveMode){
            PointF c=new PointF(pose.centerX,pose.centerY),p12=PerspectiveMasterRenderer.projectPoint(pose,0,-1);
            float d=dist(q,c);if(d<bd){bd=d;best=0;}d=dist(q,p12);if(d<bd){bd=d;best=1;}
        }else{
            PointF[] pts={PerspectiveMasterRenderer.projectPoint(pose,0,-1),PerspectiveMasterRenderer.projectPoint(pose,1,0),PerspectiveMasterRenderer.projectPoint(pose,0,1),PerspectiveMasterRenderer.projectPoint(pose,-1,0)};
            for(int i=0;i<pts.length;i++){float d=dist(q,pts[i]);if(d<bd){bd=d;best=i+1;}}
        }
        return bd<=threshold?best:-1;
    }

    private void moveHandle(int handle,PointF q){
        if(handle==0){
            if(pose.perspectiveMode)return;
            float dx=q.x-pose.centerX,dy=q.y-pose.centerY;pose.centerX=q.x;pose.centerY=q.y;pose.anchor12X+=dx;pose.anchor12Y+=dy;return;
        }
        if(handle==1){pose.anchor12X=q.x;pose.anchor12Y=q.y;return;}
        if(handle==2){pose.anchor3X=q.x;pose.anchor3Y=q.y;return;}
        if(handle==3){pose.anchor6X=q.x;pose.anchor6Y=q.y;return;}
        if(handle==4){pose.anchor9X=q.x;pose.anchor9Y=q.y;}
    }

    private void togglePerspective(){
        if(!pose.perspectiveMode){
            PointF p3=PerspectiveMasterRenderer.projectPoint(pose,1,0),p6=PerspectiveMasterRenderer.projectPoint(pose,0,1),p9=PerspectiveMasterRenderer.projectPoint(pose,-1,0);
            pose.anchor3X=p3.x;pose.anchor3Y=p3.y;pose.anchor6X=p6.x;pose.anchor6Y=p6.y;pose.anchor9X=p9.x;pose.anchor9Y=p9.y;
            pose.perspectiveMode=true;for(int i=1;i<locked.length;i++)locked[i]=false;selectHandle(-1);
            perspectiveButton.setText("Perspective: ON");instruction.setText("Place 12, 3, 6 and 9 independently on the yellow dial edge");
        }else{
            PointF predictedCentre=PerspectiveMasterRenderer.projectPoint(pose,0,0);
            pose.centerX=predictedCentre.x;pose.centerY=predictedCentre.y;pose.perspectiveMode=false;selectHandle(-1);
            perspectiveButton.setText("Perspective: OFF");instruction.setText("Set CENTER on pinion and 12 on the yellow dial edge");
        }
        renderNow();
    }

    private void resetPose(){float s=Math.min(base.getWidth(),base.getHeight())*0.41f;pose.centerX=base.getWidth()/2f;pose.centerY=base.getHeight()/2f;pose.scalePx=s;pose.pitchDeg=pose.yawDeg=pose.rollDeg=0;pose.alpha=1f;pose.anchorMode=true;pose.perspectiveMode=false;pose.anchor12X=pose.centerX;pose.anchor12Y=pose.centerY-s;pose.anchor3X=pose.centerX+s;pose.anchor3Y=pose.centerY;pose.anchor6X=pose.centerX;pose.anchor6Y=pose.centerY+s;pose.anchor9X=pose.centerX-s;pose.anchor9Y=pose.centerY;for(int i=0;i<locked.length;i++)locked[i]=false;selectedHandle=-1;fineStep=true;if(perspectiveButton!=null)perspectiveButton.setText("Perspective: OFF");if(stepButton!=null)stepButton.setText("Fine 0.5 px");if(instruction!=null)instruction.setText("Set CENTER on pinion and 12 on the yellow dial edge");}

    private void openLockedInspection(){Bitmap overlay=PerspectiveMasterRenderer.renderOverlay(base.getWidth(),base.getHeight(),modelRef,pose);InspectionImageStore.setOverlay(base,overlay,"QC master · true perspective ruler · α41",pose,modelRef);Toast.makeText(this,"Alignment set · 12 triangle check available",Toast.LENGTH_SHORT).show();startActivity(new Intent(this,FullscreenInspectActivity.class));}
    private void renderThrottled(){long now=System.currentTimeMillis();if(now-lastRender<22)return;lastRender=now;renderNow();}
    private void renderNow(){image.setImageBitmapPreserveZoom(PerspectiveMasterRenderer.renderAlignment(base,modelRef,pose));}

    private PointF screenToBitmap(float sx,float sy){float vw=image.getWidth(),vh=image.getHeight();if(vw<=0||vh<=0)return null;float fit=Math.min(vw/base.getWidth(),vh/base.getHeight());float ox=(vw-base.getWidth()*fit)/2f,oy=(vh-base.getHeight()*fit)/2f;return new PointF((sx-ox)/fit,(sy-oy)/fit);}
    private float screenPxToBitmap(float px){float fit=Math.min(image.getWidth()/(float)base.getWidth(),image.getHeight()/(float)base.getHeight());return fit>0?px/fit:px;}
    private float dist(PointF a,PointF b){float dx=a.x-b.x,dy=a.y-b.y;return (float)Math.sqrt(dx*dx+dy*dy);}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);return b;}
    private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);return t;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
