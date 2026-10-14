package com.watchalign.mobile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.Assert.*;

/**
 * Synthetic checks of the luminous rigid fit (triangle, baton, round): exact recovery of a 4 px shift and a 1.5 degree
 * rotation, a hidden edge (inferred from the known shape and labelled), a lit bevel merged with the lume (withheld,
 * never fitted confidently to the wrong edge), brightness change, partial round arcs, local outliers, and that the
 * judged marker never moves its own reference. These validate code behaviour only, not real-photo accuracy.
 */
public class LuminousAlignmentTest {
    static final double RPX=300,CX=520,CY=520,BEVEL=0.016,SEP=0.004;
    static final double[] H={RPX,0,CX,0,RPX,CY,0,0,1};
    static final double[] NO_RING={0,0,0,0};

    /** Markers on a dark dial: lume, dark separator, lit metal band, dial; 1 px ramps. */
    static final class Scene implements LuminousAlignment.Image {
        double[][] lume;double[] circle;   // the main marker: canonical lume polygon or {cx,cy,r}
        final List<double[][]> others=new ArrayList<>();final List<double[]> otherCircles=new ArrayList<>();
        double lumeV=210,sepV=45,metalV=150,dialV=20,gain=1,offset=0;
        /** region (canonical) where the main marker's bevel is lit as brightly as the lume, with no separator */
        BiPredicate<Double,Double> merged;
        final List<BiPredicate<Double,Double>> shadows=new ArrayList<>();
        final List<double[]> specks=new ArrayList<>();
        public double at(double px,double py){
            double x=(px-CX)/RPX,y=(py-CY)/RPX;
            for(double[] s:specks)if(Math.hypot(px-s[0],py-s[1])<1.2)return gain*sepV+offset;
            for(BiPredicate<Double,Double> s:shadows)if(s.test(x,y))return gain*dialV+offset;
            double d=(circle!=null?Math.hypot(x-circle[0],y-circle[1])-circle[2]:lume!=null?signedDistance(lume,x,y):1e9)*RPX;
            boolean merge=merged!=null&&merged.test(x,y);
            for(double[][] p:others){double e=signedDistance(p,x,y)*RPX;if(e<d){d=e;merge=false;}}
            for(double[] c:otherCircles){double e=(Math.hypot(x-c[0],y-c[1])-c[2])*RPX;if(e<d){d=e;merge=false;}}
            double sep=merge?lumeV:sepV,metal=merge?lumeV:metalV;
            double v=ramp(d,0,lumeV,sep);
            if(d>0.5)v=ramp(d,SEP*RPX,sep,metal);
            if(d>SEP*RPX+0.5)v=ramp(d,BEVEL*RPX,metal,dialV);
            return gain*v+offset;
        }
        static double ramp(double d,double at,double in,double out){double t=Math.max(0,Math.min(1,d-at+0.5));return in+(out-in)*t;}
    }

    static double signedDistance(double[][] p,double x,double y){
        double best=Double.POSITIVE_INFINITY;int n=p.length;
        for(int i=0;i<n;i++){double[] a=p[i],b=p[(i+1)%n];double ex=b[0]-a[0],ey=b[1]-a[1];
            double t=Math.max(0,Math.min(1,((x-a[0])*ex+(y-a[1])*ey)/(ex*ex+ey*ey)));best=Math.min(best,Math.hypot(x-a[0]-t*ex,y-a[1]-t*ey));}
        return LuminousAlignment.inside(p,x,y)?-best:best;
    }

    static double[][] moved(double[][] v,double dx,double dy,double deg){
        double[] c=LuminousAlignment.centroid(v);double a=Math.toRadians(deg),co=Math.cos(a),si=Math.sin(a);double[][] o=new double[v.length][];
        for(int i=0;i<v.length;i++){double x=v[i][0]-c[0],y=v[i][1]-c[1];o[i]=new double[]{c[0]+dx+co*x-si*y,c[1]+dy+si*x+co*y};}
        return o;
    }

    static ModelSpec.Marker marker(int hour){return TestModels.gmt().atHour(hour);}
    static double[][] lumeOf(ModelSpec.Marker m){return LuminousAlignment.inset(LuminousAlignment.masterPolygon(m,NO_RING),BEVEL);}
    static LuminousAlignment.Setup setup(){LuminousAlignment.Setup s=new LuminousAlignment.Setup(BEVEL,0.002);s.growthLimitR=0.006;return s;}
    static LuminousAlignment.Result fit(Scene s,int hour){return LuminousAlignment.measure(s,H,marker(hour),NO_RING,setup());}

