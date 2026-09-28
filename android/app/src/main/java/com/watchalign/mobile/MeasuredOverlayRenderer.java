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
 * The overlay is built from measured elements only, so it cannot disagree with the
 * results: a faint ring for the fitted dial edge, and for the 12 marker the detected
 * triangle, the 59/60/01 tick ends, the gap bracket and the 59/01 spacing brackets, each
 * coloured by its verdict. Further checks add their own elements here.
 */
final class MeasuredOverlayRenderer {
    static final int CLEAR=Color.rgb(40,210,120), CHECK=Color.rgb(255,176,0),
            STRONG=Color.rgb(255,45,45), UNKNOWN=Color.rgb(165,175,190), TICK=Color.rgb(90,220,255);

    /** Everything needed to draw; image coordinates of the analysed bitmap. */
    static final class Drawing {
        double dialCx=Double.NaN,dialCy=Double.NaN,dialA=Double.NaN,dialB=Double.NaN,dialAngleDeg=0;
        GmtTwelveLandmarkAnalyzer.Geometry twelve;
        GmtHumanQcMath.Attention gap=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention alignment=GmtHumanQcMath.Attention.UNASSESSABLE;
        double gapValue=Double.NaN, spacing59=Double.NaN, spacing01=Double.NaN;
        /** Why the 12 marker was not judged, or null when it was. Drawn grey and dashed. */
        String notJudged;
        // 6 o'clock baton (alpha55)
        GmtSixLandmarkAnalyzer.Geometry six;
        GmtHumanQcMath.Attention sixAttention=GmtHumanQcMath.Attention.UNASSESSABLE;
        double sixCentring=Double.NaN;
        String sixNotJudged;
        // 9 o'clock baton (alpha59)
        GmtSixLandmarkAnalyzer.Geometry nine;
        GmtHumanQcMath.Attention nineAttention=GmtHumanQcMath.Attention.UNASSESSABLE;
        double nineCentring=Double.NaN;
        String nineNotJudged;

        // Round markers (alpha61): every marker found, with its verdict in GmtRoundMarkerAnalyzer.Marker.
        java.util.List<GmtRoundMarkerAnalyzer.Marker> round=new java.util.ArrayList<>();

        boolean hasAnything(){return Double.isFinite(dialCx)||twelve!=null||six!=null||nine!=null||!round.isEmpty();}
    }

    private MeasuredOverlayRenderer(){}

    static int colour(GmtHumanQcMath.Attention a){
        switch(a){case CLEAR:return CLEAR;case CHECK:return CHECK;case STRONG:return STRONG;default:return UNKNOWN;}
    }

