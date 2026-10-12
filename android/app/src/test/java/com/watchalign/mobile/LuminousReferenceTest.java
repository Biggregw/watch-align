package com.watchalign.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class LuminousReferenceTest {
    @Test public void everyApplicableKeyHasItsOwnMultiWatchExperimentalInnerPolygon()throws Exception{
        for(String id:new String[]{"gmt_126710","submariner_124060","submariner_126610"}){
            ModelSpec model=ModelSpec.load(ModelSpec.directory(TestModels.assets()),id);
            LuminousReference ref=LuminousReference.load(ModelSpec.directory(TestModels.assets()),id);
            for(ModelSpec.Marker m:model.markers){double[][] p=ref.expected(m,new double[]{0,0,0,0});assertNotNull(id+" "+m.hour,p);
                assertTrue(ref.groups.get(LuminousReference.key(m))>=3);assertNotNull(MarkerPolygonMismatch.compare(p,p));
                double[][] outer=ExpectedMarkerOutline.polygon(m,ModelReference.load(ModelSpec.directory(TestModels.assets()),model),new double[]{0,0,0,0});
                assertTrue("Inner geometry must not reuse the outer polygon",MarkerPolygonMismatch.area(p)<MarkerPolygonMismatch.area(outer));
            }
        }
    }
    @Test public void unverifiedSingleWatchEntryCannotBecomeAReference()throws Exception{
        String json="{\"experimental\":true,\"model\":\"test\",\"shapes\":{\"nine\":{\"recorded_groups\":1,\"polygon\":[[0,0],[1,0],[0,1]]}}}";
        LuminousReference r=LuminousReference.load(path->new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"test");assertTrue(r.shapes.isEmpty());
    }
}