    static Scene triangle(double dxPx,double dyPx,double deg){Scene s=new Scene();s.lume=moved(lumeOf(marker(12)),dxPx/RPX,dyPx/RPX,deg);return s;}

    @Test public void cleanTriangleReadsZero(){
        LuminousAlignment.Result r=fit(triangle(0,0,0),12);
        assertTrue(r.reason,r.assessed);assertTrue(r.growthFree);assertFalse(r.inferred);
        assertEquals(0,r.tangentialR*RPX,0.15);assertEquals(0,r.radialR*RPX,0.15);assertEquals(0,r.rotationDeg,0.05);
        assertEquals(0,r.growthR*RPX,0.3);
    }

    @Test public void recoversFourPixelShift(){
        LuminousAlignment.Result r=fit(triangle(4,0,0),12);
        assertTrue(r.reason,r.assessed);
        assertEquals(4,r.tangentialR*RPX,0.15);          // + tangential at 12 = right
        assertEquals(0,r.radialR*RPX,0.15);
        LuminousAlignment.Result up=fit(triangle(0,-4,0),12);
        assertEquals(4,up.radialR*RPX,0.15);              // + radial at 12 = up, towards the minute track
    }

    @Test public void recoversOneAndAHalfDegreeRotation(){
        LuminousAlignment.Result r=fit(triangle(0,0,1.5),12);
        assertTrue(r.reason,r.assessed);assertEquals(1.5,r.rotationDeg,0.08);
        assertEquals(0,r.tangentialR*RPX,0.2);assertEquals(0,r.radialR*RPX,0.2);
        assertEquals(-1.5,fit(triangle(0,0,-1.5),12).rotationDeg,0.08);
    }

    @Test public void hiddenTopEdgeIsInferredFromTheKnownShapeAndLabelled(){
        Scene s=triangle(3,0,0);
        double top=LuminousAlignment.centroid(s.lume)[1]-0.04;   // the top of the marker unlit: no edge, no lume start
        s.shadows.add((x,y)->y<top);
        LuminousAlignment.Result r=fit(s,12);
        assertTrue(r.reason,r.assessed);
        assertTrue("two sides seen, the third inferred",r.inferred);assertFalse(r.growthFree);
        assertFalse(r.sides.get(r.sides.size()-1).observed&&r.sides.get(0).observed&&r.sides.get(1).observed);
        assertEquals(3,r.tangentialR*RPX,0.3);
        assertTrue("inferred components carry the known-size uncertainty",r.seRadialR>=0.0019);
    }

    @Test public void litBevelMergedWithTheLumeIsWithheldNotFittedToTheWrongEdge(){
        // A triangle cannot reveal a merged top from its own three sides (it reads as a shift plus a size change); the
        // photo's other markers fix the edge definition, so the 12 is withheld instead of reported as displaced.
        Scene s=dial();
        double top=LuminousAlignment.centroid(s.lume)[1]-0.07;s.merged=(x,y)->y<top;
        AlignmentAnalysis.Output o=whole(s);
        AlignmentDecision.Marker twelve=o.markers.get(0);
        assertEquals(12,twelve.hour);
        assertEquals(AlignmentDecision.Outcome.CANNOT_ASSESS,twelve.outcome);
        assertTrue(twelve.reason,twelve.reason.contains("bevel"));
        for(AlignmentDecision.Marker m:o.markers)if(m.hour!=12)assertEquals("hour "+m.hour+" "+m.reason,AlignmentDecision.Outcome.NO_DEFECT,m.outcome);
    }

    @Test public void wholeDialFindsTheOneDisplacedMarkerAndNamesItsDirection(){
        Scene s=dial();s.lume=moved(s.lume,6/RPX,0,0);   // 12 six px right
        AlignmentAnalysis.Output o=whole(s);
        AlignmentDecision.Marker twelve=o.markers.get(0);
        assertEquals(AlignmentDecision.Outcome.DEFECT,twelve.outcome);
        assertEquals(LuminousAlignment.Param.TANGENTIAL,twelve.primary.param);
        assertEquals("Positioned too far right",twelve.primary.headline());
        for(AlignmentDecision.Marker m:o.markers)if(m.hour!=12)assertEquals("hour "+m.hour,AlignmentDecision.Outcome.NO_DEFECT,m.outcome);
        assertEquals(o.markers.size(),TestModels.gmt().markers.size());   // every marker accounted for
    }

