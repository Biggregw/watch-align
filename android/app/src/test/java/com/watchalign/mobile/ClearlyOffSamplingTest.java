package com.watchalign.mobile;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.*;

/** Sampling/storage changes must preserve exact numeric output, including off-image samples. */
public class ClearlyOffSamplingTest {
    @Test public void rowSamplingMatchesTheOriginalFullGridBitForBit(){
        int w=43,h=37;float[] pixels=new float[w*h];Random random=new Random(194063);
        for(int k=0;k<pixels.length;k++)pixels[k]=random.nextFloat()*255;
        double[] H={18,2,20,-1,16,17,.04,-.03,1};
        ClearlyOff.Img image=new ClearlyOff.Img(pixels,w,h,H);
        double[] radii={0,.12,.55,.92,1.2,1.8},angles=new double[181];
        for(int i=0;i<angles.length;i++)angles[i]=i*2*Math.PI/180;
        double[] x=new double[radii.length*angles.length],y=new double[x.length];
        for(int i=0;i<radii.length;i++)for(int j=0;j<angles.length;j++){
            x[i*angles.length+j]=radii[i]*Math.sin(angles[j]);
            y[i*angles.length+j]=-radii[i]*Math.cos(angles[j]);
        }
        double[] expected=image.sampleCanon(x,y);double[][] actual=image.polar(radii,angles);
        for(int i=0;i<radii.length;i++)for(int j=0;j<angles.length;j++)
            assertEquals(Double.doubleToLongBits(expected[i*angles.length+j]),Double.doubleToLongBits(actual[i][j]));
    }

    @Test public void primitiveMedianPreservesOriginalSemanticsAndInput(){
        for(double[][] values:new double[][][]{
                {},{{Double.NaN}},{{3,Double.NaN,-2},{9,1}},{{3},{8,Double.NaN,4,-2}},
                {{-0.0,0.0},{Double.NaN}},{{Double.NEGATIVE_INFINITY,3,Double.POSITIVE_INFINITY}}}){
            List<Double> original=new ArrayList<>();
            for(double[] row:values)for(double x:row)if(!Double.isNaN(x))original.add(x);
            double[][] copy=new double[values.length][];for(int i=0;i<values.length;i++)copy[i]=values[i].clone();
            double expected=ClearlyOff.median(ClearlyOff.toArray(original));
            assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(ClearlyOff.nanmedian(values)));
            for(int i=0;i<values.length;i++)assertArrayEquals(copy[i],values[i],0);
        }
    }
}
