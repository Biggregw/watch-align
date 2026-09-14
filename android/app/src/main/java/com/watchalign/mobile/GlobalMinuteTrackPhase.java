package com.watchalign.mobile;

import com.watchalign.mobile.qc.QcModuleResult;
import org.opencv.core.Mat;
import java.util.Arrays;

/** One robust 60-tick phase estimate from the complete rectified minute-track annulus. */
final class GlobalMinuteTrackPhase {
    static final class Result {final boolean available;final double phaseDeg;final double support;final QcModuleResult.Confidence confidence;final String reason;Result(boolean a,double p,double s,QcModuleResult.Confidence c,String r){available=a;phaseDeg=p;support=s;confidence=c;reason=r;}}
    private GlobalMinuteTrackPhase(){}

    static Result estimate(CanonicalGmtDial dial){
        int bins=1440;double[] signal=new double[bins];
        for(int i=0;i<bins;i++){double deg=i*360.0/bins,a=Math.toRadians(deg),sum=0;int n=0;for(double rr=.89;rr<=.99;rr+=.012){int x=(int)Math.round(CanonicalGmtDial.CENTER+Math.sin(a)*CanonicalGmtDial.RADIUS*rr),y=(int)Math.round(CanonicalGmtDial.CENTER-Math.cos(a)*CanonicalGmtDial.RADIUS*rr);if(x<1||y<1||x>=dial.gray.cols()-1||y>=dial.gray.rows()-1)continue;double radial=Math.abs(dial.gray.get(y,x)[0]-dial.gray.get((int)Math.round(CanonicalGmtDial.CENTER-Math.cos(a)*CanonicalGmtDial.RADIUS*(rr-.012)),(int)Math.round(CanonicalGmtDial.CENTER+Math.sin(a)*CanonicalGmtDial.RADIUS*(rr-.012)))[0]);sum+=radial;n++;}signal[i]=n==0?0:sum/n;}
        return estimate(signal,dial.rectification.confidence());
    }

    static Result estimate(double[] signal,QcModuleResult.Confidence rectification){
        if(signal==null||signal.length<360)return unavailable("minute-track angular signal is too short");int phases=Math.max(6,signal.length/60);double[] score=new double[phases];
        for(int phase=0;phase<phases;phase++){double[] ticks=new double[60];for(int k=0;k<60;k++)ticks[k]=signal[(phase+k*phases)%signal.length];Arrays.sort(ticks);double sum=0;for(int i=12;i<48;i++)sum+=ticks[i];score[phase]=sum/36.0;}
        int best=0;for(int i=1;i<phases;i++)if(score[i]>score[best])best=i;double[] copy=score.clone();Arrays.sort(copy);double median=copy[copy.length/2],madValues[]=new double[copy.length];for(int i=0;i<copy.length;i++)madValues[i]=Math.abs(copy[i]-median);Arrays.sort(madValues);double mad=madValues[madValues.length/2];double support=(score[best]-median)/Math.max(1e-6,3*mad);if(!Double.isFinite(support)||support<1.0)return unavailable("global 60-tick periodic support is insufficient; profile axes are used with lower confidence");QcModuleResult.Confidence component=support>=2.0?QcModuleResult.Confidence.HIGH:QcModuleResult.Confidence.MEDIUM;if(component.ordinal()>rectification.ordinal())component=rectification;
        // The minute track repeats every 6 degrees. Report the displacement to the NEAREST
        // expected tick, not the raw 0..6 degree phase. Example: +5.75 degrees is -0.25 degrees.
        double raw=best*360.0/signal.length,phase=normalizeSixDegreePhase(raw);
        return new Result(true,phase,support,component,"global 60-position annulus consensus; phase normalized to nearest 6° tick");
    }
    static double normalizeSixDegreePhase(double raw){double p=raw%6.0;if(p<0)p+=6.0;if(p>=3.0)p-=6.0;return p;}
    private static Result unavailable(String reason){return new Result(false,0,0,QcModuleResult.Confidence.LOW,reason);}
}