    /** the GMT dial: 12 triangle as the main marker, every other marker clean at its master position */
    static Scene dial(){
        Scene s=triangle(0,0,0);
        for(ModelSpec.Marker m:TestModels.gmt().markers){
            if(m.shape==ModelSpec.Shape.BATON)s.others.add(lumeOf(m));
            else if(m.shape==ModelSpec.Shape.ROUND){double[] c=m.masterPoint();s.otherCircles.add(new double[]{c[0],c[1],m.outerR-BEVEL});}
        }
        return s;
    }

    static AlignmentAnalysis.Output whole(Scene s){
        ModelSpec model=TestModels.gmt();
        java.util.Map<Integer,Alpha99MarkerInterference.Check> checks=new java.util.HashMap<>();
        for(ModelSpec.Marker m:model.markers){Alpha99MarkerInterference.Check c=new Alpha99MarkerInterference.Check(m.hour);c.clean=true;checks.put(m.hour,c);}
        AlignmentReference ref=syntheticReference();
        return AlignmentAnalysis.run((mk,ring)->s,H,report(0),checks,model,null,ref,ref::setup);
    }

    /** test-only reference: zero nominals, envelopes of 0.5 % R / 0.5 deg, edge-offset spread 0.1 % R */
    static AlignmentReference syntheticReference(){
        AlignmentReference r=new AlignmentReference("gmt_126710","synthetic test reference");
        for(String k:new String[]{"triangle","baton","round"}){AlignmentReference.ShapeRef x=new AlignmentReference.ShapeRef();
            x.insetR=BEVEL;x.growthSdR=0.001;x.growthLimitR=0.004;x.photoGrowthLimitR=0.001;x.radialLimitR=0.005;x.tangentialLimitR=0.005;x.rotationLimitDeg=0.5;x.supported=true;x.watches=30;r.shapes.put(k,x);}
        for(ModelSpec.Marker m:TestModels.gmt().markers){AlignmentReference.HourRef h=new AlignmentReference.HourRef();h.radialR=0;h.tangentialR=0;h.rotationDeg=0;r.hours.put(m.hour,h);}
        return r;
    }

    @Test public void brightnessChangeDoesNotMoveTheFit(){
        LuminousAlignment.Result a=fit(triangle(2,1,0.5),12);
        Scene s=triangle(2,1,0.5);s.gain=0.8;s.offset=12;
        LuminousAlignment.Result b=fit(s,12);
        assertTrue(b.assessed);
        assertEquals(a.tangentialR*RPX,b.tangentialR*RPX,0.1);assertEquals(a.radialR*RPX,b.radialR*RPX,0.1);assertEquals(a.rotationDeg,b.rotationDeg,0.05);
    }

    @Test public void localOutliersBarelyMoveTheRobustFit(){
        Scene s=triangle(0,0,0);
        double[][] p=s.lume;   // dark specks just inside the left side: early falls on a few rays
        for(int k=0;k<6;k++){double u=0.3+0.05*k;double x=p[0][0]+u*(p[2][0]-p[0][0]),y=p[0][1]+u*(p[2][1]-p[0][1]);
            s.specks.add(new double[]{CX+(x+0.006)*RPX,CY+y*RPX});}
        LuminousAlignment.Result r=fit(s,12);
        assertTrue(r.reason,r.assessed);
        assertEquals(0,r.tangentialR*RPX,0.35);assertEquals(0,r.rotationDeg,0.15);
    }

    @Test public void batonRecoversShiftAndRotation(){
        Scene s=new Scene();s.lume=moved(lumeOf(marker(6)),-3/RPX,2/RPX,-1.5);
        LuminousAlignment.Result r=fit(s,6);
        assertTrue(r.reason,r.assessed);assertTrue(r.growthFree);
        // at 6: radial = down (+y), tangential = left (-x)
        assertEquals(2,r.radialR*RPX,0.2);assertEquals(3,r.tangentialR*RPX,0.2);assertEquals(-1.5,r.rotationDeg,0.08);
    }

