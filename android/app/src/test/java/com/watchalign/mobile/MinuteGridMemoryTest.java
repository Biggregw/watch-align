package com.watchalign.mobile;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/** The minute grid is held as float (owner's 12 MP phone photo ran a 256 MB phone out of memory as double): the samples
 *  are float-exact, so the float median and reads are the double ones, bit for bit. */
public class MinuteGridMemoryTest {
    @Test public void floatMedianIsTheDoubleMedian(){
        Random r=new Random(7);
        for(int trial=0;trial<200;trial++){
            int nr=1+r.nextInt(9),na=1+r.nextInt(40);
            float[][] f=new float[nr][na];double[][] d=new double[nr][na];
            for(int i=0;i<nr;i++)for(int j=0;j<na;j++){
                float v=r.nextInt(10)==0?Float.NaN:(float)(r.nextDouble()*255);
                f[i][j]=v;d[i][j]=v;
            }
            assertEquals(Double.doubleToLongBits(ClearlyOff.nanmedian(d)),Double.doubleToLongBits(ClearlyOff.nanmedian(f)));
        }
    }

    @Test public void aSampleOutsideThePhotoReadsAsTheFill(){
        float[][] v={{1.5f,Float.NaN}};
        assertEquals(1.5,ClearlyOff.at(v,0,0,9.25),0);
        assertEquals(9.25,ClearlyOff.at(v,0,1,9.25),0);
    }
}
