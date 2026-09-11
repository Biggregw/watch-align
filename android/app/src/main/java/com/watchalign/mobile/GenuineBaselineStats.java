package com.watchalign.mobile;

import java.util.Arrays;

/** Robust statistics for genuine-reference distributions. */
final class GenuineBaselineStats {
    static final class Summary {
        final int n;
        final double median;
        final double mad;
        final double sigma;
        Summary(int n,double median,double mad,double sigma){this.n=n;this.median=median;this.mad=mad;this.sigma=sigma;}
        double tolerance(double floor){ return Math.max(floor, 3.0*sigma); }
        double delta(double value){ return value-median; }
        double robustZ(double value,double floorSigma){ return delta(value)/Math.max(floorSigma,sigma); }
    }

    static Summary summarize(double[] values,int count){
        if(values==null||count<=0)return new Summary(0,Double.NaN,Double.NaN,Double.NaN);
        double[] clean=new double[count];int n=0;
        for(int i=0;i<Math.min(count,values.length);i++)if(Double.isFinite(values[i]))clean[n++]=values[i];
        if(n==0)return new Summary(0,Double.NaN,Double.NaN,Double.NaN);
        clean=Arrays.copyOf(clean,n);Arrays.sort(clean);
        double med=medianSorted(clean);
        double[] dev=new double[n];for(int i=0;i<n;i++)dev[i]=Math.abs(clean[i]-med);Arrays.sort(dev);
        double mad=medianSorted(dev);
        return new Summary(n,med,mad,1.4826*mad);
    }

    static int severity(double value,Summary baseline,double floorTolerance){
        if(baseline==null||baseline.n==0||!Double.isFinite(value)||!Double.isFinite(baseline.median))return 0;
        double a=Math.abs(value-baseline.median);
        double tol=baseline.tolerance(floorTolerance);
        if(a>tol*1.75)return 2;
        if(a>tol)return 1;
        return 0;
    }

    private static double medianSorted(double[] a){int n=a.length;return (n&1)==1?a[n/2]:(a[n/2-1]+a[n/2])*0.5;}
    private GenuineBaselineStats(){}
}