    @Test public void batonWithoutItsEndsWithholdsTheRadialPosition(){
        Scene s=new Scene();s.lume=moved(lumeOf(marker(6)),2/RPX,0,0);
        double[] c=LuminousAlignment.centroid(s.lume);
        s.shadows.add((x,y)->Math.abs(y-c[1])>0.10);   // both short ends hidden
        LuminousAlignment.Result r=fit(s,6);
        assertTrue(r.reason,r.assessed);
        assertFalse("two parallel sides do not fix the position along them",r.radialOk);
        assertTrue(r.tangentialOk);assertTrue(r.rotationOk);
        assertEquals(-2,r.tangentialR*RPX,0.2);
    }

    @Test public void roundRecoversCentreAndIgnoresRotation(){
        ModelSpec.Marker m=marker(1);double[] c=m.masterPoint();
        Scene s=new Scene();s.circle=new double[]{c[0]+3/RPX,c[1]-1/RPX,m.outerR-BEVEL};
        LuminousAlignment.Result r=fit(s,1);
        assertTrue(r.reason,r.assessed);assertFalse(r.rotationOk);assertTrue(r.growthFree);
        double[] er=LuminousAlignment.radial(1),et=LuminousAlignment.tangential(1);
        assertEquals(3*er[0]-1*er[1],r.radialR*RPX,0.15);assertEquals(3*et[0]-1*et[1],r.tangentialR*RPX,0.15);
    }

    @Test public void roundWithAPartlyMergedBevelSetsTheMergedArcAside(){
        ModelSpec.Marker m=marker(4);double[] c=m.masterPoint();
        Scene s=new Scene();s.circle=new double[]{c[0]+2/RPX,c[1],m.outerR-BEVEL};
        s.merged=(x,y)->x>c[0]+0.045;               // a lit bevel on about a fifth of the outline
        LuminousAlignment.Result r=fit(s,4);
        assertTrue(r.reason,r.assessed);assertTrue(r.mergedShare>0.1);
        double[] et=LuminousAlignment.tangential(4),er=LuminousAlignment.radial(4);
        assertEquals(2*er[0],r.radialR*RPX,0.3);assertEquals(2*et[0],r.tangentialR*RPX,0.3);
        Scene t=new Scene();t.circle=s.circle;t.merged=(x,y)->x>c[0]-0.02;   // most of the outline merged
        assertFalse(fit(t,4).assessed);
    }

    @Test public void roundWithAnUnlitHalfIsWithheld(){
        ModelSpec.Marker m=marker(4);double[] c=m.masterPoint();
        Scene s=new Scene();s.circle=new double[]{c[0],c[1],m.outerR-BEVEL};
        s.shadows.add((x,y)->x>c[0]-0.015);           // more than half the marker unlit
        assertFalse(fit(s,4).assessed);
    }

    @Test public void noLumeContrastCannotBeAssessed(){
        Scene s=triangle(0,0,0);s.lumeV=35;s.metalV=35;s.sepV=25;
        LuminousAlignment.Result r=fit(s,12);
        assertFalse(r.assessed);assertTrue(r.reason.contains("not distinguishable"));
    }

    @Test public void theJudgedMarkerNeverMovesItsOwnReference(){
        Alpha94MarkerMeasurement.Report rep=report(0);
        double[] a=LuminousAlignment.ringWithout(rep,6);
        Alpha94MarkerMeasurement.Report moved=report(0.02);   // the 6 itself badly out of place
        double[] b=LuminousAlignment.ringWithout(moved,6);
        for(int i=0;i<4;i++)assertEquals(a[i],b[i],1e-12);
        assertNotEquals(a[0],LuminousAlignment.ringWithout(moved,9)[0],1e-6);   // but it does enter other markers' rings
    }

    static Alpha94MarkerMeasurement.Report report(double sixShift){
        List<Alpha94MarkerMeasurement.Marker> ms=new ArrayList<>();
        for(ModelSpec.Marker sm:TestModels.gmt().ringMarkers()){
            Alpha94MarkerMeasurement.Marker m=new Alpha94MarkerMeasurement.Marker(sm.hour,sm.kind());m.spec=sm;m.usable=true;
            m.canonDx=0.001+(sm.hour==6?sixShift:0);m.canonDy=-0.0005;ms.add(m);
        }
        return new Alpha94MarkerMeasurement.Report(ms,new Alpha94MarkerMeasurement.Ring(),RPX);
    }
}
