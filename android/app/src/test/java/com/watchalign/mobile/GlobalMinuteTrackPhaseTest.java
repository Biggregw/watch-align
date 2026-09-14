package com.watchalign.mobile;
import static org.junit.Assert.*;import com.watchalign.mobile.qc.QcModuleResult;import org.junit.Test;
public class GlobalMinuteTrackPhaseTest {
 @Test public void recoversOneGlobalSixtyTickPhase(){double[] s=new double[1440];int phase=7,spacing=s.length/60;for(int k=0;k<60;k++){s[phase+k*spacing]=10;s[(phase+k*spacing+1)%s.length]=6;}GlobalMinuteTrackPhase.Result r=GlobalMinuteTrackPhase.estimate(s,QcModuleResult.Confidence.HIGH);assertTrue(r.available);assertEquals(phase*360.0/s.length,r.phaseDeg,.26);}
 @Test public void wrapsLatePeriodicPhaseToNearestTick(){double[] s=new double[1440];int phase=23,spacing=s.length/60;for(int k=0;k<60;k++)s[phase+k*spacing]=10;GlobalMinuteTrackPhase.Result r=GlobalMinuteTrackPhase.estimate(s,QcModuleResult.Confidence.HIGH);assertTrue(r.available);assertEquals(-0.25,r.phaseDeg,.01);assertEquals(-0.25,GlobalMinuteTrackPhase.normalizeSixDegreePhase(5.75),.0001);}
 @Test public void insufficientPeriodicSupportIsWithheld(){double[] s=new double[1440];for(int i=0;i<s.length;i++)s[i]=1;GlobalMinuteTrackPhase.Result r=GlobalMinuteTrackPhase.estimate(s,QcModuleResult.Confidence.HIGH);assertFalse(r.available);assertTrue(r.reason.contains("insufficient"));}
 @Test public void componentConfidenceIsCappedByRectification(){double[] s=new double[1440];for(int k=0;k<60;k++)s[3+k*24]=10;GlobalMinuteTrackPhase.Result r=GlobalMinuteTrackPhase.estimate(s,QcModuleResult.Confidence.MEDIUM);assertTrue(r.available);assertEquals(QcModuleResult.Confidence.MEDIUM,r.confidence);}
}
