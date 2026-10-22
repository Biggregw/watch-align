package com.watchalign.mobile;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase B: the Java MarkerGeometry stage reproduces the research reference (tools/research/measurement_architecture/
 * methods.py, LB and the ring level) on real measurements: parity.csv holds the inputs and the Python results for 35
 * ArchMeasure records of the committed public genuine photographs (originals and synthetic marker moves).
 */
public class MarkerGeometryTest {
    static final double TOL=1e-6;

    static ModelSpec.Shape shape(String s){return ModelSpec.Shape.valueOf(s.toUpperCase());}
    static double num(String s){return s.isEmpty()?Double.NaN:Double.parseDouble(s);}

    @Test public void reproducesTheResearchReferenceOnRealMeasurements() throws Exception {
        Map<String,List<String[]>> recs=new LinkedHashMap<>();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/marker_geometry/parity.csv"),StandardCharsets.UTF_8))){
            String head=r.readLine();assertNotNull(head);String line;
            while((line=r.readLine())!=null)recs.computeIfAbsent(line.split(",",-1)[0],k->new ArrayList<>()).add(line.split(",",-1));
        }
        assertTrue(recs.size()>=30);
        int values=0;
        for(List<String[]> rows:recs.values()){
            String[] h0=rows.get(0)[1].split(";");double[] H=new double[9];for(int i=0;i<9;i++)H[i]=Double.parseDouble(h0[i]);
            double rpx=num(rows.get(0)[2]);
            List<MarkerGeometry.In> in=new ArrayList<>();
            for(String[] c:rows){
                boolean ok=MarkerGeometry.eligible("1".equals(c[10]),"1".equals(c[11]),num(c[12]),"1".equals(c[13]),c[14].replace('|',','));
                assertEquals("eligibility of hour "+c[3],"1".equals(c[15]),ok);
                in.add(new MarkerGeometry.In(Integer.parseInt(c[3]),shape(c[4]),num(c[5]),num(c[6]),num(c[7]),num(c[8]),num(c[9]),ok));
            }
            MarkerGeometry.Result res=MarkerGeometry.compute(H,rpx,in,MarkerGeometry.K_PARALLAX);
            for(String[] c:rows){
                MarkerGeometry.Marker m=res.at(Integer.parseInt(c[3]));
                double[] exp={num(c[16]),num(c[17]),num(c[18])},got={m.tangPct,m.radPct,m.tiltDeg};
                for(int i=0;i<3;i++){assertEquals("hour "+c[3]+" value "+i,Double.isNaN(exp[i]),Double.isNaN(got[i]));
                    if(!Double.isNaN(exp[i])){assertEquals(exp[i],got[i],TOL);values++;}}
            }
            String[] c=rows.get(0);
            assertEquals(!c[19].isEmpty(),res.ring.ok);
            if(res.ring.ok){
                double[] exp={num(c[19]),num(c[20]),num(c[21]),num(c[22]),num(c[23]),num(c[24])};
                double[] got={res.ring.txPct,res.ring.tyPct,res.ring.scalePct,res.ring.rotDeg,res.ring.placeXPct,res.ring.placeYPct};
                for(int i=0;i<6;i++){assertEquals(exp[i],got[i],TOL);values++;}
            }
        }
        assertTrue(values>900);
    }

    /** twelve markers at r = 0.75, moved by a ring (tx, ty, scale, rotation) plus their own offsets */
    static List<MarkerGeometry.In> dial(double tx,double ty,double s,double rot,int hour,double dx,double dy){
        List<MarkerGeometry.In> in=new ArrayList<>();
        for(int h=1;h<=12;h++){
            double a=Math.toRadians(30*(h%12)),fx=0.75*Math.sin(a),fy=-0.75*Math.cos(a);
            double ox=(1+s)*(Math.cos(rot)*fx-Math.sin(rot)*fy)+tx+(h==hour?dx:0),oy=(1+s)*(Math.sin(rot)*fx+Math.cos(rot)*fy)+ty+(h==hour?dy:0);
            ModelSpec.Shape sh=h==12?ModelSpec.Shape.TRIANGLE:h%3==0?ModelSpec.Shape.BATON:ModelSpec.Shape.ROUND;
            in.add(new MarkerGeometry.In(h,sh,fx,fy,ox,oy,sh==ModelSpec.Shape.ROUND?Double.NaN:Math.toDegrees(rot),true));
        }
        return in;
    }

    static final double[] FLAT={300,0,500,0,300,500,0,0,1};

    @Test public void oneDisplacedMarkerIsMeasuredAndDoesNotMoveTheOthers(){
        double a=Math.toRadians(120);   // the 4: tangential direction (cos a, sin a)
        MarkerGeometry.Result r=MarkerGeometry.compute(FLAT,300,dial(0,0,0,0,4,0.005*Math.cos(a),0.005*Math.sin(a)),MarkerGeometry.K_PARALLAX);
        assertEquals(0.5,r.at(4).tangPct,1e-6);assertEquals(0,r.at(4).radPct,1e-6);
        for(MarkerGeometry.Marker m:r.markers)if(m.hour!=4)assertEquals(0,m.posPct(),1e-9);
    }

    @Test public void aWholeRingMoveIsReportedAtRingLevelNotPerMarker(){
        MarkerGeometry.Result r=MarkerGeometry.compute(FLAT,300,dial(0.004,-0.002,0.003,Math.toRadians(0.4),0,0,0),MarkerGeometry.K_PARALLAX);
        for(MarkerGeometry.Marker m:r.markers)assertTrue(m.posPct()<1e-3);
        assertTrue(r.ring.ok);
        assertEquals(0.4,r.ring.txPct,1e-6);assertEquals(-0.2,r.ring.tyPct,1e-6);
        assertEquals(0.3,r.ring.scalePct,0.005);assertEquals(0.4,r.ring.rotDeg,0.005);
        assertEquals(0.4,r.ring.placeXPct,1e-6);   // a face-on photo: no parallax
    }

    @Test public void parallaxIsPredictedTowardsTheFarSide(){
        double[] tilted={300,0,500,0,300,500,0,-0.05,1};   // depth grows towards the 12
        double[] p=MarkerGeometry.pose(tilted);
        assertTrue(p[0]>0);assertEquals(0,p[1],1e-12);assertEquals(-1,p[2],1e-12);
    }

    @Test public void ineligibleMarkersNeverShapeTheRing(){
        List<MarkerGeometry.In> in=dial(0,0,0,0,2,0.02,0.02);
        List<MarkerGeometry.In> out=new ArrayList<>();
        for(MarkerGeometry.In m:in)out.add(m.hour!=2?m:new MarkerGeometry.In(m.hour,m.shape,m.fx,m.fy,m.ox,m.oy,m.tiltAbsDeg,false));
        MarkerGeometry.Result r=MarkerGeometry.compute(FLAT,300,out,MarkerGeometry.K_PARALLAX);
        for(MarkerGeometry.Marker m:r.markers)if(m.hour!=2)assertEquals(0,m.posPct(),1e-9);
        assertFalse(r.at(2).eligible);
    }

    @Test public void eligibilityFollowsTheArchiveRules(){
        assertTrue(MarkerGeometry.eligible(true,true,0.95,false,""));
        assertFalse(MarkerGeometry.eligible(true,true,0.95,true,""));                 // a hand
        assertFalse(MarkerGeometry.eligible(true,true,0.85,false,""));                // 80-90 % seen: untested
        assertFalse(MarkerGeometry.eligible(false,true,1.0,false,""));                // withheld
        assertTrue(MarkerGeometry.eligible(true,false,0.95,false,"12% of its visible edge is inconsistent (shadowed or reflecting metal)"));
        assertFalse(MarkerGeometry.eligible(true,false,0.95,false,"its visible edge is rough (3.0 px); 12% of its visible edge is inconsistent"));
        assertFalse(MarkerGeometry.eligible(true,false,0.95,false,""));               // unreliable without a known reason
    }

    @Test public void offByDefault(){
        assertFalse(MarkerGeometry.ENABLED);
    }
}
