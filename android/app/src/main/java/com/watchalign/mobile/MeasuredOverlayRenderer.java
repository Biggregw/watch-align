package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.Locale;

/**
 * Draws what the analysis actually measured, not a predicted template.
 *
 * GMT supplies judged CLEAR/CHECK/STRONG states. New families can use measurementOnly and supply
 * measured/not-judged states until their own QC limits are calibrated. Geometry, whole-dial markup
 * and close-up planning stay shared.
 */
final class MeasuredOverlayRenderer {
    static final int CLEAR=Color.rgb(40,210,120), CHECK=Color.rgb(255,176,0),
            STRONG=Color.rgb(255,45,45), UNKNOWN=Color.rgb(165,175,190), TICK=Color.rgb(90,220,255),
            MEASURED=Color.rgb(50,213,242);

    /** Model-neutral baton supplied by a family adapter. GMT keeps its legacy six/side fields unchanged. */
    static final class BatonDrawing {
        String key,label;
        double clockDeg;
        GmtSixLandmarkAnalyzer.Geometry geometry;
        boolean measured;
        String notJudged,measuredText;
    }

    /** Everything needed to draw; image coordinates of the analysed bitmap. */
    static final class Drawing {
        double dialCx=Double.NaN,dialCy=Double.NaN,dialA=Double.NaN,dialB=Double.NaN,dialAngleDeg=0;
        GmtTwelveLandmarkAnalyzer.Geometry twelve;
        GmtHumanQcMath.Attention gap=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention alignment=GmtHumanQcMath.Attention.UNASSESSABLE;
        double gapValue=Double.NaN, spacing59=Double.NaN, spacing01=Double.NaN;
        /** Why the 12 marker was not judged, or null when it was. Drawn grey and dashed. */
        String notJudged;
        // 6 o'clock baton (mature GMT path)
        GmtSixLandmarkAnalyzer.Geometry six;
        GmtHumanQcMath.Attention sixAttention=GmtHumanQcMath.Attention.UNASSESSABLE;
        double sixCentring=Double.NaN;
        String sixNotJudged;
        // GMT side baton (9, or 3 on a date-at-9 dial)
        GmtSixLandmarkAnalyzer.Geometry nine;
        GmtHumanQcMath.Attention nineAttention=GmtHumanQcMath.Attention.UNASSESSABLE;
        double nineCentring=Double.NaN;
        String nineNotJudged;
        String nineLabel="9";
        boolean sixOffCentre,sixRotated,nineOffCentre,nineRotated,nineSideUnknown;
        double sixRotationDeg=Double.NaN,nineRotationDeg=Double.NaN;
        boolean sixUnstable,nineUnstable;
        /** Photo-wide confidence limitation used by GMT pose policy. */
        String photoNote;
        String twelveKind;
        double twelveRotationDeg=Double.NaN;

        // Shared round markers.
        java.util.List<GmtRoundMarkerAnalyzer.Marker> round=new java.util.ArrayList<>();

        // Family-neutral presentation mode. Defaults preserve the mature GMT output path exactly.
        boolean measurementOnly;
        boolean genericBatonsOnly;
        boolean twelveMeasured,gapMeasured,alignmentMeasured;
        String twelveMeasuredText,bannerText;
        double markerCenterR=Gmt126710BlnrMaster.MARKER_CENTER_R;
        final java.util.List<BatonDrawing> batons=new java.util.ArrayList<>();
        final java.util.Set<Integer> roundMeasuredHours=new java.util.HashSet<>();
        final java.util.Map<Integer,String> roundNotJudged=new java.util.HashMap<>();

        boolean hasAnything(){return Double.isFinite(dialCx)||twelve!=null||six!=null||nine!=null||!round.isEmpty()||!batons.isEmpty();}

        /** Maps everything from full-resolution crop coordinates onto the preview. */
        void mapTo(GmtDialCrop.Crop c){
            java.util.Set<double[]> done=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            dialCx=c.toPreviewX(dialCx);dialCy=c.toPreviewY(dialCy);dialA=c.toPreviewLength(dialA);dialB=c.toPreviewLength(dialB);
            if(twelve!=null)for(double[] p:new double[][]{twelve.triLeft,twelve.triRight,twelve.triTip,twelve.tick59,twelve.tick60,twelve.tick01})map(p,c,done);
            for(GmtSixLandmarkAnalyzer.Geometry g:new GmtSixLandmarkAnalyzer.Geometry[]{six,nine})
                if(g!=null)for(double[] p:new double[][]{g.outerLeft,g.outerRight,g.innerLeft,g.innerRight,g.tickAfter,g.tickCentre,g.tickBefore})map(p,c,done);
            for(BatonDrawing b:batons){
                GmtSixLandmarkAnalyzer.Geometry g=b.geometry;
                if(g!=null)for(double[] p:new double[][]{g.outerLeft,g.outerRight,g.innerLeft,g.innerRight,g.tickAfter,g.tickCentre,g.tickBefore})map(p,c,done);
            }
            for(GmtRoundMarkerAnalyzer.Marker m:round){
                m.x=c.toPreviewX(m.x);m.y=c.toPreviewY(m.y);m.radiusPx=c.toPreviewLength(m.radiusPx);
                m.seedX=c.toPreviewX(m.seedX);m.seedY=c.toPreviewY(m.seedY);m.expectedRadiusPx=c.toPreviewLength(m.expectedRadiusPx);
                for(double[] p:new double[][]{m.tickBefore,m.tickCentre,m.tickAfter})map(p,c,done);
            }
        }
        private static void map(double[] p,GmtDialCrop.Crop c,java.util.Set<double[]> done){
            if(p==null||!done.add(p))return;
            p[0]=c.toPreviewX(p[0]);p[1]=c.toPreviewY(p[1]);
        }
    }

    private MeasuredOverlayRenderer(){}

    static int colour(GmtHumanQcMath.Attention a){
        switch(a){case CLEAR:return CLEAR;case CHECK:return CHECK;case STRONG:return STRONG;default:return UNKNOWN;}
    }

