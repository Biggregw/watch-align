package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

/** Fixed model master with simple 2-point alignment and optional 4-handle perspective. */
final class PerspectiveMasterRenderer {
    static final class Pose {
        float centerX, centerY;
        float scalePx;
        float pitchDeg, yawDeg, rollDeg;
        float alpha = 1f;
        boolean anchorMode=false;
        boolean perspectiveMode=false;
        float anchor12X,anchor12Y,anchor3X,anchor3Y,anchor9X,anchor9Y;
        Pose copy(){Pose p=new Pose();p.centerX=centerX;p.centerY=centerY;p.scalePx=scalePx;p.pitchDeg=pitchDeg;p.yawDeg=yawDeg;p.rollDeg=rollDeg;p.alpha=alpha;p.anchorMode=anchorMode;p.perspectiveMode=perspectiveMode;p.anchor12X=anchor12X;p.anchor12Y=anchor12Y;p.anchor3X=anchor3X;p.anchor3Y=anchor3Y;p.anchor9X=anchor9X;p.anchor9Y=anchor9Y;return p;}
    }

    static Bitmap render(Bitmap base,String modelRef,Pose pose){Bitmap out=base.copy(Bitmap.Config.ARGB_8888,true);if(Gmt126710BlnrMaster.supports(modelRef))drawMaster(new Canvas(out),out.getWidth(),out.getHeight(),pose,true,false);return out;}
    static Bitmap renderAlignment(Bitmap base,String modelRef,Pose pose){Bitmap out=base.copy(Bitmap.Config.ARGB_8888,true);if(Gmt126710BlnrMaster.supports(modelRef))drawMaster(new Canvas(out),out.getWidth(),out.getHeight(),pose,true,true);return out;}
    static Bitmap renderOverlay(int width,int height,String modelRef,Pose pose){Bitmap out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);if(Gmt126710BlnrMaster.supports(modelRef))drawMaster(new Canvas(out),width,height,pose,true,false);return out;}
    static PointF projectPoint(Pose pose,double x,double y){return new Projector(pose).project(x,y);}

    private static void drawMaster(Canvas c,int width,int height,Pose pose,boolean markers,boolean handles){
        Projector pr=new Projector(pose);float unit=Math.max(1f,Math.min(width,height)/900f);
        int a=Math.max(90,Math.min(255,Math.round(255*pose.alpha)));
        Paint halo=paint(Color.BLACK,7.0f*unit,235);
        Paint red=paint(Color.rgb(255,28,28),3.5f*unit,a);
        Paint whiteHalo=paint(Color.BLACK,5.0f*unit,225);
        Paint white=paint(Color.WHITE,2.2f*unit,a);
        Paint guideHalo=paint(Color.BLACK,7.0f*unit,240);
        Paint guide=paint(Color.rgb(255,235,0),3.2f*unit,255);

        drawCircle(c,pr,Gmt126710BlnrMeasured.DIAL_EDGE_R,guideHalo);drawCircle(c,pr,Gmt126710BlnrMeasured.DIAL_EDGE_R,guide);
        if(markers){
            for(int h:new int[]{1,2,4,5,7,8,10,11}){
                drawMeasured(c,pr,Gmt126710BlnrMeasured.ROUND_OUTER,Gmt126710BlnrMeasured.MARKER_CENTER_R,h,1.0f,halo);
                drawMeasured(c,pr,Gmt126710BlnrMeasured.ROUND_OUTER,Gmt126710BlnrMeasured.MARKER_CENTER_R,h,1.0f,red);
                drawMeasured(c,pr,Gmt126710BlnrMeasured.ROUND_OUTER,Gmt126710BlnrMeasured.MARKER_CENTER_R,h,0.76f,whiteHalo);
                drawMeasured(c,pr,Gmt126710BlnrMeasured.ROUND_OUTER,Gmt126710BlnrMeasured.MARKER_CENTER_R,h,0.76f,white);
            }
            for(int h:new int[]{6,9}){
                drawMeasured(c,pr,Gmt126710BlnrMeasured.BATON_OUTER,Gmt126710BlnrMeasured.BATON_CENTER_R,h,1.0f,halo);
                drawMeasured(c,pr,Gmt126710BlnrMeasured.BATON_OUTER,Gmt126710BlnrMeasured.BATON_CENTER_R,h,1.0f,red);
                drawMeasured(c,pr,Gmt126710BlnrMeasured.BATON_OUTER,Gmt126710BlnrMeasured.BATON_CENTER_R,h,0.78f,whiteHalo);
                drawMeasured(c,pr,Gmt126710BlnrMeasured.BATON_OUTER,Gmt126710BlnrMeasured.BATON_CENTER_R,h,0.78f,white);
            }
            drawMeasured(c,pr,Gmt126710BlnrMeasured.TRI_OUTER,Gmt126710BlnrMeasured.TRI_CENTER_R,12,1.0f,halo);
            drawMeasured(c,pr,Gmt126710BlnrMeasured.TRI_OUTER,Gmt126710BlnrMeasured.TRI_CENTER_R,12,1.0f,red);
            drawMeasured(c,pr,Gmt126710BlnrMeasured.TRI_OUTER,Gmt126710BlnrMeasured.TRI_CENTER_R,12,0.79f,whiteHalo);
            drawMeasured(c,pr,Gmt126710BlnrMeasured.TRI_OUTER,Gmt126710BlnrMeasured.TRI_CENTER_R,12,0.79f,white);
        }
        if(handles)drawHandles(c,pose,pr,unit);
    }

    /** Draw one marker path measured in local tangential/radial coordinates from the official image. */
    private static void drawMeasured(Canvas c,Projector pr,float[][] local,double centerR,int hour,float shapeScale,Paint paint){
        double a=Gmt126710BlnrMaster.angleForHour(hour),ux=Math.cos(a),uy=Math.sin(a),vx=-uy,vy=ux;
        double cx=centerR*ux,cy=centerR*uy;Path path=new Path();
        for(int i=0;i<local.length;i++){
            double tang=local[i][0]*shapeScale,rad=local[i][1]*shapeScale;
            double x=cx+vx*tang+ux*rad,y=cy+vy*tang+uy*rad;PointF q=pr.project(x,y);
            if(i==0)path.moveTo(q.x,q.y);else path.lineTo(q.x,q.y);
        }
        path.close();c.drawPath(path,paint);
    }

    private static void drawHandles(Canvas c,Pose p,Projector pr,float unit){
        Paint black=fill(Color.BLACK,230),yellow=fill(Color.rgb(255,230,0),255),cyan=fill(Color.CYAN,255),magenta=fill(Color.rgb(255,60,220),255),text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setColor(Color.WHITE);text.setTextSize(14*unit);text.setFakeBoldText(true);
        PointF centre=new PointF(p.centerX,p.centerY),twelve=pr.project(0,-1),three=pr.project(1,0),nine=pr.project(-1,0);
        handle(c,centre,black,yellow,9*unit,"CENTER",text,11*unit,-12*unit);
        handle(c,twelve,black,cyan,9*unit,"12",text,11*unit,-12*unit);
        if(p.perspectiveMode){handle(c,three,black,magenta,8*unit,"3",text,10*unit,-10*unit);handle(c,nine,black,magenta,8*unit,"9",text,10*unit,-10*unit);}
    }
    private static void handle(Canvas c,PointF q,Paint black,Paint fill,float r,String label,Paint text,float tx,float ty){c.drawCircle(q.x,q.y,r+3,black);c.drawCircle(q.x,q.y,r,fill);c.drawText(label,q.x+tx,q.y+ty,text);}

    private static final class Projector {
        private final Pose p;private final boolean homography;private final double[] H;
        Projector(Pose p){this.p=p;this.homography=p.anchorMode&&p.perspectiveMode;this.H=homography?buildH(p):null;}
        PointF project(double x,double y){
            if(p.anchorMode){
                if(homography&&H!=null){double d=H[6]*x+H[7]*y+1.0;if(Math.abs(d)>1e-8)return new PointF((float)((H[0]*x+H[1]*y+H[2])/d),(float)((H[3]*x+H[4]*y+H[5])/d));}
                double vx=p.anchor12X-p.centerX,vy=p.anchor12Y-p.centerY;double s=Math.hypot(vx,vy);if(s<1)s=Math.max(1,p.scalePx);double ang=Math.atan2(vy,vx)+Math.PI/2.0;double ca=Math.cos(ang),sa=Math.sin(ang);return new PointF(p.centerX+(float)(s*(x*ca-y*sa)),p.centerY+(float)(s*(x*sa+y*ca)));
            }
            double pitch=Math.toRadians(p.pitchDeg),yaw=Math.toRadians(p.yawDeg),roll=Math.toRadians(p.rollDeg);double cp=Math.cos(pitch),sp=Math.sin(pitch),cy=Math.cos(yaw),sy=Math.sin(yaw),cr=Math.cos(roll),sr=Math.sin(roll);double y1=y*cp,z1=y*sp;double x2=x*cy+z1*sy;double z2=-x*sy+z1*cy;double xr=x2*cr-y1*sr,yr=x2*sr+y1*cr;double camera=3.20,perspective=camera/Math.max(1.25,camera-z2);return new PointF(p.centerX+(float)(p.scalePx*xr*perspective),p.centerY+(float)(p.scalePx*yr*perspective));
        }
    }

    private static double[] buildH(Pose p){
        double p6x=2*p.centerX-p.anchor12X,p6y=2*p.centerY-p.anchor12Y;
        double[][] src={{0,-1},{1,0},{0,1},{-1,0}};double[][] dst={{p.anchor12X,p.anchor12Y},{p.anchor3X,p.anchor3Y},{p6x,p6y},{p.anchor9X,p.anchor9Y}};
        double[][] a=new double[8][9];for(int i=0;i<4;i++){double x=src[i][0],y=src[i][1],u=dst[i][0],v=dst[i][1];int r=2*i;a[r][0]=x;a[r][1]=y;a[r][2]=1;a[r][6]=-u*x;a[r][7]=-u*y;a[r][8]=u;a[r+1][3]=x;a[r+1][4]=y;a[r+1][5]=1;a[r+1][6]=-v*x;a[r+1][7]=-v*y;a[r+1][8]=v;}
        for(int col=0;col<8;col++){int pivot=col;for(int r=col+1;r<8;r++)if(Math.abs(a[r][col])>Math.abs(a[pivot][col]))pivot=r;if(Math.abs(a[pivot][col])<1e-8)return null;double[] tmp=a[col];a[col]=a[pivot];a[pivot]=tmp;double div=a[col][col];for(int j=col;j<9;j++)a[col][j]/=div;for(int r=0;r<8;r++){if(r==col)continue;double f=a[r][col];for(int j=col;j<9;j++)a[r][j]-=f*a[col][j];}}
        double[] h=new double[8];for(int i=0;i<8;i++)h[i]=a[i][8];return h;
    }

    private static void drawCircle(Canvas c,Projector pr,double r,Paint paint){Path path=new Path();for(int i=0;i<=180;i++){double a=2*Math.PI*i/180.0;PointF q=pr.project(r*Math.cos(a),r*Math.sin(a));if(i==0)path.moveTo(q.x,q.y);else path.lineTo(q.x,q.y);}c.drawPath(path,paint);}
    private static Paint paint(int color,float width,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(color);p.setAlpha(alpha);return p;}
    private static Paint fill(int color,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setAlpha(alpha);return p;}
    private PerspectiveMasterRenderer(){}
}