    static Bitmap render(Bitmap watch,Drawing d){
        if(watch==null||d==null||!d.hasAnything())return null;
        Bitmap out=Bitmap.createBitmap(watch.getWidth(),watch.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        float base=Math.max(1f,Math.min(out.getWidth(),out.getHeight())/900f);

        // Measured dial edge: faint, context only.
        if(Double.isFinite(d.dialCx)&&Double.isFinite(d.dialA)&&Double.isFinite(d.dialB)){
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

        drawBaton(c,d.six,d.sixAttention,d.sixCentring,d.sixNotJudged);
        drawBaton(c,d.nine,d.nineAttention,d.nineCentring,d.nineNotJudged);
        for(GmtRoundMarkerAnalyzer.Marker m:d.round)drawRound(c,m);

        GmtTwelveLandmarkAnalyzer.Geometry g=d.twelve;
        if(g==null)return out;
        double width=Math.hypot(g.triRight[0]-g.triLeft[0],g.triRight[1]-g.triLeft[1]);
        float lw=(float)Math.max(1.0,width/40.0);
        int alignCol=colour(d.alignment), gapCol=colour(d.gap);

        if(d.notJudged!=null){
            // Not judged: only the outline that was found, grey and dashed, so it is never
            // read as a verdict. No brackets or numbers.
            Paint nj=stroke(UNKNOWN,lw,220);
            double[][] pts={g.triLeft,g.triRight,g.triTip};
            for(int i=0;i<3;i++)dashed(c,pts[i],pts[(i+1)%3],width/8.0,nj);
            return out;
        }

        // Detected triangle, coloured by the alignment verdict.
        Paint tri=stroke(alignCol,lw,235);
        Path tp=new Path();
        tp.moveTo((float)g.triLeft[0],(float)g.triLeft[1]);
        tp.lineTo((float)g.triRight[0],(float)g.triRight[1]);
        tp.lineTo((float)g.triTip[0],(float)g.triTip[1]);
        tp.close();
        c.drawPath(tp,tri);

        // 59/60/01 tick ends and the local-12 reference line through 59 and 01.
        Paint tick=stroke(TICK,Math.max(1f,lw*0.8f),230);
        c.drawLine((float)g.tick59[0],(float)g.tick59[1],(float)g.tick01[0],(float)g.tick01[1],tick);
        Paint dot=fill(TICK,230);
        float dr=(float)Math.max(1.5,width/30.0);
        for(double[] q:new double[][]{g.tick59,g.tick60,g.tick01})c.drawCircle((float)q[0],(float)q[1],dr,dot);

        // Gap bracket: base midpoint to the 59-01 line, coloured by the gap verdict.
        double mx=(g.triLeft[0]+g.triRight[0])/2,my=(g.triLeft[1]+g.triRight[1])/2;
        double[] foot=footOnLine(mx,my,g.tick59,g.tick01);
        Paint gapP=stroke(gapCol,lw,245);
        c.drawLine((float)mx,(float)my,(float)foot[0],(float)foot[1],gapP);
        double capx=(g.tick01[0]-g.tick59[0]),capy=(g.tick01[1]-g.tick59[1]),cl=Math.hypot(capx,capy);
        if(cl>1e-9){capx=capx/cl*width*0.08;capy=capy/cl*width*0.08;
            c.drawLine((float)(mx-capx),(float)(my-capy),(float)(mx+capx),(float)(my+capy),gapP);
            c.drawLine((float)(foot[0]-capx),(float)(foot[1]-capy),(float)(foot[0]+capx),(float)(foot[1]+capy),gapP);}

        // 59/01 spacing: triangle base corners to their ticks, coloured by alignment.
        Paint sp=stroke(alignCol,Math.max(1f,lw*0.7f),200);
        c.drawLine((float)g.triLeft[0],(float)g.triLeft[1],(float)g.tick59[0],(float)g.tick59[1],sp);
        c.drawLine((float)g.triRight[0],(float)g.triRight[1],(float)g.tick01[0],(float)g.tick01[1],sp);

        // Labels, small and outlined so they read on any dial.
        float ts=(float)Math.max(9.0,width*0.22);
        if(Double.isFinite(d.gapValue))
            label(c,String.format(Locale.US,"gap %.2f",d.gapValue),(float)(g.triRight[0]+width*0.25),(float)(my-width*0.05),ts,gapCol);
        if(Double.isFinite(d.spacing59))
            label(c,String.format(Locale.US,"%.2f",d.spacing59),(float)(g.triLeft[0]-width*0.95),(float)(g.triLeft[1]+width*0.35),ts,alignCol);
        if(Double.isFinite(d.spacing01))
            label(c,String.format(Locale.US,"%.2f",d.spacing01),(float)(g.triRight[0]+width*0.25),(float)(g.triRight[1]+width*0.35),ts,alignCol);
        return out;
    }

    /** Baton outline (6 or 9), the neighbouring tick ends and the reference midway between them. */
    private static void drawBaton(Canvas c,GmtSixLandmarkAnalyzer.Geometry s,GmtHumanQcMath.Attention attention,double centring,String notJudged){
        if(s==null)return;
        double w=Math.hypot(s.outerRight[0]-s.outerLeft[0],s.outerRight[1]-s.outerLeft[1]);
        float lw=(float)Math.max(1.0,w/16.0);
        double[][] p=s.polygon();
        if(notJudged!=null){
            Paint nj=stroke(UNKNOWN,lw,220);
            for(int i=0;i<4;i++)dashed(c,p[i],p[(i+1)%4],w/4.0,nj);
            return;
        }
        int col=colour(attention);
        Paint o=stroke(col,lw,235);
        Path path=new Path();path.moveTo((float)p[0][0],(float)p[0][1]);
        for(int i=1;i<4;i++)path.lineTo((float)p[i][0],(float)p[i][1]);
        path.close();c.drawPath(path,o);
        Paint tick=stroke(TICK,Math.max(1f,lw*0.8f),230);
        c.drawLine((float)s.tickAfter[0],(float)s.tickAfter[1],(float)s.tickBefore[0],(float)s.tickBefore[1],tick);
        Paint dot=fill(TICK,230);float dr=(float)Math.max(1.5,w/12.0);
        for(double[] q:new double[][]{s.tickAfter,s.tickBefore})c.drawCircle((float)q[0],(float)q[1],dr,dot);
        // Where the baton's centre line should meet the track: midway between the 29 and 31
        // ticks (the coronet sits there, there is no 30 tick). A short tick-coloured stub
        // inward from that point, and a dot at the baton's measured outer-end centre, show
        // any sideways offset directly.
        double mx=(s.outerLeft[0]+s.outerRight[0])/2,my=(s.outerLeft[1]+s.outerRight[1])/2;
        double ix=(s.innerLeft[0]+s.innerRight[0])/2,iy=(s.innerLeft[1]+s.innerRight[1])/2;
        double ax=ix-mx,ay=iy-my,al=Math.hypot(ax,ay);
        if(al>1e-9){ax/=al;ay/=al;
            double rx=(s.tickAfter[0]+s.tickBefore[0])/2,ry=(s.tickAfter[1]+s.tickBefore[1])/2;
            c.drawLine((float)rx,(float)ry,(float)(rx+ax*w*0.9),(float)(ry+ay*w*0.9),tick);}
        c.drawCircle((float)mx,(float)my,dr,fill(col,240));
        if(Double.isFinite(centring)){
            float ts=(float)Math.max(9.0,w*0.55);
            // Beside the baton on the tickBefore side (right of the 6, below the 9), clear of the
            // outline and the minute track.
            double bx=(p[0][0]+p[1][0]+p[2][0]+p[3][0])/4,by=(p[0][1]+p[1][1]+p[2][1]+p[3][1])/4;
            double sx=s.tickBefore[0]-s.tickAfter[0],sy=s.tickBefore[1]-s.tickAfter[1],sn=Math.hypot(sx,sy);
            if(sn>1e-9){sx/=sn;sy/=sn;}
            float lx=(float)(bx+sx*w*1.05-(sx<-0.5?ts*2.2:0)),ly=(float)(by+sy*w*1.05+ts*0.35+(sy>0.5?ts*0.6:0));
            label(c,String.format(Locale.US,"%+.2f",centring),lx,ly,ts,col);
        }
    }

    /**
     * Round marker: the fitted outline coloured by its verdict (grey and dashed when not judged),
     * the tick ends one minute either side, and a short stub inward from midway between them
     * (where the marker's centre should line up) with a dot at the measured centre.
     */
    private static void drawRound(Canvas c,GmtRoundMarkerAnalyzer.Marker m){
        if(m==null||!m.found)return;
        double d=m.diameterPx();
        float lw=(float)Math.max(1.0,d/22.0);
        boolean judged=m.attention!=GmtHumanQcMath.Attention.UNASSESSABLE;
        if(!judged){
            Paint nj=stroke(UNKNOWN,lw,200);
            double[][] p=m.polygon();
            for(int i=0;i<p.length;i+=2)c.drawLine((float)p[i][0],(float)p[i][1],(float)p[(i+1)%p.length][0],(float)p[(i+1)%p.length][1],nj);
            return;
        }
        int col=colour(m.attention);
        c.drawCircle((float)m.x,(float)m.y,(float)m.radiusPx,stroke(col,lw,235));
        Paint tick=stroke(TICK,Math.max(1f,lw*0.8f),230);
        c.drawLine((float)m.tickBefore[0],(float)m.tickBefore[1],(float)m.tickAfter[0],(float)m.tickAfter[1],tick);
        Paint dot=fill(TICK,230);float dr=(float)Math.max(1.5,d/16.0);
        for(double[] q:new double[][]{m.tickBefore,m.tickAfter})c.drawCircle((float)q[0],(float)q[1],dr,dot);
        double rx=(m.tickBefore[0]+m.tickAfter[0])/2,ry=(m.tickBefore[1]+m.tickAfter[1])/2;
        double nx=-(m.tickAfter[1]-m.tickBefore[1]),ny=m.tickAfter[0]-m.tickBefore[0],nl=Math.hypot(nx,ny);
        if(nl>1e-9){
            if(nx*(m.x-rx)+ny*(m.y-ry)<0){nx=-nx;ny=-ny;}   // towards the marker
            c.drawLine((float)rx,(float)ry,(float)(rx+nx/nl*d*0.6),(float)(ry+ny/nl*d*0.6),tick);
        }
        c.drawCircle((float)m.x,(float)m.y,dr,fill(col,240));
        if(m.attention!=GmtHumanQcMath.Attention.CLEAR&&Double.isFinite(m.offset)){
            float ts=(float)Math.max(9.0,d*0.4);
            label(c,String.format(Locale.US,"%+.2f",m.offset),(float)(m.x+m.radiusPx*1.1),(float)(m.y+m.radiusPx*1.2),ts,col);
        }
    }

    /** Worst verdict colour at 12, or grey when not judged. */
    static int statusColour(Drawing d){
        if(d.notJudged!=null||d.twelve==null)return UNKNOWN;
        GmtHumanQcMath.Attention a=d.gap,b=d.alignment;
        if(a==GmtHumanQcMath.Attention.STRONG||b==GmtHumanQcMath.Attention.STRONG)return STRONG;
        if(a==GmtHumanQcMath.Attention.CHECK||b==GmtHumanQcMath.Attention.CHECK)return CHECK;
        if(a==GmtHumanQcMath.Attention.CLEAR&&b==GmtHumanQcMath.Attention.CLEAR)return CLEAR;
        return UNKNOWN;
    }

    static String statusText(Drawing d){
        if(d.notJudged!=null)return "12 NOT JUDGED: "+d.notJudged;
        if(d.twelve==null)return "12 NOT JUDGED: 12 marker not found";
        int c=statusColour(d);
        if(c==STRONG)return "12: CHECK CLOSELY";
        if(c==CHECK)return "12: WORTH A LOOK";
        if(c==CLEAR)return "12: NOTHING FLAGGED";
        if(d.alignment==GmtHumanQcMath.Attention.CLEAR)return "12: NOTHING FLAGGED · GAP NOT CALLED";
        if(d.gap==GmtHumanQcMath.Attention.CLEAR)return "12: GAP CLEAR · ALIGNMENT NOT CALLED";
        return "12: NOT CALLED (LOW CONFIDENCE)";
    }

    /**
     * Close-ups of the 12 marker and, when measured, the 6 baton, side by side: photo plus
     * overlay, enlarged, each with a status strip. Returns null when there is nothing to frame.
     */
    static Bitmap closeUp(Bitmap watch,Bitmap overlay,Drawing d,int size){
        if(watch==null||d==null)return null;
        java.util.List<Bitmap> ps=new java.util.ArrayList<>();
        Bitmap a=panel12(watch,overlay,d,size);if(a!=null)ps.add(a);
        if(d.six!=null)ps.add(panelBaton(watch,overlay,"6",d.six,d.sixAttention,d.sixNotJudged,size));
        if(d.nine!=null)ps.add(panelBaton(watch,overlay,"9",d.nine,d.nineAttention,d.nineNotJudged,size));
        // Round markers: only those flagged, worst first, at most two.
        java.util.List<GmtRoundMarkerAnalyzer.Marker> fl=new java.util.ArrayList<>();
        for(GmtRoundMarkerAnalyzer.Marker m:d.round)if(m.found&&(m.attention==GmtHumanQcMath.Attention.CHECK||m.attention==GmtHumanQcMath.Attention.STRONG))fl.add(m);
        java.util.Collections.sort(fl,(x,y)->Integer.compare(y.attention==GmtHumanQcMath.Attention.STRONG?1:0,x.attention==GmtHumanQcMath.Attention.STRONG?1:0));
        for(int i=0;i<Math.min(2,fl.size());i++)ps.add(panelRound(watch,overlay,fl.get(i),size));
        if(ps.isEmpty())return null;
        if(ps.size()==1)return ps.get(0);
        int gap=Math.max(6,size/40),w=0,h=0;
        for(Bitmap p:ps){w+=p.getWidth();h=Math.max(h,p.getHeight());}
        w+=gap*(ps.size()-1);
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);c.drawColor(Color.rgb(12,16,22));
        int x=0;for(Bitmap p:ps){c.drawBitmap(p,x,0,null);x+=p.getWidth()+gap;}
        return out;
    }

    private static Bitmap panel12(Bitmap watch,Bitmap overlay,Drawing d,int size){
        double cx,cy,half;
        GmtTwelveLandmarkAnalyzer.Geometry g=d.twelve;
        if(g!=null){
            double w=Math.hypot(g.triRight[0]-g.triLeft[0],g.triRight[1]-g.triLeft[1]);
            cx=(g.triLeft[0]+g.triRight[0]+g.triTip[0]+g.tick60[0])/4;
            cy=(g.triLeft[1]+g.triRight[1]+g.triTip[1]+g.tick60[1])/4;
            half=Math.max(20,1.9*w);                   // room for the gap and spacing labels
        }else if(Double.isFinite(d.dialCx)&&Double.isFinite(d.dialB)){
            double r=Math.max(d.dialA,d.dialB);
            cx=d.dialCx;cy=d.dialCy-0.78*r;half=Math.max(20,0.30*r);
        }else return null;
        boolean dashedBorder=d.notJudged!=null||g==null;
        return panel(watch,overlay,cx,cy,half,size,statusColour(d),statusText(d),dashedBorder);
    }

    private static Bitmap panelBaton(Bitmap watch,Bitmap overlay,String L,GmtSixLandmarkAnalyzer.Geometry s,
                                     GmtHumanQcMath.Attention attention,String notJudged,int size){
        double w=Math.hypot(s.outerRight[0]-s.outerLeft[0],s.outerRight[1]-s.outerLeft[1]);
        double cx=(s.outerLeft[0]+s.outerRight[0]+s.innerLeft[0]+s.innerRight[0]+2*s.tickCentre[0])/6;
        double cy=(s.outerLeft[1]+s.outerRight[1]+s.innerLeft[1]+s.innerRight[1]+2*s.tickCentre[1])/6;
        double half=Math.max(20,2.6*w);
        int col;String text;
        if(notJudged!=null){col=UNKNOWN;text=L+" NOT JUDGED: "+notJudged;}
        else{
            col=colour(attention);
            text=attention==GmtHumanQcMath.Attention.STRONG?L+": CHECK CLOSELY"
                    :attention==GmtHumanQcMath.Attention.CHECK?L+": WORTH A LOOK"
                    :attention==GmtHumanQcMath.Attention.CLEAR?L+": NOTHING FLAGGED"
                    :L+": NOT CALLED (LOW CONFIDENCE)";
        }
        return panel(watch,overlay,cx,cy,half,size,col,text,notJudged!=null);
    }

    private static Bitmap panelRound(Bitmap watch,Bitmap overlay,GmtRoundMarkerAnalyzer.Marker m,int size){
        double cx=(m.x+(m.tickBefore[0]+m.tickAfter[0])/2)/2,cy=(m.y+(m.tickBefore[1]+m.tickAfter[1])/2)/2;
        double half=Math.max(20,1.6*m.diameterPx());
        String L=String.valueOf(m.hour);
        int col=colour(m.attention);
        String text=m.attention==GmtHumanQcMath.Attention.STRONG?L+": CHECK CLOSELY":L+": WORTH A LOOK";
        return panel(watch,overlay,cx,cy,half,size,col,text,false);
    }

    private static Bitmap panel(Bitmap watch,Bitmap overlay,double cx,double cy,double half,int size,int col,String text,boolean dashedBorder){
        int side=(int)Math.round(2*half);
        side=Math.min(side,Math.min(watch.getWidth(),watch.getHeight()));
        int x0=(int)Math.round(cx-side/2.0),y0=(int)Math.round(cy-side/2.0);
        x0=Math.max(0,Math.min(watch.getWidth()-side,x0));y0=Math.max(0,Math.min(watch.getHeight()-side,y0));
        Bitmap crop=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);
        Canvas cc=new Canvas(crop);
        cc.drawBitmap(Bitmap.createBitmap(watch,x0,y0,side,side),0,0,null);
        if(overlay!=null)cc.drawBitmap(Bitmap.createBitmap(overlay,x0,y0,side,side),0,0,null);
        int strip=Math.max(24,size/8);
        Bitmap out=Bitmap.createBitmap(size,size+strip,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        c.drawColor(Color.rgb(12,16,22));
        c.drawBitmap(Bitmap.createScaledBitmap(crop,size,size,true),0,0,null);
        float bw=Math.max(2f,size/120f);
        Paint border=stroke(col,bw,255);
        if(dashedBorder){
            double[][] cs={{bw/2,bw/2},{size-bw/2,bw/2},{size-bw/2,size+strip-bw/2},{bw/2,size+strip-bw/2}};
            for(int i=0;i<4;i++)dashed(c,cs[i],cs[(i+1)%4],size/24.0,border);
        }else c.drawRect(bw/2,bw/2,size-bw/2,size+strip-bw/2,border);
        Paint t=new Paint(Paint.ANTI_ALIAS_FLAG);t.setColor(col);t.setTextSize(strip*0.45f);t.setFakeBoldText(true);
        while(t.measureText(text)>size-3*bw-8&&t.getTextSize()>8)t.setTextSize(t.getTextSize()-1);
        c.drawText(text,bw+6,size+strip*0.66f,t);
        return out;
    }

    private static void dashed(Canvas c,double[] a,double[] b,double dash,Paint p){
        double len=Math.hypot(b[0]-a[0],b[1]-a[1]);if(len<1e-9)return;
        dash=Math.max(2,dash);
        for(double s=0;s<len;s+=2*dash){
            double e=Math.min(len,s+dash);
            c.drawLine((float)(a[0]+(b[0]-a[0])*s/len),(float)(a[1]+(b[1]-a[1])*s/len),
                    (float)(a[0]+(b[0]-a[0])*e/len),(float)(a[1]+(b[1]-a[1])*e/len),p);
        }
    }

    static double[] footOnLine(double px,double py,double[] a,double[] b){
        double ux=b[0]-a[0],uy=b[1]-a[1],l2=ux*ux+uy*uy;
        if(l2<1e-12)return new double[]{a[0],a[1]};
        double t=((px-a[0])*ux+(py-a[1])*uy)/l2;
        return new double[]{a[0]+ux*t,a[1]+uy*t};
    }

    private static void label(Canvas c,String s,float x,float y,float size,int colour){
        Paint o=new Paint(Paint.ANTI_ALIAS_FLAG);o.setTextSize(size);o.setColor(Color.BLACK);o.setAlpha(200);
        o.setStyle(Paint.Style.STROKE);o.setStrokeWidth(Math.max(2f,size/5f));
        c.drawText(s,x,y,o);
        Paint f=new Paint(Paint.ANTI_ALIAS_FLAG);f.setTextSize(size);f.setColor(colour);
        c.drawText(s,x,y,f);
    }
    private static Paint stroke(int col,float w,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w);p.setColor(col);p.setAlpha(alpha);return p;}
    private static Paint fill(int col,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.FILL);p.setColor(col);p.setAlpha(alpha);return p;}
}