    /** Whole-dial overlay. GMT verdict semantics remain unchanged; measurementOnly uses cyan M / grey dash. */
    static Bitmap render(Bitmap watch,Drawing d){
        if(watch==null||d==null||!d.hasAnything())return null;
        Bitmap out=Bitmap.createBitmap(watch.getWidth(),watch.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        float base=Math.max(1f,Math.min(out.getWidth(),out.getHeight())/900f);
        double R=Double.isFinite(d.dialA)&&Double.isFinite(d.dialB)?Math.max(d.dialA,d.dialB):Double.NaN;
        if(Double.isFinite(d.dialCx)&&Double.isFinite(R)){
            Paint ring=stroke(Color.WHITE,1.0f*base,90);
            Path p=new Path();
            double t=Math.toRadians(d.dialAngleDeg),ct=Math.cos(t),st=Math.sin(t);
            for(int i=0;i<=180;i++){
                double a=2*Math.PI*i/180.0,x=d.dialA*Math.cos(a),y=d.dialB*Math.sin(a);
                float px=(float)(d.dialCx+x*ct-y*st),py=(float)(d.dialCy+x*st+y*ct);
                if(i==0)p.moveTo(px,py);else p.lineTo(px,py);
            }
            c.drawPath(p,ring);
        }
        float badge=(float)(Double.isFinite(R)?Math.max(6.0,0.045*R):Math.max(6.0,10*base));
        float text=badge*1.05f;

        // Round markers use one geometry path. Only state-to-style mapping differs before calibration.
        for(GmtRoundMarkerAnalyzer.Marker m:d.round){
            if(m==null)continue;
            if(!m.found){
                if(!Double.isFinite(m.seedX)||!(m.expectedRadiusPx>0))continue;
                Paint nj=stroke(UNKNOWN,(float)Math.max(1.0,m.expectedRadiusPx/11.0),170);
                circleDashed(c,m.seedX,m.seedY,m.expectedRadiusPx,nj);
                badge(c,m.seedX,m.seedY,badge,GmtHumanQcMath.Attention.UNASSESSABLE);
                word(c,d,m.seedX,m.seedY,m.expectedRadiusPx,d.measurementOnly?roundReason(d,m,"not found"):"not found",UNKNOWN,text);
                continue;
            }
            float lw=(float)Math.max(1.0,m.diameterPx()/22.0);
            if(d.measurementOnly){
                boolean measured=d.roundMeasuredHours.contains(m.hour);
                if(measured){
                    c.drawCircle((float)m.x,(float)m.y,(float)m.radiusPx,stroke(MEASURED,lw,235));
                    measuredBadge(c,m.x,m.y,badge);
                }else{
                    circleDashed(c,m.x,m.y,m.radiusPx,stroke(UNKNOWN,lw,200));
                    badge(c,m.x,m.y,badge,GmtHumanQcMath.Attention.UNASSESSABLE);
                    word(c,d,m.x,m.y,m.radiusPx,roundReason(d,m,"not measured"),UNKNOWN,text);
                }
            }else{
                GmtHumanQcMath.Attention a=m.attention;
                if(a==GmtHumanQcMath.Attention.UNASSESSABLE)circleDashed(c,m.x,m.y,m.radiusPx,stroke(UNKNOWN,lw,200));
                else c.drawCircle((float)m.x,(float)m.y,(float)m.radiusPx,stroke(colour(a),lw,235));
                badge(c,m.x,m.y,badge,a);
                String w=roundWord(m);
                if(w!=null)word(c,d,m.x,m.y,m.radiusPx,w,colour(a),text);
            }
        }

        if(d.genericBatonsOnly){
            for(BatonDrawing b:d.batons)measurementBatonMark(c,d,b,badge,text);
        }else{
            // Mature GMT 6 and side-baton rendering is deliberately unchanged.
            if(d.six==null){double[] at=expectedAt(d,180,Gmt126710BlnrMaster.MARKER_CENTER_R);
                if(at!=null){badge(c,at[0],at[1],badge,GmtHumanQcMath.Attention.UNASSESSABLE);word(c,d,at[0],at[1],badge*2.5,"not found",UNKNOWN,text);}}
            if(d.nine==null&&!d.nineSideUnknown){double[] at=expectedAt(d,"3".equals(d.nineLabel)?90:270,Gmt126710BlnrMaster.MARKER_CENTER_R);
                if(at!=null){badge(c,at[0],at[1],badge,GmtHumanQcMath.Attention.UNASSESSABLE);word(c,d,at[0],at[1],badge*2.5,"not found",UNKNOWN,text);}}
            batonMark(c,d,d.six,d.sixAttention,d.sixNotJudged,batonWord(d.sixAttention,d.sixOffCentre,d.sixRotated,d.sixNotJudged),badge,text);
            String sideNj=d.nineSideUnknown?"date side not determined":d.nineNotJudged;
            batonMark(c,d,d.nine,d.nineSideUnknown?GmtHumanQcMath.Attention.UNASSESSABLE:d.nineAttention,sideNj,
                    d.nineSideUnknown?"date side unknown":batonWord(d.nineAttention,d.nineOffCentre,d.nineRotated,d.nineNotJudged),badge,text);
        }

        // 12 triangle: same geometry, either calibrated verdict state or neutral measured state.
        GmtTwelveLandmarkAnalyzer.Geometry g=d.twelve;
        if(g!=null){
            double width=Math.hypot(g.triRight[0]-g.triLeft[0],g.triRight[1]-g.triLeft[1]);
            float lw=(float)Math.max(1.0,width/40.0);
            double[][] pts={g.triLeft,g.triRight,g.triTip};
            double bx=(g.triLeft[0]+g.triRight[0]+g.triTip[0])/3,by=(g.triLeft[1]+g.triRight[1]+g.triTip[1])/3;
            if(d.measurementOnly){
                if(d.twelveMeasured){
                    Path tp=new Path();tp.moveTo((float)g.triLeft[0],(float)g.triLeft[1]);tp.lineTo((float)g.triRight[0],(float)g.triRight[1]);tp.lineTo((float)g.triTip[0],(float)g.triTip[1]);tp.close();
                    c.drawPath(tp,stroke(MEASURED,lw,235));measuredBadge(c,bx,by,badge);
                }else{
                    Paint nj=stroke(UNKNOWN,lw,220);for(int i=0;i<3;i++)dashed(c,pts[i],pts[(i+1)%3],width/8.0,nj);
                    badge(c,bx,by,badge,GmtHumanQcMath.Attention.UNASSESSABLE);
                    if(d.notJudged!=null)word(c,d,bx,by,width*0.45,d.notJudged,UNKNOWN,text);
                }
            }else{
                GmtHumanQcMath.Attention a=twelveAttention(d);
                if(d.notJudged!=null||a==GmtHumanQcMath.Attention.UNASSESSABLE){
                    Paint nj=stroke(UNKNOWN,lw,220);
                    for(int i=0;i<3;i++)dashed(c,pts[i],pts[(i+1)%3],width/8.0,nj);
                }else{
                    Path tp=new Path();
                    tp.moveTo((float)g.triLeft[0],(float)g.triLeft[1]);tp.lineTo((float)g.triRight[0],(float)g.triRight[1]);
                    tp.lineTo((float)g.triTip[0],(float)g.triTip[1]);tp.close();
                    c.drawPath(tp,stroke(colour(a),lw,235));
                }
                badge(c,bx,by,badge,d.notJudged!=null?GmtHumanQcMath.Attention.UNASSESSABLE:a);
                String w=twelveWord(d);
                if(w!=null)word(c,d,bx,by,width*0.45,w,d.notJudged!=null?UNKNOWN:colour(a),text);
            }
        }else if(Double.isFinite(d.dialCx)&&Double.isFinite(R)){
            badge(c,d.dialCx,d.dialCy-0.75*R,badge,GmtHumanQcMath.Attention.UNASSESSABLE);
            word(c,d,d.dialCx,d.dialCy-0.75*R,badge,"12 not found",UNKNOWN,text);
        }

        String bannerText=d.bannerText!=null?d.bannerText:d.photoNote;
        if(bannerText!=null&&Double.isFinite(R))banner(c,bannerText,d.dialCx,d.dialCy-R,2*R,out.getWidth(),badge);
        if(d.measurementOnly)measurementLegend(c,out.getWidth(),out.getHeight(),Double.isFinite(R)?d.dialCx:out.getWidth()/2.0,
                Double.isFinite(R)?d.dialCy+R:Double.NaN,Double.isFinite(R)?2*R:out.getWidth(),badge);
        else legend(c,out.getWidth(),out.getHeight(),Double.isFinite(R)?d.dialCx:out.getWidth()/2.0,Double.isFinite(R)?d.dialCy+R:Double.NaN,
                Double.isFinite(R)?2*R:out.getWidth(),badge);
        return out;
    }

    static GmtHumanQcMath.Attention twelveAttention(Drawing d){
        GmtHumanQcMath.Attention a=d.gap,b=d.alignment;
        if(a==GmtHumanQcMath.Attention.STRONG||b==GmtHumanQcMath.Attention.STRONG)return GmtHumanQcMath.Attention.STRONG;
        if(a==GmtHumanQcMath.Attention.CHECK||b==GmtHumanQcMath.Attention.CHECK)return GmtHumanQcMath.Attention.CHECK;
        if(a==GmtHumanQcMath.Attention.CLEAR||b==GmtHumanQcMath.Attention.CLEAR)return GmtHumanQcMath.Attention.CLEAR;
        return GmtHumanQcMath.Attention.UNASSESSABLE;
    }

    static boolean flagged(GmtHumanQcMath.Attention a){return a==GmtHumanQcMath.Attention.CHECK||a==GmtHumanQcMath.Attention.STRONG;}

    static String twelveWord(Drawing d){
        if(d.notJudged!=null)return d.notJudged.contains("hand")?"hand in the way":d.notJudged.contains("small")?"too small":null;
        java.util.List<String> w=new java.util.ArrayList<>();
        if(flagged(d.gap))w.add(Double.isFinite(d.gapValue)&&d.gapValue>=0.094?"gap large":"gap small");
        if(flagged(d.alignment))w.add(d.twelveKind!=null?d.twelveKind:"alignment");
        return w.isEmpty()?null:String.join(" + ",w);
    }

    static String batonWord(GmtHumanQcMath.Attention a,boolean off,boolean rot,String notJudged){
        if(notJudged!=null)return notJudged.contains("hand")?"hand in the way":notJudged.contains("small")?"too small":null;
        if(flagged(a))return off&&rot?"off-centre + rotated":off?"off-centre":rot?"rotated":"position";
        return null;
    }

    static String roundWord(GmtRoundMarkerAnalyzer.Marker m){
        if(m.hand)return "hand in the way";
        if(m.tooSmall)return "too small";
        if(flagged(m.attention))return m.offCentre&&m.sizeOdd?"off-centre + size":m.sizeOdd?"size":"off-centre";
        return null;
    }

    private static String roundReason(Drawing d,GmtRoundMarkerAnalyzer.Marker m,String fallback){
        String n=d.roundNotJudged.get(m.hour);
        if(n!=null&&!n.isEmpty())return n;
        if(m.hand)return "hand in the way";
        if(m.tooSmall)return "too small";
        if(m.lowReason!=null&&!m.lowReason.isEmpty())return m.lowReason;
        if(m.note!=null&&!m.note.isEmpty())return m.note;
        return fallback;
    }

    private static void batonMark(Canvas c,Drawing d,GmtSixLandmarkAnalyzer.Geometry s,GmtHumanQcMath.Attention a,String notJudged,String w,float badge,float text){
        if(s==null)return;
        double bw=Math.hypot(s.outerRight[0]-s.outerLeft[0],s.outerRight[1]-s.outerLeft[1]);
        float lw=(float)Math.max(1.0,bw/16.0);
        double[][] p=s.polygon();
        boolean judged=notJudged==null&&a!=GmtHumanQcMath.Attention.UNASSESSABLE;
        if(!judged){Paint nj=stroke(UNKNOWN,lw,220);for(int i=0;i<4;i++)dashed(c,p[i],p[(i+1)%4],bw/4.0,nj);}
        else{
            Path path=new Path();path.moveTo((float)p[0][0],(float)p[0][1]);
            for(int i=1;i<4;i++)path.lineTo((float)p[i][0],(float)p[i][1]);
            path.close();c.drawPath(path,stroke(colour(a),lw,235));
        }
        double cx=(p[0][0]+p[1][0]+p[2][0]+p[3][0])/4,cy=(p[0][1]+p[1][1]+p[2][1]+p[3][1])/4;
        double len=Math.hypot(s.outerLeft[0]-s.innerLeft[0],s.outerLeft[1]-s.innerLeft[1]);
        badge(c,cx,cy,badge,judged?a:GmtHumanQcMath.Attention.UNASSESSABLE);
        if(w!=null)word(c,d,cx,cy,len*0.5,w,judged?colour(a):UNKNOWN,text);
    }

    private static void measurementBatonMark(Canvas c,Drawing d,BatonDrawing b,float badge,float text){
        GmtSixLandmarkAnalyzer.Geometry s=b.geometry;
        if(s==null){
            double[] at=expectedAt(d,b.clockDeg,d.markerCenterR);
            if(at!=null){badge(c,at[0],at[1],badge,GmtHumanQcMath.Attention.UNASSESSABLE);word(c,d,at[0],at[1],badge*2.5,b.notJudged!=null?b.notJudged:"not found",UNKNOWN,text);}
            return;
        }
        double bw=Math.hypot(s.outerRight[0]-s.outerLeft[0],s.outerRight[1]-s.outerLeft[1]);
        float lw=(float)Math.max(1.0,bw/16.0);double[][] p=s.polygon();
        double cx=(p[0][0]+p[1][0]+p[2][0]+p[3][0])/4,cy=(p[0][1]+p[1][1]+p[2][1]+p[3][1])/4;
        if(b.measured){
            Path path=new Path();path.moveTo((float)p[0][0],(float)p[0][1]);for(int i=1;i<4;i++)path.lineTo((float)p[i][0],(float)p[i][1]);path.close();
            c.drawPath(path,stroke(MEASURED,lw,235));measuredBadge(c,cx,cy,badge);
        }else{
            Paint nj=stroke(UNKNOWN,lw,220);for(int i=0;i<4;i++)dashed(c,p[i],p[(i+1)%4],bw/4.0,nj);
            badge(c,cx,cy,badge,GmtHumanQcMath.Attention.UNASSESSABLE);
            if(b.notJudged!=null)word(c,d,cx,cy,bw*1.5,b.notJudged,UNKNOWN,text);
        }
    }

    /** Verdict badge: a filled disc with a symbol, so the verdict does not rely on colour alone. */
    private static void badge(Canvas c,double x,double y,float r,GmtHumanQcMath.Attention a){
        int col=colour(a);
        c.drawCircle((float)x,(float)y,r*1.12f,fill(Color.rgb(12,16,22),210));
        c.drawCircle((float)x,(float)y,r,fill(col,245));
        int ink=a==GmtHumanQcMath.Attention.CHECK?Color.rgb(20,20,20):Color.WHITE;
        float sw=Math.max(1.5f,r*0.26f);
        Paint pen=stroke(ink,sw,255);
        switch(a){
            case CLEAR:{
                Path t=new Path();t.moveTo((float)(x-r*0.48),(float)(y+r*0.02));t.lineTo((float)(x-r*0.12),(float)(y+r*0.38));t.lineTo((float)(x+r*0.50),(float)(y-r*0.36));c.drawPath(t,pen);break;
            }
            case CHECK:bang(c,x,y,r,pen,sw,ink);break;
            case STRONG:bang(c,x-r*0.30,y,r,pen,sw,ink);bang(c,x+r*0.30,y,r,pen,sw,ink);break;
            default:c.drawLine((float)(x-r*0.45),(float)y,(float)(x+r*0.45),(float)y,pen);
        }
    }

    private static void measuredBadge(Canvas c,double x,double y,float r){
        c.drawCircle((float)x,(float)y,r*1.12f,fill(Color.rgb(12,16,22),210));
        c.drawCircle((float)x,(float)y,r,fill(MEASURED,245));
        Paint t=new Paint(Paint.ANTI_ALIAS_FLAG);t.setColor(Color.rgb(12,16,22));t.setFakeBoldText(true);t.setTextAlign(Paint.Align.CENTER);t.setTextSize(r*1.15f);
        float yy=(float)y-(t.ascent()+t.descent())/2f;c.drawText("M",(float)x,yy,t);
    }

    private static void bang(Canvas c,double x,double y,float r,Paint pen,float sw,int ink){
        c.drawLine((float)x,(float)(y-r*0.55),(float)x,(float)(y+r*0.12),pen);
        c.drawCircle((float)x,(float)(y+r*0.45),sw*0.62f,fill(ink,255));
    }

    private static void word(Canvas c,Drawing d,double x,double y,double clearance,String w,int col,float size){
        double dx=d.dialCx-x,dy=d.dialCy-y,dl=Math.hypot(dx,dy);
        if(!Double.isFinite(dl)||dl<1e-6){dx=0;dy=1;dl=1;}
        dx/=dl;dy/=dl;
        Paint t=new Paint(Paint.ANTI_ALIAS_FLAG);t.setTextSize(size);t.setFakeBoldText(true);t.setColor(col);
        float tw=t.measureText(w),th=size;double off=clearance+size*0.9;
        double px=x+dx*(off+Math.abs(dx)*tw*0.5),py=y+dy*(off+Math.abs(dy)*th*0.5);
        float l=(float)(px-tw/2-size*0.35),r=(float)(px+tw/2+size*0.35),tp=(float)(py-th*0.72),bt=(float)(py+th*0.42);
        c.drawRect(l,tp,r,bt,fill(Color.rgb(12,16,22),205));c.drawText(w,(float)(px-tw/2),(float)(py+th*0.3),t);
    }

    private static void legend(Canvas c,int W,int H,double centreX,double dialBottom,double maxWidth,float badge){
        float r=Math.max(5f,badge*0.8f),size=r*1.9f,gap=size*0.8f;
        String[] words={"nothing flagged","worth a look","check closely","not judged"};
        GmtHumanQcMath.Attention[] as={GmtHumanQcMath.Attention.CLEAR,GmtHumanQcMath.Attention.CHECK,GmtHumanQcMath.Attention.STRONG,GmtHumanQcMath.Attention.UNASSESSABLE};
        Paint t=new Paint(Paint.ANTI_ALIAS_FLAG);t.setTextSize(size);t.setColor(Color.WHITE);
        float total=0;for(String w:words)total+=2*r+size*0.35f+t.measureText(w)+gap;total-=gap;
        float lim=(float)Math.min(W*0.96,maxWidth);
        if(total>lim){float k=lim/total;r*=k;size*=k;gap*=k;t.setTextSize(size);total*=k;}
        float y=(float)Math.min(H-r*1.6f,Double.isFinite(dialBottom)&&dialBottom+r*2.4f<H?dialBottom+r*1.8f:H-r*1.6f);
        float x=(float)Math.max(r,Math.min(W-total-r,centreX-total/2));
        c.drawRect(x-r,y-r*1.45f,x+total+r,y+r*1.45f,fill(Color.rgb(12,16,22),185));
        for(int i=0;i<4;i++){badge(c,x+r,y,r,as[i]);x+=2*r+size*0.35f;c.drawText(words[i],x,y+size*0.35f,t);x+=t.measureText(words[i])+gap;}
    }

    private static void measurementLegend(Canvas c,int W,int H,double centreX,double dialBottom,double maxWidth,float badge){
        float r=Math.max(5f,badge*0.8f),size=r*1.9f,gap=size*0.9f;
        String a="measured · not yet judged",b="not judged on this photo";
        Paint t=new Paint(Paint.ANTI_ALIAS_FLAG);t.setTextSize(size);t.setColor(Color.WHITE);
        float total=2*r+size*0.35f+t.measureText(a)+gap+2*r+size*0.35f+t.measureText(b);
        float lim=(float)Math.min(W*0.96,maxWidth);
        if(total>lim){float k=lim/total;r*=k;size*=k;gap*=k;t.setTextSize(size);total*=k;}
        float y=(float)Math.min(H-r*1.6f,Double.isFinite(dialBottom)&&dialBottom+r*2.4f<H?dialBottom+r*1.8f:H-r*1.6f);
        float x=(float)Math.max(r,Math.min(W-total-r,centreX-total/2));
        c.drawRect(x-r,y-r*1.45f,x+total+r,y+r*1.45f,fill(Color.rgb(12,16,22),185));
        measuredBadge(c,x+r,y,r);x+=2*r+size*0.35f;c.drawText(a,x,y+size*0.35f,t);x+=t.measureText(a)+gap;
        badge(c,x+r,y,r,GmtHumanQcMath.Attention.UNASSESSABLE);x+=2*r+size*0.35f;c.drawText(b,x,y+size*0.35f,t);
    }

    private static void banner(Canvas c,String text,double cx,double top,double maxWidth,int W,float badge){
        Paint t=new Paint(Paint.ANTI_ALIAS_FLAG);t.setTextSize(badge*1.5f);t.setFakeBoldText(true);t.setColor(Color.WHITE);
        float lim=(float)Math.min(W*0.96,maxWidth*0.8);
        while(t.measureText(text)>lim&&t.getTextSize()>8)t.setTextSize(t.getTextSize()-1);
        float tw=t.measureText(text),ts=t.getTextSize(),y=(float)Math.max(ts*1.4,top+ts*1.6);
        float x=(float)Math.max(ts*0.5,Math.min(W-tw-ts*0.5,cx-tw/2));
        c.drawRect(x-ts*0.5f,y-ts*1.05f,x+tw+ts*0.5f,y+ts*0.45f,fill(Color.rgb(12,16,22),215));c.drawText(text,x,y,t);
    }

    private static void circleDashed(Canvas c,double x,double y,double R,Paint p){
        for(int i=0;i<24;i+=2){double a0=2*Math.PI*i/24,a1=2*Math.PI*(i+1)/24;c.drawLine((float)(x+R*Math.cos(a0)),(float)(y+R*Math.sin(a0)),(float)(x+R*Math.cos(a1)),(float)(y+R*Math.sin(a1)),p);}
    }

    /** Detail overlay for the shared close-up planner. */
    static Bitmap renderDetail(Bitmap watch,Drawing d){
        if(watch==null||d==null||!d.hasAnything())return null;
        Bitmap out=Bitmap.createBitmap(watch.getWidth(),watch.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);float base=Math.max(1f,Math.min(out.getWidth(),out.getHeight())/900f);
        if(Double.isFinite(d.dialCx)&&Double.isFinite(d.dialA)&&Double.isFinite(d.dialB)){
            Paint ring=stroke(Color.WHITE,1.0f*base,90);Path p=new Path();double t=Math.toRadians(d.dialAngleDeg),ct=Math.cos(t),st=Math.sin(t);
            for(int i=0;i<=180;i++){double a=2*Math.PI*i/180.0,x=d.dialA*Math.cos(a),y=d.dialB*Math.sin(a);float px=(float)(d.dialCx+x*ct-y*st),py=(float)(d.dialCy+x*st+y*ct);if(i==0)p.moveTo(px,py);else p.lineTo(px,py);}c.drawPath(p,ring);
        }

        if(d.genericBatonsOnly)for(BatonDrawing b:d.batons)drawMeasurementBaton(c,b);
        else{
            drawBaton(c,d.six,d.sixAttention,d.sixCentring,d.sixNotJudged);
            drawBaton(c,d.nine,d.nineAttention,d.nineCentring,d.nineSideUnknown?"date side not determined":d.nineNotJudged);
        }
        for(GmtRoundMarkerAnalyzer.Marker m:d.round)drawRound(c,d,m);

        GmtTwelveLandmarkAnalyzer.Geometry g=d.twelve;
        if(g==null)return out;
        double width=Math.hypot(g.triRight[0]-g.triLeft[0],g.triRight[1]-g.triLeft[1]);float lw=(float)Math.max(1.0,width/40.0);

        if(d.measurementOnly){
            double[][] pts={g.triLeft,g.triRight,g.triTip};
            if(!d.twelveMeasured){Paint nj=stroke(UNKNOWN,lw,220);for(int i=0;i<3;i++)dashed(c,pts[i],pts[(i+1)%3],width/8.0,nj);return out;}
            Path tp=new Path();tp.moveTo((float)g.triLeft[0],(float)g.triLeft[1]);tp.lineTo((float)g.triRight[0],(float)g.triRight[1]);tp.lineTo((float)g.triTip[0],(float)g.triTip[1]);tp.close();c.drawPath(tp,stroke(MEASURED,lw,235));
            if(g.tick59==null||g.tick60==null||g.tick01==null)return out;
            Paint tick=stroke(TICK,Math.max(1f,lw*0.8f),230);c.drawLine((float)g.tick59[0],(float)g.tick59[1],(float)g.tick01[0],(float)g.tick01[1],tick);
            Paint dot=fill(TICK,230);float dr=(float)Math.max(1.5,width/30.0);for(double[] q:new double[][]{g.tick59,g.tick60,g.tick01})c.drawCircle((float)q[0],(float)q[1],dr,dot);
            double mx=(g.triLeft[0]+g.triRight[0])/2,my=(g.triLeft[1]+g.triRight[1])/2;double[] foot=footOnLine(mx,my,g.tick59,g.tick01);
            Paint gapP=stroke(d.gapMeasured?MEASURED:UNKNOWN,lw,245);c.drawLine((float)mx,(float)my,(float)foot[0],(float)foot[1],gapP);
            double capx=g.tick01[0]-g.tick59[0],capy=g.tick01[1]-g.tick59[1],cl=Math.hypot(capx,capy);
            if(cl>1e-9){capx=capx/cl*width*0.08;capy=capy/cl*width*0.08;c.drawLine((float)(mx-capx),(float)(my-capy),(float)(mx+capx),(float)(my+capy),gapP);c.drawLine((float)(foot[0]-capx),(float)(foot[1]-capy),(float)(foot[0]+capx),(float)(foot[1]+capy),gapP);}
            Paint sp=stroke(d.alignmentMeasured?MEASURED:UNKNOWN,Math.max(1f,lw*0.7f),200);c.drawLine((float)g.triLeft[0],(float)g.triLeft[1],(float)g.tick59[0],(float)g.tick59[1],sp);c.drawLine((float)g.triRight[0],(float)g.triRight[1],(float)g.tick01[0],(float)g.tick01[1],sp);
            return out;
        }

        int alignCol=colour(d.alignment), gapCol=colour(d.gap);
        if(d.notJudged!=null){Paint nj=stroke(UNKNOWN,lw,220);double[][] pts={g.triLeft,g.triRight,g.triTip};for(int i=0;i<3;i++)dashed(c,pts[i],pts[(i+1)%3],width/8.0,nj);return out;}
        Path tp=new Path();tp.moveTo((float)g.triLeft[0],(float)g.triLeft[1]);tp.lineTo((float)g.triRight[0],(float)g.triRight[1]);tp.lineTo((float)g.triTip[0],(float)g.triTip[1]);tp.close();c.drawPath(tp,stroke(alignCol,lw,235));
        Paint tick=stroke(TICK,Math.max(1f,lw*0.8f),230);c.drawLine((float)g.tick59[0],(float)g.tick59[1],(float)g.tick01[0],(float)g.tick01[1],tick);
        Paint dot=fill(TICK,230);float dr=(float)Math.max(1.5,width/30.0);for(double[] q:new double[][]{g.tick59,g.tick60,g.tick01})c.drawCircle((float)q[0],(float)q[1],dr,dot);
        double mx=(g.triLeft[0]+g.triRight[0])/2,my=(g.triLeft[1]+g.triRight[1])/2;double[] foot=footOnLine(mx,my,g.tick59,g.tick01);
        Paint gapP=stroke(gapCol,flagged(d.gap)?lw*2.2f:lw,245);c.drawLine((float)mx,(float)my,(float)foot[0],(float)foot[1],gapP);
        double capx=(g.tick01[0]-g.tick59[0]),capy=(g.tick01[1]-g.tick59[1]),cl=Math.hypot(capx,capy);
        if(cl>1e-9){capx=capx/cl*width*0.08;capy=capy/cl*width*0.08;c.drawLine((float)(mx-capx),(float)(my-capy),(float)(mx+capx),(float)(my+capy),gapP);c.drawLine((float)(foot[0]-capx),(float)(foot[1]-capy),(float)(foot[0]+capx),(float)(foot[1]+capy),gapP);}
        Paint sp=stroke(alignCol,Math.max(1f,lw*0.7f),200);c.drawLine((float)g.triLeft[0],(float)g.triLeft[1],(float)g.tick59[0],(float)g.tick59[1],sp);c.drawLine((float)g.triRight[0],(float)g.triRight[1],(float)g.tick01[0],(float)g.tick01[1],sp);
        return out;
    }

    private static void drawBaton(Canvas c,GmtSixLandmarkAnalyzer.Geometry s,GmtHumanQcMath.Attention attention,double centring,String notJudged){
        if(s==null)return;double w=Math.hypot(s.outerRight[0]-s.outerLeft[0],s.outerRight[1]-s.outerLeft[1]);float lw=(float)Math.max(1.0,w/16.0);double[][] p=s.polygon();
        if(notJudged!=null){Paint nj=stroke(UNKNOWN,lw,220);for(int i=0;i<4;i++)dashed(c,p[i],p[(i+1)%4],w/4.0,nj);return;}
        int col=colour(attention);Paint o=stroke(col,lw,235);Path path=new Path();path.moveTo((float)p[0][0],(float)p[0][1]);for(int i=1;i<4;i++)path.lineTo((float)p[i][0],(float)p[i][1]);path.close();c.drawPath(path,o);
        drawBatonReferences(c,s,w,lw,col);
    }

    private static void drawMeasurementBaton(Canvas c,BatonDrawing b){
        GmtSixLandmarkAnalyzer.Geometry s=b.geometry;if(s==null)return;double w=Math.hypot(s.outerRight[0]-s.outerLeft[0],s.outerRight[1]-s.outerLeft[1]);float lw=(float)Math.max(1.0,w/16.0);double[][] p=s.polygon();
        if(!b.measured){Paint nj=stroke(UNKNOWN,lw,220);for(int i=0;i<4;i++)dashed(c,p[i],p[(i+1)%4],w/4.0,nj);return;}
        Path path=new Path();path.moveTo((float)p[0][0],(float)p[0][1]);for(int i=1;i<4;i++)path.lineTo((float)p[i][0],(float)p[i][1]);path.close();c.drawPath(path,stroke(MEASURED,lw,235));drawBatonReferences(c,s,w,lw,MEASURED);
    }

    private static void drawBatonReferences(Canvas c,GmtSixLandmarkAnalyzer.Geometry s,double w,float lw,int col){
        if(s.tickAfter==null||s.tickBefore==null)return;Paint tick=stroke(TICK,Math.max(1f,lw*0.8f),230);c.drawLine((float)s.tickAfter[0],(float)s.tickAfter[1],(float)s.tickBefore[0],(float)s.tickBefore[1],tick);
        Paint dot=fill(TICK,230);float dr=(float)Math.max(1.5,w/12.0);for(double[] q:new double[][]{s.tickAfter,s.tickBefore})c.drawCircle((float)q[0],(float)q[1],dr,dot);
        double mx=(s.outerLeft[0]+s.outerRight[0])/2,my=(s.outerLeft[1]+s.outerRight[1])/2;double ix=(s.innerLeft[0]+s.innerRight[0])/2,iy=(s.innerLeft[1]+s.innerRight[1])/2;double ax=ix-mx,ay=iy-my,al=Math.hypot(ax,ay);
        if(al>1e-9){ax/=al;ay/=al;double rx=(s.tickAfter[0]+s.tickBefore[0])/2,ry=(s.tickAfter[1]+s.tickBefore[1])/2;c.drawLine((float)rx,(float)ry,(float)(rx+ax*w*0.9),(float)(ry+ay*w*0.9),tick);}c.drawCircle((float)mx,(float)my,dr,fill(col,240));
    }

    private static void drawRound(Canvas c,Drawing d,GmtRoundMarkerAnalyzer.Marker m){
        if(m==null)return;
        if(!m.found){if(!Double.isFinite(m.seedX)||!(m.expectedRadiusPx>0))return;Paint nj=stroke(UNKNOWN,(float)Math.max(1.0,m.expectedRadiusPx/11.0),170);for(int i=0;i<24;i+=2){double a0=2*Math.PI*i/24,a1=2*Math.PI*(i+1)/24,R=m.expectedRadiusPx;c.drawLine((float)(m.seedX+R*Math.cos(a0)),(float)(m.seedY+R*Math.sin(a0)),(float)(m.seedX+R*Math.cos(a1)),(float)(m.seedY+R*Math.sin(a1)),nj);}return;}
        double dd=m.diameterPx();float lw=(float)Math.max(1.0,dd/22.0);
        if(d.measurementOnly){
            if(!d.roundMeasuredHours.contains(m.hour)){Paint nj=stroke(UNKNOWN,lw,200);double[][] p=m.polygon();for(int i=0;i<p.length;i+=2)c.drawLine((float)p[i][0],(float)p[i][1],(float)p[(i+1)%p.length][0],(float)p[(i+1)%p.length][1],nj);return;}
            c.drawCircle((float)m.x,(float)m.y,(float)m.radiusPx,stroke(MEASURED,lw,235));drawRoundReferences(c,m,dd,lw,MEASURED);return;
        }
        boolean judged=m.attention!=GmtHumanQcMath.Attention.UNASSESSABLE;
        if(!judged){Paint nj=stroke(UNKNOWN,lw,200);double[][] p=m.polygon();for(int i=0;i<p.length;i+=2)c.drawLine((float)p[i][0],(float)p[i][1],(float)p[(i+1)%p.length][0],(float)p[(i+1)%p.length][1],nj);return;}
        int col=colour(m.attention);c.drawCircle((float)m.x,(float)m.y,(float)m.radiusPx,stroke(col,lw,235));drawRoundReferences(c,m,dd,lw,col);
    }

    private static void drawRoundReferences(Canvas c,GmtRoundMarkerAnalyzer.Marker m,double d,float lw,int col){
        if(m.tickBefore==null||m.tickAfter==null)return;Paint tick=stroke(TICK,Math.max(1f,lw*0.8f),230);c.drawLine((float)m.tickBefore[0],(float)m.tickBefore[1],(float)m.tickAfter[0],(float)m.tickAfter[1],tick);
        Paint dot=fill(TICK,230);float dr=(float)Math.max(1.5,d/16.0);for(double[] q:new double[][]{m.tickBefore,m.tickAfter})c.drawCircle((float)q[0],(float)q[1],dr,dot);
        double rx=(m.tickBefore[0]+m.tickAfter[0])/2,ry=(m.tickBefore[1]+m.tickAfter[1])/2;double nx=-(m.tickAfter[1]-m.tickBefore[1]),ny=m.tickAfter[0]-m.tickBefore[0],nl=Math.hypot(nx,ny);
        if(nl>1e-9){if(nx*(m.x-rx)+ny*(m.y-ry)<0){nx=-nx;ny=-ny;}c.drawLine((float)rx,(float)ry,(float)(rx+nx/nl*d*0.6),(float)(ry+ny/nl*d*0.6),tick);}c.drawCircle((float)m.x,(float)m.y,dr,fill(col,240));
    }

    static int statusColour(Drawing d){
        if(d.measurementOnly)return d.twelveMeasured?MEASURED:UNKNOWN;
        if(d.notJudged!=null||d.twelve==null)return UNKNOWN;
        GmtHumanQcMath.Attention a=d.gap,b=d.alignment;
        if(a==GmtHumanQcMath.Attention.STRONG||b==GmtHumanQcMath.Attention.STRONG)return STRONG;
        if(a==GmtHumanQcMath.Attention.CHECK||b==GmtHumanQcMath.Attention.CHECK)return CHECK;
        if(a==GmtHumanQcMath.Attention.CLEAR&&b==GmtHumanQcMath.Attention.CLEAR)return CLEAR;
        return UNKNOWN;
    }

    static String statusText(Drawing d){
        if(d.measurementOnly){
            if(d.twelve==null)return "12 NOT JUDGED · 12 marker not found";
            if(!d.twelveMeasured)return "12 NOT JUDGED · "+(d.notJudged!=null?d.notJudged:"measurement unavailable");
            return d.twelveMeasuredText!=null?d.twelveMeasuredText:"12 MEASURED · not yet judged";
        }
        if(d.notJudged!=null)return "12 NOT JUDGED · "+d.notJudged;
        if(d.twelve==null)return "12 NOT JUDGED · 12 marker not found";
        GmtHumanQcMath.Attention a=d.gap,b=d.alignment;
        boolean strong=a==GmtHumanQcMath.Attention.STRONG||b==GmtHumanQcMath.Attention.STRONG;
        boolean check=!strong&&(a==GmtHumanQcMath.Attention.CHECK||b==GmtHumanQcMath.Attention.CHECK);
        boolean clear=a==GmtHumanQcMath.Attention.CLEAR&&b==GmtHumanQcMath.Attention.CLEAR;
        String head=strong?"12: CHECK CLOSELY":check?"12: WORTH A LOOK":clear?"12: NOTHING FLAGGED"
                :d.alignment==GmtHumanQcMath.Attention.CLEAR?"12: NOTHING FLAGGED · GAP NOT CALLED"
                :d.gap==GmtHumanQcMath.Attention.CLEAR?"12: GAP CLEAR · ALIGNMENT NOT CALLED":"12: NOT CALLED (LOW CONFIDENCE)";
        java.util.List<String> why=new java.util.ArrayList<>();
        if(flagged(d.gap)&&Double.isFinite(d.gapValue))why.add(String.format(Locale.US,"gap %.2f (genuine about 0.08-0.11)",d.gapValue));
        if(flagged(d.alignment)){String k=d.twelveKind!=null?d.twelveKind:"alignment";why.add(Double.isFinite(d.twelveRotationDeg)&&!"off-centre".equals(k)?String.format(Locale.US,"%s %.1f°",k,Math.abs(d.twelveRotationDeg)):k);}
        return why.isEmpty()?head:head+" · "+String.join(", ",why);
    }

    static String batonReason(boolean off,boolean rot,double centring,double rotationDeg){
        java.util.List<String> why=new java.util.ArrayList<>();if(off&&Double.isFinite(centring))why.add(String.format(Locale.US,"off-centre by %.2f of its width",Math.abs(centring)));if(rot&&Double.isFinite(rotationDeg))why.add(String.format(Locale.US,"rotated %.1f°",Math.abs(rotationDeg)));return String.join(", ",why);
    }

    static final int MAX_CLOSE_UPS=4;

    static java.util.List<String> closeUpPlan(Drawing d){
        if(d.measurementOnly)return measurementCloseUpPlan(d);
        java.util.List<String[]> c=new java.util.ArrayList<>();
        GmtHumanQcMath.Attention a12=twelveAttention(d);
        if(d.twelve==null||d.notJudged!=null)c.add(new String[]{"2","12"});
        else if(a12==GmtHumanQcMath.Attention.STRONG)c.add(new String[]{"0","12"});
        else if(a12==GmtHumanQcMath.Attention.CHECK)c.add(new String[]{"1","12"});
        else if(!(d.gap==GmtHumanQcMath.Attention.CLEAR&&d.alignment==GmtHumanQcMath.Attention.CLEAR))c.add(new String[]{"3","12"});
        batonPlan(c,"6",d.six,d.sixAttention,d.sixNotJudged,false);batonPlan(c,"side",d.nine,d.nineAttention,d.nineNotJudged,d.nineSideUnknown);
        for(GmtRoundMarkerAnalyzer.Marker m:d.round){if(m==null)continue;String k="r"+m.hour;if(!m.found){if(Double.isFinite(m.seedX))c.add(new String[]{"2",k});continue;}if(m.attention==GmtHumanQcMath.Attention.STRONG)c.add(new String[]{"0",k});else if(m.attention==GmtHumanQcMath.Attention.CHECK)c.add(new String[]{"1",k});else if(m.attention==GmtHumanQcMath.Attention.UNASSESSABLE)c.add(new String[]{m.hand||m.tooSmall?"2":"3",k});}
        java.util.List<String> out=new java.util.ArrayList<>();for(int pr=0;pr<=3;pr++){if(pr==3&&d.photoNote!=null)break;for(String[] e:c)if(Integer.parseInt(e[0])==pr&&out.size()<MAX_CLOSE_UPS)out.add(e[1]);}
        if(out.isEmpty()&&(d.twelve!=null||Double.isFinite(d.dialCx)))out.add("12");return out;
    }

    private static java.util.List<String> measurementCloseUpPlan(Drawing d){
        java.util.List<String> out=new java.util.ArrayList<>();
        if(d.twelve==null||!d.twelveMeasured)out.add("12");
        for(BatonDrawing b:d.batons)if(!b.measured&&out.size()<MAX_CLOSE_UPS)out.add("b:"+b.key);
        for(GmtRoundMarkerAnalyzer.Marker m:d.round)if(m!=null&&(!m.found||!d.roundMeasuredHours.contains(m.hour))&&out.size()<MAX_CLOSE_UPS)out.add("r"+m.hour);
        if(out.isEmpty()&&(d.twelve!=null||Double.isFinite(d.dialCx)))out.add("12");
        return out;
    }

    private static void batonPlan(java.util.List<String[]> c,String key,GmtSixLandmarkAnalyzer.Geometry g,GmtHumanQcMath.Attention a,String notJudged,boolean sideUnknown){
        if(g==null){c.add(new String[]{"2",key});return;}if(sideUnknown||notJudged!=null){c.add(new String[]{"2",key});return;}if(a==GmtHumanQcMath.Attention.STRONG)c.add(new String[]{"0",key});else if(a==GmtHumanQcMath.Attention.CHECK)c.add(new String[]{"1",key});else if(a==GmtHumanQcMath.Attention.UNASSESSABLE)c.add(new String[]{"3",key});
    }

    static double[] expectedAt(Drawing d,double clockDeg,double rho){
        if(d.twelve==null||d.twelve.tick60==null||!Double.isFinite(d.dialCx))return null;double R=Math.max(d.dialA,d.dialB);double a12=Math.atan2(d.twelve.tick60[1]-d.dialCy,d.twelve.tick60[0]-d.dialCx)+Math.toRadians(clockDeg);return new double[]{d.dialCx+Math.cos(a12)*rho*R,d.dialCy+Math.sin(a12)*rho*R};
    }

    static Bitmap closeUp(Bitmap watch,Bitmap overlay,Drawing d,int size){
        if(watch==null||d==null)return null;overlay=renderDetail(watch,d);java.util.List<Bitmap> ps=new java.util.ArrayList<>();double R=Math.max(d.dialA,d.dialB);
        for(String k:closeUpPlan(d)){
            Bitmap b=null;
            if(k.equals("12"))b=panel12(watch,overlay,d,size);
            else if(k.startsWith("b:")){
                String key=k.substring(2);for(BatonDrawing x:d.batons)if(key.equals(x.key))b=panelMeasurementBaton(watch,overlay,d,x,size);
            }else if(k.equals("6")||k.equals("side")){
                boolean six=k.equals("6");GmtSixLandmarkAnalyzer.Geometry g=six?d.six:d.nine;String L=six?"6":d.nineSideUnknown?"3/9":d.nineLabel;boolean angled=d.photoNote!=null;
                if(g!=null)b=six?panelBaton(watch,overlay,"6",g,d.sixAttention,d.sixNotJudged,batonReason(d.sixOffCentre,d.sixRotated,d.sixCentring,d.sixRotationDeg),d.sixUnstable,angled,size)
                        :panelBaton(watch,overlay,L,g,d.nineAttention,d.nineSideUnknown?"date side not determined":d.nineNotJudged,batonReason(d.nineOffCentre,d.nineRotated,d.nineCentring,d.nineRotationDeg),d.nineUnstable,angled,size);
                else{double[] at=expectedAt(d,six?180:"3".equals(d.nineLabel)?90:270,Gmt126710BlnrMaster.MARKER_CENTER_R);if(at!=null&&!(d.nineSideUnknown&&!six))b=panel(watch,overlay,at[0],at[1],0.26*R,size,UNKNOWN,L+" NOT FOUND · baton or its ticks not found, often a hand over it",true);}
            }else if(k.startsWith("r")){
                int h=Integer.parseInt(k.substring(1));for(GmtRoundMarkerAnalyzer.Marker m:d.round)if(m!=null&&m.hour==h)b=panelRound(watch,overlay,d,m,size);
            }
            if(b!=null)ps.add(b);
        }
        if(ps.isEmpty())return null;if(ps.size()==1)return ps.get(0);int cols=ps.size()<=2?ps.size():2,rows=(ps.size()+cols-1)/cols;int gap=Math.max(6,size/40),cw=0,rh=0;for(Bitmap p:ps){cw=Math.max(cw,p.getWidth());rh=Math.max(rh,p.getHeight());}
        Bitmap out=Bitmap.createBitmap(cols*cw+gap*(cols-1),rows*rh+gap*(rows-1),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(Color.rgb(12,16,22));for(int i=0;i<ps.size();i++)c.drawBitmap(ps.get(i),(i%cols)*(cw+gap),(i/cols)*(rh+gap),null);return out;
    }

    private static Bitmap panel12(Bitmap watch,Bitmap overlay,Drawing d,int size){
        double cx,cy,half;GmtTwelveLandmarkAnalyzer.Geometry g=d.twelve;
        if(g!=null){double w=Math.hypot(g.triRight[0]-g.triLeft[0],g.triRight[1]-g.triLeft[1]);if(g.tick60!=null){cx=(g.triLeft[0]+g.triRight[0]+g.triTip[0]+g.tick60[0])/4;cy=(g.triLeft[1]+g.triRight[1]+g.triTip[1]+g.tick60[1])/4;}else{cx=(g.triLeft[0]+g.triRight[0]+g.triTip[0])/3;cy=(g.triLeft[1]+g.triRight[1]+g.triTip[1])/3;}half=Math.max(20,1.9*w);}
        else if(Double.isFinite(d.dialCx)&&Double.isFinite(d.dialB)){double r=Math.max(d.dialA,d.dialB);cx=d.dialCx;cy=d.dialCy-0.78*r;half=Math.max(20,0.30*r);}else return null;
        boolean dashedBorder=d.measurementOnly?(!d.twelveMeasured||g==null):(d.notJudged!=null||g==null);return panel(watch,overlay,cx,cy,half,size,statusColour(d),statusText(d),dashedBorder);
    }

    private static Bitmap panelMeasurementBaton(Bitmap watch,Bitmap overlay,Drawing d,BatonDrawing b,int size){
        GmtSixLandmarkAnalyzer.Geometry s=b.geometry;if(s==null){double[] at=expectedAt(d,b.clockDeg,d.markerCenterR);return at==null?null:panel(watch,overlay,at[0],at[1],Math.max(20,0.20*Math.max(d.dialA,d.dialB)),size,UNKNOWN,b.label+" NOT JUDGED · "+(b.notJudged!=null?b.notJudged:"not found"),true);}
        double w=Math.hypot(s.outerRight[0]-s.outerLeft[0],s.outerRight[1]-s.outerLeft[1]);double cx,cy;if(s.tickCentre!=null){cx=(s.outerLeft[0]+s.outerRight[0]+s.innerLeft[0]+s.innerRight[0]+2*s.tickCentre[0])/6;cy=(s.outerLeft[1]+s.outerRight[1]+s.innerLeft[1]+s.innerRight[1]+2*s.tickCentre[1])/6;}else{cx=(s.outerLeft[0]+s.outerRight[0]+s.innerLeft[0]+s.innerRight[0])/4;cy=(s.outerLeft[1]+s.outerRight[1]+s.innerLeft[1]+s.innerRight[1])/4;}double half=Math.max(20,2.6*w);
        if(!b.measured)return panel(watch,overlay,cx,cy,half,size,UNKNOWN,b.label+" NOT JUDGED · "+(b.notJudged!=null?b.notJudged:"measurement unavailable"),true);
        String text=b.label+" MEASURED · not yet judged"+(b.measuredText!=null&&!b.measuredText.isEmpty()?" · "+b.measuredText:"");return panel(watch,overlay,cx,cy,half,size,MEASURED,text,false);
    }

    private static Bitmap panelBaton(Bitmap watch,Bitmap overlay,String L,GmtSixLandmarkAnalyzer.Geometry s,GmtHumanQcMath.Attention attention,String notJudged,String reason,boolean unstable,boolean angled,int size){
        double w=Math.hypot(s.outerRight[0]-s.outerLeft[0],s.outerRight[1]-s.outerLeft[1]);double cx=(s.outerLeft[0]+s.outerRight[0]+s.innerLeft[0]+s.innerRight[0]+2*s.tickCentre[0])/6;double cy=(s.outerLeft[1]+s.outerRight[1]+s.innerLeft[1]+s.innerRight[1]+2*s.tickCentre[1])/6;double half=Math.max(20,2.6*w);
        if(notJudged!=null||attention==GmtHumanQcMath.Attention.UNASSESSABLE){String why=notJudged!=null?notJudged:unstable?"the reading changes when the photo is resized slightly":angled?"photo too angled to judge it":"measured with low confidence";return panel(watch,overlay,cx,cy,half,size,UNKNOWN,L+" NOT JUDGED · "+why,true);}
        int col=colour(attention);String text=attention==GmtHumanQcMath.Attention.STRONG?L+": CHECK CLOSELY":attention==GmtHumanQcMath.Attention.CHECK?L+": WORTH A LOOK":L+": NOTHING FLAGGED";if(flagged(attention)&&reason!=null&&!reason.isEmpty())text+=" · "+reason;return panel(watch,overlay,cx,cy,half,size,col,text,false);
    }

    private static Bitmap panelRound(Bitmap watch,Bitmap overlay,Drawing d,GmtRoundMarkerAnalyzer.Marker m,int size){
        String L=String.valueOf(m.hour);if(!m.found){double half=Math.max(20,3.2*m.expectedRadiusPx);return panel(watch,overlay,m.seedX,m.seedY,half,size,UNKNOWN,L+" NOT FOUND · "+(d.measurementOnly?roundReason(d,m,"the marker or its ticks were not found here"):"the marker or its ticks were not found here"),true);}
        double cx=m.tickBefore!=null&&m.tickAfter!=null?(m.x+(m.tickBefore[0]+m.tickAfter[0])/2)/2:m.x,cy=m.tickBefore!=null&&m.tickAfter!=null?(m.y+(m.tickBefore[1]+m.tickAfter[1])/2)/2:m.y;double half=Math.max(20,1.6*m.diameterPx());
        if(d.measurementOnly){if(!d.roundMeasuredHours.contains(m.hour))return panel(watch,overlay,cx,cy,half,size,UNKNOWN,L+" NOT JUDGED · "+roundReason(d,m,"measurement unavailable"),true);return panel(watch,overlay,cx,cy,half,size,MEASURED,L+" MEASURED · not yet judged",false);}
        if(m.attention==GmtHumanQcMath.Attention.UNASSESSABLE){String why=m.hand?"a hand is over or next to it":m.tooSmall?"too small in this photo":m.lowReason!=null&&!m.lowReason.isEmpty()?m.lowReason:m.note!=null&&!m.note.isEmpty()?m.note:"measured with low confidence";return panel(watch,overlay,cx,cy,half,size,UNKNOWN,L+" NOT JUDGED · "+why,true);}
        int col=colour(m.attention);String text=m.attention==GmtHumanQcMath.Attention.STRONG?L+": CHECK CLOSELY":m.attention==GmtHumanQcMath.Attention.CHECK?L+": WORTH A LOOK":L+": NOTHING FLAGGED";java.util.List<String> why=new java.util.ArrayList<>();if(m.offCentre&&Double.isFinite(m.offset))why.add(String.format(Locale.US,"off-centre by %.2f of its width",Math.abs(m.offset)));if(m.sizeOdd&&Double.isFinite(m.sizeRatio))why.add(String.format(Locale.US,"%.2fx the size of the others",m.sizeRatio));if(!why.isEmpty())text+=" · "+String.join(", ",why);return panel(watch,overlay,cx,cy,half,size,col,text,false);
    }

    private static Bitmap panel(Bitmap watch,Bitmap overlay,double cx,double cy,double half,int size,int col,String text,boolean dashedBorder){
        int side=(int)Math.round(2*half);side=Math.min(side,Math.min(watch.getWidth(),watch.getHeight()));int x0=(int)Math.round(cx-side/2.0),y0=(int)Math.round(cy-side/2.0);x0=Math.max(0,Math.min(watch.getWidth()-side,x0));y0=Math.max(0,Math.min(watch.getHeight()-side,y0));
        Bitmap crop=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);Canvas cc=new Canvas(crop);cc.drawBitmap(Bitmap.createBitmap(watch,x0,y0,side,side),0,0,null);if(overlay!=null)cc.drawBitmap(Bitmap.createBitmap(overlay,x0,y0,side,side),0,0,null);
        int strip=Math.max(40,size/5);String head=text,detail="";int dot=text.indexOf(" · ");if(dot>0&&!text.startsWith("12: NOTHING FLAGGED · GAP")&&!text.startsWith("12: GAP CLEAR")){head=text.substring(0,dot);detail=text.substring(dot+3);}
        Bitmap out=Bitmap.createBitmap(size,size+strip,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(Color.rgb(12,16,22));c.drawBitmap(Bitmap.createScaledBitmap(crop,size,size,true),0,0,null);float bw=Math.max(2f,size/120f);Paint border=stroke(col,bw,255);
        if(dashedBorder){double[][] cs={{bw/2,bw/2},{size-bw/2,bw/2},{size-bw/2,size+strip-bw/2},{bw/2,size+strip-bw/2}};for(int i=0;i<4;i++)dashed(c,cs[i],cs[(i+1)%4],size/24.0,border);}else c.drawRect(bw/2,bw/2,size-bw/2,size+strip-bw/2,border);
        Paint t=new Paint(Paint.ANTI_ALIAS_FLAG);t.setColor(col);t.setTextSize(strip*0.30f);t.setFakeBoldText(true);while(t.measureText(head)>size-3*bw-8&&t.getTextSize()>8)t.setTextSize(t.getTextSize()-1);c.drawText(head,bw+6,size+strip*0.40f,t);
        if(!detail.isEmpty()){Paint u=new Paint(Paint.ANTI_ALIAS_FLAG);u.setColor(Color.WHITE);u.setTextSize(strip*0.24f);while(u.measureText(detail)>size-3*bw-8&&u.getTextSize()>8)u.setTextSize(u.getTextSize()-1);c.drawText(detail,bw+6,size+strip*0.80f,u);}return out;
    }

    private static void dashed(Canvas c,double[] a,double[] b,double dash,Paint p){double len=Math.hypot(b[0]-a[0],b[1]-a[1]);if(len<1e-9)return;dash=Math.max(2,dash);for(double s=0;s<len;s+=2*dash){double e=Math.min(len,s+dash);c.drawLine((float)(a[0]+(b[0]-a[0])*s/len),(float)(a[1]+(b[1]-a[1])*s/len),(float)(a[0]+(b[0]-a[0])*e/len),(float)(a[1]+(b[1]-a[1])*e/len),p);}}

    static double[] footOnLine(double px,double py,double[] a,double[] b){double ux=b[0]-a[0],uy=b[1]-a[1],l2=ux*ux+uy*uy;if(l2<1e-12)return new double[]{a[0],a[1]};double t=((px-a[0])*ux+(py-a[1])*uy)/l2;return new double[]{a[0]+ux*t,a[1]+uy*t};}

    private static void label(Canvas c,String s,float x,float y,float size,int colour){Paint o=new Paint(Paint.ANTI_ALIAS_FLAG);o.setTextSize(size);o.setColor(Color.BLACK);o.setAlpha(200);o.setStyle(Paint.Style.STROKE);o.setStrokeWidth(Math.max(2f,size/5f));c.drawText(s,x,y,o);Paint f=new Paint(Paint.ANTI_ALIAS_FLAG);f.setTextSize(size);f.setColor(colour);c.drawText(s,x,y,f);}
    private static Paint stroke(int col,float w,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w);p.setColor(col);p.setAlpha(alpha);return p;}
    private static Paint fill(int col,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.FILL);p.setColor(col);p.setAlpha(alpha);return p;}
}
